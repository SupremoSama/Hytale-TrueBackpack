package com.supremosan.truebackpack.ui;

import com.hypixel.hytale.protocol.PickupLocation;
import com.hypixel.hytale.protocol.PlaceMode;
import com.hypixel.hytale.protocol.packets.voice.VoiceInputMode;
import com.hypixel.hytale.server.core.asset.type.gameplay.PlayerConfig.ArmorVisibilityOption;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerCreativeSettings;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerVoiceSettings;

/** Checks the real engine settings records without a running server or client. */
public final class BackpackArmorVisibilityVerification {
    public static void main(String[] args) {
        try {
            policyAndSettingsMatrix();
            defaultSettingsRemainUnchanged();
            System.out.println("Backpack armor visibility verification passed (192 policy/slot/state combinations and 4 default round trips).");
        } catch (Throwable failure) {
            // Engine logging can replace System.err before a server exists.
            failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err)));
            throw failure;
        }
    }

    private static void policyAndSettingsMatrix() {
        for (ArmorVisibilityOption policy : ArmorVisibilityOption.values()) {
            for (int mask = 0; mask < 16; mask++) {
                PlayerSettings original = customSettings(mask);
                PlayerSettings snapshot = (PlayerSettings) original.clone();
                for (BackpackArmorVisibility.Slot slot : BackpackArmorVisibility.Slot.values()) {
                    String context = policy + "/" + slot + "/" + mask;
                    // Expected permissions are independent of the helper's allowed() implementation.
                    boolean permitted = policy == ArmorVisibilityOption.ALL
                            || policy == ArmorVisibilityOption.HELMET_ONLY && slot == BackpackArmorVisibility.Slot.HELMET;
                    require(slot.allowed(policy) == permitted, context + ": incorrect world policy permission");

                    PlayerSettings result = BackpackArmorVisibility.toggle(original, slot, policy);
                    int expectedMask = permitted ? mask ^ (1 << slot.ordinal()) : mask;
                    require(armorMask(result) == expectedMask, context + ": incorrect armor flags or another slot changed");
                    preserveUnrelatedSettings(original, result, context);
                    require(original.equals(snapshot), context + ": original settings were mutated");

                    if (permitted) {
                        require(result != original, context + ": permitted toggle did not replace the settings record");
                        PlayerSettings restored = BackpackArmorVisibility.toggle(result, slot, policy);
                        require(restored.equals(original), context + ": second click did not restore the settings");
                        require(armorMask(result) == expectedMask, context + ": second click mutated the first result");
                    } else {
                        require(result == original, context + ": denied toggle replaced settings unnecessarily");
                    }
                }
            }
        }
    }

    private static void defaultSettingsRemainUnchanged() {
        PlayerSettings defaults = PlayerSettings.defaults();
        PlayerSettings snapshot = (PlayerSettings) defaults.clone();
        require(armorMask(defaults) == 0, "engine defaults unexpectedly hide armor");
        for (BackpackArmorVisibility.Slot slot : BackpackArmorVisibility.Slot.values()) {
            PlayerSettings hidden = BackpackArmorVisibility.toggle(defaults, slot, ArmorVisibilityOption.ALL);
            require(armorMask(hidden) == 1 << slot.ordinal(), slot + ": default hide failed");
            preserveUnrelatedSettings(defaults, hidden, slot + "/defaults");
            PlayerSettings shown = BackpackArmorVisibility.toggle(hidden, slot, ArmorVisibilityOption.ALL);
            require(shown.equals(snapshot), slot + ": default show failed");
            require(defaults.equals(snapshot) && PlayerSettings.defaults() == defaults,
                    slot + ": shared default settings changed");
        }
    }

    private static PlayerSettings customSettings(int armorMask) {
        return new PlayerSettings(
                true,
                PickupLocation.Storage,
                PickupLocation.Backpack,
                PickupLocation.Storage,
                PickupLocation.Backpack,
                PickupLocation.Storage,
                new PlayerCreativeSettings(true, true, PlaceMode.Replace, 37, false, true, true, false),
                (armorMask & 1) != 0,
                (armorMask & 2) != 0,
                (armorMask & 4) != 0,
                (armorMask & 8) != 0,
                new PlayerVoiceSettings(true, true, VoiceInputMode.VoiceActivity));
    }

    private static int armorMask(PlayerSettings settings) {
        return (settings.hideHelmet() ? 1 : 0)
                | (settings.hideCuirass() ? 2 : 0)
                | (settings.hideGauntlets() ? 4 : 0)
                | (settings.hidePants() ? 8 : 0);
    }

    private static void preserveUnrelatedSettings(PlayerSettings before, PlayerSettings after, String context) {
        require(before.showEntityMarkers() == after.showEntityMarkers(), context + ": entity markers changed");
        require(before.armorItemsPreferredPickupLocation() == after.armorItemsPreferredPickupLocation(),
                context + ": armor pickup preference changed");
        require(before.weaponAndToolItemsPreferredPickupLocation() == after.weaponAndToolItemsPreferredPickupLocation(),
                context + ": weapon/tool pickup preference changed");
        require(before.usableItemsItemsPreferredPickupLocation() == after.usableItemsItemsPreferredPickupLocation(),
                context + ": usable item pickup preference changed");
        require(before.solidBlockItemsPreferredPickupLocation() == after.solidBlockItemsPreferredPickupLocation(),
                context + ": block pickup preference changed");
        require(before.miscItemsPreferredPickupLocation() == after.miscItemsPreferredPickupLocation(),
                context + ": miscellaneous pickup preference changed");
        require(before.creativeSettings() == after.creativeSettings(), context + ": creative settings changed");
        require(before.voiceSettings() == after.voiceSettings(), context + ": voice settings changed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
