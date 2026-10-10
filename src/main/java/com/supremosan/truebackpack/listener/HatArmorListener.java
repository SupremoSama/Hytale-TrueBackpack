package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.protocol.DynamicLightUpdate;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.supremosan.custominventory.api.EquipmentManager;
import com.supremosan.custominventory.api.ExtraEquipment;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.factory.HatItemFactory;
import com.supremosan.truebackpack.registries.HatRegistry;
import com.supremosan.truebackpack.registries.HatRegistry.HatEntry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hat model and light for CustomInventory's hat slot. CustomInventory reports slot changes and
 * renders {@link #attachment} while the slot's eye is open; this class adds the hat specifics.
 */
public final class HatArmorListener implements EquipmentManager.Listener {
    private static final short HEAD_SLOT = ExtraEquipment.HAT;

    private static final Map<String, String> LAST_KNOWN_EQUIPPED = new ConcurrentHashMap<>();
    private static final Set<String> PROCESSING_EQUIP = ConcurrentHashMap.newKeySet();

    public static final HatArmorListener INSTANCE = new HatArmorListener();

    private HatArmorListener() {
    }

    public static boolean hasEquippedHat(@Nonnull String playerUuid) {
        return LAST_KNOWN_EQUIPPED.containsKey(playerUuid);
    }

    @Override
    public void onEquipmentChanged(@Nonnull EquipmentManager.Change change) {
        Ref<EntityStore> ref = change.ref();
        Store<EntityStore> store = change.store();

        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;

        if (store.getComponent(ref, Player.getComponentType()) == null) return;

        ExtraEquipment storageComp = store.getComponent(ref, ExtraEquipment.getComponentType());
        if (storageComp == null) return;

        handleEquipChange(ref, store, change.commands(), storageComp, uuidComp.getUuid().toString());
    }

    @Override
    @Nullable
    public ModelAttachment attachment(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                      @Nonnull ItemStack equipped) {
        return resolveVisual(equipped.getItemId());
    }

    private void handleEquipChange(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull ExtraEquipment storageComp,
            @Nonnull String playerUuid) {
        if (!PROCESSING_EQUIP.add(playerUuid)) return;

        try {
            ItemStack headSlot = storageComp.getInventory().getItemStack(HEAD_SLOT);
            ItemStack currentEquipped = isHat(headSlot) ? headSlot : null;

            String lastInstanceId = LAST_KNOWN_EQUIPPED.get(playerUuid);

            if (currentEquipped != null) {
                String currentInstanceId = HatItemFactory.getInstanceId(currentEquipped);
                if (currentInstanceId != null && currentInstanceId.equals(lastInstanceId)) return;

                if (lastInstanceId != null) {
                    clearEquippedFlag(storageComp, lastInstanceId);
                }

                currentEquipped = ensureInstanceId(currentEquipped, storageComp.getInventory());
                String newInstanceId = HatItemFactory.getInstanceId(currentEquipped);
                LAST_KNOWN_EQUIPPED.put(playerUuid, newInstanceId);
                updateDynamicLight(ref, commandBuffer, store, currentEquipped);
            } else {
                if (lastInstanceId == null) return;

                clearEquippedFlag(storageComp, lastInstanceId);
                LAST_KNOWN_EQUIPPED.remove(playerUuid);
                removeDynamicLight(ref, commandBuffer);
            }
        } finally {
            PROCESSING_EQUIP.remove(playerUuid);
        }
    }

    private static void clearEquippedFlag(@Nonnull ExtraEquipment storageComp, @Nonnull String instanceId) {
        ItemContainer container = storageComp.getInventory();
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack candidate = container.getItemStack(slot);
            if (candidate == null || candidate.isEmpty()) continue;
            if (instanceId.equals(HatItemFactory.getInstanceId(candidate))) {
                if (HatItemFactory.isEquipped(candidate)) {
                    container.setItemStackForSlot(slot, HatItemFactory.setEquipped(candidate, false));
                }
                return;
            }
        }
    }

    @Nonnull
    private static ItemStack ensureInstanceId(
            @Nonnull ItemStack item,
            @Nonnull ItemContainer container) {
        boolean changed = false;
        if (!HatItemFactory.hasInstanceId(item)) {
            item = HatItemFactory.createHatInstance(item);
            changed = true;
        }
        if (!HatItemFactory.isEquipped(item)) {
            item = HatItemFactory.setEquipped(item, true);
            changed = true;
        }
        if (changed) container.setItemStackForSlot(HEAD_SLOT, item);
        return item;
    }

    private static void updateDynamicLight(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Store<EntityStore> store,
            @Nonnull ItemStack equippedItem) {
        ColorLight light = resolveLight(equippedItem.getItemId());
        if (light == null) {
            removeDynamicLight(ref, commandBuffer);
            return;
        }

        DynamicLight existing = commandBuffer.getComponent(ref, DynamicLight.getComponentType());
        if (existing != null) {
            existing.setColorLight(light);
            queueDynamicLightUpdate(ref, store, light);
        } else {
            commandBuffer.putComponent(ref, DynamicLight.getComponentType(), new DynamicLight(light));
        }
    }

    private static void removeDynamicLight(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (commandBuffer.getComponent(ref, DynamicLight.getComponentType()) != null) {
            commandBuffer.removeComponent(ref, DynamicLight.getComponentType());
        }
    }

    private static void queueDynamicLightUpdate(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull ColorLight light) {
        EntityTrackerSystems.Visible visible = store.getComponent(ref, EntityTrackerSystems.Visible.getComponentType());
        if (visible == null) return;

        DynamicLightUpdate update = new DynamicLightUpdate(light);
        for (EntityTrackerSystems.EntityViewer viewer : visible.visibleTo.values()) {
            viewer.queueUpdate(ref, update);
        }
    }

    @Nullable
    private static ModelAttachment resolveVisual(@Nullable String itemId) {
        if (itemId == null) return null;
        HatEntry entry = HatRegistry.getByItem(itemId);
        if (entry == null) return null;
        return new ModelAttachment(entry.modelPath(), entry.texturePath(), null, null, 1.0);
    }

    @Nullable
    private static ColorLight resolveLight(@Nullable String itemId) {
        if (itemId == null) return null;
        HatEntry entry = HatRegistry.getByItem(itemId);
        if (entry == null) return null;
        return entry.dynamicLight();
    }

    private static boolean isHat(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return HatRegistry.isHat(stack.getItemId());
    }

    public static void onPlayerRemove(@Nonnull String playerUuid) {
        LAST_KNOWN_EQUIPPED.remove(playerUuid);
        PROCESSING_EQUIP.remove(playerUuid);
    }
}
