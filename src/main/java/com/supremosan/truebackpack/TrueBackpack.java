package com.supremosan.truebackpack;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.StateData;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.supremosan.truebackpack.commands.ToggleCosmeticCommand;
import com.supremosan.truebackpack.cosmetic.CosmeticPreference;
import com.supremosan.truebackpack.data.BackpackContainerState;
import com.supremosan.truebackpack.interactions.BackpackPlaceInteraction;
import com.supremosan.truebackpack.listener.*;
import com.supremosan.truebackpack.registries.BackpackBlockRegistry;

public class TrueBackpack extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public TrueBackpack(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // 0. Configure backpack placement interaction.
        this.getCodecRegistry(Interaction.CODEC).register(
                "TrueBackpack_PlaceBackpack",
                BackpackPlaceInteraction.class,
                BackpackPlaceInteraction.CODEC);

        // 1. Register all backpack blocks.
        BackpackBlockRegistry.registerDefaults();

        // 2. Handle player cosmetic settings.
        CosmeticListener.register(this);
        CosmeticPreference.register(this);
        this.getCommandRegistry().registerCommand(new ToggleCosmeticCommand());

        // 3. Implement a quiver listener to check for arrows in inventory.
        QuiverListener.register(this);

        // 4. Create a backpack listener to manage equip, unequip, swapping, and per-instance data persistence.
        BackpackArmorListener.register(this);
        BackpackTooltipListener.register();

        // 5. Prevent backpacks from being placed inside other backpacks.
        BackpackNestingListener.register(this);

        // 6. Clear per-player caches upon disconnect.
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            String uuidStr = playerRef.getUuid().toString();

            BackpackTooltipListener.onPlayerLeave(playerRef.getUuid());
            CosmeticListener.onPlayerLeave(uuidStr);
        });

        LOGGER.atInfo().log("[TrueBackpack] Setup complete");
    }

    @Override
    protected void start() {
        // 0. Register the codec for the custom container.
        StateData.CODEC.register(
                "truebackpack:backpack_container",
                ItemContainerState.ItemContainerStateData.class,
                ItemContainerState.ItemContainerStateData.CODEC
        );

        // 1. Define the block type associated with the new codec.
        this.getBlockStateRegistry().registerBlockState(
                BackpackContainerState.class,
                "truebackpack:backpack_container",
                BackpackContainerState.CODEC
        );

        LOGGER.atInfo().log("[TrueBackpack] Ready");
    }
}