package com.supremosan.truebackpack.ui;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.window.SimpleCraftingWindow;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.BenchUpgradeRequirement;

/** Native crafting backend attached to the single custom workbench page. */
public final class BackpackCraftingWindow extends SimpleCraftingWindow {
    private Runnable changed;

    public BackpackCraftingWindow(int x, int y, int z, int rotation, BlockType type, BenchBlock block) {
        super(x, y, z, rotation, type, block);
    }

    public void onChanged(Runnable changed) { this.changed = changed; }
    public String benchId() { return bench.getId(); }
    public int tier() { return getBenchTierLevel(); }
    public boolean isMaster() { return benchId().equals("Backpack_Workbench_Master") || tier() >= 2; }
    public BenchUpgradeRequirement upgradeRequirement() {
        var tier = bench.getTierLevel(tier());
        return tier == null ? null : tier.getUpgradeRequirement();
    }

    @Override
    protected void invalidate() {
        super.invalidate();
        if (changed != null) changed.run();
    }
}
