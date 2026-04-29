package com.supremosan.truebackpack.factory;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.supremosan.truebackpack.registries.HatRegistry;
import com.supremosan.truebackpack.registries.HatRegistry.HatEntry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public final class HatItemFactory {

    public static final KeyedCodec<String> INSTANCE_ID_CODEC =
            new KeyedCodec<>("Hat_instance_id", Codec.STRING);

    public static final KeyedCodec<Boolean> EQUIPPED_CODEC =
            new KeyedCodec<>("Hat_equipped", Codec.BOOLEAN);

    private HatItemFactory() {}

    @Nonnull
    public static ItemStack createHatInstance(@Nonnull ItemStack original) {
        HatEntry entry = HatRegistry.getByItem(original.getItemId());
        double maxDurability = entry != null ? entry.maxDurability() : original.getMaxDurability();
        return original
                .withRestoredDurability(maxDurability)
                .withMetadata(INSTANCE_ID_CODEC, UUID.randomUUID().toString())
                .withMetadata(EQUIPPED_CODEC, false);
    }

    @Nullable
    public static String getInstanceId(@Nonnull ItemStack stack) {
        return stack.getFromMetadataOrNull(INSTANCE_ID_CODEC);
    }

    public static boolean hasInstanceId(@Nonnull ItemStack stack) {
        return getInstanceId(stack) != null;
    }

    public static boolean isEquipped(@Nonnull ItemStack stack) {
        Boolean val = stack.getFromMetadataOrNull(EQUIPPED_CODEC);
        return val != null && val;
    }

    @Nonnull
    public static ItemStack setEquipped(@Nonnull ItemStack stack, boolean equipped) {
        return stack.withMetadata(EQUIPPED_CODEC, equipped);
    }

    public static int getDurability(@Nonnull ItemStack stack) {
        return (int) stack.getDurability();
    }

    @Nonnull
    public static ItemStack setDurability(@Nonnull ItemStack stack, int durability) {
        return stack.withDurability(durability);
    }

    public static boolean isBroken(@Nonnull ItemStack stack) {
        return stack.isBroken();
    }
}