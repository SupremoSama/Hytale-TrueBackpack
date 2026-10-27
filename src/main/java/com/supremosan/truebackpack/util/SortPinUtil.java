package com.supremosan.truebackpack.util;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SortPinUtil {

    public static final KeyedCodec<Boolean> PINNED_CODEC =
            new KeyedCodec<>("TrueBackpack_sort_pinned", Codec.BOOLEAN);

    private SortPinUtil() {}

    public static boolean isPinned(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Boolean val = stack.getFromMetadataOrNull(PINNED_CODEC);
        return val != null && val;
    }

    @Nonnull
    public static ItemStack setPinned(@Nonnull ItemStack stack, boolean pinned) {
        return stack.withMetadata(PINNED_CODEC, pinned);
    }
}
