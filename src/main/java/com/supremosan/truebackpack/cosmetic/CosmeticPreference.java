package com.supremosan.truebackpack.cosmetic;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.supremosan.truebackpack.TrueBackpack;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nonnull;

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
                    .build();

    private boolean showBackpack = true;
    private boolean showQuiver = true;
    private boolean showHat = true;

    public CosmeticPreference() {
    }

    private CosmeticPreference(boolean showBackpack, boolean showQuiver, boolean showHat) {
        this.showBackpack = showBackpack;
        this.showQuiver = showQuiver;
        this.showHat = showHat;
    }

    public boolean isShowBackpack() {
        return showBackpack;
    }

    public boolean isShowQuiver() {
        return showQuiver;
    }

    public boolean isShowHat() {
        return showHat;
    }

    public void setShowBackpack(boolean showBackpack) {
        this.showBackpack = showBackpack;
    }

    public void setShowQuiver(boolean showQuiver) {
        this.showQuiver = showQuiver;
    }

    public void setShowHat(boolean showHat) {
        this.showHat = showHat;
    }

    @Override
    public @NonNull Component<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> clone() {
        return new CosmeticPreference(showBackpack, showQuiver, showHat);
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        TYPE = plugin.getEntityStoreRegistry().registerComponent(
                CosmeticPreference.class,
                "TrueBackpack_CosmeticPreference",
                CODEC
        );
    }
}