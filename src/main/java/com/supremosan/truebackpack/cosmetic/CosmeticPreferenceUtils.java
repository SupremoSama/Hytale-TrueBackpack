package com.supremosan.truebackpack.cosmetic;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.custominventory.api.EquipmentManager;
import com.supremosan.custominventory.api.ExtraEquipment;
import com.supremosan.truebackpack.registries.BackpackRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Backpack and hat visibility is CustomInventory's per-slot state (the Gear eyes), which it applies
 * when rendering. Only the quiver, which is not an equipment slot, keeps a TrueBackpack preference.
 */
public final class CosmeticPreferenceUtils {

    private CosmeticPreferenceUtils() {
    }

    @Nonnull
    public static CosmeticPreference getOrCreate(@Nonnull Store<EntityStore> store,
                                                 @Nonnull Ref<EntityStore> ref) {
        CosmeticPreference pref = store.getComponent(ref, CosmeticPreference.TYPE);
        if (pref == null) {
            pref = new CosmeticPreference();
            store.addComponent(ref, CosmeticPreference.TYPE, pref);
        }
        return pref;
    }

    /** Helipacks are functional equipment and stay visible independently of the slot's eye. */
    public static boolean isAlwaysVisibleBackpack(@Nullable ItemStack equipped) {
        if (ItemStack.isEmpty(equipped)) return false;
        var entry = BackpackRegistry.getByItem(equipped.getItemId());
        return entry != null && entry.isHelipack();
    }

    public static boolean isQuiverVisible(@Nonnull Store<EntityStore> store,
                                          @Nonnull Ref<EntityStore> ref) {
        CosmeticPreference pref = store.getComponent(ref, CosmeticPreference.TYPE);
        return pref == null || pref.isShowQuiver();
    }

    public static void setQuiverVisible(@Nonnull Store<EntityStore> store,
                                        @Nonnull Ref<EntityStore> ref,
                                        boolean visible) {
        CosmeticPreference pref = getOrCreate(store, ref);
        pref.setShowQuiver(visible);
        store.replaceComponent(ref, CosmeticPreference.TYPE, pref);
    }

    public static boolean toggleQuiver(@Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> ref) {
        boolean next = !isQuiverVisible(store, ref);
        setQuiverVisible(store, ref, next);
        return next;
    }

    /** Moves hidden backpack/hat preferences from older saves into the matching Gear slots, once. */
    public static void migrateEquipmentVisibility(@Nonnull Store<EntityStore> store,
                                                  @Nonnull Ref<EntityStore> ref) {
        CosmeticPreference pref = store.getComponent(ref, CosmeticPreference.TYPE);
        if (pref == null || pref.isEquipmentVisibilityMigrated()) return;
        if (!pref.isShowBackpack()) EquipmentManager.setVisible(ref, store, ExtraEquipment.BACKPACK, false);
        if (!pref.isShowHat()) EquipmentManager.setVisible(ref, store, ExtraEquipment.HAT, false);
        pref.markEquipmentVisibilityMigrated();
        store.replaceComponent(ref, CosmeticPreference.TYPE, pref);
    }
}
