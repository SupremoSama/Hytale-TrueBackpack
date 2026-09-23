package com.supremosan.truebackpack.util;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.MessageFormat;

public final class I18nHelper {

    private I18nHelper() {
    }

    @Nonnull
    public static String getOrFallback(@Nullable String language, @Nonnull String key) {
        I18nModule i18n = I18nModule.get();
        if (i18n != null) {
            try {
                String value = i18n.getMessage(language, key);
                if (value != null && !value.isBlank() && !value.equals(key)) {
                    return value;
                }
                if (!key.startsWith("server.")) {
                    String withServer = "server." + key;
                    value = i18n.getMessage(language, withServer);
                    if (value != null && !value.isBlank() && !value.equals(withServer)) {
                        return value;
                    }
                } else {
                    String withoutServer = key.substring("server.".length());
                    value = i18n.getMessage(language, withoutServer);
                    if (value != null && !value.isBlank() && !value.equals(withoutServer)) {
                        return value;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return formatFallback(key);
    }

    @Nonnull
    public static String getOrFallback(@Nullable String language, @Nonnull String key, Object... args) {
        String template = getOrFallback(language, key);
        if (args != null && args.length > 0) {
            try {
                return MessageFormat.format(template, args);
            } catch (Exception ignored) {
            }
        }
        return template;
    }

    @Nonnull
    public static String resolveItemName(@Nullable String itemId, @Nullable String language) {
        if (itemId == null || itemId.isBlank()) return "Unknown";

        try {
            Item asset = Item.getAssetMap().getAsset(itemId);
            if (asset != null) {
                String nameKey = asset.getTranslationKey();
                I18nModule i18n = I18nModule.get();
                if (i18n != null) {
                    String translated = i18n.getMessage(language, nameKey);
                    if (translated != null && !translated.isBlank() && !translated.equals(nameKey)) {
                        return translated;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            I18nModule i18n = I18nModule.get();
            if (i18n != null) {
                String tbKey = "server.truebackpack.items." + itemId + ".name";
                String translated = i18n.getMessage(language, tbKey);
                if (translated != null && !translated.isBlank() && !translated.equals(tbKey)) {
                    return translated;
                }
                String conventionKey = "server.items." + itemId + ".name";
                translated = i18n.getMessage(language, conventionKey);
                if (translated != null && !translated.isBlank() && !translated.equals(conventionKey)) {
                    return translated;
                }
            }
        } catch (Exception ignored) {
        }

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
    public static String formatFallback(@Nonnull String key) {
        String[] parts = key.split("\\.");
        String last = parts[parts.length - 1];
        if (last.isEmpty()) return key;
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }
}

