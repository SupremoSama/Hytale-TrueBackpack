package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.TrueBackpack;
import com.supremosan.truebackpack.cosmetic.CosmeticPreferenceUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class QuiverListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String ATTACHMENT_SLOT_KEY = "truebackpack:quiver";
    private static final String ARROW_ID_FRAGMENT = "Weapon_Arrow";

    private static final ModelAttachment QUIVER_ATTACHMENT =
            new ModelAttachment("Items/Back/Quiver.blockymodel", "Items/Back/Quiver_Texture.png", "", "", 1.0);

    private static final ModelAttachment QUIVER_BACKPACK_ATTACHMENT =
            new ModelAttachment("Items/Quivers/Horizontal_Quiver.blockymodel", "Items/Quivers/Horizontal_Quiver_Texture.png", "", "", 1.0);

    private static final Map<String, Boolean> PLAYER_HAS_ARROW = new HashMap<>();

    private QuiverListener() {}

    public static void register(@Nonnull TrueBackpack plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new InventoryChangeSystem());
        plugin.getEntityStoreRegistry().registerSystem(new PlayerRemoveSystem());
        BackpackArmorListener.addEquipChangeListener(QuiverListener::onBackpackEquipChange);
        LOGGER.atInfo().log("[TrueBackpack] QuiverListener registered");
    }

    public static void syncQuiverAttachment(@Nonnull String playerUuid,
                                            @Nonnull Player player,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull Ref<EntityStore> ref) {

        InventoryComponent.Hotbar hotbarComp = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

        boolean hasArrow = hasArrowInInventory(hotbarComp, storageComp, backpackComp);
        PLAYER_HAS_ARROW.put(playerUuid, hasArrow);

        updateQuiver(playerUuid, player, store, ref, hasArrow);
    }

    private static void onBackpackEquipChange(@Nonnull String playerUuid,
                                              @Nonnull Player player,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull Ref<EntityStore> ref) {

        boolean hasArrow = PLAYER_HAS_ARROW.getOrDefault(playerUuid, false);
        updateQuiver(playerUuid, player, store, ref, hasArrow);
    }

    private static void handleInventoryChange(@Nonnull Ref<EntityStore> ref,
                                              @Nonnull Store<EntityStore> store) {

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;

        String playerUuid = uuidComponent.getUuid().toString();
        if (CosmeticListener.isProcessing()) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        InventoryComponent.Hotbar hotbarComp = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

        boolean hasArrowNow = hasArrowInInventory(hotbarComp, storageComp, backpackComp);
        boolean hadArrowBefore = PLAYER_HAS_ARROW.getOrDefault(playerUuid, false);

        if (hasArrowNow == hadArrowBefore) return;

        PLAYER_HAS_ARROW.put(playerUuid, hasArrowNow);
        updateQuiver(playerUuid, player, store, ref, hasArrowNow);
    }

    private static void updateQuiver(@Nonnull String playerUuid,
                                     @Nonnull Player player,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull Ref<EntityStore> ref,
                                     boolean hasArrow) {

        boolean visible = CosmeticPreferenceUtils.isQuiverVisible(store, ref);
        boolean shouldHave = hasArrow && visible;

        if (!shouldHave) {
            if (!CosmeticListener.hasAttachment(playerUuid, ATTACHMENT_SLOT_KEY)) return;
            CosmeticListener.removeAttachment(playerUuid, ATTACHMENT_SLOT_KEY);
            CosmeticListener.scheduleRebuild(player, store, ref, playerUuid);
            return;
        }

        ModelAttachment correct = BackpackArmorListener.hasEquippedBackpack(playerUuid)
                ? QUIVER_BACKPACK_ATTACHMENT
                : QUIVER_ATTACHMENT;

        ModelAttachment current = CosmeticListener.getAttachment(playerUuid, ATTACHMENT_SLOT_KEY);

        if (correct.equals(current)) return;

        CosmeticListener.putAttachment(playerUuid, ATTACHMENT_SLOT_KEY, correct);
        CosmeticListener.scheduleRebuild(player, store, ref, playerUuid);
    }

    private static boolean hasArrowInInventory(
            @Nullable InventoryComponent.Hotbar hotbarComp,
            @Nullable InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Backpack backpackComp) {

        ItemContainer[] containers = {
                hotbarComp != null ? hotbarComp.getInventory() : null,
                storageComp != null ? storageComp.getInventory() : null,
                backpackComp != null ? backpackComp.getInventory() : null
        };

        for (ItemContainer container : containers) {
            if (container == null) continue;

            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (stack == null || stack.isEmpty()) continue;

                if (stack.getItemId().contains(ARROW_ID_FRAGMENT)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static class InventoryChangeSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

        public InventoryChangeSystem() {
            super(InventoryChangeEvent.class);
        }

        @Override
        public void handle(int index,
                           @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer,
                           @Nonnull InventoryChangeEvent event) {

            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            handleInventoryChange(ref, store);
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Player.getComponentType();
        }
    }

    public static class PlayerRemoveSystem extends RefSystem<EntityStore> {

        @Override
        public void onEntityAdded(@Nonnull Ref<EntityStore> ref,
                                  @Nonnull AddReason reason,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        }

        @Override
        public void onEntityRemove(@Nonnull Ref<EntityStore> ref,
                                   @Nonnull RemoveReason reason,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull CommandBuffer<EntityStore> commandBuffer) {

            UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComp == null) return;

            String playerUuid = uuidComp.getUuid().toString();

            PLAYER_HAS_ARROW.remove(playerUuid);
            CosmeticListener.removeAttachment(playerUuid, ATTACHMENT_SLOT_KEY);
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Player.getComponentType();
        }
    }
}