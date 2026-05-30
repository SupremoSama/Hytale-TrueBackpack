package com.supremosan.truebackpack.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFaceSupport;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.registries.BackpackRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BackpackInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<BackpackInteraction> CODEC = BuilderCodec
            .builder(BackpackInteraction.class,
                    BackpackInteraction::new,
                    SimpleInstantInteraction.CODEC)
            .build();

    private static final short STORAGE_SLOT = 0;

    @Override
    protected void firstRun(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {

        if (type != InteractionType.Use && type != InteractionType.Primary && type != InteractionType.Secondary) {
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

        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null || heldItem.isEmpty()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        String itemId = heldItem.getItem().getId();
        BackpackRegistry.BackpackEntry entry = BackpackRegistry.getByItem(itemId);
        if (entry == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        InventoryComponent.Hotbar hotbar = store.getComponent(owningEntity, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        MovementStatesComponent movementStates = store.getComponent(owningEntity, MovementStatesComponent.getComponentType());
        boolean crouching = movementStates != null && movementStates.getMovementStates().crouching;

        World world = player.getWorld();
        if (world == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        BlockPosition targetBlock = context.getTargetBlock();

        if (type == InteractionType.Primary || type == InteractionType.Use) {
            handlePrimary(context, store, owningEntity, entry, heldItem, hotbar, world, targetBlock, crouching, type);
        } else {
            handleSecondary(context, store, owningEntity, entry, heldItem, hotbar, world, targetBlock, crouching, type);
        }
    }

    private void handlePrimary(
            @Nonnull InteractionContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> owningEntity,
            @Nonnull BackpackRegistry.BackpackEntry entry,
            @Nonnull ItemStack heldItem,
            @Nonnull InventoryComponent.Hotbar hotbar,
            @Nonnull World world,
            @Nullable BlockPosition targetBlock,
            boolean crouching,
            @Nonnull InteractionType type) {

        if (targetBlock != null && crouching) {
            Ref<ChunkStore> blockEntityRef = BlockModule.getBlockEntity(world, targetBlock.x, targetBlock.y, targetBlock.z);
            if (blockEntityRef != null && blockEntityRef.isValid() && hasItemContainer(blockEntityRef)) {
                handleChestTransfer(InteractionType.Primary, context, entry, heldItem, hotbar, world, targetBlock, type == InteractionType.Use);
                return;
            }
        }

        if (type == InteractionType.Use && !crouching) {
            if (targetBlock != null) {
                Ref<ChunkStore> blockEntityRef = BlockModule.getBlockEntity(world, targetBlock.x, targetBlock.y, targetBlock.z);
                if (blockEntityRef != null && blockEntityRef.isValid() && hasItemContainer(blockEntityRef)) {
                    return;
                }
            }
            handleEquipFromHotbar(context, store, owningEntity, heldItem, hotbar);
            return;
        }

        context.getState().state = InteractionState.Skip;
    }

    private void handleEquipFromHotbar(
            @Nonnull InteractionContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> owningEntity,
            @Nonnull ItemStack heldItem,
            @Nonnull InventoryComponent.Hotbar hotbar) {

        InventoryComponent.Storage storageComp = store.getComponent(owningEntity, InventoryComponent.Storage.getComponentType());
        if (storageComp == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        ItemContainer targetContainer = storageComp.getInventory();
        ItemStack existing = targetContainer.getItemStack(STORAGE_SLOT);
        short heldSlot = context.getHeldItemSlot();

        targetContainer.setItemStackForSlot(STORAGE_SLOT, heldItem);
        hotbar.getInventory().setItemStackForSlot(heldSlot, (existing != null && !existing.isEmpty()) ? existing : ItemStack.EMPTY);

        context.getState().state = InteractionState.Finished;
    }

    private void handleSecondary(
            @Nonnull InteractionContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> owningEntity,
            @Nonnull BackpackRegistry.BackpackEntry entry,
            @Nonnull ItemStack heldItem,
            @Nonnull InventoryComponent.Hotbar hotbar,
            @Nonnull World world,
            @Nullable BlockPosition targetBlock,
            boolean crouching,
            @Nonnull InteractionType type) {

        if (targetBlock == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (crouching) {
            Ref<ChunkStore> blockEntityRef = BlockModule.getBlockEntity(world, targetBlock.x, targetBlock.y, targetBlock.z);
            if (blockEntityRef != null && blockEntityRef.isValid() && hasItemContainer(blockEntityRef)) {
                handleChestTransfer(InteractionType.Secondary, context, entry, heldItem, hotbar, world, targetBlock, type == InteractionType.Use);
                return;
            }
        }

        handlePlace(context, entry, heldItem, hotbar, store, owningEntity, world, targetBlock);
    }

    private boolean hasItemContainer(@Nonnull Ref<ChunkStore> blockEntityRef) {
        return blockEntityRef.getStore().getComponent(blockEntityRef, ItemContainerBlock.getComponentType()) != null;
    }

    private boolean isBackpack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return BackpackRegistry.getByItem(stack.getItem().getId()) != null;
    }

    private void handlePlace(
            @Nonnull InteractionContext context,
            @Nonnull BackpackRegistry.BackpackEntry entry,
            @Nonnull ItemStack heldItem,
            @Nonnull InventoryComponent.Hotbar hotbar,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> owningEntity,
            @Nonnull World world,
            @Nonnull BlockPosition targetBlock) {

        if (Objects.equals(entry.blockId(), "")) {
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

        int placeX = targetBlock.x;
        int placeY = targetBlock.y + 1;
        int placeZ = targetBlock.z;

        BlockType occupying = world.getBlockType(placeX, placeY, placeZ);
        if (occupying != null && occupying.getMaterial() != BlockMaterial.Empty) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (BackpackItemFactory.hasInstanceId(heldItem)) {
            heldItem = BackpackItemFactory.createBackpackInstance(heldItem);
            hotbar.getInventory().setItemStackForSlot(context.getHeldItemSlot(), heldItem);
        }

        Rotation yaw = Rotation.None;
        TransformComponent transform = store.getComponent(owningEntity, TransformComponent.getComponentType());
        if (transform != null) {
            Rotation3f rotation = transform.getRotation();
            float radians = rotation.yaw();
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

        Ref<ChunkStore> blockEntityRef = BlockModule.getBlockEntity(world, placeX, placeY, placeZ);
        if (blockEntityRef == null || !blockEntityRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Store<ChunkStore> chunkStore = blockEntityRef.getStore();
        ItemContainerBlock containerBlock = chunkStore.getComponent(blockEntityRef, ItemContainerBlock.getComponentType());
        if (containerBlock == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        List<ItemStack> contents = BackpackItemFactory.loadContents(heldItem);
        for (int i = 0; i < contents.size() && i < containerBlock.getItemContainer().getCapacity(); i++) {
            ItemStack content = contents.get(i);
            if (content != null && !content.isEmpty()) {
                containerBlock.getItemContainer().setItemStackForSlot((short) i, content);
            }
        }

        hotbar.getInventory().removeItemStackFromSlot(context.getHeldItemSlot(), 1, true, false);
    }

    private void handleChestTransfer(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull BackpackRegistry.BackpackEntry entry,
            @Nonnull ItemStack heldItem,
            @Nonnull InventoryComponent.Hotbar hotbar,
            @Nonnull World world,
            @Nonnull BlockPosition targetBlock,
            boolean matchOnly) {

        if (BackpackItemFactory.hasInstanceId(heldItem)) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref<ChunkStore> blockEntityRef = BlockModule.getBlockEntity(world, targetBlock.x, targetBlock.y, targetBlock.z);
        if (blockEntityRef == null || !blockEntityRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Store<ChunkStore> chunkStore = blockEntityRef.getStore();
        ItemContainerBlock containerBlock = chunkStore.getComponent(blockEntityRef, ItemContainerBlock.getComponentType());
        if (containerBlock == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        ItemContainer chestContainer = containerBlock.getItemContainer();
        List<ItemStack> backpackContents = BackpackItemFactory.loadContents(heldItem);
        int backpackCapacity = entry.capacity();

        ItemStack updatedBackpackItem;

        List<ItemStack> updatedBackpack = new ArrayList<>(backpackContents);
        if (type == InteractionType.Primary) {
            for (int i = 0; i < updatedBackpack.size(); i++) {
                ItemStack item = updatedBackpack.get(i);
                if (item == null || item.isEmpty()) continue;
                if (isBackpack(item)) continue;
                updatedBackpack.set(i, insertIntoContainer(chestContainer, item, !matchOnly));
            }
            while (updatedBackpack.size() < backpackCapacity) updatedBackpack.add(null);
        } else {
            while (updatedBackpack.size() < backpackCapacity) updatedBackpack.add(null);
            for (short slot = 0; slot < chestContainer.getCapacity(); slot++) {
                ItemStack item = chestContainer.getItemStack(slot);
                if (item == null || item.isEmpty()) continue;
                if (isBackpack(item)) continue;
                chestContainer.setItemStackForSlot(slot, insertIntoList(updatedBackpack, item, backpackCapacity, !matchOnly));
            }
        }
        updatedBackpackItem = BackpackItemFactory.saveContents(heldItem, updatedBackpack);

        hotbar.getInventory().setItemStackForSlot(context.getHeldItemSlot(), updatedBackpackItem);
    }

    private @Nullable ItemStack insertIntoContainer(@Nonnull ItemContainer container, @Nonnull ItemStack toInsert, boolean fillEmpty) {
        int remaining = toInsert.getQuantity();
        int maxStack = toInsert.getItem().getMaxStack();

        for (short slot = 0; slot < container.getCapacity() && remaining > 0; slot++) {
            ItemStack existing = container.getItemStack(slot);
            if (existing != null && !existing.isEmpty() && existing.getItemId().equals(toInsert.getItemId())) {
                int space = maxStack - existing.getQuantity();
                if (space <= 0) continue;
                int transfer = Math.min(space, remaining);
                container.setItemStackForSlot(slot, existing.withQuantity(existing.getQuantity() + transfer));
                remaining -= transfer;
            }
        }

        if (fillEmpty) {
            for (short slot = 0; slot < container.getCapacity() && remaining > 0; slot++) {
                ItemStack existing = container.getItemStack(slot);
                if (existing == null || existing.isEmpty()) {
                    int transfer = Math.min(maxStack, remaining);
                    container.setItemStackForSlot(slot, toInsert.withQuantity(transfer));
                    remaining -= transfer;
                }
            }
        }

        return remaining <= 0 ? ItemStack.EMPTY : toInsert.withQuantity(remaining);
    }

    private @Nullable ItemStack insertIntoList(@Nonnull List<ItemStack> list, @Nonnull ItemStack toInsert, int capacity, boolean fillEmpty) {
        int remaining = toInsert.getQuantity();
        int maxStack = toInsert.getItem().getMaxStack();

        for (int i = 0; i < Math.min(list.size(), capacity) && remaining > 0; i++) {
            ItemStack existing = list.get(i);
            if (existing != null && !existing.isEmpty() && existing.getItemId().equals(toInsert.getItemId())) {
                int space = maxStack - existing.getQuantity();
                if (space <= 0) continue;
                int transfer = Math.min(space, remaining);
                list.set(i, existing.withQuantity(existing.getQuantity() + transfer));
                remaining -= transfer;
            }
        }

        if (fillEmpty) {
            for (int i = 0; i < Math.min(list.size(), capacity) && remaining > 0; i++) {
                ItemStack existing = list.get(i);
                if (existing == null || existing.isEmpty()) {
                    int transfer = Math.min(maxStack, remaining);
                    list.set(i, toInsert.withQuantity(transfer));
                    remaining -= transfer;
                }
            }
        }

        return remaining <= 0 ? ItemStack.EMPTY : toInsert.withQuantity(remaining);
    }
}