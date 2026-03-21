package com.supremosan.truebackpack.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.supremosan.truebackpack.registries.BackpackRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BackpackContainerState implements Component<ChunkStore> {

    private static ComponentType<ChunkStore, BackpackContainerState> componentType;

    public static final BuilderCodec<BackpackContainerState> CODEC = BuilderCodec
            .builder(BackpackContainerState.class, BackpackContainerState::new)
            .addField(new KeyedCodec<>("Custom", Codec.BOOLEAN),
                    (state, o) -> state.custom = o,
                    state -> state.custom)
            .addField(new KeyedCodec<>("AllowViewing", Codec.BOOLEAN),
                    (state, o) -> state.allowViewing = o,
                    state -> state.allowViewing)
            .addField(new KeyedCodec<>("Droplist", Codec.STRING),
                    (state, o) -> state.droplist = o,
                    state -> state.droplist)
            .addField(new KeyedCodec<>("ItemContainer", SimpleItemContainer.CODEC),
                    (state, o) -> state.itemContainer = o,
                    state -> state.itemContainer)
            .addField(new KeyedCodec<>("Capacity", Codec.SHORT),
                    (state, o) -> state.capacity = o,
                    state -> state.capacity)
            .addField(new KeyedCodec<>("BackpackBlockId", Codec.STRING),
                    (state, o) -> state.cachedBlockId = o,
                    state -> state.cachedBlockId)
            .build();

    @Nullable
    private String cachedBlockId;
    private boolean custom;
    private boolean allowViewing;
    @Nullable
    private String droplist;
    @Nullable
    private SimpleItemContainer itemContainer;
    private short capacity = 20;

    public BackpackContainerState() {
    }

    public BackpackContainerState(BackpackContainerState other) {
        this.cachedBlockId = other.cachedBlockId;
        this.custom = other.custom;
        this.allowViewing = other.allowViewing;
        this.droplist = other.droplist;
        this.capacity = other.capacity;
        this.itemContainer = other.itemContainer != null ? other.itemContainer.clone() : null;
    }

    public static void setComponentType(ComponentType<ChunkStore, BackpackContainerState> type) {
        componentType = type;
    }

    public static ComponentType<ChunkStore, BackpackContainerState> getComponentType() {
        return componentType;
    }

    @Nullable
    public String getCachedBlockId() {
        return cachedBlockId;
    }

    @Nonnull
    @Override
    public Component<ChunkStore> clone() {
        return new BackpackContainerState(this);
    }
}