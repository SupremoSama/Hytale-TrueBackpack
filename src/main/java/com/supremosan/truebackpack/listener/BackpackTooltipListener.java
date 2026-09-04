package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InventorySection;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.supremosan.truebackpack.data.BackpackDataStorage;
import com.supremosan.truebackpack.registries.BackpackRegistry;
import com.supremosan.truebackpack.ui.BackpackTooltipProvider;
import org.bson.BsonDocument;
import org.bson.BsonString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BackpackTooltipListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final ThreadLocal<Boolean> PROCESSING =
            ThreadLocal.withInitial(() -> false);

    public static void register() {
        PacketAdapters.registerOutbound(BackpackTooltipListener::onOutbound);
    }

    private static void onOutbound(@Nonnull PlayerRef playerRef,
                                   @Nonnull com.hypixel.hytale.protocol.Packet packet) {

        if (PROCESSING.get()) return;
        if (!(packet instanceof UpdatePlayerInventory inv)) return;

        PROCESSING.set(true);
        try {
            processInventory(playerRef, inv);
        } catch (Exception e) {
            LOGGER.atWarning().log("[TrueBackpack] Error processing tooltip metadata: " + e.getMessage());
        } finally {
            PROCESSING.set(false);
        }
    }

    private static void processInventory(@Nonnull PlayerRef playerRef,
                                         @Nonnull UpdatePlayerInventory packet) {

        UUID uuid = playerRef.getUuid();
        String lang = playerRef.getLanguage();

        processSection(packet.hotbar, lang);
        processSection(packet.storage, lang);
        processSection(packet.utility, lang);
        processSection(packet.tools, lang);
        processSection(packet.backpack, lang);
        processSection(packet.abilitySlots, lang);
        processSection(packet.runeBag, lang);

        processArmor(packet.armor, uuid, lang);
    }

    private static void processSection(@Nullable InventorySection section,
                                       String lang) {

        if (section == null || section.items == null) return;

        for (Map.Entry<Integer, ItemWithAllMetadata> e : section.items.entrySet()) {

            ItemWithAllMetadata item = e.getValue();
            if (item == null || item.itemId.isBlank()) continue;

            if (!BackpackRegistry.isBackpack(item.itemId)) continue;

            ItemStack stack = buildFakeStack(item);
            if (stack == null) continue;

            String tooltip = BackpackTooltipProvider.buildTooltip(stack, lang);
            if (tooltip == null) continue;

            ItemWithAllMetadata clone = item.clone();
            attachTooltip(clone, tooltip);
            e.setValue(clone);
        }
    }

    private static void processArmor(@Nullable InventorySection armor,
                                     UUID uuid,
                                     String lang) {

        if (armor == null || armor.items == null) return;

        ItemWithAllMetadata chest = armor.items.get(1);
        if (chest == null || chest.itemId.isBlank()) return;

        short size = BackpackRegistry.getCapacity(chest.itemId);
        if (size == 0) return;

        List<ItemStack> contents = BackpackDataStorage.getLiveContents(uuid.toString());

        String tooltip = contents != null
                ? BackpackTooltipProvider.buildTooltipFromLiveContents(contents, size, lang)
                : BackpackTooltipProvider.buildEmptyTooltip(size, lang);

        ItemWithAllMetadata clone = chest.clone();
        attachTooltip(clone, tooltip);
        armor.items.put(1, clone);
    }

    private static void attachTooltip(@Nonnull ItemWithAllMetadata item, @Nonnull String tooltip) {
        BsonDocument root;
        if (item.metadata != null && !item.metadata.isBlank()) {
            try {
                root = BsonDocument.parse(item.metadata);
            } catch (Exception e) {
                root = new BsonDocument();
            }
        } else {
            root = new BsonDocument();
        }

        BsonDocument descDoc = new BsonDocument("RawText", new BsonString(tooltip));
        BsonDocument itemDisplayDoc = new BsonDocument("Description", descDoc);
        root.put(ItemDisplayMetadata.KEY, itemDisplayDoc);

        item.metadata = root.toJson();
    }

    private static ItemStack buildFakeStack(ItemWithAllMetadata item) {
        try {
            return new ItemStack(
                    item.itemId,
                    item.quantity,
                    item.metadata != null && !item.metadata.isBlank()
                            ? BsonDocument.parse(item.metadata)
                            : new BsonDocument()
            );
        } catch (Exception e) {
            return null;
        }
    }
}