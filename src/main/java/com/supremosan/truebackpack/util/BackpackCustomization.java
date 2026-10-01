package com.supremosan.truebackpack.util;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.registries.BackpackRegistry;

/** Builds a validated draft. Persistence is a separate, single inventory transaction. */
public final class BackpackCustomization {
    private BackpackCustomization() {}
    public static ItemStack draft(ItemStack original, String name, String skin, String color, boolean usePaint) {
        var base = BackpackRegistry.getByItem(original.getItemId());
        if (base == null) throw new IllegalArgumentException("Not a backpack");
        String target = skin == null || skin.equals("default") ? null : skin;
        if (target != null) {
            var appearance = BackpackRegistry.getByItem(target);
            if (base.isHelipack() || appearance == null || appearance.isHelipack()) throw new IllegalArgumentException("Invalid appearance");
            target = appearance.itemId();
        }
        var result = BackpackItemFactory.setTransmogSkin(original, target);
        result = BackpackItemFactory.setCustomName(result, name);
        if (BackpackPaintService.canPaint(result)) {
            if (usePaint && (color == null || color.isBlank())) throw new IllegalArgumentException("Missing color");
            result = BackpackItemFactory.setPaintColor(result, usePaint ? color : null);
        }
        return result;
    }
}
