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
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.cosmetic.CosmeticListener;
import com.supremosan.truebackpack.helpers.CosmeticPreferenceUtils;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QuiverListener extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String ATTACHMENT_SLOT_KEY = "truebackpack:quiver";
    private static final String ARROW_ID_FRAGMENT = "Weapon_Arrow";

    private static final ModelAttachment QUIVER_ATTACHMENT =
            new ModelAttachment("Items/Back/Quiver.blockymodel", "Items/Back/Quiver_Texture.png", "", "", 1.0);

    private static final ModelAttachment QUIVER_BACKPACK_ATTACHMENT =
            new ModelAttachment("Items/Quivers/Horizontal_Quiver.blockymodel", "Items/Quivers/Horizontal_Quiver_Texture.png", "", "", 1.0);

    private static final Query<EntityStore> QUERY = Query.any();
    private static final Map<String, Boolean> PROCESSING = new ConcurrentHashMap<>();

    private static QuiverListener INSTANCE;

    public QuiverListener() {
        INSTANCE = this;
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        INSTANCE = new QuiverListener();
        plugin.getEntityStoreRegistry().registerSystem(INSTANCE);

        plugin.getEventRegistry().registerGlobal(
                LivingEntityInventoryChangeEvent.class,
                event -> INSTANCE.handle(event));

        BackpackArmorListener.addEquipChangeListener(INSTANCE::onBackpackEquipChange);

        LOGGER.atInfo().log("[TrueBackpack] QuiverListener registered");
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
        CosmeticListener.removeAttachment(playerUuid, ATTACHMENT_SLOT_KEY);
    }

    public static void syncQuiverAttachment(@Nonnull String playerUuid,
                                            @Nonnull Player player,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull Ref<EntityStore> ref) {
        boolean hasArrow = hasArrowInInventory(player.getInventory());
        if (!hasArrow) return;
        ModelAttachment quiver = BackpackArmorListener.hasEquippedBackpack(playerUuid)
                ? QUIVER_BACKPACK_ATTACHMENT
                : QUIVER_ATTACHMENT;
        CosmeticListener.putAttachment(playerUuid, ATTACHMENT_SLOT_KEY, quiver);
    }

    private void onBackpackEquipChange(@Nonnull String playerUuid, @Nonnull Player player,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> ref) {
        if (Boolean.TRUE.equals(PROCESSING.get(playerUuid))) return;
        if (!CosmeticListener.hasAttachment(playerUuid, ATTACHMENT_SLOT_KEY)) return;

        if (!CosmeticPreferenceUtils.isQuiverVisible(store, ref)) return;
        ModelAttachment quiver = BackpackArmorListener.hasEquippedBackpack(playerUuid)
                ? QUIVER_BACKPACK_ATTACHMENT
                : QUIVER_ATTACHMENT;

        CosmeticListener.putAttachment(playerUuid, ATTACHMENT_SLOT_KEY, quiver);
        CosmeticListener.scheduleRebuild(player, store, ref, playerUuid);
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

        PROCESSING.put(playerUuid, Boolean.TRUE);
        try {
            boolean hasArrow = hasArrowInInventory(player.getInventory());
            boolean quiverVisible = CosmeticPreferenceUtils.isQuiverVisible(store, ref);

            if (hasArrow && quiverVisible) {
                ModelAttachment quiver = BackpackArmorListener.hasEquippedBackpack(playerUuid)
                        ? QUIVER_BACKPACK_ATTACHMENT
                        : QUIVER_ATTACHMENT;
                CosmeticListener.putAttachment(playerUuid, ATTACHMENT_SLOT_KEY, quiver);
            } else {
                CosmeticListener.removeAttachment(playerUuid, ATTACHMENT_SLOT_KEY);
            }

            CosmeticListener.scheduleRebuild(player, store, ref, playerUuid);
        } finally {
            PROCESSING.remove(playerUuid);
        }
    }

    private static boolean hasArrowInInventory(@Nonnull Inventory inv) {
        ItemContainer[] containers = {
                inv.getArmor(), inv.getStorage(), inv.getBackpack(), inv.getHotbar()
        };
        for (ItemContainer container : containers) {
            if (container == null) continue;
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (stack == null || stack.isEmpty()) continue;
                if (stack.getItemId().contains(ARROW_ID_FRAGMENT)) return true;
            }
        }
        return false;
    }
}