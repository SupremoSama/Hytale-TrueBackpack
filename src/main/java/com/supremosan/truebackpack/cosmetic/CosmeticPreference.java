package com.supremosan.truebackpack.cosmetic;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.supremosan.truebackpack.TrueBackpack;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nonnull;

/**
 * TrueBackpack-only cosmetic preferences. Backpack and hat visibility moved to CustomInventory's
 * per-slot equipment visibility; ShowBackpack/ShowHat are read once to migrate older saves.
 */
public final class CosmeticPreference implements Component<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> {

    public static ComponentType<com.hypixel.hytale.server.core.universe.world.storage.EntityStore, CosmeticPreference> TYPE;

    public static final BuilderCodec<CosmeticPreference> CODEC =
            BuilderCodec.builder(CosmeticPreference.class, CosmeticPreference::new)
                    .append(
                            new KeyedCodec<>("ShowBackpack", Codec.BOOLEAN),
                            (p, v) -> p.showBackpack = v,
                            (p) -> p.showBackpack
                    ).add()
                    .append(
                            new KeyedCodec<>("ShowQuiver", Codec.BOOLEAN),
                            (p, v) -> p.showQuiver = v,
                            (p) -> p.showQuiver
                    ).add()
                    .append(
                            new KeyedCodec<>("ShowHat", Codec.BOOLEAN),
                            (p, v) -> p.showHat = v,
                            (p) -> p.showHat
                    ).add()
                    .append(
                            new KeyedCodec<>("EquipmentVisibilityMigrated", Codec.BOOLEAN),
                            (p, v) -> p.equipmentVisibilityMigrated = v,
                            (p) -> p.equipmentVisibilityMigrated
                    ).add()
                    .build();

    private boolean showBackpack = true;
    private boolean showQuiver = true;
    private boolean showHat = true;
    private boolean equipmentVisibilityMigrated;

    public CosmeticPreference() {
    }

    private CosmeticPreference(boolean showBackpack, boolean showQuiver, boolean showHat,
                               boolean equipmentVisibilityMigrated) {
        this.showBackpack = showBackpack;
        this.showQuiver = showQuiver;
        this.showHat = showHat;
        this.equipmentVisibilityMigrated = equipmentVisibilityMigrated;
    }

    /** Legacy value; only read by the CustomInventory migration. */
    public boolean isShowBackpack() {
        return showBackpack;
    }

    public boolean isShowQuiver() {
        return showQuiver;
    }

    /** Legacy value; only read by the CustomInventory migration. */
    public boolean isShowHat() {
        return showHat;
    }

    public boolean isEquipmentVisibilityMigrated() {
        return equipmentVisibilityMigrated;
    }

    public void setShowQuiver(boolean showQuiver) {
        this.showQuiver = showQuiver;
    }

    /** Resets the legacy values so a repeated migration cannot hide anything again. */
    public void markEquipmentVisibilityMigrated() {
        this.showBackpack = true;
        this.showHat = true;
        this.equipmentVisibilityMigrated = true;
    }

    @Override
    public @NonNull Component<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> clone() {
        return new CosmeticPreference(showBackpack, showQuiver, showHat, equipmentVisibilityMigrated);
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        TYPE = plugin.getEntityStoreRegistry().registerComponent(
                CosmeticPreference.class,
                "TrueBackpack_CosmeticPreference",
                CODEC
        );
    }
}
