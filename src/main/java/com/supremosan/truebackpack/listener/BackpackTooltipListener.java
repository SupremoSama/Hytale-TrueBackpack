package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.supremosan.truebackpack.data.BackpackDataStorage;
import com.supremosan.truebackpack.ui.BackpackTooltipProvider;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BackpackTooltipListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String VIRTUAL_SEP = "__bp_";
    private static final String DESC_KEY_PREFIX = "server.items.dynamic.backpack.";

    private static final ThreadLocal<Boolean> PROCESSING =
            ThreadLocal.withInitial(() -> false);

    private static final Map<UUID, Set<String>> SENT_VIRTUAL_IDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, String>> LAST_SENT_TRANSLATIONS = new ConcurrentHashMap<>();

    private static PacketFilter outboundFilter;
    private static PacketFilter inboundFilter;

    private BackpackTooltipListener() {}

    public static void register() {
        outboundFilter = PacketAdapters.registerOutbound(BackpackTooltipListener::onOutbound);
        inboundFilter = PacketAdapters.registerInbound(BackpackTooltipListener::onInbound);
    }

    public static void deregister() {
        safeDeregisterOutbound();
        safeDeregisterInbound();
    }

    private static void safeDeregisterOutbound() {
        if (outboundFilter == null) return;
        try {
            PacketAdapters.deregisterOutbound(outboundFilter);
        } catch (Exception ignored) {}
        outboundFilter = null;
    }

    private static void safeDeregisterInbound() {
        if (inboundFilter == null) return;
        try {
            PacketAdapters.deregisterInbound(inboundFilter);
        } catch (Exception ignored) {}
        inboundFilter = null;
    }

    public static void onPlayerLeave(@Nonnull UUID uuid) {
        SENT_VIRTUAL_IDS.remove(uuid);
        LAST_SENT_TRANSLATIONS.remove(uuid);
    }

    private static boolean onInbound(@Nonnull PlayerRef playerRef,
                                     @Nonnull com.hypixel.hytale.protocol.Packet packet) {

        try {
            if (packet instanceof MouseInteraction mouse) {
                if (isVirtualId(mouse.itemInHandId)) {
                    mouse.itemInHandId = getBaseItemId(mouse.itemInHandId);
                }
            } else if (packet instanceof SyncInteractionChains sync) {
                for (SyncInteractionChain chain : sync.updates) {
                    if (chain != null) translateChain(chain);
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[TrueBackpack] inbound error: " + e.getMessage());
        }

        return false;
    }

    private static void translateChain(@Nonnull SyncInteractionChain chain) {
        if (isVirtualId(chain.itemInHandId)) {
            chain.itemInHandId = getBaseItemId(chain.itemInHandId);
        }
        if (isVirtualId(chain.utilityItemId)) {
            chain.utilityItemId = getBaseItemId(chain.utilityItemId);
        }
        if (isVirtualId(chain.toolsItemId)) {
            chain.toolsItemId = getBaseItemId(chain.toolsItemId);
        }
        if (chain.newForks != null) {
            for (SyncInteractionChain fork : chain.newForks) {
                if (fork != null) translateChain(fork);
            }
        }
    }

    private static void onOutbound(@Nonnull PlayerRef playerRef,
                                   @Nonnull com.hypixel.hytale.protocol.Packet packet) {

        if (PROCESSING.get()) return;
        if (!(packet instanceof UpdatePlayerInventory inv)) return;

        PROCESSING.set(true);
        try {
            processInventory(playerRef, inv);
        } finally {
            PROCESSING.set(false);
        }
    }

    private static void processInventory(@Nonnull PlayerRef playerRef,
                                         @Nonnull UpdatePlayerInventory packet) {

        UUID uuid = playerRef.getUuid();
        String lang = playerRef.getLanguage();

        Map<String, ItemBase> newVirtual = new HashMap<>();
        Map<String, String> translations = new HashMap<>();

        processSection(packet.hotbar, lang, newVirtual, translations);
        processSection(packet.utility, lang, newVirtual, translations);
        processSection(packet.tools, lang, newVirtual, translations);
        processSection(packet.storage, lang, newVirtual, translations);
        processSection(packet.backpack, lang, newVirtual, translations);

        processArmor(packet.armor, uuid, lang, newVirtual, translations);

        if (newVirtual.isEmpty() && translations.isEmpty()) return;

        sendAux(playerRef, newVirtual, translations);
    }

    private static void processSection(@Nullable InventorySection section,
                                       String lang,
                                       Map<String, ItemBase> newVirtual,
                                       Map<String, String> translations) {

        if (section == null || section.items == null) return;

        for (Map.Entry<Integer, ItemWithAllMetadata> e : section.items.entrySet()) {

            ItemWithAllMetadata item = e.getValue();
            if (item == null || item.itemId.isBlank()) continue;
            if (isVirtualId(item.itemId)) continue;

            if (BackpackArmorListener.getBackpackSize(item.itemId) == 0) continue;

            ItemStack stack = buildFakeStack(item);
            if (stack == null) continue;

            String tooltip = BackpackTooltipProvider.buildTooltip(stack, lang);
            if (tooltip == null) continue;

            String hash = fastHash(tooltip);
            String virtualId = item.itemId + VIRTUAL_SEP + hash;
            String descKey = DESC_KEY_PREFIX + virtualId + ".description";

            ItemBase base = buildVirtual(item.itemId, virtualId, descKey);
            if (base == null) continue;

            newVirtual.put(virtualId, base);
            translations.put(descKey, tooltip);

            ItemWithAllMetadata clone = item.clone();
            clone.itemId = virtualId;
            e.setValue(clone);
        }
    }

    private static void processArmor(@Nullable InventorySection armor,
                                     UUID uuid,
                                     String lang,
                                     Map<String, ItemBase> newVirtual,
                                     Map<String, String> translations) {

        if (armor == null || armor.items == null) return;

        ItemWithAllMetadata chest = armor.items.get(1);
        if (chest == null || chest.itemId.isBlank()) return;
        if (isVirtualId(chest.itemId)) return;

        short size = BackpackArmorListener.getBackpackSize(chest.itemId);
        if (size == 0) return;

        List<ItemStack> contents = BackpackDataStorage.getLiveContents(uuid.toString());

        String tooltip = contents != null
                ? BackpackTooltipProvider.buildTooltipFromLiveContents(contents, size, lang)
                : BackpackTooltipProvider.buildEmptyTooltip(size, lang);

        String hash = fastHash(tooltip);
        String virtualId = chest.itemId + VIRTUAL_SEP + hash;
        String descKey = DESC_KEY_PREFIX + virtualId + ".description";

        ItemBase base = buildVirtual(chest.itemId, virtualId, descKey);
        if (base == null) return;

        newVirtual.put(virtualId, base);
        translations.put(descKey, tooltip);

        ItemWithAllMetadata clone = chest.clone();
        clone.itemId = virtualId;
        armor.items.put(1, clone);
    }

    private static void sendAux(PlayerRef ref,
                                Map<String, ItemBase> items,
                                Map<String, String> translations) {

        UUID uuid = ref.getUuid();

        Set<String> sent = SENT_VIRTUAL_IDS.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        Map<String, String> last = LAST_SENT_TRANSLATIONS.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

        items.entrySet().removeIf(e -> !sent.add(e.getKey()));

        if (!items.isEmpty()) {
            UpdateItems pkt = new UpdateItems();
            pkt.type = UpdateType.AddOrUpdate;
            pkt.items = items;
            pkt.removedItems = new String[0];
            ref.getPacketHandler().writeNoCache(pkt);
        }

        Map<String, String> delta = new HashMap<>();
        for (Map.Entry<String, String> e : translations.entrySet()) {
            if (!e.getValue().equals(last.get(e.getKey()))) {
                delta.put(e.getKey(), e.getValue());
            }
        }

        if (!delta.isEmpty()) {
            ref.getPacketHandler().writeNoCache(new UpdateTranslations(UpdateType.AddOrUpdate, delta));
            last.putAll(delta);
        }

        if (sent.size() > 500) {
            sent.clear();
        }
    }

    private static boolean isVirtualId(String id) {
        return id != null && id.contains(VIRTUAL_SEP);
    }

    private static String getBaseItemId(String id) {
        int i = id.indexOf(VIRTUAL_SEP);
        return i > 0 ? id.substring(0, i) : id;
    }

    private static String fastHash(String s) {
        return Integer.toHexString(s.hashCode());
    }

    private static ItemBase buildVirtual(String baseId, String virtualId, String descKey) {
        try {
            Item item = Item.getAssetMap().getAsset(baseId);
            if (item == null) return null;

            ItemBase base = item.toPacket().clone();
            base.id = virtualId;

            base.translationProperties = base.translationProperties != null
                    ? base.translationProperties.clone()
                    : new ItemTranslationProperties();

            base.translationProperties.description = descKey;
            base.variant = true;

            return base;
        } catch (Exception e) {
            return null;
        }
    }

    private static ItemStack buildFakeStack(ItemWithAllMetadata item) {
        try {
            return new ItemStack(
                    item.itemId,
                    item.quantity,
                    item.metadata != null ? BsonDocument.parse(item.metadata) : new BsonDocument()
            );
        } catch (Exception e) {
            return null;
        }
    }
}