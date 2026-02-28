package com.supremosan.truebackpack.cosmetic;

import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPartTexture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public final class CosmeticUtils {

    private static final double DEFAULT_SCALE = 1.0D;

    private CosmeticUtils() {}

    @Nonnull
    public static ModelAttachment fromPlayerSkinPart(@Nonnull PlayerSkinPart part,
                                                     @Nonnull String gradientId) {
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
                                                    @Nonnull String[] parts,
                                                    @Nonnull String bodyGradientId) {
        String a = parts.length > 1 ? parts[1] : null;
        String b = parts.length > 2 ? parts[2] : null;

        String partGradient = a != null ? a : bodyGradientId;

        ModelAttachment att;

        att = tryVariant(part, a, b, partGradient);
        if (att != null) return att;

        att = tryVariant(part, b, a, partGradient);
        if (att != null) return att;

        att = tryTexture(part, a, b, partGradient);
        if (att != null) return att;

        att = tryTexture(part, b, a, partGradient);
        if (att != null) return att;

        return fromPlayerSkinPart(part, partGradient);
    }

    @Nullable
    private static ModelAttachment tryVariant(@Nonnull PlayerSkinPart part,
                                              @Nullable String variantKey,
                                              @Nullable String gradientOrTextureKey,
                                              @Nonnull String fallbackGradient) {
        PlayerSkinPart.Variant variant = get(part.getVariants(), variantKey);
        if (variant == null) return null;

        PlayerSkinPartTexture tex = get(variant.getTextures(), gradientOrTextureKey);
        if (tex != null) {
            return new ModelAttachment(
                    variant.getModel(),
                    tex.getTexture(),
                    part.getGradientSet(),
                    fallbackGradient,
                    DEFAULT_SCALE
            );
        }

        String gradient = gradientOrTextureKey != null ? gradientOrTextureKey : fallbackGradient;
        return new ModelAttachment(
                variant.getModel(),
                variant.getGreyscaleTexture(),
                part.getGradientSet(),
                gradient,
                DEFAULT_SCALE
        );
    }

    @Nullable
    private static ModelAttachment tryTexture(@Nonnull PlayerSkinPart part,
                                              @Nullable String textureKey,
                                              @Nullable String gradientKey,
                                              @Nonnull String fallbackGradient) {
        PlayerSkinPartTexture tex = get(part.getTextures(), textureKey);
        if (tex == null) return null;

        String gradient = gradientKey != null ? gradientKey : fallbackGradient;
        return new ModelAttachment(
                part.getModel(),
                tex.getTexture(),
                part.getGradientSet(),
                gradient,
                DEFAULT_SCALE
        );
    }

    @Nullable
    private static <K, V> V get(@Nullable Map<K, V> map, @Nullable K key) {
        return (map == null || key == null) ? null : map.get(key);
    }
}