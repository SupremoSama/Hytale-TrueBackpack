package com.supremosan.truebackpack.events;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFaceSupport;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Rotation3fc;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.supremosan.truebackpack.config.settings.TrueBackpackSettings;
import com.supremosan.truebackpack.data.BackpackContainerState;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.listener.BackpackArmorListener;
import com.supremosan.truebackpack.registries.BackpackRegistry;
import com.supremosan.truebackpack.util.BlockPlacementUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class BackpackDeathEvent extends DeathSystems.OnDeathSystem {
    private static final String EMPTY_BLOCK_ID = "Empty";

    private static final float PLACE_RADIUS_MIN = 1.5f;
    private static final float PLACE_RADIUS_MAX = 4.0f;
    private static final int MAX_ATTEMPTS_PER_BACKPACK = 24;
    private static final int SCAN_DEPTH = 16;

    private static final Random RANDOM = new Random();

    @Override
    public @Nonnull Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new com.hypixel.hytale.component.dependency.SystemDependency<>(
                com.hypixel.hytale.component.dependency.Order.BEFORE,
                com.supremosan.custominventory.inventory.ExtraEquipmentDeathSystem.class),
                new com.hypixel.hytale.component.dependency.SystemDependency<>(
                com.hypixel.hytale.component.dependency.Order.BEFORE, DeathSystems.DropPlayerDeathItems.class));
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (player.getGameMode() == GameMode.Creative) return;

        World world = player.getWorld();
        if (world == null) return;

        // Use the resolved death settings (same source as DropPlayerDeathItems), so we only touch
        // backpacks the game itself would drop.
        if (component.getItemsLossMode() == DeathConfig.ItemsLossMode.NONE) return;

        boolean keepOnDeath = TrueBackpackSettings.get().keepBackpacksOnDeath;

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        InventoryComponent.Armor armorComp = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Hotbar hotbarComp = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        String playerUuid = uuidComp != null ? uuidComp.getUuid().toString() : null;

        List<BackpackEntry> backpacks = collectAndRemoveBackpacks(component,
                armorComp, storageComp, hotbarComp, backpackComp, playerUuid,
                store.getComponent(ref, com.supremosan.custominventory.api.ExtraEquipment.TYPE));

        if (backpacks.isEmpty()) return;

        if (keepOnDeath && playerUuid != null) {
            List<BackpackDeathKeeper.Kept> kept = new ArrayList<>();
            for (BackpackEntry entry : backpacks) {
                kept.add(new BackpackDeathKeeper.Kept(entry.containerIndex(), entry.slot(), entry.keptItem()));
            }
            BackpackDeathKeeper.stash(playerUuid, kept);
            return;
        }

        if (!world.getGameplayConfig().getWorldConfig().isBlockPlacementAllowed()) {
            dropBackpackItems(backpacks, store, ref, commandBuffer);
            return;
        }

        int originX = (int) Math.floor(transform.getPosition().x);
        int originY = (int) Math.floor(transform.getPosition().y);
        int originZ = (int) Math.floor(transform.getPosition().z);

        for (BackpackEntry entry : backpacks) {
            int[] pos = findPlacementPosition(world, originX, originY, originZ);
            if (pos == null) {
                int fallbackY = findGroundY(world, originX, originY, originZ);
                if (fallbackY >= 0) {
                    placeBackpackBlock(world, entry, originX, fallbackY, originZ);
                }
                continue;
            }
            placeBackpackBlock(world, entry, pos[0], pos[1], pos[2]);
        }
    }

    private static boolean isEmpty(@Nullable BlockType blockType) {
        return blockType != null && !EMPTY_BLOCK_ID.equals(blockType.getId());
    }

    private static boolean isSolid(@Nullable BlockType blockType) {
        return isEmpty(blockType);
    }

    private static boolean hasUpSupport(@Nonnull World world, int x, int y, int z, @Nonnull BlockType blockType) {
        int rotationIndex = BlockPlacementUtil.getRotationIndex(world, x, y, z);
        Map<BlockFace, BlockFaceSupport[]> supporting = blockType.getSupporting(rotationIndex);
        return supporting != null && supporting.containsKey(BlockFace.UP);
    }

    @Nullable
    private int[] findPlacementPosition(@Nonnull World world, int originX, int originY, int originZ) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_BACKPACK; attempt++) {
            float angle = RANDOM.nextFloat() * 2f * (float) Math.PI;
            float radius = PLACE_RADIUS_MIN + RANDOM.nextFloat() * (PLACE_RADIUS_MAX - PLACE_RADIUS_MIN);

            int candidateX = originX + Math.round((float) Math.cos(angle) * radius);
            int candidateZ = originZ + Math.round((float) Math.sin(angle) * radius);

            int groundY = findGroundY(world, candidateX, originY, candidateZ);
            if (groundY < 0) continue;

            return new int[]{candidateX, groundY, candidateZ};
        }
        return null;
    }

    private int findGroundY(@Nonnull World world, int x, int startY, int z) {
        int searchTop = startY + SCAN_DEPTH;
        int searchBottom = Math.max(0, startY - SCAN_DEPTH);

        for (int y = searchTop; y >= searchBottom; y--) {
            BlockType floor = BlockPlacementUtil.getBlockType(world, x, y, z);
            BlockType place = BlockPlacementUtil.getBlockType(world, x, y + 1, z);

            if (!isSolid(floor) || isEmpty(place)) continue;
            if (!hasUpSupport(world, x, y, z, floor)) continue;

            return y + 1;
        }

        return -1;
    }

    private void placeBackpackBlock(
            @Nonnull World world,
            @Nonnull BackpackEntry entry,
            int x, int y, int z) {

        if (!BlockPlacementUtil.placeBlock(world, x, y, z, entry.blockId, Rotation.None, Rotation.None, Rotation.None)) return;

        Ref<ChunkStore> blockEntityRef = BlockModule.getBlockEntity(world, x, y, z);
        if (blockEntityRef == null || !blockEntityRef.isValid()) return;

        Store<ChunkStore> chunkStore = blockEntityRef.getStore();
        ItemContainerBlock containerBlock = chunkStore.getComponent(blockEntityRef, ItemContainerBlock.getComponentType());
        if (containerBlock == null) return;

        BackpackContainerState backpackState = chunkStore.getComponent(blockEntityRef, BackpackContainerState.getComponentType());
        if (backpackState != null) {
            backpackState.setTransmogSkin(entry.transmogSkin());
            backpackState.setUpgradeLevel(entry.upgradeLevel());
            backpackState.setCustomName(entry.customName());
            backpackState.setPaintColor(entry.paintColor());
        }

        BackpackRegistry.BackpackEntry regEntry = BackpackRegistry.getByBlock(entry.blockId());
        short baseCap = regEntry != null ? regEntry.capacity() : 20;
        short totalCapacity = (short) (baseCap + entry.upgradeLevel() * BackpackItemFactory.SLOTS_PER_UPGRADE_LEVEL);
        var info = chunkStore.getComponent(blockEntityRef, BlockModule.BlockStateInfo.getComponentType());
        com.supremosan.truebackpack.system.BackpackContainerSystem.resizeContainer(containerBlock, totalCapacity, () -> { if (info != null) info.markNeedsSaving(); });
        if (info != null) info.markNeedsSaving();

        List<ItemStack> contents = entry.contents;
        int capacity = containerBlock.getItemContainer().getCapacity();
        for (int i = 0; i < contents.size() && i < capacity; i++) {
            ItemStack content = contents.get(i);
            if (content != null && !content.isEmpty()) {
                containerBlock.getItemContainer().setItemStackForSlot((short) i, content);
            }
        }
    }

    private void dropBackpackItems(
            @Nonnull List<BackpackEntry> backpacks,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        List<ItemStack> items = new ArrayList<>();
        for (BackpackEntry entry : backpacks) {
            ItemStack item = BackpackItemFactory.createFromContainer(
                    entry.blockId,
                    entry.contents,
                    entry.transmogSkin,
                    entry.upgradeLevel, entry.customName, entry.paintColor
            );
            if (item != null) {
                items.add(item);
            }
        }

        if (items.isEmpty()) return;

        Vector3d position = transform.getPosition();
        Vector3d dropPos = new Vector3d(position.x, position.y + 1, position.z);
        Rotation3fc rotation = new Rotation3f(0f, 0f, 0f);

        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                store, items, dropPos, rotation);

        if (holders.length > 0) {
            commandBuffer.addEntities(holders, AddReason.SPAWN);
        }
    }

    @Nonnull
    private List<BackpackEntry> collectAndRemoveBackpacks(
            @Nonnull DeathComponent death,
            @Nullable InventoryComponent.Armor armorComp,
            @Nullable InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Hotbar hotbarComp,
            @Nullable InventoryComponent.Backpack backpackComp,
            @Nullable String playerUuid,
            @Nullable com.supremosan.custominventory.api.ExtraEquipment equipment) {

        List<BackpackEntry> found = new ArrayList<>();

        ItemContainer[] containers = {
                equipment != null ? equipment.getInventory() : null,
                armorComp != null ? armorComp.getInventory() : null,
                storageComp != null ? storageComp.getInventory() : null,
                hotbarComp != null ? hotbarComp.getInventory() : null,
                backpackComp != null ? backpackComp.getInventory() : null
        };

        for (int containerIndex = 0; containerIndex < containers.length; containerIndex++) {
            ItemContainer container = containers[containerIndex];
            if (container == null) continue;
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack item = container.getItemStack(slot);
                if (item == null || item.isEmpty()) continue;
                if (BackpackArmorListener.getBackpackSize(item.getItemId()) <= 0) continue;
                if (!gameDropsOnDeath(death, item)) continue;

                BackpackRegistry.BackpackEntry registry = BackpackRegistry.getByItem(item.getItemId());
                if (registry == null || registry.blockId().isEmpty()) continue;

                List<ItemStack> contents;
                boolean isEquipped = equipment != null && container == equipment.getInventory()
                        && slot == com.supremosan.custominventory.api.ExtraEquipment.BACKPACK
                        && backpackComp != null
                        && playerUuid != null;

                if (isEquipped) {
                    ItemContainer bp = backpackComp.getInventory();
                    contents = new ArrayList<>(bp.getCapacity());
                    for (short i = 0; i < bp.getCapacity(); i++) {
                        contents.add(bp.getItemStack(i));
                    }
                    backpackComp.resize((short) 0, new ObjectArrayList<>());
                } else if (BackpackItemFactory.hasContents(item)) {
                    contents = BackpackItemFactory.loadContents(item);
                } else {
                    contents = List.of();
                }

                String transmogSkin = BackpackItemFactory.getTransmogSkin(item);
                int upgradeLevel = BackpackItemFactory.getUpgradeLevel(item);

                ItemStack keptItem = BackpackItemFactory.setEquipped(BackpackItemFactory.saveContents(item, contents), false);

                container.removeItemStackFromSlot(slot);
                found.add(new BackpackEntry(registry.blockId(), contents, transmogSkin, upgradeLevel, BackpackItemFactory.getCustomName(item), BackpackItemFactory.getPaintColor(item),
                        containerIndex, slot, keptItem));
            }
        }

        return found;
    }

    /** Mirrors DeathSystems.DropPlayerDeathItems / ExtraEquipmentDeathSystem: would the game drop this item? */
    private static boolean gameDropsOnDeath(@Nonnull DeathComponent death, @Nonnull ItemStack item) {
        return switch (death.getItemsLossMode()) {
            case ALL -> true;
            case CONFIGURED -> death.getItemsAmountLossPercentage() > 0.0 && item.getItem().dropsOnDeath();
            case NONE -> false;
        };
    }

    @Override
    public void onComponentRemoved(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        BackpackDeathKeeper.restore(ref, commandBuffer);
    }

    /** Containers in the same order as {@link #collectAndRemoveBackpacks}; used to put kept backpacks back. */
    @Nonnull
    static ItemContainer[] playerContainers(@Nonnull Ref<EntityStore> ref, @Nonnull com.hypixel.hytale.component.ComponentAccessor<EntityStore> accessor) {
        var equipment = accessor.getComponent(ref, com.supremosan.custominventory.api.ExtraEquipment.TYPE);
        var armor = accessor.getComponent(ref, InventoryComponent.Armor.getComponentType());
        var storage = accessor.getComponent(ref, InventoryComponent.Storage.getComponentType());
        var hotbar = accessor.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        return new ItemContainer[]{
                equipment != null ? equipment.getInventory() : null,
                armor != null ? armor.getInventory() : null,
                storage != null ? storage.getInventory() : null,
                hotbar != null ? hotbar.getInventory() : null,
                null // backpack grid: never restore a backpack into a backpack
        };
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    private record BackpackEntry(String blockId, List<ItemStack> contents, @Nullable String transmogSkin,
                                 int upgradeLevel, @Nullable String customName, @Nullable String paintColor,
                                 int containerIndex, short slot, ItemStack keptItem) {}
}
