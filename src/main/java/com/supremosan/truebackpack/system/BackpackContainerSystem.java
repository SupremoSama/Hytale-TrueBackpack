package com.supremosan.truebackpack.system;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Rotation3fc;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.data.BackpackContainerState;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.registries.BackpackRegistry;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BackpackContainerSystem extends RefSystem<ChunkStore> {

    private final ComponentType<ChunkStore, BackpackContainerState> backpackStateType;
    private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> blockStateInfoType;
    private final ComponentType<ChunkStore, ItemContainerBlock> itemContainerBlockType;
    private final Query<ChunkStore> query;

    public BackpackContainerSystem() {
        this.backpackStateType = BackpackContainerState.getComponentType();
        this.blockStateInfoType = BlockModule.BlockStateInfo.getComponentType();
        this.itemContainerBlockType = ItemContainerBlock.getComponentType();
        this.query = Query.and(backpackStateType, blockStateInfoType, itemContainerBlockType);
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> ref,
                              @Nonnull AddReason reason,
                              @Nonnull Store<ChunkStore> store,
                              @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        BackpackContainerState backpackState = commandBuffer.getComponent(ref, backpackStateType);
        ItemContainerBlock itemContainerBlock = commandBuffer.getComponent(ref, itemContainerBlockType);

        if (backpackState == null || itemContainerBlock == null) return;

        String blockId = backpackState.getCachedBlockId();
        BackpackRegistry.BackpackEntry entry = BackpackRegistry.getByBlock(blockId);
        if (entry == null) return;

        // Native initialization resizes to the block asset's base capacity. Keep the full
        // upgraded inventory aside so it cannot spill overflow items during chunk reload.
        backpackState.pendingContainer = itemContainerBlock.getItemContainer();
        itemContainerBlock.setItemContainer(new SimpleItemContainer(entry.capacity()));
    }

    @Override
    public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<ChunkStore>> getDependencies() {
        return java.util.Set.of(new com.hypixel.hytale.component.dependency.SystemDependency<>(
                com.hypixel.hytale.component.dependency.Order.BEFORE,
                com.hypixel.hytale.server.core.modules.block.system.ItemContainerSystems.OnAddedOrRemoved.class));
    }

    public static void configureContainer(SimpleItemContainer container) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            container.setSlotFilter(FilterActionType.ADD, slot,
                    (_, _, _, item) -> ItemStack.isEmpty(item) || !BackpackRegistry.isBackpack(item.getItemId()));
            container.setSlotFilter(FilterActionType.DROP, slot, (_, _, _, _) -> false);
        }
    }

    public static void resizeContainer(ItemContainerBlock block, short capacity, Runnable markDirty) {
        SimpleItemContainer old = block.getItemContainer();
        if (old.getCapacity() != capacity) {
            // Never silently discard filled overflow slots if a server lowers capacities.
            for (short slot = capacity; slot < old.getCapacity(); slot++)
                if (!ItemStack.isEmpty(old.getItemStack(slot))) { capacity = old.getCapacity(); break; }
            SimpleItemContainer resized = new SimpleItemContainer(capacity);
            for (short slot = 0; slot < Math.min(old.getCapacity(), capacity); slot++)
                resized.setItemStackForSlot(slot, old.getItemStack(slot));
            block.setItemContainer(resized);
            resized.registerChangeEvent(com.hypixel.hytale.event.EventPriority.LAST, _ -> markDirty.run());
        }
        configureContainer(block.getItemContainer());
    }

    public static final class AfterNativeContainerSetup extends RefSystem<ChunkStore> {
        @Override public Query<ChunkStore> getQuery() {
            return Query.and(BackpackContainerState.getComponentType(), BlockModule.BlockStateInfo.getComponentType(), ItemContainerBlock.getComponentType());
        }
        @Override public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<ChunkStore>> getDependencies() {
            return java.util.Set.of(new com.hypixel.hytale.component.dependency.SystemDependency<>(
                    com.hypixel.hytale.component.dependency.Order.AFTER,
                    com.hypixel.hytale.server.core.modules.block.system.ItemContainerSystems.OnAddedOrRemoved.class));
        }
        @Override public void onEntityAdded(Ref<ChunkStore> ref, AddReason reason, Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {
            var state = cb.getComponent(ref, BackpackContainerState.getComponentType());
            var block = cb.getComponent(ref, ItemContainerBlock.getComponentType());
            var info = cb.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
            var entry = BackpackRegistry.getByBlock(state.getCachedBlockId());
            if (entry == null || state.pendingContainer == null) return;
            block.setItemContainer(state.pendingContainer);
            state.pendingContainer = null;
            short capacity = (short)(entry.capacity() + state.getUpgradeLevel() * BackpackItemFactory.SLOTS_PER_UPGRADE_LEVEL);
            resizeContainer(block, capacity, info::markNeedsSaving);
            block.getItemContainer().registerChangeEvent(com.hypixel.hytale.event.EventPriority.LAST, _ -> info.markNeedsSaving());
        }
        @Override public void onEntityRemove(Ref<ChunkStore> ref, RemoveReason reason, Store<ChunkStore> store, CommandBuffer<ChunkStore> cb) {}
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref,
                               @Nonnull RemoveReason reason,
                               @Nonnull Store<ChunkStore> store,
                               @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        if (reason == RemoveReason.UNLOAD) return;

        BackpackContainerState backpackState = commandBuffer.getComponent(ref, backpackStateType);
        ItemContainerBlock itemContainerBlock = commandBuffer.getComponent(ref, itemContainerBlockType);
        BlockModule.BlockStateInfo blockStateInfo = commandBuffer.getComponent(ref, blockStateInfoType);

        if (backpackState == null || itemContainerBlock == null || blockStateInfo == null) return;

        String blockId = backpackState.getCachedBlockId();
        if (blockId == null) return;

        BackpackRegistry.BackpackEntry entry = BackpackRegistry.getByBlock(blockId);
        if (entry == null) return;

        List<ItemStack> contents = new ArrayList<>();
        short capacity = itemContainerBlock.getItemContainer().getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = itemContainerBlock.getItemContainer().getItemStack(i);
            contents.add((stack != null && !stack.isEmpty()) ? stack : null);
        }

        ItemStack backpackItem = BackpackItemFactory.createFromContainer(
                blockId,
                contents,
                backpackState.getTransmogSkin(),
                backpackState.getUpgradeLevel(), backpackState.getCustomName(), backpackState.getPaintColor()
        );
        if (backpackItem == null) return;

        Vector3i worldPos = new Vector3i();
        if (!blockStateInfo.fillWorldPos(commandBuffer, worldPos)) return;

        World world = store.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();

        Vector3d dropPosition = new Vector3d(
                worldPos.x() + 0.5,
                worldPos.y(),
                worldPos.z() + 0.5
        );

        Rotation3fc rotation = new Rotation3f(0f, 0f, 0f);

        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                entityStore,
                List.of(backpackItem),
                dropPosition,
                rotation
        );

        if (holders.length > 0) {
            // Our serialized backpack owns the items now. Native removal must see an empty container.
            itemContainerBlock.getItemContainer().clear();
            world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
        }
    }

    @Nullable
    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }
}