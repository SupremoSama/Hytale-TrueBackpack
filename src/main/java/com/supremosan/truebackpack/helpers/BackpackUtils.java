package com.supremosan.truebackpack.helpers;

import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPartTexture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public final class BackpackUtils {

    private static final double DEFAULT_SCALE = 1.0D;

    private BackpackUtils() {}

    @Nonnull
    public static ModelAttachment fromPlayerSkinPart(@Nonnull PlayerSkinPart part,
                                                     @Nonnull String        gradientId) {
        return new ModelAttachment(
                part.getModel(),
                part.getGreyscaleTexture(),
                part.getGradientSet(),
                gradientId,
                DEFAULT_SCALE
        );
    }

    @Nonnull
    public static ModelAttachment resolveAttachment(@Nonnull PlayerSkinPart part,
                                                    @Nonnull String[]       parts,
                                                    @Nonnull String         defaultGradientId) {
        String a = parts.length > 1 ? parts[1] : null;
        String b = parts.length > 2 ? parts[2] : null;

        ModelAttachment att;

        att = tryVariant(part, a, b, defaultGradientId);
        if (att != null) return att;

        att = tryVariant(part, b, a, defaultGradientId);
        if (att != null) return att;

        att = tryTexture(part, a, b, defaultGradientId);
        if (att != null) return att;

        att = tryTexture(part, b, a, defaultGradientId);
        if (att != null) return att;

        return fromPlayerSkinPart(part, a != null ? a : defaultGradientId);
    }

    @Nullable
    private static ModelAttachment tryVariant(@Nonnull  PlayerSkinPart part,
                                              @Nullable String         variantKey,
                                              @Nullable String         textureKey,
                                              @Nonnull  String         defaultGradientId) {
        PlayerSkinPart.Variant variant = get(part.getVariants(), variantKey);
        if (variant == null) return null;

        PlayerSkinPartTexture tex = get(variant.getTextures(), textureKey);
        if (tex != null) {
            return new ModelAttachment(
                    variant.getModel(),
                    tex.getTexture(),
                    part.getGradientSet(),
                    defaultGradientId,
                    DEFAULT_SCALE
            );
        }

        return new ModelAttachment(
                variant.getModel(),
                variant.getGreyscaleTexture(),
                part.getGradientSet(),
                textureKey != null ? textureKey : defaultGradientId,
                DEFAULT_SCALE
        );
    }

    @Nullable
    private static ModelAttachment tryTexture(@Nonnull  PlayerSkinPart part,
                                              @Nullable String         textureKey,
                                              @Nullable String         gradientKey,
                                              @Nonnull  String         defaultGradientId) {
        PlayerSkinPartTexture tex = get(part.getTextures(), textureKey);
        if (tex == null) return null;

        return new ModelAttachment(
                part.getModel(),
                tex.getTexture(),
                part.getGradientSet(),
                gradientKey != null ? gradientKey : defaultGradientId,
                DEFAULT_SCALE
        );
    }

    @Nullable
    private static <K, V> V get(@Nullable Map<K, V> map, @Nullable K key) {
        return (map == null || key == null) ? null : map.get(key);
    }
}