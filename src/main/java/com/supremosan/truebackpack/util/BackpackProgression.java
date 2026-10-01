package com.supremosan.truebackpack.util;

import com.hypixel.hytale.builtin.adventure.memories.MemoriesPlugin;
import com.hypixel.hytale.builtin.adventure.memories.MemoriesGameplayConfig;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;

/** Uses the native recipe requirement and world memory thresholds, including server overrides. */
public final class BackpackProgression {
    private BackpackProgression() {}
    public record Gate(boolean unlocked, int recorded, int required, int level) {}
    public static int requiredLevel(String itemId) {
        var recipe = CraftingRecipe.getAssetMap().getAsset(itemId + "_Recipe_Generated_0");
        if (recipe != null) return recipe.getRequiredMemoriesLevel();
        // Fail closed if generated recipes are temporarily unavailable during asset reload.
        return itemId.equals("Utility_Leather_Big_Backpack") || itemId.equals("Utility_Leather_Extra_Big_Backpack") ? 5 : 0;
    }
    public static int threshold(int level, int[] amounts) {
        if (level <= 1) return 0;
        return amounts == null || level - 2 >= amounts.length ? -1 : amounts[level - 2];
    }
    public static boolean benchAllows(CraftingRecipe recipe, String benchId, int tier) {
        if (benchId == null || recipe.getBenchRequirement() == null) return false;
        for (var requirement : recipe.getBenchRequirement()) {
            if (benchId.equals(requirement.id) && tier >= requirement.requiredTierLevel) return true;
        }
        return false;
    }
    public static boolean canAccessRecipe(CraftingRecipe recipe, String benchId, int tier, int memoriesLevel) {
        return benchAllows(recipe, benchId, tier) && memoriesLevel >= recipe.getRequiredMemoriesLevel();
    }
    public static Gate gate(String itemId, GameplayConfig gameplay) {
        return gate(requiredLevel(itemId), gameplay);
    }
    public static Gate gate(int level, GameplayConfig gameplay) {
        if (level <= 1) return new Gate(true, 0, 0, level);
        var plugin = MemoriesPlugin.get();
        var config = MemoriesGameplayConfig.get(gameplay);
        int needed = threshold(level, config == null ? null : config.getMemoriesAmountPerLevel());
        if (plugin == null) return new Gate(false, 0, needed, level);
        return new Gate(plugin.getMemoriesLevel(gameplay) >= level, plugin.getRecordedMemories().size(), needed, level);
    }
}
