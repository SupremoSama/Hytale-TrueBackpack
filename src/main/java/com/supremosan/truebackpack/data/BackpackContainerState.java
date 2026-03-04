package com.supremosan.truebackpack.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.registries.BackpackBlockRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BackpackContainerState extends ItemContainerState {
    private static final KeyedCodec<String> BLOCK_ID_CODEC =
            new KeyedCodec<>("BackpackBlockId", Codec.STRING);

    public static final BuilderCodec<BackpackContainerState> CODEC =
            ((BuilderCodec.Builder<BackpackContainerState>)
                    ((BuilderCodec.Builder<BackpackContainerState>)
                            ((BuilderCodec.Builder<BackpackContainerState>)
                                    ((BuilderCodec.Builder<BackpackContainerState>)
                                            BuilderCodec.builder(
                                                            BackpackContainerState.class,
                                                            BackpackContainerState::new,
                                                            BlockState.BASE_CODEC
                                                    )
                                                    .addField(new KeyedCodec<>("Custom", Codec.BOOLEAN),
                                                            (state, o) -> state.custom = o,
                                                            state -> state.custom))
                                            .addField(new KeyedCodec<>("AllowViewing", Codec.BOOLEAN),
                                                    (state, o) -> state.allowViewing = o,
                                                    state -> state.allowViewing))
                                    .addField(new KeyedCodec<>("Droplist", Codec.STRING),
                                            (state, o) -> state.droplist = o,
                                            state -> state.droplist))
                            .addField(new KeyedCodec<>("ItemContainer", SimpleItemContainer.CODEC),
                                    (state, o) -> state.itemContainer = o,
                                    state -> state.itemContainer))
                    .addField(
                            BLOCK_ID_CODEC,
                            (state, id) -> state.cachedBlockId = id,
                            state -> state.cachedBlockId
                    )
                    .build();

    @Nullable
    private String cachedBlockId;

    @Override
    public boolean initialize(@Nonnull BlockType blockType) {
        String incomingId = blockType.getId();

        if (incomingId != null && BackpackBlockRegistry.getByBlock(incomingId) != null) {
            this.cachedBlockId = incomingId;
        }

        BackpackBlockRegistry.BackpackBlockEntry entry = BackpackBlockRegistry.getByBlock(this.cachedBlockId);
        short capacity = entry != null ? entry.capacity() : 20;

        if (this.itemContainer == null || this.itemContainer.getCapacity() != capacity) {
            List<ItemStack> remainder = new ArrayList<>();
            this.itemContainer = ItemContainer.ensureContainerCapacity(
                    this.itemContainer, capacity, SimpleItemContainer::new, remainder);
        }

        this.itemContainer.registerChangeEvent(EventPriority.LAST, this::onItemChange);

        for (short slot = 0; slot < this.itemContainer.getCapacity(); slot++) {
            this.itemContainer.setSlotFilter(FilterActionType.ADD, slot,
                    (_, _, _, item) ->
                            item == null
                                    || item.isEmpty()
                                    || BackpackBlockRegistry.getByItem(item.getItem().getId()) == null);
        }

        this.markNeedsSave();
        return true;
    }

    @Override
    public void onDestroy() {
        super.getWindows().clear();

        WorldChunk chunk = this.getChunk();
        World world = chunk.getWorld();
        Store<EntityStore> store = world.getEntityStore().getStore();

        List<ItemStack> contents = new ArrayList<>();
        for (short i = 0; i < this.itemContainer.getCapacity(); i++) {
            ItemStack stack = this.itemContainer.getItemStack(i);
            contents.add((stack != null && !stack.isEmpty()) ? stack : null);
        }

        this.itemContainer.dropAllItemStacks();

        ItemStack backpackItem =
                BackpackItemFactory.createFromContainer(this.cachedBlockId, contents);

        if (backpackItem == null) return;

        Vector3d dropPosition = this.getBlockPosition().toVector3d().add(0.5, 0.0, 0.5);

        List<ItemStack> toDrop = new ArrayList<>();
        toDrop.add(backpackItem);

        Holder<EntityStore>[] itemEntityHolders =
                ItemComponent.generateItemDrops(
                        store,
                        toDrop,
                        dropPosition,
                        Vector3f.ZERO
                );

        if (itemEntityHolders.length > 0) {
            world.execute(() ->
                    store.addEntities(itemEntityHolders, AddReason.SPAWN)
            );
        }
    }
}