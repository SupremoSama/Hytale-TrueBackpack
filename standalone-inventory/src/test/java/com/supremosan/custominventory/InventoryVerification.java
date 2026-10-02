package com.supremosan.custominventory;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.supremosan.custominventory.api.InventoryButtonDefinition;
import com.supremosan.custominventory.api.InventoryEventBindings;
import com.supremosan.custominventory.api.InventoryPageDefinition;
import com.supremosan.custominventory.api.InventoryRegistry;
import com.supremosan.custominventory.inventory.InventoryDisplay;
import com.supremosan.custominventory.inventory.InventoryOperations;
import com.supremosan.custominventory.inventory.InventorySelection;
import com.supremosan.custominventory.inventory.NativeInventoryContent;
import com.supremosan.custominventory.inventory.NativeInventorySection;
import com.supremosan.custominventory.ui.InventoryShellPage;
import org.bson.BsonDocument;
import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonInt64;
import org.bson.BsonInt32;
import org.bson.BsonString;

import java.util.List;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises request validation against real engine containers without starting or modifying a server. */
public final class InventoryVerification {
    private static final Item TEST_ITEM = new Item("verification_item");

    public static void main(String[] args) throws IOException {
        try {
            selectionLeavesSourceUntouched();
            metadataSnapshotIsDetached();
            staleSourceIsRejected();
            replacedContainerIsRejected();
            invalidRangesAreRejected();
            invalidQuantitiesAreRejected();
            lockedAndSameSlotAreRejected();
            validRequestsDoNotMutateDuringValidation();
            nativeSectionsAreAllowlisted();
            displayPreservesAuthoritativeMetadata();
            emptyGridSlotsHaveNoFakeItem();
            standardDisplayOverridesAreProjected();
            duplicateAndInvalidRegistrationsFail();
            registrationsHaveStableOrder();
            unregisterDoesNotRemoveReplacement();
            oldHandlesCannotRemoveReusedDefinitions();
            factoriesCreateIndependentSessions();
            scopedEventsPreserveRoutingEnvelope();
            mountedSessionGateRejectsStaleEvents();
            System.out.println("Custom inventory verification passed (19 scenarios). Native UI/client flows require in-game validation.");
        } catch (Throwable failure) {
            // Engine logging may replace System.err before a server exists.
            failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err)));
            throw failure;
        }
    }

    private static void selectionLeavesSourceUntouched() {
        ItemContainer source = container();
        ItemStack stack = stack(5, "one");
        set(source, 0, stack);
        InventorySelection selection = InventorySelection.capture(NativeInventorySection.STORAGE, source, 0);
        require(selection != null && selection.quantity() == 5, "selection missing source item");
        require(source.getItemStack((short) 0) == stack, "selecting moved or replaced the real item");
        require(InventorySelection.capture(NativeInventorySection.STORAGE, source, 1) == null, "empty slot selected");
    }

    private static void metadataSnapshotIsDetached() {
        ItemContainer source = container();
        TestStack stack = stack(5, "one");
        set(source, 0, stack);
        InventorySelection selection = InventorySelection.capture(NativeInventorySection.STORAGE, source, 0);
        ItemStack copy = InventorySelection.snapshot(stack);
        require(copy.getMetadata().equals(stack.getMetadata()), "snapshot changed BSON value types");
        BsonDocument changed = stack.getMetadata();
        changed.getDocument("Nested").put("Value", new BsonString("mutated"));
        changed.getArray("Array").get(0).asDocument().put("Value", new BsonString("mutated"));
        changed.getBinary("Binary").getData()[0] = 99;
        require(selection.matches(stack), "mutating returned metadata changed the authoritative source");
        require("original".equals(copy.getMetadata().getArray("Array").get(0).asDocument().getString("Value").getValue()),
                "snapshot array mutated through a returned metadata copy");
        require(copy.getMetadata().getBinary("Binary").getData()[0] == 1, "snapshot binary mutated through returned metadata copy");
        ItemStack replacement = stack.withMetadata(changed);
        set(source, 0, replacement);
        require(!selection.matches(replacement), "replacement metadata bypassed stale selection detection");
    }

    private static void staleSourceIsRejected() {
        ItemContainer source = container();
        ItemContainer target = container();
        ItemStack stack = stack(5, "one");
        set(source, 0, stack);
        InventorySelection selection = InventorySelection.capture(NativeInventorySection.STORAGE, source, 0);
        for (ItemStack replacement : List.of(stack(5, "two"), stack(4, "one"), stack.withDurability(2), stack.withQuality(1))) {
            set(source, 0, replacement);
            require(validate(selection, source, target, 0, 5) == InventoryOperations.Result.STALE_SELECTION,
                    "metadata, quantity, durability or quality change bypassed stale selection check");
            require(source.getItemStack((short) 0) == replacement && target.isEmpty(), "stale request changed items");
        }
        source.removeItemStackFromSlot((short) 0);
        require(validate(selection, source, target, 0, 5) == InventoryOperations.Result.STALE_SELECTION,
                "disappearing source bypassed stale selection check");
    }

    private static void replacedContainerIsRejected() {
        ItemContainer original = container();
        ItemContainer replacement = container();
        ItemStack stack = stack(5, "one");
        set(original, 0, stack);
        set(replacement, 0, stack);
        InventorySelection selection = InventorySelection.capture(NativeInventorySection.STORAGE, original, 0);
        require(validate(selection, replacement, container(), 0, 5) == InventoryOperations.Result.STALE_SELECTION,
                "replacement container inherited the old selection");
    }

    private static void invalidRangesAreRejected() {
        ItemContainer source = container();
        ItemContainer target = container();
        set(source, 0, stack(5, "one"));
        InventorySelection selection = InventorySelection.capture(NativeInventorySection.STORAGE, source, 0);
        for (int slot : new int[]{-1, 2, 65536, Integer.MAX_VALUE}) {
            require(InventorySelection.capture(NativeInventorySection.STORAGE, source, slot) == null,
                    "out-of-range source slot selected");
            require(validate(selection, source, target, slot, 5) == InventoryOperations.Result.INVALID_TARGET,
                    "invalid target wrapped into a valid short slot");
        }
        require(validate(null, source, target, 0, 5) == InventoryOperations.Result.INVALID_SOURCE, "missing source accepted");
        require(validate(selection, null, target, 0, 5) == InventoryOperations.Result.INVALID_SOURCE, "missing container accepted");
        require(validate(selection, source, null, 0, 5) == InventoryOperations.Result.INVALID_TARGET, "missing target accepted");
    }

    private static void invalidQuantitiesAreRejected() {
        ItemContainer source = container();
        ItemContainer target = container();
        ItemStack stack = stack(5, "one");
        set(source, 0, stack);
        InventorySelection selection = InventorySelection.capture(NativeInventorySection.STORAGE, source, 0);
        for (int quantity : new int[]{0, -1, 6, Integer.MAX_VALUE}) {
            require(validate(selection, source, target, 0, quantity) == InventoryOperations.Result.INVALID_QUANTITY,
                    "invalid quantity accepted: " + quantity);
        }
        require(stack.equals(source.getItemStack((short) 0)) && target.isEmpty(), "invalid requests changed inventories");
    }

    private static void lockedAndSameSlotAreRejected() {
        ItemContainer source = container();
        set(source, 0, stack(5, "one"));
        InventorySelection selection = InventorySelection.capture(NativeInventorySection.STORAGE, source, 0);
        require(InventoryOperations.validate(selection, source, container(), 0, 5, true) == InventoryOperations.Result.LOCKED,
                "inventory lock did not veto request");
        require(validate(selection, source, source, 0, 5) == InventoryOperations.Result.SAME_SLOT, "same slot accepted");
    }

    private static void validRequestsDoNotMutateDuringValidation() {
        ItemContainer source = container();
        ItemContainer target = container();
        ItemStack original = stack(5, "one");
        set(source, 0, original);
        InventorySelection selection = InventorySelection.capture(NativeInventorySection.STORAGE, source, 0);
        require(validate(selection, source, target, 1, 5) == InventoryOperations.Result.SUBMITTED, "valid whole stack rejected");
        require(validate(selection, source, target, 1, 2) == InventoryOperations.Result.SUBMITTED, "valid partial request rejected");
        require(source.getItemStack((short) 0) == original && target.isEmpty(), "validation moved an item before native submission");
        ItemStack displayed = InventorySelection.snapshot(original);
        set(source, 0, stack(5, "new-item"));
        InventorySelection delayedClick = InventorySelection.fromDisplayed(NativeInventorySection.STORAGE, source, 0, displayed);
        require(validate(delayedClick, source, target, 0, 5) == InventoryOperations.Result.STALE_SELECTION,
                "click substituted an item received after rendering");
    }

    private static void nativeSectionsAreAllowlisted() {
        for (var section : NativeInventorySection.values()) {
            require(NativeInventorySection.fromId(section.id()) == section, "section ID lookup failed");
            require(NativeInventorySection.parse(section.name()) == section, "section event lookup failed");
        }
        require(NativeInventorySection.fromId(0) == null && NativeInventorySection.fromId(1) == null,
                "unopened external container IDs were allowlisted");
        require(NativeInventorySection.fromId(-11) == null && NativeInventorySection.parse("ANYTHING") == null
                && NativeInventorySection.parse(null) == null, "unimplemented section was allowlisted");
    }

    private static void displayPreservesAuthoritativeMetadata() {
        ItemStack stack = stack(5, "private-server-data");
        stack.setOverrideDroppedItemAnimation(true);
        BsonDocument original = stack.getMetadata().clone();
        BsonDocument encoded = ItemGridSlot.CODEC.encode(InventoryDisplay.slot(stack)).asDocument().getDocument("ItemStack");
        require(!encoded.containsKey("Metadata"), "display leaked arbitrary server BSON");
        require(encoded.getInt32("Quantity").getValue() == 5 && encoded.getDouble("Durability").getValue() == 12
                && encoded.getDouble("MaxDurability").getValue() == 50
                && encoded.getInt32("QualityOverride").getValue() == 0, "display lost item attributes");
        require(original.equals(stack.getMetadata()) && stack.getOverrideDroppedItemAnimation(), "display modified the real item");
        require(InventorySelection.snapshot(stack).getOverrideDroppedItemAnimation(), "snapshot lost animation flag");
    }

    private static void emptyGridSlotsHaveNoFakeItem() {
        var command = new UICommandBuilder().set("#Grid.Slots", new ItemGridSlot[]{
                InventoryDisplay.slot(null), InventoryDisplay.slot(ItemStack.EMPTY), InventoryDisplay.slot(stack(1, "one"))
        }).getCommands()[0];
        var slots = BsonDocument.parse(command.data).getArray("0");
        require(slots.size() == 3, "slot positions changed during display encoding");
        for (int i = 0; i < 2; i++) {
            var slot = slots.get(i).asDocument();
            require(!slot.containsKey("ItemStack") && !slot.containsKey("Name"), "empty slot emitted fake tooltip/item");
            require(slot.getBoolean("IsActivatable").getValue(), "empty destination is not selectable");
        }
    }

    private static void standardDisplayOverridesAreProjected() {
        ItemStack original = stack(1, "one");
        BsonDocument metadata = original.getMetadata().append(ItemDisplayMetadata.KEYED_CODEC.getKey(),
                ItemDisplayMetadata.CODEC.encode(new ItemDisplayMetadata(Message.raw("Personal item"), Message.raw("Personal description"))));
        ItemStack stack = original.withMetadata(metadata);
        BsonDocument encoded = ItemGridSlot.CODEC.encode(InventoryDisplay.slot(stack)).asDocument();
        require("Personal item".equals(encoded.getString("Name").getValue())
                && "Personal description".equals(encoded.getString("Description").getValue()), "native display overrides lost");
        require(stack.getMetadata().containsKey("Instance"), "display customization destroyed private server data");
    }

    private static void duplicateAndInvalidRegistrationsFail() {
        InventoryRegistry registry = new InventoryRegistry();
        registry.registerInventoryPage(page("sample:one", 0));
        expectIllegal(() -> registry.registerInventoryPage(page("sample:one", 0)), "duplicate page accepted");
        registry.registerInventoryButton(button("sample:one", 0));
        expectIllegal(() -> registry.registerInventoryButton(button("sample:one", 0)), "duplicate button accepted");
        for (String id : new String[]{"unscoped", "Sample:one", "sample:", "sample:bad id"}) {
            expectIllegal(() -> page(id, 0), "invalid page identifier accepted");
            expectIllegal(() -> button(id, 0), "invalid button identifier accepted");
        }
    }

    private static void registrationsHaveStableOrder() {
        InventoryRegistry registry = new InventoryRegistry();
        registry.registerInventoryPage(page("sample:z", 20));
        registry.registerInventoryPage(page("sample:b", 10));
        registry.registerInventoryPage(page("sample:a", 10));
        registry.registerInventoryButton(button("sample:z", 20));
        registry.registerInventoryButton(button("sample:b", 10));
        registry.registerInventoryButton(button("sample:a", 10));
        require(registry.pagesSnapshot().stream().map(InventoryPageDefinition::id).toList()
                .equals(List.of("sample:a", "sample:b", "sample:z")), "page order is nondeterministic");
        require(registry.buttonsSnapshot().stream().map(InventoryButtonDefinition::id).toList()
                .equals(List.of("sample:a", "sample:b", "sample:z")), "button order is nondeterministic");
        try {
            registry.pagesSnapshot().clear();
            throw new AssertionError("registry exposed a mutable snapshot");
        } catch (UnsupportedOperationException expected) {}
    }

    private static void unregisterDoesNotRemoveReplacement() {
        InventoryRegistry registry = new InventoryRegistry();
        var original = registry.registerInventoryPage(page("sample:one", 0));
        original.close();
        InventoryPageDefinition replacement = page("sample:one", 1);
        var replacementHandle = registry.registerInventoryPage(replacement);
        original.close();
        require(registry.getPage("sample:one") == replacement, "stale handle removed new page registration");
        replacementHandle.close();
        replacementHandle.close();
        require(registry.getPage("sample:one") == null, "page registration failed to unregister");
        var firstButton = registry.registerInventoryButton(button("sample:one", 0));
        firstButton.close();
        var newButton = button("sample:one", 1);
        registry.registerInventoryButton(newButton);
        firstButton.close();
        require(registry.getButton("sample:one") == newButton, "stale handle removed new button registration");
        registry.clear();
        require(registry.pagesSnapshot().isEmpty() && registry.buttonsSnapshot().isEmpty(), "registry shutdown leaked registrations");
    }

    private static void factoriesCreateIndependentSessions() {
        AtomicInteger calls = new AtomicInteger();
        InventoryPageDefinition page = new InventoryPageDefinition("sample:session", "Sessions", 0, context -> {
            calls.incrementAndGet();
            return new NativeInventoryContent();
        });
        // The factory ignores context in this standalone test; a running host supplies each player context.
        var first = page.factory().apply(null);
        var second = page.factory().apply(null);
        require(first != second && calls.get() == 2, "factory shared mutable inventory session state");
    }

    private static void oldHandlesCannotRemoveReusedDefinitions() {
        InventoryRegistry registry = new InventoryRegistry();
        InventoryPageDefinition reusedPage = page("sample:reused", 0);
        InventoryButtonDefinition reusedButton = button("sample:reused", 0);
        var oldPage = registry.registerInventoryPage(reusedPage);
        var oldButton = registry.registerInventoryButton(reusedButton);
        var oldPageEntry = registry.getPageRegistration(reusedPage.id());
        var oldButtonEntry = registry.getButtonRegistration(reusedButton.id());
        registry.clear();
        var currentPage = registry.registerInventoryPage(reusedPage);
        var currentButton = registry.registerInventoryButton(reusedButton);
        require(oldPageEntry != registry.getPageRegistration(reusedPage.id())
                && oldButtonEntry != registry.getButtonRegistration(reusedButton.id()),
                "re-registration retained a stale rendered mount identity");
        oldPage.close();
        oldButton.close();
        require(registry.getPage(reusedPage.id()) == reusedPage && registry.getButton(reusedButton.id()) == reusedButton,
                "old handle removed the same definition reused after clear");
        currentPage.close();
        currentButton.close();
        var latestPage = registry.registerInventoryPage(reusedPage);
        var latestButton = registry.registerInventoryButton(reusedButton);
        currentPage.close();
        currentButton.close();
        require(registry.getPage(reusedPage.id()) == reusedPage && registry.getButton(reusedButton.id()) == reusedButton,
                "idempotent close removed the same definition's later registration");
        latestPage.close();
        latestButton.close();
    }

    private static void scopedEventsPreserveRoutingEnvelope() throws IOException {
        UIEventBuilder events = new UIEventBuilder();
        InventoryEventBindings bindings = new InventoryEventBindings(events, "#ContentHost", "sample:one", "session-token");
        bindings.bind(CustomUIEventBindingType.SlotClicking, "#StorageGrid", "Select", "STORAGE", false);
        var event = events.getEvents()[0];
        BsonDocument data = BsonDocument.parse(event.data);
        require("#ContentHost #StorageGrid".equals(event.selector) && !event.locksInterface, "binding escaped scope or blocked interface");
        require("Content".equals(data.getString("Action").getValue()) && "sample:one".equals(data.getString("PageId").getValue())
                && "session-token".equals(data.getString("SessionId").getValue())
                && "Select".equals(data.getString("ContentAction").getValue())
                && "STORAGE".equals(data.getString("Payload").getValue()), "binding lost scoped routing data");
        expectIllegal(() -> bindings.selector("StorageGrid"), "unscoped element selector accepted");
        // Simulate the native ItemGrid-added slot field alongside the host's routing envelope.
        data.append("SlotIndex", new BsonInt32(1));
        var decoded = InventoryShellPage.Event.CODEC.decodeJson(RawJsonReader.fromJsonString(data.toJson()), new ExtraInfo());
        require("Content".equals(decoded.action) && "sample:one".equals(decoded.pageId)
                && "session-token".equals(decoded.sessionId) && "Select".equals(decoded.contentAction)
                && "STORAGE".equals(decoded.payload) && decoded.slotIndex == 1,
                "host codec did not preserve native slot data and scoped routing fields");
        data.remove("SlotIndex");
        var absentSlot = InventoryShellPage.Event.CODEC.decodeJson(RawJsonReader.fromJsonString(data.toJson()), new ExtraInfo());
        require(absentSlot.slotIndex == null, "absent slot index became an invented slot zero");
    }

    private static void mountedSessionGateRejectsStaleEvents() {
        require(InventoryShellPage.acceptsEvent("page-one:2", "page-one:2"), "current rendered event rejected");
        require(!InventoryShellPage.acceptsEvent("page-one:2", "page-one:1"), "event from an old render accepted");
        require(!InventoryShellPage.acceptsEvent("page-one:2", "page-two:2"), "event from another page instance accepted");
        require(!InventoryShellPage.acceptsEvent("page-one:2", null)
                && !InventoryShellPage.acceptsEvent(null, "page-one:2")
                && !InventoryShellPage.acceptsEvent(null, null), "missing session tokens accepted");
    }

    private static InventoryPageDefinition page(String id, int order) {
        return new InventoryPageDefinition(id, id, order, context -> new NativeInventoryContent());
    }

    private static InventoryButtonDefinition button(String id, int order) {
        return new InventoryButtonDefinition(id, id, order, context -> {});
    }

    private static InventoryOperations.Result validate(InventorySelection selection, ItemContainer source,
                                                       ItemContainer target, int targetSlot, int quantity) {
        return InventoryOperations.validate(selection, source, target, targetSlot, quantity, false);
    }

    private static SimpleItemContainer container() { return new SimpleItemContainer((short) 2); }

    private static TestStack stack(int quantity, String instance) {
        return new TestStack(quantity, 12, 50, 0, new BsonDocument("Instance", new BsonString(instance))
                .append("Nested", new BsonDocument("Value", new BsonString("original")))
                .append("Array", new BsonArray(List.of(new BsonDocument("Value", new BsonString("original")))))
                .append("Long", new BsonInt64(1))
                .append("Binary", new BsonBinary(new byte[]{1, 2, 3})));
    }

    private static void set(ItemContainer container, int slot, ItemStack stack) {
        require(container.setItemStackForSlot((short) slot, stack, false).succeeded(), "test fixture setup failed");
    }

    private static void expectIllegal(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {}
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Supplies a synthetic asset while retaining engine item/container codecs and algorithms. */
    private static final class TestStack extends ItemStack {
        TestStack(int quantity, double durability, double maxDurability, int quality, BsonDocument metadata) {
            super("verification_item", quantity, durability, maxDurability, quality, metadata);
        }

        @Override public Item getItem() { return TEST_ITEM; }

        @Override public ItemStack withMetadata(BsonDocument metadata) {
            return new TestStack(getQuantity(), getDurability(), getMaxDurability(), getQualityIndex(), metadata);
        }

        @Override public ItemStack withDurability(double durability) {
            return new TestStack(getQuantity(), durability, getMaxDurability(), getQualityIndex(), getMetadata());
        }

        @Override public ItemStack withQuality(int quality) {
            return new TestStack(getQuantity(), getDurability(), getMaxDurability(), quality, getMetadata());
        }
    }
}
