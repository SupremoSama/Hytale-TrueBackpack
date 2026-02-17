package com.supremosan.truebackpack.helpers;

import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.*;

import javax.annotation.Nonnull;
import java.util.*;

public class BackpackDataStorage {

    private static final String BACKPACK_CONTENTS_KEY = "BackpackContents";

    @Nonnull
    public static ItemStack saveContentsToItem(@Nonnull ItemStack backpackItem, @Nonnull List<ItemStack> contents) {
        if (contents.isEmpty()) {
            return clearContentsFromItem(backpackItem);
        }

        BsonArray contentsArray = new BsonArray();

        for (ItemStack item : contents) {
            if (item == null || item.isEmpty()) {
                contentsArray.add(new BsonNull());
                continue;
            }

            BsonDocument itemDoc = new BsonDocument()
                    .append("id", new BsonString(item.getItemId()))
                    .append("quantity", new BsonInt32(item.getQuantity()))
                    .append("durability", new BsonDouble(item.getDurability()))
                    .append("maxDurability", new BsonDouble(item.getMaxDurability()));

            if (item.getMetadata() != null) {
                itemDoc.put("metadata", item.getMetadata());
            }

            contentsArray.add(itemDoc);
        }

        return backpackItem.withMetadata(BACKPACK_CONTENTS_KEY, contentsArray);
    }

    @Nonnull
    public static List<ItemStack> loadContentsFromItem(@Nonnull ItemStack backpackItem) {
        return loadFromMetadata(backpackItem.getMetadata());
    }

    @Nonnull
    public static List<ItemStack> loadContentsFromItem(@Nonnull ItemWithAllMetadata backpackItem) {
        if (backpackItem.metadata == null || backpackItem.metadata.isEmpty()) {
            return new ArrayList<>();
        }
        return loadFromMetadata(BsonDocument.parse(backpackItem.metadata));
    }

    @Nonnull
    private static List<ItemStack> loadFromMetadata(BsonDocument metadata) {
        List<ItemStack> result = new ArrayList<>();

        if (metadata == null || !metadata.containsKey(BACKPACK_CONTENTS_KEY)) {
            return result;
        }

        BsonValue value = metadata.get(BACKPACK_CONTENTS_KEY);
        if (!value.isArray()) {
            return result;
        }

        for (BsonValue itemValue : value.asArray()) {
            if (itemValue.isNull()) {
                result.add(null);
                continue;
            }

            if (!itemValue.isDocument()) {
                continue;
            }

            result.add(deserializeItem(itemValue.asDocument()));
        }

        return result;
    }

    private static ItemStack deserializeItem(BsonDocument itemDoc) {
        String id = itemDoc.getString("id").getValue();
        int quantity = itemDoc.getInt32("quantity").getValue();
        double durability = itemDoc.getDouble("durability").getValue();
        double maxDurability = itemDoc.getDouble("maxDurability").getValue();

        BsonDocument itemMetadata = itemDoc.containsKey("metadata")
                ? itemDoc.getDocument("metadata")
                : null;

        return new ItemStack(id, quantity, durability, maxDurability, itemMetadata);
    }

    @Nonnull
    public static ItemStack clearContentsFromItem(@Nonnull ItemStack backpackItem) {
        return backpackItem.withMetadata(BACKPACK_CONTENTS_KEY, (BsonValue) null);
    }

    public static boolean hasStoredContents(@Nonnull ItemStack backpackItem) {
        BsonDocument metadata = backpackItem.getMetadata();
        return metadata != null && metadata.containsKey(BACKPACK_CONTENTS_KEY);
    }

    public static boolean hasStoredContents(@Nonnull ItemWithAllMetadata backpackItem) {
        if (backpackItem.metadata == null || backpackItem.metadata.isEmpty()) {
            return false;
        }
        BsonDocument metadata = BsonDocument.parse(backpackItem.metadata);
        return metadata.containsKey(BACKPACK_CONTENTS_KEY);
    }
}