package com.supremosan.truebackpack.events;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
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
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.listener.BackpackArmorListener;
import com.supremosan.truebackpack.registries.BackpackRegistry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NonNull;

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
    public @NonNull Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
                new SystemDependency<>(Order.BEFORE, DeathSystems.DropPlayerDeathItems.class)
        );
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

        DeathConfig deathConfig = world.getDeathConfig();
        if (deathConfig.getItemsLossMode() == DeathConfig.ItemsLossMode.NONE) return;

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        InventoryComponent.Armor armorComp = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Hotbar hotbarComp = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        String playerUuid = uuidComp != null ? uuidComp.getUuid().toString() : null;

        List<BackpackEntry> backpacks = collectAndRemoveBackpacks(
                armorComp, storageComp, hotbarComp, backpackComp, playerUuid);

        if (backpacks.isEmpty()) return;

        int originX = (int) Math.floor(transform.getPosition().getX());
        int originY = (int) Math.floor(transform.getPosition().getY());
        int originZ = (int) Math.floor(transform.getPosition().getZ());

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
        int rotationIndex = world.getBlockRotationIndex(x, y, z);
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
            BlockType floor = world.getBlockType(x, y, z);
            BlockType place = world.getBlockType(x, y + 1, z);

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

        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return;

        chunk.placeBlock(x, y, z, entry.blockId, Rotation.None, Rotation.None, Rotation.None);

        Ref<ChunkStore> blockEntityRef = BlockModule.getBlockEntity(world, x, y, z);
        if (blockEntityRef == null || !blockEntityRef.isValid()) return;

        Store<ChunkStore> chunkStore = blockEntityRef.getStore();
        ItemContainerBlock containerBlock = chunkStore.getComponent(blockEntityRef, ItemContainerBlock.getComponentType());
        if (containerBlock == null) return;

        List<ItemStack> contents = entry.contents;
        int capacity = containerBlock.getItemContainer().getCapacity();
        for (int i = 0; i < contents.size() && i < capacity; i++) {
            ItemStack content = contents.get(i);
            if (content != null && !content.isEmpty()) {
                containerBlock.getItemContainer().setItemStackForSlot((short) i, content);
            }
        }
    }

    @Nonnull
    private List<BackpackEntry> collectAndRemoveBackpacks(
            @Nullable InventoryComponent.Armor armorComp,
            @Nullable InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Hotbar hotbarComp,
            @Nullable InventoryComponent.Backpack backpackComp,
            @Nullable String playerUuid) {

        List<BackpackEntry> found = new ArrayList<>();

        ItemContainer[] containers = {
                armorComp != null ? armorComp.getInventory() : null,
                storageComp != null ? storageComp.getInventory() : null,
                hotbarComp != null ? hotbarComp.getInventory() : null,
                backpackComp != null ? backpackComp.getInventory() : null
        };

        for (ItemContainer container : containers) {
            if (container == null) continue;
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack item = container.getItemStack(slot);
                if (item == null || item.isEmpty()) continue;
                if (BackpackArmorListener.getBackpackSize(item.getItemId()) <= 0) continue;

                BackpackRegistry.BackpackEntry registry = BackpackRegistry.getByItem(item.getItemId());
                if (registry == null || registry.blockId().isEmpty()) continue;

                List<ItemStack> contents;
                boolean isEquipped = BackpackItemFactory.isEquipped(item)
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

                container.removeItemStackFromSlot(slot);
                found.add(new BackpackEntry(registry.blockId(), contents));
            }
        }

        return found;
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    private record BackpackEntry(String blockId, List<ItemStack> contents) {
        private BackpackEntry(@Nonnull String blockId, @Nonnull List<ItemStack> contents) {
            this.blockId = blockId;
            this.contents = contents;
        }
    }
}