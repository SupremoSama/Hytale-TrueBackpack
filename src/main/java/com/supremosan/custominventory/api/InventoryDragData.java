package com.supremosan.custominventory.api;

/** Native ItemGrid source metadata; both naming variants are emitted by supported clients. */
public record InventoryDragData(Integer sourceInventorySectionId, Integer sourceSlotId,
                                Integer dragSourceInventorySectionId, Integer dragSourceSlotId,
                                String itemStackId, Integer itemStackQuantity,
                                String dragItemStackId, Integer dragItemStackQuantity) {
    public Integer sectionId() { return sourceInventorySectionId != null ? sourceInventorySectionId : dragSourceInventorySectionId; }
    public Integer slotId() { return sourceSlotId != null ? sourceSlotId : dragSourceSlotId; }
    public String itemId() {
        String id = itemStackId != null && !itemStackId.isBlank() ? itemStackId : dragItemStackId;
        return id == null || id.isBlank() ? null : id;
    }
    public Integer quantity() { return itemStackQuantity != null ? itemStackQuantity : dragItemStackQuantity; }
}
