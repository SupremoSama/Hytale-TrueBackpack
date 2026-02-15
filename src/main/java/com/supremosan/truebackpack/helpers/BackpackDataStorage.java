package com.supremosan.truebackpack.helpers;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.*;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Utility class for storing and retrieving backpack contents in item metadata
 */
public class BackpackDataStorage {

    private static final String BACKPACK_CONTENTS_KEY = "BackpackContents";

    /**
     * Store ALL backpack contents in the backpack item's metadata
     * This saves the entire backpack inventory when unequipped
     */
    @Nonnull
    public static ItemStack saveContentsToItem(@Nonnull ItemStack backpackItem, @Nonnull List<ItemStack> contents) {
        if (contents.isEmpty()) {
            return clearContentsFromItem(backpackItem);
        }

        // Serialize to BsonArray
        BsonArray contentsArray = new BsonArray();
        for (ItemStack item : contents) {
            if (item == null || item.isEmpty()) {
                // Store null/empty slots to preserve slot positions
                contentsArray.add(new BsonNull());
                continue;
            }

            BsonDocument itemDoc = new BsonDocument();
            itemDoc.put("id", new BsonString(item.getItemId()));
            itemDoc.put("quantity", new BsonInt32(item.getQuantity()));
            itemDoc.put("durability", new BsonDouble(item.getDurability()));
            itemDoc.put("maxDurability", new BsonDouble(item.getMaxDurability()));

            if (item.getMetadata() != null) {
                itemDoc.put("metadata", item.getMetadata());
            }

            contentsArray.add(itemDoc);
        }

        // Store in backpack metadata
        return backpackItem.withMetadata(BACKPACK_CONTENTS_KEY, contentsArray);
    }

    /**
     * Retrieve backpack contents from item metadata
     * Returns list of items in the same order they were stored
     */
    @Nonnull
    public static List<ItemStack> loadContentsFromItem(@Nonnull ItemStack backpackItem) {
        List<ItemStack> result = new ArrayList<>();

        BsonDocument metadata = backpackItem.getMetadata();
        if (metadata == null || !metadata.containsKey(BACKPACK_CONTENTS_KEY)) {
            return result;
        }

        BsonValue value = metadata.get(BACKPACK_CONTENTS_KEY);
        if (!value.isArray()) {
            return result;
        }

        BsonArray contentsArray = value.asArray();
        for (BsonValue itemValue : contentsArray) {
            if (itemValue.isNull()) {
                result.add(null); // Empty slot
                continue;
            }

            if (!itemValue.isDocument()) continue;

            BsonDocument itemDoc = itemValue.asDocument();

            String id = itemDoc.getString("id").getValue();
            int quantity = itemDoc.getInt32("quantity").getValue();
            double durability = itemDoc.getDouble("durability").getValue();
            double maxDurability = itemDoc.getDouble("maxDurability").getValue();

            BsonDocument itemMetadata = null;
            if (itemDoc.containsKey("metadata")) {
                itemMetadata = itemDoc.getDocument("metadata");
            }

            ItemStack item = new ItemStack(id, quantity, durability, maxDurability, itemMetadata);
            result.add(item);
        }

        return result;
    }

    /**
     * Clear all saved contents from backpack item metadata
     */
    @Nonnull
    public static ItemStack clearContentsFromItem(@Nonnull ItemStack backpackItem) {
        return backpackItem.withMetadata(BACKPACK_CONTENTS_KEY, (BsonValue) null);
    }

    /**
     * Get the count of stored items in a backpack
     */
    public static int getStoredItemCount(@Nonnull ItemStack backpackItem) {
        List<ItemStack> contents = loadContentsFromItem(backpackItem);
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && !item.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Check if a backpack has any stored contents
     */
    public static boolean hasStoredContents(@Nonnull ItemStack backpackItem) {
        BsonDocument metadata = backpackItem.getMetadata();
        return metadata != null && metadata.containsKey(BACKPACK_CONTENTS_KEY);
    }
}