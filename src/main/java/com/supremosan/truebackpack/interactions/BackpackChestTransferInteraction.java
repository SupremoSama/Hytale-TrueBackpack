package com.supremosan.truebackpack.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.BlockPosition;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.registries.BackpackRegistry;
import org.jspecify.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BackpackChestTransferInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<BackpackChestTransferInteraction> CODEC = BuilderCodec
            .builder(BackpackChestTransferInteraction.class,
                    BackpackChestTransferInteraction::new,
                    SimpleInstantInteraction.CODEC)
            .build();

    @Override
    protected void firstRun(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {

        if (type != InteractionType.Primary && type != InteractionType.Secondary) {
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

        MovementStatesComponent movementStatesComponent = store.getComponent(owningEntity, MovementStatesComponent.getComponentType());
        if (movementStatesComponent == null || !movementStatesComponent.getMovementStates().crouching) {
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

        if (!BackpackItemFactory.hasInstanceId(heldItem)) {
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

        if (type == InteractionType.Primary) {
            transferBackpackToChest(backpackContents, chestContainer, backpackCapacity, heldItem, context);
        } else {
            transferChestToBackpack(chestContainer, backpackContents, backpackCapacity, heldItem, context);
        }
    }

    private void transferBackpackToChest(
            @Nonnull List<ItemStack> backpackContents,
            @Nonnull ItemContainer chestContainer,
            int backpackCapacity,
            @Nonnull ItemStack heldItem,
            @Nonnull InteractionContext context) {

        List<ItemStack> updatedBackpack = new ArrayList<>(backpackContents);

        for (int i = 0; i < updatedBackpack.size(); i++) {
            ItemStack item = updatedBackpack.get(i);
            if (item == null || item.isEmpty()) continue;

            ItemStack remainder = insertIntoContainer(chestContainer, item);
            updatedBackpack.set(i, remainder);
        }

        while (updatedBackpack.size() < backpackCapacity) {
            updatedBackpack.add(null);
        }

        ItemStack updatedBackpackItem = BackpackItemFactory.saveContents(heldItem, updatedBackpack);
        Objects.requireNonNull(context.getOwningEntity().getStore()
                        .getComponent(context.getOwningEntity(),
                                InventoryComponent.Hotbar.getComponentType()))
                .getInventory()
                .setItemStackForSlot(context.getHeldItemSlot(), updatedBackpackItem);
    }

    private void transferChestToBackpack(
            @Nonnull ItemContainer chestContainer,
            @Nonnull List<ItemStack> backpackContents,
            int backpackCapacity,
            @Nonnull ItemStack heldItem,
            @Nonnull InteractionContext context) {

        List<ItemStack> updatedBackpack = new ArrayList<>(backpackContents);

        while (updatedBackpack.size() < backpackCapacity) {
            updatedBackpack.add(null);
        }

        for (short slot = 0; slot < chestContainer.getCapacity(); slot++) {
            ItemStack item = chestContainer.getItemStack(slot);
            if (item == null || item.isEmpty()) continue;

            ItemStack remainder = insertIntoList(updatedBackpack, item, backpackCapacity);
            chestContainer.setItemStackForSlot(slot, remainder);
        }

        ItemStack updatedBackpackItem = BackpackItemFactory.saveContents(heldItem, updatedBackpack);
        Objects.requireNonNull(context.getOwningEntity().getStore()
                        .getComponent(context.getOwningEntity(),
                                InventoryComponent.Hotbar.getComponentType()))
                .getInventory()
                .setItemStackForSlot(context.getHeldItemSlot(), updatedBackpackItem);
    }

    private @Nullable ItemStack insertIntoContainer(@Nonnull ItemContainer container, @Nonnull ItemStack toInsert) {
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

        for (short slot = 0; slot < container.getCapacity() && remaining > 0; slot++) {
            ItemStack existing = container.getItemStack(slot);
            if (existing == null || existing.isEmpty()) {
                int transfer = Math.min(maxStack, remaining);
                container.setItemStackForSlot(slot, toInsert.withQuantity(transfer));
                remaining -= transfer;
            }
        }

        if (remaining <= 0) return ItemStack.EMPTY;
        return toInsert.withQuantity(remaining);
    }

    private @Nullable ItemStack insertIntoList(@Nonnull List<ItemStack> list, @Nonnull ItemStack toInsert, int capacity) {
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

        for (int i = 0; i < Math.min(list.size(), capacity) && remaining > 0; i++) {
            ItemStack existing = list.get(i);
            if (existing == null || existing.isEmpty()) {
                int transfer = Math.min(maxStack, remaining);
                list.set(i, toInsert.withQuantity(transfer));
                remaining -= transfer;
            }
        }

        if (remaining <= 0) return ItemStack.EMPTY;
        return toInsert.withQuantity(remaining);
    }
}