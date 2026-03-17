package com.supremosan.truebackpack.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFaceSupport;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.registries.BackpackRegistry;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

@SuppressWarnings("removal")
public class BackpackPlaceInteraction extends SimpleInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final BuilderCodec<BackpackPlaceInteraction> CODEC = BuilderCodec
            .builder(BackpackPlaceInteraction.class,
                    BackpackPlaceInteraction::new,
                    SimpleInteraction.CODEC)
            .build();

    @Override
    protected void tick0(
            boolean firstRun,
            float time,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {

        super.tick0(firstRun, time, type, context, cooldownHandler);

        if (!firstRun) {
            return;
        }

        if (type != InteractionType.Use) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref<EntityStore> owningEntity = context.getOwningEntity();
        Store<EntityStore> store = owningEntity.getStore();
        Player player = store.getComponent(owningEntity, Player.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        BlockPosition targetBlock = context.getTargetBlock();
        if (targetBlock == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        BlockType supportBlockType = world.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);
        if (supportBlockType == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        int rotationIndex = world.getBlockRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);
        Map<BlockFace, BlockFaceSupport[]> supporting = supportBlockType.getSupporting(rotationIndex);
        if (supporting == null || !supporting.containsKey(BlockFace.UP)) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        String itemId = heldItem.getItem().getId();
        BackpackRegistry.BackpackEntry entry = BackpackRegistry.getByItem(itemId);
        if (entry == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (!BackpackItemFactory.hasInstanceId(heldItem)) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        int placeX = targetBlock.x;
        int placeY = targetBlock.y + 1;
        int placeZ = targetBlock.z;

        Rotation yaw = Rotation.None;
        TransformComponent transform = store.getComponent(owningEntity, TransformComponent.getComponentType());
        if (transform != null) {
            float radians = transform.getRotation().getY();
            float degrees = (float) Math.toDegrees(radians);
            float normalized = ((degrees % 360f) + 360f) % 360f;
            yaw = Rotation.closestOfDegrees(normalized);
        }

        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(placeX, placeZ));
        if (chunk == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        chunk.placeBlock(placeX, placeY, placeZ, entry.blockId(), yaw, Rotation.None, Rotation.None);

        BlockState placedState = world.getState(placeX, placeY, placeZ, false);
        if (!(placedState instanceof ItemContainerState containerState)) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        List<ItemStack> contents = BackpackItemFactory.loadContents(heldItem);
        for (int i = 0; i < contents.size() && i < containerState.getItemContainer().getCapacity(); i++) {
            ItemStack content = contents.get(i);
            if (content != null && !content.isEmpty()) {
                containerState.getItemContainer().setItemStackForSlot((short) i, content);
            }
        }

        player.getInventory().getHotbar()
                .removeItemStackFromSlot(context.getHeldItemSlot(), 1, true, false);
    }
}