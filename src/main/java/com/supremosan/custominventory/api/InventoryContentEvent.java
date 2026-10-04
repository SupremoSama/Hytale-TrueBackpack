package com.supremosan.custominventory.api;

/** Scoped content event, including optional native ItemGrid drag/drop metadata. */
public record InventoryContentEvent(String action, String payload, Integer slotIndex, InventoryDragData drag) {
    /** Retains the simple event constructor used by existing inventory extensions. */
    public InventoryContentEvent(String action, String payload, Integer slotIndex) {
        this(action, payload, slotIndex, null);
    }
}
