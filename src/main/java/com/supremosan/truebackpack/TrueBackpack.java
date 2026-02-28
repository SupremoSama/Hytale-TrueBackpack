package com.supremosan.truebackpack;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.supremosan.truebackpack.commands.ToggleCosmeticCommand;
import com.supremosan.truebackpack.cosmetic.CosmeticListener;
import com.supremosan.truebackpack.cosmetic.CosmeticPreference;
import com.supremosan.truebackpack.model.BlockyModelWrapper;

import java.io.IOException;
import java.nio.file.Path;

public class TrueBackpack extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public TrueBackpack(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // BUGS - If I drop the backpack when is equipped, it lost all items inside
        // 1. Manage the player preferences.
        CosmeticPreference.register(this);
        this.getCommandRegistry().registerCommand(new ToggleCosmeticCommand());

        // 2. Armor listener handles equip/unequip/swap and per-instance content persistence.
        BackpackArmorListener.register(this);

        // 3. Tooltip listener handles custom tooltips for backpacks.
        BackpackTooltipListener.register();

        // 4. Cosmetic listener handles visual 3D model attachment on storage slot 0.
        CosmeticListener.register(this);

        // 5. Quiver listener handles visual 3D model attachment to quiver when has arrows.
        QuiverListener.register(this);

        // 6. Tool listener handles visual 3D model attachment to tools when has on hotbar.
        try {
            Path assetDir = this.getDataDirectory().resolve("generated");
            String assetPrefix = "generated";

            LOGGER.atInfo().log("[TrueBackpack] BlockyModelWrapper assetDir=%s", assetDir);
            BlockyModelWrapper.init(assetDir, assetPrefix);
            ToolListener.register(this);
        } catch (IOException e) {
            LOGGER.atWarning().log("[TrueBackpack] Failed to initialise BlockyModelWrapper, ToolListener disabled: %s", e.getMessage());
        }

        // 7. Clean up per-player caches on disconnect.
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            String uuidStr = playerRef.getUuid().toString();

            BackpackTooltipListener.onPlayerLeave(playerRef.getUuid());
            CosmeticListener.onPlayerLeave(uuidStr);
        });

        LOGGER.atInfo().log("[TrueBackpack] Ready");
    }
}