package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.TrueBackpack;
import com.supremosan.truebackpack.registries.BackpackRegistry;

public class BackpackNestingListener {
    private BackpackNestingListener() {
    }

    public static void register(TrueBackpack plugin) {
        plugin.getEventRegistry().registerGlobal(
                LivingEntityInventoryChangeEvent.class,
                BackpackNestingListener::handle
        );
    }

    private static void handle(LivingEntityInventoryChangeEvent event) {
        ItemContainer container = event.getItemContainer();
        if (container == null) return;

        Transaction transaction = event.getTransaction();
        if (transaction == null || !transaction.succeeded()) return;

        LivingEntity entity = event.getEntity();
        if (entity == null) return;

        boolean blocked = isBlockedContainer(entity, container);
        if (!blocked) return;

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (!transaction.wasSlotModified(slot)) continue;

            ItemStack item = container.getItemStack(slot);

            if (item == null || item.isEmpty()) continue;

            String itemId = item.getItem().getId();
            boolean isBackpack = BackpackRegistry.getByItem(itemId) != null;

            if (!isBackpack) continue;

            container.setItemStackForSlot(slot, ItemStack.EMPTY);
        }
    }

    private static boolean isBlockedContainer(LivingEntity entity, ItemContainer container) {
        Ref<EntityStore> ref = entity.getReference();
        if (ref == null || !ref.isValid()) {
            return true;
        }

        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return true;
        }

        Inventory inv = player.getInventory();

        boolean isHotbar = container == inv.getHotbar();
        boolean isStorage = container == inv.getStorage();
        boolean isArmor = container == inv.getArmor();
        boolean isUtility = container == inv.getUtility();
        boolean isTools = container == inv.getTools();

        return !isHotbar && !isStorage && !isArmor && !isUtility && !isTools;
    }
}