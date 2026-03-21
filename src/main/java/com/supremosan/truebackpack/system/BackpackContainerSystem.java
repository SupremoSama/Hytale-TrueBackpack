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
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.data.BackpackContainerState;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.registries.BackpackRegistry;

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

        short capacity = entry.capacity();
        for (short slot = 0; slot < capacity; slot++) {
            itemContainerBlock.getItemContainer().setSlotFilter(FilterActionType.ADD, slot,
                    (_, _, _, item) ->
                            item == null
                                    || item.isEmpty()
                                    || BackpackRegistry.getByItem(item.getItem().getId()) == null);

            itemContainerBlock.getItemContainer().setSlotFilter(FilterActionType.DROP, slot,
                    (_, _, _, _) -> false);
        }
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

        ItemStack backpackItem = BackpackItemFactory.createFromContainer(blockId, contents);
        if (backpackItem == null) return;

        Ref<ChunkStore> chunkRef = blockStateInfo.getChunkRef();
        if (!chunkRef.isValid()) return;

        BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) return;

        int index = blockStateInfo.getIndex();
        int x = ChunkUtil.xFromBlockInColumn(index);
        int y = ChunkUtil.yFromBlockInColumn(index);
        int z = ChunkUtil.zFromBlockInColumn(index);

        World world = ((ChunkStore) store.getExternalData()).getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();

        Vector3d dropPosition = new Vector3d(
                ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), x) + 0.5,
                y,
                ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), z) + 0.5
        );

        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                entityStore,
                List.of(backpackItem),
                dropPosition,
                Vector3f.ZERO
        );

        if (holders.length > 0) {
            world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
        }
    }

    @Nullable
    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }
}