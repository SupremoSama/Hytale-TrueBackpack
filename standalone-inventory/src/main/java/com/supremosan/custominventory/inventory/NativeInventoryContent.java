package com.supremosan.custominventory.inventory;

import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.supremosan.custominventory.api.InventoryContent;
import com.supremosan.custominventory.api.InventoryContentEvent;
import com.supremosan.custominventory.api.InventoryContext;
import com.supremosan.custominventory.api.InventoryEventBindings;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** A per-player projection of original containers with select-source, select-target transfers. */
public final class NativeInventoryContent implements InventoryContent {
    private final Map<NativeInventorySection, ItemStack[]> displayed = new EnumMap<>(NativeInventorySection.class);
    private final Map<NativeInventorySection, ItemContainer> displayedContainers = new EnumMap<>(NativeInventorySection.class);
    private final Map<ItemContainer, EventRegistration<Void, ItemContainer.ItemContainerChangeEvent>> listeners = new IdentityHashMap<>();
    private InventorySelection selection;
    private String status = "Select an item, then select a destination slot.";

    @Override
    public void build(InventoryContext context, UICommandBuilder commands,
                      InventoryEventBindings events, String selector) {
        commands.append(selector, "Inventory/NativeInventory.ui");
        Set<ItemContainer> current = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var section : NativeInventorySection.values()) {
            ItemContainer container = InventoryOperations.resolveContainer(context.ref(), context.store(), section);
            int capacity = container == null ? 0 : container.getCapacity();
            ItemGridSlot[] slots = new ItemGridSlot[capacity];
            ItemStack[] snapshot = new ItemStack[capacity];
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack stack = container.getItemStack(slot);
                slots[slot] = InventoryDisplay.slot(stack);
                snapshot[slot] = InventorySelection.snapshot(stack);
            }
            displayed.put(section, snapshot);
            if (container == null) {
                displayedContainers.remove(section);
            } else {
                displayedContainers.put(section, container);
                current.add(container);
                listeners.computeIfAbsent(container, c -> c.registerChangeEvent(change -> context.requestRefresh()));
            }
            String grid = "#" + section.gridId();
            commands.set(selector + " " + grid + ".Slots", slots);
            events.bind(CustomUIEventBindingType.SlotClicking, grid, "Select", section.name(), false);
        }
        listeners.entrySet().removeIf(entry -> {
            if (current.contains(entry.getKey())) return false;
            entry.getValue().unregister();
            return true;
        });
        commands.set(selector + " #Status.Text", status);
        events.bind(CustomUIEventBindingType.Activating, "#ClearSelectionButton", "Clear", "", false);
    }

    @Override
    public void handleEvent(InventoryContext context, InventoryContentEvent event) {
        if ("Clear".equals(event.action())) {
            selection = null;
            status = "Select an item, then select a destination slot.";
            return;
        }
        if (!"Select".equals(event.action()) || event.slotIndex() == null) return;
        NativeInventorySection section = NativeInventorySection.parse(event.payload());
        ItemStack[] visible = section == null ? null : displayed.get(section);
        int slot = event.slotIndex();
        if (visible == null || slot < 0 || slot >= visible.length) {
            status = "Invalid inventory slot.";
            return;
        }
        if (InventoryOperations.locked(context.ref(), context.store())) {
            selection = null;
            status = "Inventory access is currently locked.";
            return;
        }

        if (selection == null) {
            selection = InventorySelection.fromDisplayed(section, displayedContainers.get(section), slot, visible[slot]);
            status = selection == null ? "Select a slot that contains an item."
                    : "Selected " + section.name().toLowerCase(java.util.Locale.ROOT) + " slot " + (slot + 1)
                    + ". Select a destination or clear the selection.";
            return;
        }

        var result = InventoryOperations.move(context.ref(), context.store(), selection, section, slot);
        selection = null;
        status = switch (result) {
            case SUBMITTED -> "Transfer requested.";
            case LOCKED -> "Inventory access is currently locked.";
            case STALE_SELECTION -> "The selected item changed. Select the item again.";
            case SAME_SLOT -> "Selection cleared.";
            case INVALID_SOURCE, INVALID_TARGET -> "The inventory changed. Select the item again.";
            case INVALID_QUANTITY -> "Invalid item quantity.";
        };
    }

    @Override
    public void onDismiss(InventoryContext context) {
        for (var listener : listeners.values()) listener.unregister();
        listeners.clear();
        displayed.clear();
        displayedContainers.clear();
        selection = null;
    }
}
