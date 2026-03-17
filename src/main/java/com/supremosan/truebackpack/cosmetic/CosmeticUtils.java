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
                                                    @Nonnull String[] parts,
                                                    @Nonnull String bodyGradientId) {
        String wardrobeVariantId = parts.length > 1 ? parts[1] : null;
        String wardrobeOptionId = parts.length > 2 ? parts[2] : null;

        PlayerSkinPart.Variant variant = get(part.getVariants(), wardrobeOptionId);
        if (variant != null) {
            if (variant.getTextures() != null) {
                PlayerSkinPartTexture tex = get(variant.getTextures(), wardrobeVariantId);
                if (tex != null) {
                    return new ModelAttachment(
                            variant.getModel(),
                            tex.getTexture(),
                            null,
                            null,
                            DEFAULT_SCALE
                    );
                }
            } else {
                return new ModelAttachment(
                        variant.getModel(),
                        variant.getGreyscaleTexture(),
                        part.getGradientSet(),
                        wardrobeVariantId != null ? wardrobeVariantId : bodyGradientId,
                        DEFAULT_SCALE
                );
            }
        }

        if (part.getTextures() != null) {
            PlayerSkinPartTexture tex = get(part.getTextures(), wardrobeVariantId);
            if (tex != null) {
                return new ModelAttachment(
                        part.getModel(),
                        tex.getTexture(),
                        null,
                        null,
                        DEFAULT_SCALE
                );
            }
        }

        return fromPlayerSkinPart(part, wardrobeVariantId != null ? wardrobeVariantId : bodyGradientId);
    }

    @Nullable
    private static <K, V> V get(@Nullable Map<K, V> map, @Nullable K key) {
        return (map == null || key == null) ? null : map.get(key);
    }
}