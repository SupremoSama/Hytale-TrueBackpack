package com.supremosan.custominventory.api;

/** Explicit event envelope; slotIndex is supplied by the client's ItemGrid SlotClicking event. */
public record InventoryContentEvent(String action, String payload, Integer slotIndex) {}
