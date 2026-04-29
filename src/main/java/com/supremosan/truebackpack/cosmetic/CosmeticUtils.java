package com.supremosan.truebackpack.cosmetic;

import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPartTexture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public final class CosmeticUtils {
    private static final double DEFAULT_SCALE = 1.0D;

    private CosmeticUtils() {
    }

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
                                                    @Nullable String textureId,
                                                    @Nullable String variantId,
                                                    @Nonnull String bodyGradientId) {
        if (part.getVariants() != null && !part.getVariants().isEmpty()) {
            PlayerSkinPart.Variant variant = get(part.getVariants(), variantId);
            if (variant == null) {
                return fromPlayerSkinPart(part, textureId != null ? textureId : bodyGradientId);
            }

            String model = variant.getModel();
            String texture;
            String gradientSet = null;
            String gradientId = null;

            if (variant.getTextures() != null) {
                PlayerSkinPartTexture tex = get(variant.getTextures(), textureId);
                if (tex == null) {
                    return fromPlayerSkinPart(part, textureId != null ? textureId : bodyGradientId);
                }
                texture = tex.getTexture();
            } else {
                texture = variant.getGreyscaleTexture();
                gradientSet = part.getGradientSet();
                gradientId = textureId;
            }

            return new ModelAttachment(model, texture, gradientSet, gradientId, DEFAULT_SCALE);
        }

        String model = part.getModel();
        String texture;
        String gradientSet = null;
        String gradientId = null;

        if (part.getTextures() != null) {
            PlayerSkinPartTexture tex = get(part.getTextures(), textureId);
            if (tex == null) {
                return fromPlayerSkinPart(part, textureId != null ? textureId : bodyGradientId);
            }
            texture = tex.getTexture();
        } else {
            texture = part.getGreyscaleTexture();
            gradientSet = part.getGradientSet();
            gradientId = textureId != null ? textureId : bodyGradientId;
        }

        return new ModelAttachment(model, texture, gradientSet, gradientId, DEFAULT_SCALE);
    }

    @Nonnull
    public static String[] splitId(@Nonnull String rawId) {
        return rawId.split("\\.", -1);
    }

    @Nullable
    public static String part(@Nonnull String[] parts, int index) {
        return (index < parts.length && !parts[index].isEmpty()) ? parts[index] : null;
    }

    @Nullable
    private static <K, V> V get(@Nullable Map<K, V> map, @Nullable K key) {
        return (map == null || key == null) ? null : map.get(key);
    }
}