package com.supremosan.truebackpack.ui;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.listener.BackpackArmorListener;

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
        short sizeBonus = BackpackArmorListener.getBackpackSize(itemId);
        if (sizeBonus == 0) return null;

        if (BackpackItemFactory.isEquipped(stack)) {
            I18nModule i18n = I18nModule.get();
            return resolve(i18n, language, KEY_EQUIPPED);
        }

        if (!BackpackItemFactory.hasContents(stack)) {
            return buildEmptyTooltip(sizeBonus, language);
        }

        List<ItemStack> contents = BackpackItemFactory.loadContents(stack);
        return buildContentsTooltip(contents, sizeBonus, language);
    }

    @Nonnull
    public static String buildTooltipFromLiveContents(@Nonnull List<ItemStack> liveContents,
                                                      short sizeBonus,
                                                      @Nullable String language) {
        return buildContentsTooltip(liveContents, sizeBonus, language);
    }

    @Nonnull
    public static String buildEmptyTooltip(short sizeBonus, @Nullable String language) {
        I18nModule i18n  = I18nModule.get();
        String title     = resolve(i18n, language, KEY_TITLE);
        String slotsWord = resolve(i18n, language, KEY_SLOTS);
        String emptyWord = resolve(i18n, language, KEY_EMPTY);

        return title + " (" + sizeBonus + " " + slotsWord + ")\n" + emptyWord;
    }

    @Nonnull
    private static String buildContentsTooltip(@Nonnull List<ItemStack> contents,
                                               short sizeBonus,
                                               @Nullable String language) {
        I18nModule i18n  = I18nModule.get();
        String title     = resolve(i18n, language, KEY_TITLE);
        String slotsWord = resolve(i18n, language, KEY_SLOTS);
        String itemsWord = resolve(i18n, language, KEY_ITEMS);
        String emptyWord = resolve(i18n, language, KEY_EMPTY);

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

        if (usedSlots == 0) {
            tooltip.append("\n").append(emptyWord);
        } else {
            tooltip.append(lines);
        }

        return tooltip.toString();
    }

    @Nonnull
    private static String resolveItemName(@Nullable String itemId, @Nullable String language) {
        if (itemId == null || itemId.isBlank()) return "Unknown";

        try {
            Item asset = Item.getAssetMap().getAsset(itemId);
            if (asset != null) {
                String nameKey = asset.getTranslationKey();
                I18nModule i18n = I18nModule.get();
                if (i18n != null) {
                    String translated = i18n.getMessage(language, nameKey);
                    if (translated != null && !translated.isBlank()
                            && !translated.equals(nameKey)) {
                        return translated;
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            I18nModule i18n = I18nModule.get();
            if (i18n != null) {
                String conventionKey = "server.items." + itemId + ".name";
                String translated = i18n.getMessage(language, conventionKey);
                if (translated != null && !translated.isBlank()
                        && !translated.equals(conventionKey)) {
                    return translated;
                }
            }
        } catch (Exception ignored) {}

        String[] parts = itemId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
        }
        return !sb.isEmpty() ? sb.toString() : itemId;
    }

    @Nonnull
    private static String resolve(@Nullable I18nModule i18n,
                                  @Nullable String language,
                                  @Nonnull String key) {
        if (i18n != null) {
            try {
                String value = i18n.getMessage(language, key);
                if (value != null && !value.isBlank() && !value.equals(key)) {
                    return value;
                }
            } catch (Exception ignored) {}
        }

        String[] parts = key.split("\\.");
        String last = parts[parts.length - 1];
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }
}