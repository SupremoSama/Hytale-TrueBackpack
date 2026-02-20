package com.supremosan.truebackpack.helpers;

import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPartTexture;

import java.util.Map;

public final class BackpackUtils {

    private static final double DEFAULT_SCALE = 1.0D;

    private BackpackUtils() {}

    public static ModelAttachment fromPlayerSkinPart(PlayerSkinPart part, String gradientId) {
        return new ModelAttachment(
                part.getModel(),
                part.getGreyscaleTexture(),
                part.getGradientSet(),
                gradientId,
                DEFAULT_SCALE
        );
    }

    public static ModelAttachment resolveAttachment(PlayerSkinPart part, String[] parts, String defaultGradientId) {
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

    private static ModelAttachment tryVariant(PlayerSkinPart part, String variantKey, String textureKey, String defaultGradientId) {
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

    private static ModelAttachment tryTexture(PlayerSkinPart part, String textureKey, String gradientKey, String defaultGradientId) {
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

    private static <K, V> V get(Map<K, V> map, K key) {
        return (map == null || key == null) ? null : map.get(key);
    }
}