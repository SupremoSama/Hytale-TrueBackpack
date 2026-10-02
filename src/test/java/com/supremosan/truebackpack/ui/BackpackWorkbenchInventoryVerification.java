package com.supremosan.truebackpack.ui;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemUtility;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import org.bson.BsonDocument;
import org.bson.BsonString;

import java.io.IOException;
import java.util.Map;

/** Runs against real engine containers and their built-in armor/utility filters, without a server. */
public final class BackpackWorkbenchInventoryVerification {
    private static final Map<String, Item> ITEMS = Map.of(
            "material", new TestItem("material", 100, null, false),
            "helmet", new TestItem("helmet", 1, ItemArmorSlot.Head, false),
            "torch", new TestItem("torch", 100, null, true)
    );

    public static void main(String[] args) throws IOException {
        try {
            verifyInventory();
        } catch (Throwable failure) {
            // Engine logging may replace System.err before a server exists. Keep standalone failures visible.
            failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err)));
            throw failure;
        }
    }

    private static void verifyInventory() throws IOException {
        movePreservesMetadata();
        partialMergePreservesRemainder();
        equipmentFiltersApply();
        removalFiltersApply();
        staleSelectionDoesNotMove();
        invalidSlotsDoNotMove();
        partialOccupiedTargetsDoNotSwap();
        successfulSwapsPreserveBothStacks();
        swapFiltersApply();
        partialMovesPreserveRemainders();
        invalidQuantitiesDoNotMove();
        nativeGridEventsDecode();
        gridSlotsSerializeWithoutFakeEmptyItems();
        displayProjectionKeepsServerMetadata();
        System.out.println("Backpack workbench inventory verification passed (14 scenarios).");
    }

    private static void nativeGridEventsDecode() throws IOException {
        var dropped = BackpackWorkbenchPage.PageData.CODEC.decodeJson(RawJsonReader.fromJsonString("""
                {"Action":"InventoryDrop","Target":"HOTBAR","SlotIndex":2,
                 "SourceInventorySectionId":-2,"SourceSlotId":4,
                 "ItemStackId":"material","ItemStackQuantity":7}
                """), new ExtraInfo());
        require("InventoryDrop".equals(dropped.action) && "HOTBAR".equals(dropped.target), "drop destination did not decode");
        require(dropped.slotIndex == 2 && dropped.sourceSectionId == InventoryComponent.STORAGE_SECTION_ID
                && dropped.sourceSlotId == 4, "native drop slot/section fields did not decode");
        require("material".equals(dropped.itemStackId) && dropped.itemStackQuantity == 7,
                "native partial drag quantity did not decode");

        var clicked = BackpackWorkbenchPage.PageData.CODEC.decodeJson(RawJsonReader.fromJsonString("""
                {"Action":"InventoryDragSource","Target":"STORAGE","SlotIndex":4}
                """), new ExtraInfo());
        require(clicked.slotIndex == 4 && clicked.sourceSectionId == null && clicked.sourceSlotId == null
                && clicked.itemStackQuantity == null, "absent native drag fields were assigned invented values");
    }

    private static void gridSlotsSerializeWithoutFakeEmptyItems() {
        var original = stack("material", 5, "serialized-instance");
        var selected = original.withMetadata(original.getMetadata()
                .append(BackpackItemFactory.CUSTOM_NAME_CODEC.getKey(), new BsonString("My backpack")));
        var command = new UICommandBuilder().set("#StorageGrid.Slots", new ItemGridSlot[]{
                BackpackWorkbenchPage.inventoryGridSlot(null),
                BackpackWorkbenchPage.inventoryGridSlot(ItemStack.EMPTY),
                BackpackWorkbenchPage.inventoryGridSlot(selected)
        }).getCommands()[0];
        var slots = BsonDocument.parse(command.data).getArray("0");
        require(slots.size() == 3, "grid slot serialization changed the number of slots");
        for (int i = 0; i < 2; i++) {
            var empty = slots.get(i).asDocument();
            require(!empty.containsKey("ItemStack") && !empty.containsKey("Name") && !empty.containsKey("Description"),
                    "empty grid slot serialized a fake item or tooltip");
        }
        var occupied = slots.get(2).asDocument();
        var encodedStack = occupied.getDocument("ItemStack");
        require("material".equals(encodedStack.getString("Id").getValue()) && encodedStack.getInt32("Quantity").getValue() == 5,
                "occupied grid slot lost its item/quantity");
        require(!encodedStack.containsKey("Metadata"), "grid emitted server BSON metadata that the client cannot decode");
        require("My backpack".equals(occupied.getString("Name").getValue()), "grid serialization lost the custom name");
        for (var slot : slots) require(slot.asDocument().getBoolean("IsActivatable").getValue(), "grid slot cannot start a drag");
    }

    private static void displayProjectionKeepsServerMetadata() {
        var original = stack("material", 5, "display-instance");
        var selected = original.withMetadata(original.getMetadata()
                .append(BackpackItemFactory.CUSTOM_NAME_CODEC.getKey(), new BsonString("My backpack")));
        var metadata = selected.getMetadata().clone();
        var projected = BackpackWorkbenchPage.inventoryGridSlot(selected);
        var encoded = ItemGridSlot.CODEC.encode(projected).asDocument().getDocument("ItemStack");
        require(!encoded.containsKey("Metadata"), "display projection contains private server metadata");
        require(encoded.getDouble("Durability").getValue() == selected.getDurability()
                && encoded.getDouble("MaxDurability").getValue() == selected.getMaxDurability(),
                "display projection lost durability");
        require(encoded.getInt32("QualityOverride").getValue() == 0, "display projection lost its quality override");
        require(metadata.equals(selected.getMetadata()), "display projection modified the real item metadata");
        var source = container();
        var target = container();
        set(source, (short) 0, selected);
        require(move(source, 0, target, 1, selected), "displayed item could not move");
        require(metadata.equals(target.getItemStack((short) 1).getMetadata()), "display projection caused the move to lose metadata");
    }

    private static void movePreservesMetadata() {
        var source = container();
        var target = container();
        var selected = stack("material", 5, "backpack-instance");
        set(source, (short) 0, selected);

        require(move(source, 0, target, 1, selected), "move into an empty slot failed");
        require(source.getItemStack((short) 0) == null, "source was not emptied");
        require(selected.equals(target.getItemStack((short) 1)), "move lost metadata or durability");
    }

    private static void partialMergePreservesRemainder() {
        var source = container();
        var target = container();
        var selected = stack("material", 20, "merge");
        set(source, (short) 0, selected);
        set(target, (short) 0, stack("material", 95, "merge"));

        require(move(source, 0, target, 0, selected), "partial merge failed");
        var remainder = source.getItemStack((short) 0);
        var merged = target.getItemStack((short) 0);
        require(remainder.getQuantity() == 15 && merged.getQuantity() == 100, "merge lost or duplicated items");
        require(selected.isStackableWith(remainder) && selected.isStackableWith(merged), "merge lost metadata");
        require(!move(source, 0, target, 0, remainder), "full destination accepted a move");
        require(source.getItemStack((short) 0).getQuantity() == 15, "full destination consumed source items");
    }

    private static void equipmentFiltersApply() {
        var source = container();
        ItemContainer armor = new InventoryComponent.Armor((short) 4).getInventory();
        ItemContainer utility = new InventoryComponent.Utility((short) 4).getInventory();
        var material = stack("material", 3, "invalid-equipment");
        set(source, (short) 0, material);
        require(!move(source, 0, armor, 0, material), "armor accepted a non-armor item");
        require(!move(source, 0, utility, 0, material), "utility accepted an unusable item");
        require(material.equals(source.getItemStack((short) 0)), "rejected equipment move changed source");

        var helmet = stack("helmet", 1, "helmet");
        set(source, (short) 0, helmet);
        require(!move(source, 0, armor, 1, helmet), "helmet entered the chest slot");
        require(move(source, 0, armor, 0, helmet), "helmet could not be equipped");
        require(move(armor, 0, source, 1, helmet), "helmet could not be unequipped");

        var torch = stack("torch", 3, "utility");
        set(source, (short) 0, torch);
        require(move(source, 0, utility, 0, torch), "usable utility item could not be equipped");
        require(torch.equals(utility.getItemStack((short) 0)), "utility equip lost metadata");
    }

    private static void removalFiltersApply() {
        var source = container();
        var target = container();
        var selected = stack("material", 5, "locked");
        set(source, (short) 0, selected);
        source.setSlotFilter(FilterActionType.REMOVE, (short) 0, SlotFilter.DENY);
        require(!move(source, 0, target, 0, selected), "source removal restriction was bypassed");
        require(selected.equals(source.getItemStack((short) 0)) && target.isEmpty(), "failed removal changed inventories");

        source.setSlotFilter(FilterActionType.REMOVE, (short) 0, SlotFilter.ALLOW);
        target.setSlotFilter(FilterActionType.ADD, (short) 0, SlotFilter.DENY);
        require(!move(source, 0, target, 0, selected), "destination add restriction was bypassed");
        require(selected.equals(source.getItemStack((short) 0)) && target.isEmpty(), "failed add changed inventories");
    }

    private static void staleSelectionDoesNotMove() {
        var source = container();
        var target = container();
        var selected = stack("material", 5, "selected");
        set(source, (short) 0, selected.withQuantity(4));
        require(!move(source, 0, target, 0, selected), "stale quantity was accepted");

        var replacement = stack("material", 5, "replacement");
        set(source, (short) 0, replacement);
        require(!move(source, 0, target, 0, selected), "stale metadata was accepted");
        require(replacement.equals(source.getItemStack((short) 0)) && target.isEmpty(), "stale selection changed inventories");

        set(source, (short) 0, new TestStack("material", 5, 11, 50, selected.getMetadata()));
        require(!move(source, 0, target, 0, selected), "stale durability was accepted");
    }

    private static void invalidSlotsDoNotMove() {
        var source = container();
        var target = container();
        var selected = stack("material", 5, "range");
        set(source, (short) 0, selected);
        require(!move(source, -1, target, 0, selected), "negative source slot accepted");
        require(!move(source, 0, target, 2, selected), "out-of-range target slot accepted");
        require(!move(source, 2, target, 0, selected), "out-of-range source slot accepted");
        require(!move(source, 0, source, 0, selected), "same-slot move accepted");
        require(!move(source, 0, target, 0, null), "empty selection accepted");
        require(!move(null, 0, target, 0, selected), "missing source accepted");
        require(selected.equals(source.getItemStack((short) 0)) && target.isEmpty(), "invalid move changed inventories");
    }

    private static void partialOccupiedTargetsDoNotSwap() {
        var source = container();
        var target = container();
        var selected = stack("material", 5, "source");
        var occupied = stack("torch", 3, "target");
        set(source, (short) 0, selected);
        set(target, (short) 0, occupied);
        target.setSlotFilter(FilterActionType.REMOVE, (short) 0, SlotFilter.DENY);
        require(!move(source, 0, target, 0, selected), "incompatible locked destination was swapped");
        require(selected.equals(source.getItemStack((short) 0)) && occupied.equals(target.getItemStack((short) 0)),
                "rejected swap changed inventories");

        set(target, (short) 1, stack("material", 5, "different-metadata"));
        require(!move(source, 0, target, 1, selected, 2), "partial source swapped an incompatible destination");
        require(selected.equals(source.getItemStack((short) 0)), "rejected partial swap changed source");
    }

    private static void successfulSwapsPreserveBothStacks() {
        var source = container();
        var target = container();
        var selected = stack("material", 5, "source");
        var occupied = stack("torch", 3, "target");
        set(source, (short) 0, selected);
        set(target, (short) 0, occupied);
        require(move(source, 0, target, 0, selected), "whole-stack swap failed");
        require(occupied.equals(source.getItemStack((short) 0)) && selected.equals(target.getItemStack((short) 0)),
                "swap lost items or metadata");

        var differentMetadata = stack("material", 5, "other-instance");
        set(target, (short) 1, differentMetadata);
        require(move(target, 0, target, 1, selected), "same-container swap failed");
        require(differentMetadata.equals(target.getItemStack((short) 0)) && selected.equals(target.getItemStack((short) 1)),
                "different metadata stacks were merged instead of swapped");

        ItemContainer armor = new InventoryComponent.Armor((short) 4).getInventory();
        var newHelmet = stack("helmet", 1, "new-helmet");
        var equippedHelmet = stack("helmet", 1, "equipped-helmet");
        set(source, (short) 0, newHelmet);
        set(armor, (short) 0, equippedHelmet);
        require(move(source, 0, armor, 0, newHelmet), "equipped helmet replacement failed");
        require(equippedHelmet.equals(source.getItemStack((short) 0)) && newHelmet.equals(armor.getItemStack((short) 0)),
                "helmet replacement discarded equipment or metadata");
    }

    private static void swapFiltersApply() {
        var source = container();
        var target = container();
        var selected = stack("material", 5, "source");
        var occupied = stack("torch", 3, "target");
        set(source, (short) 0, selected);
        set(target, (short) 0, occupied);

        source.setSlotFilter(FilterActionType.ADD, (short) 0, SlotFilter.DENY);
        require(!move(source, 0, target, 0, selected), "source reverse ADD filter was bypassed");
        assertUnchanged(source, target, selected, occupied, "failed reverse ADD changed inventories");
        source.setSlotFilter(FilterActionType.ADD, (short) 0, SlotFilter.ALLOW);

        target.setSlotFilter(FilterActionType.REMOVE, (short) 0, SlotFilter.DENY);
        require(!move(source, 0, target, 0, selected), "destination REMOVE filter was bypassed");
        assertUnchanged(source, target, selected, occupied, "failed destination REMOVE changed inventories");
        target.setSlotFilter(FilterActionType.REMOVE, (short) 0, SlotFilter.ALLOW);

        target.setGlobalFilter(FilterType.ALLOW_INPUT_ONLY);
        require(!move(source, 0, target, 0, selected), "destination global output restriction was bypassed");
        assertUnchanged(source, target, selected, occupied, "failed global output changed inventories");
        target.setGlobalFilter(FilterType.ALLOW_ALL);

        source.setGlobalFilter(FilterType.ALLOW_OUTPUT_ONLY);
        require(!move(source, 0, target, 0, selected), "source global input restriction was bypassed");
        assertUnchanged(source, target, selected, occupied, "failed global input changed inventories");

        var reverseMoveRestricted = new NoIncomingMovesContainer();
        set(reverseMoveRestricted, (short) 0, selected);
        require(!move(reverseMoveRestricted, 0, target, 0, selected), "source reverse MOVE restriction was bypassed");
        assertUnchanged(reverseMoveRestricted, target, selected, occupied, "failed reverse MOVE changed inventories");

        ItemContainer armor = new InventoryComponent.Armor((short) 4).getInventory();
        var helmet = stack("helmet", 1, "equipped");
        set(armor, (short) 0, helmet);
        source.setGlobalFilter(FilterType.ALLOW_ALL);
        require(!move(source, 0, armor, 0, selected), "non-armor item replaced equipped armor");
        assertUnchanged(source, armor, selected, helmet, "failed armor swap changed inventories");
    }

    private static void partialMovesPreserveRemainders() {
        var source = container();
        var target = container();
        var selected = stack("material", 20, "partial");
        set(source, (short) 0, selected);
        require(move(source, 0, target, 0, selected, 5), "partial move failed");
        require(source.getItemStack((short) 0).getQuantity() == 15 && target.getItemStack((short) 0).getQuantity() == 5,
                "partial move lost or duplicated items");
        require(selected.isStackableWith(source.getItemStack((short) 0)) && selected.isStackableWith(target.getItemStack((short) 0)),
                "partial move lost metadata");

        set(source, (short) 0, selected);
        set(target, (short) 0, stack("material", 95, "partial"));
        require(move(source, 0, target, 0, selected, 7), "partial merge failed");
        require(source.getItemStack((short) 0).getQuantity() == 15 && target.getItemStack((short) 0).getQuantity() == 100,
                "partial merge lost its unrequested items or merge remainder");
        require(selected.isStackableWith(source.getItemStack((short) 0)) && selected.isStackableWith(target.getItemStack((short) 0)),
                "partial merge lost metadata");

        var remaining = source.getItemStack((short) 0);
        require(move(source, 0, source, 1, remaining, 3), "same-container partial move failed");
        require(source.getItemStack((short) 0).getQuantity() == 12 && source.getItemStack((short) 1).getQuantity() == 3,
                "same-container partial move lost items");
    }

    private static void invalidQuantitiesDoNotMove() {
        var source = container();
        var target = container();
        var selected = stack("material", 5, "invalid-quantity");
        set(source, (short) 0, selected);
        require(!move(source, 0, target, 0, selected, 0), "zero quantity was accepted");
        require(!move(source, 0, target, 0, selected, -1), "negative quantity was accepted");
        require(!move(source, 0, target, 0, selected, 6), "quantity exceeding source was accepted");
        require(!move(source, 0, target, 0, selected, Integer.MAX_VALUE), "overflow-sized quantity was accepted");
        require(selected.equals(source.getItemStack((short) 0)) && target.isEmpty(), "invalid quantity changed inventories");
    }

    private static void assertUnchanged(ItemContainer source, ItemContainer target,
                                        ItemStack selected, ItemStack occupied, String message) {
        require(selected.equals(source.getItemStack((short) 0)) && occupied.equals(target.getItemStack((short) 0)), message);
    }

    private static SimpleItemContainer container() {
        return new SimpleItemContainer((short) 2);
    }

    private static TestStack stack(String id, int quantity, String instance) {
        return new TestStack(id, quantity, 12, 50, new BsonDocument("Instance", new BsonString(instance))
                .append("Backpack", new BsonDocument("Name", new BsonString("My backpack"))));
    }

    private static void set(ItemContainer container, short slot, ItemStack stack) {
        require(container.setItemStackForSlot(slot, stack, false).succeeded(), "test setup failed");
    }

    private static boolean move(ItemContainer source, int sourceSlot, ItemContainer target, int targetSlot, ItemStack selected) {
        return BackpackWorkbenchInventory.move(source, (short) sourceSlot, target, (short) targetSlot, selected);
    }

    private static boolean move(ItemContainer source, int sourceSlot, ItemContainer target, int targetSlot,
                                ItemStack selected, int quantity) {
        return BackpackWorkbenchInventory.move(source, (short) sourceSlot, target, (short) targetSlot, selected, quantity);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class NoIncomingMovesContainer extends SimpleItemContainer {
        private NoIncomingMovesContainer() {
            super((short) 2);
        }

        @Override
        protected boolean cantMoveToSlot(ItemContainer fromContainer, short slotFrom) {
            return true;
        }
    }

    // Synthetic item assets make tests independent of the installed asset pack. The stack and
    // container transaction algorithms, metadata, and equipment filters are the engine's real ones.
    private static final class TestItem extends Item {
        private TestItem(String id, int maxStackSize, ItemArmorSlot slot, boolean utilityUsable) {
            super(id);
            maxStack = maxStackSize;
            if (slot != null) armor = new ItemArmor() {{ armorSlot = slot; }};
            utility = new ItemUtility() {{ usable = utilityUsable; }};
        }
    }

    private static final class TestStack extends ItemStack {
        private TestStack(String id, int quantity, double durability, double maxDurability, BsonDocument metadata) {
            super(id, quantity, durability, maxDurability, 0, metadata);
        }

        @Override
        public Item getItem() {
            return ITEMS.get(getItemId());
        }

        @Override
        public ItemStack withQuantity(int quantity) {
            if (quantity == 0) return null;
            if (quantity == getQuantity()) return this;
            return new TestStack(getItemId(), quantity, getDurability(), getMaxDurability(), getMetadata());
        }

        @Override
        public ItemStack withMetadata(BsonDocument metadata) {
            return new TestStack(getItemId(), getQuantity(), getDurability(), getMaxDurability(), metadata);
        }
    }
}
