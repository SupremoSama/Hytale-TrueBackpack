package com.supremosan.truebackpack.util;

import com.hypixel.hytale.server.core.modules.i18n.I18nModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
            } catch (Exception ignored) {
            }
        }
        return formatFallback(key);
    }

    @Nonnull
    public static String formatFallback(@Nonnull String key) {
        String[] parts = key.split("\\.");
        String last = parts[parts.length - 1];
        if (last.isEmpty()) return key;
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }
}
