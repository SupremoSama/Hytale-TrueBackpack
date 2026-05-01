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
        return attachment(
                part.getModel(),
                part.getGreyscaleTexture(),
                part.getGradientSet(),
                gradientId
        );
    }

    @Nonnull
    public static ModelAttachment resolveAttachment(@Nonnull PlayerSkinPart part,
                                                    @Nullable String textureId,
                                                    @Nullable String variantId,
                                                    @Nonnull String bodyGradientId) {
        String fallbackGradientId = fallback(textureId, bodyGradientId);
        Map<String, PlayerSkinPart.Variant> variants = part.getVariants();

        if (hasEntries(variants)) {
            PlayerSkinPart.Variant variant = get(variants, variantId);
            if (variant == null) {
                return fromPlayerSkinPart(part, fallbackGradientId);
            }

            return resolveVariantAttachment(part, variant, textureId, fallbackGradientId);
        }

        return resolvePartAttachment(part, textureId, fallbackGradientId);
    }

    @Nonnull
    public static String[] splitId(@Nonnull String rawId) {
        return rawId.split("\\.", -1);
    }

    @Nullable
    public static String part(@Nonnull String[] parts, int index) {
        if (index < 0 || index >= parts.length) {
            return null;
        }

        String value = parts[index];
        return value == null || value.isEmpty() ? null : value;
    }

    @Nullable
    public static String assetId(@Nonnull String rawId) {
        return part(splitId(rawId), 0);
    }

    @Nullable
    public static String textureId(@Nonnull String rawId) {
        return part(splitId(rawId), 1);
    }

    @Nullable
    public static String variantId(@Nonnull String rawId) {
        return part(splitId(rawId), 2);
    }

    @Nonnull
    public static String fallback(@Nullable String value, @Nonnull String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    @Nonnull
    private static ModelAttachment resolveVariantAttachment(@Nonnull PlayerSkinPart part,
                                                            @Nonnull PlayerSkinPart.Variant variant,
                                                            @Nullable String textureId,
                                                            @Nonnull String fallbackGradientId) {
        Map<String, PlayerSkinPartTexture> textures = variant.getTextures();

        if (hasEntries(textures)) {
            PlayerSkinPartTexture texture = get(textures, textureId);
            if (texture == null) {
                return fromPlayerSkinPart(part, fallbackGradientId);
            }

            return attachment(variant.getModel(), texture.getTexture(), null, null);
        }

        return attachment(
                variant.getModel(),
                variant.getGreyscaleTexture(),
                part.getGradientSet(),
                fallbackGradientId
        );
    }

    @Nonnull
    private static ModelAttachment resolvePartAttachment(@Nonnull PlayerSkinPart part,
                                                         @Nullable String textureId,
                                                         @Nonnull String fallbackGradientId) {
        Map<String, PlayerSkinPartTexture> textures = part.getTextures();

        if (hasEntries(textures)) {
            PlayerSkinPartTexture texture = get(textures, textureId);
            if (texture == null) {
                return fromPlayerSkinPart(part, fallbackGradientId);
            }

            return attachment(part.getModel(), texture.getTexture(), null, null);
        }

        return attachment(
                part.getModel(),
                part.getGreyscaleTexture(),
                part.getGradientSet(),
                fallbackGradientId
        );
    }

    @Nonnull
    private static ModelAttachment attachment(@Nullable String model,
                                              @Nullable String texture,
                                              @Nullable String gradientSet,
                                              @Nullable String gradientId) {
        return new ModelAttachment(model, texture, gradientSet, gradientId, DEFAULT_SCALE);
    }

    private static <K, V> boolean hasEntries(@Nullable Map<K, V> map) {
        return map != null && !map.isEmpty();
    }

    @Nullable
    private static <K, V> V get(@Nullable Map<K, V> map, @Nullable K key) {
        return map == null || key == null ? null : map.get(key);
    }
}