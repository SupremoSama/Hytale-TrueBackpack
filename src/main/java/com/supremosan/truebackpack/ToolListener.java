package com.supremosan.truebackpack;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.cosmetic.CosmeticListener;
import com.supremosan.truebackpack.model.BlockyModelWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolListener extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String ATTACHMENT_SLOT_PREFIX = "truebackpack:tool:";

    private static final Map<String, String> CATEGORY_FOLDER = Map.of(
            "Weapon", "Weapons",
            "Tool",   "Tools"
    );

    public enum ToolSlot {
        BACK("B-Attachment"),
        HIP_LEFT("L-Attachment"),
        HIP_RIGHT("R-Attachment");

        private final String boneName;

        ToolSlot(@Nonnull String boneName) {
            this.boneName = boneName;
        }

        @Nonnull
        public String getBoneName() { return boneName; }
    }

    private static final Map<String, ToolSlot> PREFIX_REGISTRY = new LinkedHashMap<>();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final Map<String, Boolean> PROCESSING = new ConcurrentHashMap<>();
    private static final Map<String, String> CURRENT_ITEM = new ConcurrentHashMap<>();

    private static ToolListener INSTANCE;

    public ToolListener() {
        INSTANCE = this;
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        INSTANCE = new ToolListener();
        plugin.getEntityStoreRegistry().registerSystem(INSTANCE);

        plugin.getEventRegistry().registerGlobal(
                LivingEntityInventoryChangeEvent.class,
                event -> INSTANCE.handle(event));

        LOGGER.atInfo().log("[TrueBackpack] ToolListener registered");
    }

    public static void registerPrefix(@Nonnull String idPrefix, @Nonnull ToolSlot slot) {
        PREFIX_REGISTRY.put(idPrefix, slot);
    }

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull CommandBuffer<EntityStore> buffer) {
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull CommandBuffer<EntityStore> buffer) {
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;

        String playerUuid = uuidComp.getUuid().toString();
        PROCESSING.remove(playerUuid);

        for (ToolSlot slot : ToolSlot.values()) {
            String key = slotKey(slot);
            CURRENT_ITEM.remove(playerUuid + ":" + key);
            CosmeticListener.removeAttachment(playerUuid, key);
            BlockyModelWrapper.delete(playerUuid, key);
        }
    }

    private void handle(@Nonnull LivingEntityInventoryChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;

        Ref<EntityStore> ref = entity.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;

        String playerUuid = uuidComponent.getUuid().toString();

        LOGGER.atInfo().log("[TrueBackpack] InventoryChangeEvent player=%s container=%s isProcessing=%s prefixRegistry=%s",
                playerUuid,
                event.getItemContainer() != null ? event.getItemContainer().getClass().getSimpleName() : "null",
                PROCESSING.get(playerUuid),
                PREFIX_REGISTRY.keySet());

        if (Boolean.TRUE.equals(PROCESSING.get(playerUuid))) return;

        if (!(entity instanceof Player player)) return;

        ItemContainer hotbar = player.getInventory().getHotbar();
        if (hotbar == null) return;

        PROCESSING.put(playerUuid, Boolean.TRUE);
        try {
            evaluateHotbar(player, store, ref, playerUuid, hotbar);
        } finally {
            PROCESSING.remove(playerUuid);
        }
    }

    private static void evaluateHotbar(@Nonnull Player player,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> ref,
                                       @Nonnull String playerUuid,
                                       @Nonnull ItemContainer hotbar) {
        Map<ToolSlot, String> resolved = new LinkedHashMap<>();

        for (short slot = 0; slot < hotbar.getCapacity(); slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null || stack.isEmpty()) continue;

            String itemId = stack.getItemId();
            ToolSlot toolSlot = matchPrefix(itemId);
            LOGGER.atInfo().log("[TrueBackpack] hotbar slot=%d itemId=%s matchedSlot=%s", slot, itemId, toolSlot);
            if (toolSlot == null) continue;

            if (resolved.containsKey(toolSlot)) continue;
            resolved.put(toolSlot, itemId);
        }

        LOGGER.atInfo().log("[TrueBackpack] evaluateHotbar player=%s resolved=%s", playerUuid, resolved);

        boolean changed = false;

        for (ToolSlot slot : ToolSlot.values()) {
            String key = slotKey(slot);
            String currentKey = playerUuid + ":" + key;
            String itemId = resolved.get(slot);
            String previousItemId = CURRENT_ITEM.get(currentKey);

            if (itemId != null) {
                if (itemId.equals(previousItemId)) {
                    LOGGER.atInfo().log("[TrueBackpack] slot=%s unchanged item=%s, skipping", slot.name(), itemId);
                    continue;
                }

                LOGGER.atInfo().log("[TrueBackpack] slot=%s item changed [%s] -> [%s]", slot.name(), previousItemId, itemId);
                ModelAttachment attachment = buildAttachment(slot, itemId, playerUuid, key);
                if (attachment != null) {
                    CURRENT_ITEM.put(currentKey, itemId);
                    CosmeticListener.putAttachment(playerUuid, key, attachment);
                    changed = true;
                } else {
                    LOGGER.atWarning().log("[TrueBackpack] slot=%s attachment build failed for item=%s", slot.name(), itemId);
                }
            } else if (previousItemId != null) {
                LOGGER.atInfo().log("[TrueBackpack] slot=%s item removed, was=%s", slot.name(), previousItemId);
                CURRENT_ITEM.remove(currentKey);
                CosmeticListener.removeAttachment(playerUuid, key);
                BlockyModelWrapper.delete(playerUuid, key);
                changed = true;
            }
        }

        LOGGER.atInfo().log("[TrueBackpack] evaluateHotbar player=%s changed=%s", playerUuid, changed);

        if (changed) {
            CosmeticListener.scheduleRebuild(player, store, ref, playerUuid);
        }
    }

    @Nullable
    private static ToolSlot matchPrefix(@Nonnull String itemId) {
        for (Map.Entry<String, ToolSlot> entry : PREFIX_REGISTRY.entrySet()) {
            if (itemId.startsWith(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    @Nullable
    private static ModelAttachment buildAttachment(@Nonnull ToolSlot slot,
                                                   @Nonnull String itemId,
                                                   @Nonnull String playerUuid,
                                                   @Nonnull String slotKey) {
        BlockyModelWrapper.delete(playerUuid, slotKey);

        String sourceModelPath = resolveModelPath(itemId);
        String wrappedPath = BlockyModelWrapper.wrap(playerUuid, slotKey, sourceModelPath);

        if (wrappedPath == null) {
            LOGGER.atWarning().log("[TrueBackpack] Failed to wrap model for item=%s slot=%s", itemId, slot.name());
            return null;
        }

        String texturePath = resolveTexturePath(itemId);
        LOGGER.atInfo().log("[TrueBackpack] Built wrapped attachment slot=%s wrappedModel=%s", slot.name(), wrappedPath);
        return new ModelAttachment(wrappedPath, texturePath, "Chest", "", 0.5);
    }

    @Nonnull
    private static String resolveModelPath(@Nonnull String itemId) {
        String[] parts = itemId.split("_", 3);
        if (parts.length < 3) return "Items/" + itemId + ".blockymodel";
        String folder = CATEGORY_FOLDER.getOrDefault(parts[0], parts[0] + "s");
        return "Items/" + folder + "/" + parts[1] + "/" + parts[2] + ".blockymodel";
    }

    @Nonnull
    private static String resolveTexturePath(@Nonnull String itemId) {
        String[] parts = itemId.split("_", 3);
        if (parts.length < 3) return "Items/" + itemId + "_Texture.png";
        String folder = CATEGORY_FOLDER.getOrDefault(parts[0], parts[0] + "s");
        return "Items/" + folder + "/" + parts[1] + "/" + parts[2] + "_Texture.png";
    }

    @Nonnull
    private static String slotKey(@Nonnull ToolSlot slot) {
        return ATTACHMENT_SLOT_PREFIX + slot.name();
    }
}