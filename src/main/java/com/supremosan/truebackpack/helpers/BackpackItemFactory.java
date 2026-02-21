package com.supremosan.truebackpack.helpers;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BackpackItemFactory {

    public static final KeyedCodec<String> INSTANCE_ID_CODEC =
            new KeyedCodec<>("Backpack_instance_id", Codec.STRING);

    public static final KeyedCodec<Boolean> EQUIPPED_CODEC =
            new KeyedCodec<>("Backpack_equipped", Codec.BOOLEAN);

    private static final String CONTENTS_KEY = "Backpack_contents";

    private BackpackItemFactory() {}

    @Nonnull
    public static ItemStack createBackpackInstance(@Nonnull ItemStack original) {
        return original.withMetadata(INSTANCE_ID_CODEC, UUID.randomUUID().toString());
    }

    @Nullable
    public static String getInstanceId(@Nonnull ItemStack stack) {
        return stack.getFromMetadataOrNull(INSTANCE_ID_CODEC);
    }

    public static boolean hasInstanceId(@Nonnull ItemStack stack) {
        return getInstanceId(stack) != null;
    }

    @Nonnull
    public static ItemStack setEquipped(@Nonnull ItemStack backpack, boolean equipped) {
        return backpack.withMetadata(EQUIPPED_CODEC, equipped);
    }

    public static boolean isEquipped(@Nonnull ItemStack backpack) {
        Boolean val = backpack.getFromMetadataOrNull(EQUIPPED_CODEC);
        return val != null && val;
    }


    @Nonnull
    public static ItemStack saveContents(@Nonnull ItemStack backpack,
                                         @Nonnull List<ItemStack> contents) {
        BsonArray array = new BsonArray();
        for (ItemStack item : contents) {
            if (item == null || item.isEmpty()) {
                array.add(BsonNull.VALUE);
            } else {
                array.add(ItemStack.CODEC.encode(item));
            }
        }
        return backpack.withMetadata(CONTENTS_KEY, array);
    }

    @Nonnull
    public static List<ItemStack> loadContents(@Nonnull ItemStack backpack) {
        BsonDocument meta = backpack.getMetadata();
        if (meta == null || !meta.containsKey(CONTENTS_KEY)) return new ArrayList<>();

        BsonArray array = meta.getArray(CONTENTS_KEY, new BsonArray());
        List<ItemStack> result = new ArrayList<>();
        for (BsonValue val : array) {
            if (val == null || val.isNull()) {
                result.add(null);
            } else {
                result.add(ItemStack.CODEC.decode(val));
            }
        }
        return result;
    }

    public static boolean hasContents(@Nonnull ItemStack backpack) {
        return loadContents(backpack).stream().anyMatch(i -> i != null && !i.isEmpty());
    }

    @Nonnull
    public static ItemStack clearContents(@Nonnull ItemStack backpack) {
        BsonDocument meta = backpack.getMetadata();
        if (meta == null || !meta.containsKey(CONTENTS_KEY)) {
            return backpack;
        }
        return backpack.withMetadata(CONTENTS_KEY, BsonNull.VALUE);
    }
}