package com.supremosan.truebackpack;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.ItemTranslationProperties;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.InventorySection;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.supremosan.truebackpack.helpers.BackpackDataStorage;
import com.supremosan.truebackpack.helpers.BackpackItemFactory;
import com.supremosan.truebackpack.helpers.BackpackTooltipProvider;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BackpackTooltipListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String VIRTUAL_SEP = "__bp_";
    private static final String DESC_KEY_PREFIX = "server.items.dynamic.backpack.";

    private static final ThreadLocal<Boolean> PROCESSING =
            ThreadLocal.withInitial(() -> false);

    private static final ConcurrentHashMap<UUID, Set<String>> SENT_VIRTUAL_IDS =
            new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<UUID, Map<String, String>> LAST_SENT_TRANSLATIONS =
            new ConcurrentHashMap<>();

    private static PacketFilter outboundFilter;
    private BackpackTooltipListener() {}

    public static void register() {
        outboundFilter = PacketAdapters.registerOutbound(BackpackTooltipListener::onOutbound);
        LOGGER.atInfo().log("[TrueBackpack] BackpackTooltipListener registered");
    }

    public static void deregister() {
        if (outboundFilter != null) {
            try {
                PacketAdapters.deregisterOutbound(outboundFilter);
            } catch (Exception e) {
                LOGGER.atWarning().log("[TrueBackpack] Failed to deregister tooltip filter: "
                        + e.getMessage());
            }
            outboundFilter = null;
        }
    }

    public static void onPlayerLeave(@Nonnull UUID playerUuid) {
        SENT_VIRTUAL_IDS.remove(playerUuid);
        LAST_SENT_TRANSLATIONS.remove(playerUuid);
    }

    private static void onOutbound(@Nonnull PlayerRef playerRef,
                                      @Nonnull com.hypixel.hytale.protocol.Packet packet) {
        if (PROCESSING.get()) return;
        if (!(packet instanceof UpdatePlayerInventory invPacket)) return;

        PROCESSING.set(true);
        try {
            processInventory(playerRef, invPacket);
        } catch (Exception e) {
            LOGGER.atWarning().log("[TrueBackpack] Error in tooltip outbound filter for "
                    + playerRef.getUuid() + ": " + e.getMessage());
        } finally {
            PROCESSING.set(false);
        }
    }

    private static void processInventory(@Nonnull PlayerRef playerRef,
                                         @Nonnull UpdatePlayerInventory packet) {
        UUID   playerUuid = playerRef.getUuid();
        String language   = playerRef.getLanguage();

        Map<String, ItemBase> newVirtualItems = new LinkedHashMap<>();
        Map<String, String>   translations    = new LinkedHashMap<>();

        processSection(packet.hotbar,       language, newVirtualItems, translations);
        processSection(packet.utility,      language, newVirtualItems, translations);
        processSection(packet.tools,        language, newVirtualItems, translations);
        processSection(packet.storage,      language, newVirtualItems, translations);
        processSection(packet.backpack,     language, newVirtualItems, translations);

        processArmorSection(playerUuid, packet.armor, language, newVirtualItems, translations);

        sendAuxiliary(playerRef, newVirtualItems, translations);
    }

    private static void processSection(
            @Nullable InventorySection section,
            @Nullable String language,
            @Nonnull Map<String, ItemBase> newVirtual,
            @Nonnull Map<String, String> translations) {

        if (section == null || section.items == null) return;

        for (Map.Entry<Integer, ItemWithAllMetadata> entry : section.items.entrySet()) {
            ItemWithAllMetadata item = entry.getValue();
            if (item == null || item.itemId.isBlank()) continue;
            if (isVirtualId(item.itemId)) continue;
            if (BackpackArmorListener.getBackpackSize(item.itemId) == 0) continue;

            ItemStack fakeStack = buildFakeStack(item);
            if (fakeStack == null) continue;

            String tooltipText = BackpackTooltipProvider.buildTooltip(fakeStack, language);
            if (tooltipText == null) continue;

            String contentHash = hash(tooltipText);
            String virtualId   = item.itemId + VIRTUAL_SEP + contentHash;
            String descKey     = DESC_KEY_PREFIX + virtualId + ".description";

            ItemBase virtualBase = buildVirtualItemBase(item.itemId, virtualId, descKey);
            if (virtualBase == null) continue;

            newVirtual.put(virtualId, virtualBase);
            translations.put(descKey, tooltipText);

            ItemWithAllMetadata cloned = item.clone();
            cloned.itemId = virtualId;
            section.items.put(entry.getKey(), cloned);
        }
    }

    private static void processArmorSection(
            @Nonnull UUID playerUuid,
            @Nullable InventorySection armorSection,
            @Nullable String language,
            @Nonnull Map<String, ItemBase> newVirtual,
            @Nonnull Map<String, String> translations) {

        if (armorSection == null || armorSection.items == null) return;

        ItemWithAllMetadata chestItem = armorSection.items.get(1);
        if (chestItem == null || chestItem.itemId.isBlank()) return;
        if (isVirtualId(chestItem.itemId)) return;

        short sizeBonus = BackpackArmorListener.getBackpackSize(chestItem.itemId);
        if (sizeBonus == 0) return;

        String playerUuidStr = playerUuid.toString();
        ItemStack activeItem = BackpackDataStorage.getActiveItem(playerUuidStr);

        String tooltipText;
        if (activeItem != null && BackpackItemFactory.hasContents(activeItem)) {
            List<ItemStack> contents = BackpackItemFactory.loadContents(activeItem);
            // Pass language so item names render in the player's locale
            tooltipText = BackpackTooltipProvider.buildTooltipFromLiveContents(
                    contents, sizeBonus, language);
        } else {
            // Equipped but no snapshot yet — fall back to metadata or empty
            ItemStack fakeStack = buildFakeStack(chestItem);
            tooltipText = fakeStack != null
                    ? BackpackTooltipProvider.buildTooltip(fakeStack, language)
                    : null;
            if (tooltipText == null) {
                // &7/&8 — Hytale color codes, not §
                tooltipText = "&7Backpack (" + sizeBonus + " slots)\n&8Empty";
            }
        }

        String contentHash = hash(tooltipText);
        String virtualId   = chestItem.itemId + VIRTUAL_SEP + contentHash;
        String descKey     = DESC_KEY_PREFIX + virtualId + ".description";

        ItemBase virtualBase = buildVirtualItemBase(chestItem.itemId, virtualId, descKey);
        if (virtualBase == null) return;

        newVirtual.put(virtualId, virtualBase);
        translations.put(descKey, tooltipText);

        ItemWithAllMetadata cloned = chestItem.clone();
        cloned.itemId = virtualId;
        armorSection.items.put(1, cloned);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Virtual item construction
    // ──────────────────────────────────────────────────────────────────────

    @Nullable
    private static ItemBase buildVirtualItemBase(@Nonnull String baseItemId,
                                                 @Nonnull String virtualId,
                                                 @Nonnull String descKey) {
        try {
            Item originalItem = Item.getAssetMap().getAsset(baseItemId);
            if (originalItem == null) {
                LOGGER.atWarning().log("[TrueBackpack] Base item not found for virtual tooltip: "
                        + baseItemId);
                return null;
            }

            ItemBase clone = originalItem.toPacket().clone();
            clone.id = virtualId;

            if (clone.translationProperties == null) {
                clone.translationProperties = new ItemTranslationProperties();
            } else {
                clone.translationProperties = clone.translationProperties.clone();
            }

            // Assign our unique description key; keep the original item's name key
            // so the backpack's own name (e.g. "Leather Backpack") is unchanged
            clone.translationProperties.description = descKey;

            // Hide from creative / item library tabs
            clone.variant = true;

            return clone;
        } catch (Exception e) {
            LOGGER.atWarning().log("[TrueBackpack] Failed to build virtual item base for "
                    + baseItemId + ": " + e.getMessage());
            return null;
        }
    }

    private static void sendAuxiliary(@Nonnull PlayerRef playerRef,
                                      @Nonnull Map<String, ItemBase> newVirtual,
                                      @Nonnull Map<String, String> translations) {
        if (newVirtual.isEmpty() && translations.isEmpty()) return;

        UUID playerUuid = playerRef.getUuid();

        Set<String> sentIds = SENT_VIRTUAL_IDS.computeIfAbsent(
                playerUuid, _ -> ConcurrentHashMap.newKeySet());

        Map<String, ItemBase> toSend = new LinkedHashMap<>();
        for (Map.Entry<String, ItemBase> e : newVirtual.entrySet()) {
            if (sentIds.add(e.getKey())) {
                toSend.put(e.getKey(), e.getValue());
            }
        }

        if (!toSend.isEmpty()) {
            try {
                UpdateItems itemsPacket = new UpdateItems();
                itemsPacket.type         = UpdateType.AddOrUpdate;
                itemsPacket.items        = toSend;
                itemsPacket.removedItems = new String[0];
                itemsPacket.updateModels = false;
                itemsPacket.updateIcons  = false;
                playerRef.getPacketHandler().writeNoCache(itemsPacket);
            } catch (Exception e) {
                LOGGER.atWarning().log("[TrueBackpack] Failed to send UpdateItems: "
                        + e.getMessage());
            }
        }

        // ── Send only changed translations (diff-based) ────────────────────
        if (!translations.isEmpty()) {
            Map<String, String> lastSent = LAST_SENT_TRANSLATIONS.get(playerUuid);
            Map<String, String> delta    = new LinkedHashMap<>();

            for (Map.Entry<String, String> e : translations.entrySet()) {
                String prev = lastSent != null ? lastSent.get(e.getKey()) : null;
                if (!e.getValue().equals(prev)) {
                    delta.put(e.getKey(), e.getValue());
                }
            }

            if (!delta.isEmpty()) {
                try {
                    UpdateTranslations transPacket =
                            new UpdateTranslations(UpdateType.AddOrUpdate, delta);
                    playerRef.getPacketHandler().writeNoCache(transPacket);

                    if (lastSent == null) {
                        LAST_SENT_TRANSLATIONS.put(playerUuid, new ConcurrentHashMap<>(delta));
                    } else {
                        lastSent.putAll(delta);
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().log("[TrueBackpack] Failed to send UpdateTranslations: "
                            + e.getMessage());
                }
            }
        }
    }

    @Nullable
    private static ItemStack buildFakeStack(@Nonnull ItemWithAllMetadata item) {
        try {
            if (item.itemId.isBlank()) return null;
            BsonDocument metadataDoc = item.metadata != null
                    ? BsonDocument.parse(item.metadata)
                    : new BsonDocument();

            return new ItemStack(item.itemId, item.quantity, metadataDoc);
        } catch (Exception e) {
            LOGGER.atFine().log("[TrueBackpack] Could not build fake stack for "
                    + item.itemId + ": " + e.getMessage());
            return null;
        }
    }

    private static boolean isVirtualId(@Nonnull String itemId) {
        return itemId.contains(VIRTUAL_SEP);
    }

    @Nonnull
    private static String hash(@Nonnull String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.format("%08x", input.hashCode());
        }
    }
}