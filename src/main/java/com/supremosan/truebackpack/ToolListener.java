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
import com.supremosan.truebackpack.helpers.GeneratedModel;

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
            "Tool", "Tools"
    );

    public enum ToolSlot {
        BACK("BACK"),
        HIP_LEFT("HIP_LEFT"),
        HIP_RIGHT("HIP_RIGHT");

        private final String slotName;

        ToolSlot(@Nonnull String slotName) {
            this.slotName = slotName;
        }

        @Nonnull
        public String getSlotName() {
            return slotName;
        }
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
    }

    public static void registerPrefix(@Nonnull String idPrefix, @Nonnull ToolSlot slot) {
        PREFIX_REGISTRY.put(idPrefix, slot);
    }

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref,
                              @Nonnull AddReason reason,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull CommandBuffer<EntityStore> buffer) {
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref,
                               @Nonnull RemoveReason reason,
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
            GeneratedModel.delete(playerUuid, key);
        }
    }

    private void handle(@Nonnull LivingEntityInventoryChangeEvent event) {

        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        Ref<EntityStore> ref = entity.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;

        String playerUuid = uuidComponent.getUuid().toString();

        if (Boolean.TRUE.equals(PROCESSING.get(playerUuid))) return;
        if (CosmeticListener.isProcessing()) return;

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
            if (toolSlot == null) continue;
            if (resolved.containsKey(toolSlot)) continue;

            resolved.put(toolSlot, itemId);
        }

        boolean changed = false;

        for (ToolSlot slot : ToolSlot.values()) {

            String key = slotKey(slot);
            String currentKey = playerUuid + ":" + key;
            String itemId = resolved.get(slot);
            String previous = CURRENT_ITEM.get(currentKey);

            if (itemId != null) {
                if (itemId.equals(previous)) continue;

                ModelAttachment attachment = buildAttachment(playerUuid, slot, key, itemId);
                if (attachment != null) {
                    CURRENT_ITEM.put(currentKey, itemId);
                    CosmeticListener.putAttachment(playerUuid, key, attachment);
                    changed = true;
                }
            } else if (previous != null) {
                CURRENT_ITEM.remove(currentKey);
                CosmeticListener.removeAttachment(playerUuid, key);
                GeneratedModel.delete(playerUuid, key);
                changed = true;
            }
        }

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
    private static ModelAttachment buildAttachment(@Nonnull String playerUuid,
                                                   @Nonnull ToolSlot slot,
                                                   @Nonnull String slotKey,
                                                   @Nonnull String itemId) {

        GeneratedModel.delete(playerUuid, slotKey);

        String sourceModel = resolveModelPath(itemId);
        String texturePath = resolveTexturePath(itemId);

        GeneratedModel generated = GeneratedModel.create(
                playerUuid,
                slotKey,
                sourceModel
        );

        if (generated == null) {
            LOGGER.atWarning().log("Failed generating model for %s", itemId);
            return null;
        }

        return new ModelAttachment(
                generated.getModelPath(),
                texturePath,
                "Chest",
                "",
                1.0
        );
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