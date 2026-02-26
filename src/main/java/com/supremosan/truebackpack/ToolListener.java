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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolListener extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String ATTACHMENT_SLOT_PREFIX = "truebackpack:tool:";

    public enum ToolSlot {
        BACK("Items/Tools/Back/Generic.blockymodel", ""),
        HIP_LEFT("Items/Tools/HipLeft/Generic.blockymodel", ""),
        HIP_RIGHT("Items/Tools/HipRight/Generic.blockymodel", "");

        private final String modelPath;
        private final String texturePath;

        ToolSlot(@Nonnull String modelPath, @Nonnull String texturePath) {
            this.modelPath = modelPath;
            this.texturePath = texturePath;
        }

        @Nonnull
        public String getModelPath() { return modelPath; }

        @Nonnull
        public String getTexturePath() { return texturePath; }
    }

    private static final Map<String, ToolSlot> PREFIX_REGISTRY = new LinkedHashMap<>();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final Map<String, Boolean> PROCESSING = new ConcurrentHashMap<>();

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
            CosmeticListener.removeAttachment(playerUuid, slotKey(slot));
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

        if (Boolean.TRUE.equals(PROCESSING.get(playerUuid))) return;
        if (CosmeticListener.isProcessing()) return;

        if (!(entity instanceof Player player)) return;

        ItemContainer hotbar = player.getInventory().getHotbar();
        if (event.getItemContainer() != hotbar) return;

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
        Map<ToolSlot, ModelAttachment> resolved = new LinkedHashMap<>();

        for (short slot = 0; slot < hotbar.getCapacity(); slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null || stack.isEmpty()) continue;

            String itemId = stack.getItemId();
            ToolSlot toolSlot = matchPrefix(itemId);
            if (toolSlot == null) continue;

            if (resolved.containsKey(toolSlot)) continue;

            resolved.put(toolSlot, buildAttachment(toolSlot));
        }

        boolean changed = false;

        for (ToolSlot slot : ToolSlot.values()) {
            String key = slotKey(slot);
            ModelAttachment attachment = resolved.get(slot);

            if (attachment != null) {
                CosmeticListener.putAttachment(playerUuid, key, attachment);
                changed = true;
            } else if (CosmeticListener.hasAttachment(playerUuid, key)) {
                CosmeticListener.removeAttachment(playerUuid, key);
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

    @Nonnull
    private static ModelAttachment buildAttachment(@Nonnull ToolSlot slot) {
        return new ModelAttachment(slot.getModelPath(), slot.getTexturePath(), "", "", 1.0);
    }

    @Nonnull
    private static String slotKey(@Nonnull ToolSlot slot) {
        return ATTACHMENT_SLOT_PREFIX + slot.name();
    }
}