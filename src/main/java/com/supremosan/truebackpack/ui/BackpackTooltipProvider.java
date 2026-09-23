package com.supremosan.truebackpack.ui;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.registries.BackpackRegistry;
import com.supremosan.truebackpack.util.I18nHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class BackpackTooltipProvider {

    private static final String KEY_TITLE    = "server.truebackpack.tooltip.title";
    private static final String KEY_EMPTY    = "server.truebackpack.tooltip.empty";
    private static final String KEY_SLOTS    = "server.truebackpack.tooltip.slots";
    private static final String KEY_ITEMS    = "server.truebackpack.tooltip.items";
    private static final String KEY_EQUIPPED = "server.truebackpack.tooltip.equipped";

    private BackpackTooltipProvider() {}

    @Nullable
    public static String buildTooltip(@Nonnull ItemStack stack, @Nullable String language) {
        if (stack.isEmpty()) return null;

        String itemId   = stack.getItemId();
        short sizeBonus = BackpackItemFactory.getTotalCapacity(stack);
        if (sizeBonus == 0) return null;

        String extra = buildExtraInfo(stack, language);

        if (BackpackItemFactory.isEquipped(stack)) {
            String equippedText = I18nHelper.getOrFallback(language, KEY_EQUIPPED);
            return extra.isEmpty() ? equippedText : equippedText + "\n" + extra;
        }

        if (!BackpackItemFactory.hasContents(stack)) {
            return buildEmptyTooltip(sizeBonus, language, extra);
        }

        List<ItemStack> contents = BackpackItemFactory.loadContents(stack);
        return buildContentsTooltip(contents, sizeBonus, language, extra);
    }

    @Nonnull
    public static String buildExtraInfo(@Nonnull ItemStack stack, @Nullable String language) {
        StringBuilder sb = new StringBuilder();
        if ("Utility_Leather_Extra_Big_Backpack".equalsIgnoreCase(stack.getItemId())) {
            int level = BackpackItemFactory.getUpgradeLevel(stack);
            if (level > 0) {
                sb.append(I18nHelper.getOrFallback(language, "server.truebackpack.tooltip.upgrade", level));
            }
        }
        if (BackpackItemFactory.hasTransmogSkin(stack)) {
            String skin = BackpackItemFactory.getTransmogSkin(stack);
            if (skin != null) {
                if (!sb.isEmpty()) sb.append(" | ");
                String skinDisplayName = I18nHelper.resolveItemName(skin, language);
                sb.append(I18nHelper.getOrFallback(language, "server.truebackpack.tooltip.appearance", skinDisplayName));
            }
        }
        return sb.toString();
    }

    @Nonnull
    public static String buildTooltipFromLiveContents(@Nonnull List<ItemStack> liveContents,
                                                      short sizeBonus,
                                                      @Nullable String language) {
        return buildContentsTooltip(liveContents, sizeBonus, language, "");
    }

    @Nonnull
    public static String buildTooltipFromLiveContents(@Nonnull List<ItemStack> liveContents,
                                                      short sizeBonus,
                                                      @Nullable String language,
                                                      @Nonnull String extra) {
        return buildContentsTooltip(liveContents, sizeBonus, language, extra);
    }

    @Nonnull
    public static String buildEmptyTooltip(short sizeBonus, @Nullable String language) {
        return buildEmptyTooltip(sizeBonus, language, "");
    }

    @Nonnull
    public static String buildEmptyTooltip(short sizeBonus, @Nullable String language, @Nonnull String extra) {
        String title     = I18nHelper.getOrFallback(language, KEY_TITLE);
        String slotsWord = I18nHelper.getOrFallback(language, KEY_SLOTS);
        String emptyWord = I18nHelper.getOrFallback(language, KEY_EMPTY);

        StringBuilder sb = new StringBuilder();
        sb.append(title).append(" (").append(sizeBonus).append(" ").append(slotsWord).append(")");
        if (!extra.isEmpty()) {
            sb.append("\n").append(extra);
        }
        sb.append("\n").append(emptyWord);
        return sb.toString();
    }

    @Nonnull
    private static String buildContentsTooltip(@Nonnull List<ItemStack> contents,
                                               short sizeBonus,
                                               @Nullable String language,
                                               @Nonnull String extra) {
        String title     = I18nHelper.getOrFallback(language, KEY_TITLE);
        String slotsWord = I18nHelper.getOrFallback(language, KEY_SLOTS);
        String itemsWord = I18nHelper.getOrFallback(language, KEY_ITEMS);
        String emptyWord = I18nHelper.getOrFallback(language, KEY_EMPTY);

        int usedSlots  = 0;
        int totalItems = 0;
        StringBuilder lines = new StringBuilder();

        for (ItemStack item : contents) {
            if (item == null || item.isEmpty()) continue;
            usedSlots++;
            totalItems += item.getQuantity();

            String displayName = resolveItemName(item.getItemId(), language);
            lines.append("\n  ")
                    .append(item.getQuantity())
                    .append("x ")
                    .append(displayName);
        }

        StringBuilder tooltip = new StringBuilder();
        tooltip.append(title)
                .append(" (")
                .append(usedSlots).append("/").append(sizeBonus)
                .append(" ").append(slotsWord)
                .append(", ")
                .append(totalItems).append(" ").append(itemsWord)
                .append(")");

        if (!extra.isEmpty()) {
            tooltip.append("\n").append(extra);
        }

        if (usedSlots == 0) {
            tooltip.append("\n").append(emptyWord);
        } else {
            tooltip.append(lines);
        }

        return tooltip.toString();
    }

    @Nonnull
    public static String resolveItemName(@Nullable String itemId, @Nullable String language) {
        return I18nHelper.resolveItemName(itemId, language);
    }
}