package com.supremosan.truebackpack.cosmetic;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

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

    public static boolean isBackpackVisible(@Nonnull Store<EntityStore> store,
                                            @Nonnull Ref<EntityStore> ref) {
        CosmeticPreference pref = store.getComponent(ref, CosmeticPreference.TYPE);
        return pref == null || pref.isShowBackpack();
    }

    public static boolean isQuiverVisible(@Nonnull Store<EntityStore> store,
                                          @Nonnull Ref<EntityStore> ref) {
        CosmeticPreference pref = store.getComponent(ref, CosmeticPreference.TYPE);
        return pref == null || pref.isShowQuiver();
    }

    public static void setBackpackVisible(@Nonnull Store<EntityStore> store,
                                          @Nonnull Ref<EntityStore> ref,
                                          boolean visible) {
        CosmeticPreference pref = getOrCreate(store, ref);
        pref.setShowBackpack(visible);
        store.replaceComponent(ref, CosmeticPreference.TYPE, pref);
    }

    public static void setQuiverVisible(@Nonnull Store<EntityStore> store,
                                        @Nonnull Ref<EntityStore> ref,
                                        boolean visible) {
        CosmeticPreference pref = getOrCreate(store, ref);
        pref.setShowQuiver(visible);
        store.replaceComponent(ref, CosmeticPreference.TYPE, pref);
    }

    public static boolean toggleBackpack(@Nonnull Store<EntityStore> store,
                                         @Nonnull Ref<EntityStore> ref) {
        boolean next = !isBackpackVisible(store, ref);
        setBackpackVisible(store, ref, next);
        return next;
    }

    public static boolean toggleQuiver(@Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> ref) {
        boolean next = !isQuiverVisible(store, ref);
        setQuiverVisible(store, ref, next);
        return next;
    }
}