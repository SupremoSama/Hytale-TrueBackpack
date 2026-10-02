package com.supremosan.truebackpack.ui;

import com.hypixel.hytale.server.core.asset.type.gameplay.PlayerConfig.ArmorVisibilityOption;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;

/** Changes native armor render preferences without changing equipment or other player settings. */
final class BackpackArmorVisibility {
    enum Slot {
        HELMET("#HelmetVisibility", "inventory.helmet"),
        CUIRASS("#CuirassVisibility", "inventory.cuirass"),
        GAUNTLETS("#GauntletsVisibility", "inventory.gauntlets"),
        PANTS("#PantsVisibility", "inventory.pants");

        final String selector;
        final String nameKey;

        Slot(String selector, String nameKey) {
            this.selector = selector;
            this.nameKey = nameKey;
        }

        boolean allowed(ArmorVisibilityOption option) {
            return switch (this) {
                case HELMET -> option.canHideHelmet();
                case CUIRASS -> option.canHideCuirass();
                case GAUNTLETS -> option.canHideGauntlets();
                case PANTS -> option.canHidePants();
            };
        }

        boolean hidden(PlayerSettings settings) {
            return switch (this) {
                case HELMET -> settings.hideHelmet();
                case CUIRASS -> settings.hideCuirass();
                case GAUNTLETS -> settings.hideGauntlets();
                case PANTS -> settings.hidePants();
            };
        }
    }

    private BackpackArmorVisibility() {}

    static PlayerSettings toggle(PlayerSettings settings, Slot slot, ArmorVisibilityOption option) {
        if (!slot.allowed(option)) return settings;
        return new PlayerSettings(
                settings.showEntityMarkers(),
                settings.armorItemsPreferredPickupLocation(),
                settings.weaponAndToolItemsPreferredPickupLocation(),
                settings.usableItemsItemsPreferredPickupLocation(),
                settings.solidBlockItemsPreferredPickupLocation(),
                settings.miscItemsPreferredPickupLocation(),
                settings.creativeSettings(),
                slot == Slot.HELMET ? !settings.hideHelmet() : settings.hideHelmet(),
                slot == Slot.CUIRASS ? !settings.hideCuirass() : settings.hideCuirass(),
                slot == Slot.GAUNTLETS ? !settings.hideGauntlets() : settings.hideGauntlets(),
                slot == Slot.PANTS ? !settings.hidePants() : settings.hidePants(),
                settings.voiceSettings());
    }
}
