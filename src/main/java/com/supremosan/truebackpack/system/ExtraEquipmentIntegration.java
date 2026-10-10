package com.supremosan.truebackpack.system;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.custominventory.api.EquipmentManager;
import com.supremosan.custominventory.api.ExtraEquipment;
import com.supremosan.custominventory.api.InventoryRegistry;
import com.supremosan.truebackpack.TrueBackpack;
import com.supremosan.truebackpack.cosmetic.CosmeticPreferenceUtils;
import com.supremosan.truebackpack.listener.BackpackArmorListener;
import com.supremosan.truebackpack.listener.HatArmorListener;
import com.supremosan.truebackpack.registries.BackpackRegistry;
import com.supremosan.truebackpack.registries.HatRegistry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Connects TrueBackpack item types to CustomInventory's equipment slots. CustomInventory reports
 * equip changes and owns each slot's visibility; TrueBackpack applies the item-specific effects.
 */
public final class ExtraEquipmentIntegration {
    private static final List<InventoryRegistry.Registration> REGISTRATIONS = new CopyOnWriteArrayList<>();

    private ExtraEquipmentIntegration() { }
    public static void register(TrueBackpack plugin) {
        ExtraEquipment.registerItems(ExtraEquipment.HAT, item -> HatRegistry.isHat(item.getItemId()));
        ExtraEquipment.registerItems(ExtraEquipment.BACKPACK, item -> BackpackRegistry.isBackpack(item.getItemId()));
        REGISTRATIONS.add(EquipmentManager.addListener(ExtraEquipment.HAT, HatArmorListener.INSTANCE));
        REGISTRATIONS.add(EquipmentManager.addListener(ExtraEquipment.BACKPACK, BackpackArmorListener.SLOT_LISTENER));
        plugin.getEntityStoreRegistry().registerSystem(new ClearUnequippedFlags());
        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            var ref = event.getPlayerRef();
            var store = ref.getStore();
            store.getExternalData().getWorld().execute(() -> initialize(ref, store));
        });
    }

    public static void unregister() {
        for (var registration : REGISTRATIONS) registration.close();
        REGISTRATIONS.clear();
    }

    /** Equipment metadata follows the slot, including swaps, shift-clicks and hotbar use. */
    public static final class ClearUnequippedFlags extends com.hypixel.hytale.component.system.EntityEventSystem<EntityStore,
            com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent> {
        public ClearUnequippedFlags() { super(com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent.class); }
        @Override public com.hypixel.hytale.component.query.Query<EntityStore> getQuery() { return ExtraEquipment.TYPE; }
        @Override public void handle(int index, com.hypixel.hytale.component.ArchetypeChunk<EntityStore> chunk,
                Store<EntityStore> store, com.hypixel.hytale.component.CommandBuffer<EntityStore> buffer,
                com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent event) {
            if (event.getComponentType() == ExtraEquipment.TYPE) return;
            var container = event.getItemContainer();
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                var item = container.getItemStack(slot);
                if (ItemStack.isEmpty(item)) continue;
                if (BackpackRegistry.isBackpack(item.getItemId()) && com.supremosan.truebackpack.factory.BackpackItemFactory.isEquipped(item))
                    container.setItemStackForSlot(slot, com.supremosan.truebackpack.factory.BackpackItemFactory.setEquipped(item, false));
                else if (HatRegistry.isHat(item.getItemId()) && com.supremosan.truebackpack.factory.HatItemFactory.isEquipped(item))
                    container.setItemStackForSlot(slot, com.supremosan.truebackpack.factory.HatItemFactory.setEquipped(item, false));
            }
        }
    }

    /** CustomInventory re-announces occupied slots on ready; this only migrates older saves. */
    private static void initialize(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!ref.isValid() || ref.getStore() != store) return;
        var equipment = ExtraEquipment.ensure(ref, store);
        if (!equipment.isLegacyTrueBackpackMigrated()) {
            var armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
            var storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
            if (armor != null) migrate(armor.getInventory(), (short) 1, equipment, ExtraEquipment.BACKPACK);
            if (storage != null) {
                migrate(storage.getInventory(), (short) 0, equipment, ExtraEquipment.BACKPACK);
                migrate(storage.getInventory(), (short) 1, equipment, ExtraEquipment.HAT);
            }
            equipment.markLegacyTrueBackpackMigrated();
        }
        CosmeticPreferenceUtils.migrateEquipmentVisibility(store, ref);
    }

    private static void migrate(ItemContainer source, short sourceSlot, ExtraEquipment equipment, short targetSlot) {
        ItemStack item = source.getItemStack(sourceSlot);
        if (!ItemStack.isEmpty(item) && ExtraEquipment.accepts(targetSlot, item)
                && ItemStack.isEmpty(equipment.getInventory().getItemStack(targetSlot))) {
            source.moveItemStackFromSlotToSlot(sourceSlot, item.getQuantity(), equipment.getInventory(), targetSlot);
        }
    }
}
