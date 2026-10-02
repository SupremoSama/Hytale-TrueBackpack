package com.supremosan.truebackpack.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.DelegateItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SortType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/** Inventory operations for the workbench's live inventory panels. Call on the player's world thread. */
public final class BackpackWorkbenchInventory {
    public enum Section {
        STORAGE, HOTBAR, ARMOR, UTILITY
    }

    private BackpackWorkbenchInventory() {}

    @Nullable
    public static ItemContainer container(Ref<EntityStore> ref, Store<EntityStore> store, Section section) {
        if (ref == null || !ref.isValid() || store == null || section == null) return null;

        InventoryComponent component = switch (section) {
            case STORAGE -> store.getComponent(ref, InventoryComponent.Storage.getComponentType());
            case HOTBAR -> store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
            case ARMOR -> store.getComponent(ref, InventoryComponent.Armor.getComponentType());
            case UTILITY -> store.getComponent(ref, InventoryComponent.Utility.getComponentType());
        };
        return component == null ? null : component.getInventory();
    }

    public static boolean move(Ref<EntityStore> ref, Store<EntityStore> store,
                               Section sourceSection, short sourceSlot,
                               Section targetSection, short targetSlot, ItemStack expectedSource) {
        return move(container(ref, store, sourceSection), sourceSlot,
                container(ref, store, targetSection), targetSlot, expectedSource);
    }

    public static boolean move(Ref<EntityStore> ref, Store<EntityStore> store,
                               Section sourceSection, short sourceSlot,
                               Section targetSection, short targetSlot, ItemStack expectedSource,
                               int quantity) {
        return move(container(ref, store, sourceSection), sourceSlot,
                container(ref, store, targetSection), targetSlot, expectedSource, quantity);
    }

    /**
     * Moves or swaps the selected whole stack, merging as much as the destination can hold.
     * The stack stays in its original inventory until this succeeds, so closing the page cannot
     * leave a cursor item.
     */
    public static boolean move(@Nullable ItemContainer source, short sourceSlot,
                               @Nullable ItemContainer target, short targetSlot,
                               @Nullable ItemStack expectedSource) {
        return move(source, sourceSlot, target, targetSlot, expectedSource,
                ItemStack.isEmpty(expectedSource) ? 0 : expectedSource.getQuantity());
    }

    /** Partial stacks can move or merge; replacing an incompatible stack requires the whole source. */
    public static boolean move(@Nullable ItemContainer source, short sourceSlot,
                               @Nullable ItemContainer target, short targetSlot,
                               @Nullable ItemStack expectedSource, int quantity) {
        if (!validSlot(source, sourceSlot) || !validSlot(target, targetSlot)
                || (source == target && sourceSlot == targetSlot)
                || ItemStack.isEmpty(expectedSource) || quantity <= 0) {
            return false;
        }

        return new TransactionView(source).move(sourceSlot, new TransactionView(target), targetSlot,
                expectedSource, quantity);
    }

    public static void sortStorage(Ref<EntityStore> ref, Store<EntityStore> store) {
        ItemContainer storage = container(ref, store, Section.STORAGE);
        if (storage != null) storage.sortItems(SortType.NAME);
    }

    private static boolean validSlot(@Nullable ItemContainer container, short slot) {
        return container != null && slot >= 0 && slot < container.getCapacity();
    }

    /** Exposes engine filter checks and holds the actual containers' locks without replacing them. */
    private static final class TransactionView extends DelegateItemContainer<ItemContainer> {
        private TransactionView(ItemContainer container) {
            super(container);
        }

        private boolean move(short sourceSlot, TransactionView target, short targetSlot,
                             ItemStack expectedSource, int quantity) {
            return writeAction(() -> target.writeAction(() -> {
                ItemContainer sourceContainer = getDelegate();
                ItemContainer targetContainer = target.getDelegate();
                if (!validSlot(sourceContainer, sourceSlot) || !validSlot(targetContainer, targetSlot)) return false;

                ItemStack current = sourceContainer.getItemStack(sourceSlot);
                // Compare quantity, durability, quality and all metadata while both slots are locked.
                if (ItemStack.isEmpty(current) || !expectedSource.equals(current)
                        || quantity > current.getQuantity()) return false;

                ItemStack destination = targetContainer.getItemStack(targetSlot);
                boolean swapping = !ItemStack.isEmpty(destination) && !current.isStackableWith(destination);
                if (swapping) {
                    // Native filtered moves check source REMOVE, forward MOVE and both ADD filters.
                    // Supply the destination REMOVE and reverse MOVE checks their swap path omits.
                    if (quantity != current.getQuantity() || target.cantRemoveFromSlot(targetSlot)
                            || cantMoveToSlot(targetContainer, targetSlot)) return false;
                } else if (!targetContainer.canAddItemStackToSlot(targetSlot, current.withQuantity(quantity), false, true)) {
                    return false;
                }

                // Use the originals so their change events still reach the real inventory components.
                return sourceContainer.moveItemStackFromSlotToSlot(sourceSlot, quantity, targetContainer, targetSlot, true)
                        .succeeded();
            }));
        }
    }
}
