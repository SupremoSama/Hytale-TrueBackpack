package com.supremosan.truebackpack;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.StateData;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.supremosan.truebackpack.commands.ToggleCosmeticCommand;
import com.supremosan.truebackpack.cosmetic.CosmeticPreference;
import com.supremosan.truebackpack.data.BackpackContainerState;
import com.supremosan.truebackpack.factory.HelipackFlySystem;
import com.supremosan.truebackpack.interactions.BackpackPlaceInteraction;
import com.supremosan.truebackpack.listener.*;
import com.supremosan.truebackpack.registries.BackpackRegistry;

public class TrueBackpack extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private HelipackFlySystem helipackFlySystem;

    public TrueBackpack(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.getCodecRegistry(Interaction.CODEC).register(
                "TrueBackpack_PlaceBackpack",
                BackpackPlaceInteraction.class,
                BackpackPlaceInteraction.CODEC);

        BackpackRegistry.registerDefaults();

        CosmeticListener.register(this);
        CosmeticPreference.register(this);
        this.getCommandRegistry().registerCommand(new ToggleCosmeticCommand());

        QuiverListener.register(this);

        BackpackArmorListener.register(this);
        BackpackTooltipListener.register();

        BackpackNestingListener.register(this);

        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            String uuidStr = playerRef.getUuid().toString();

            BackpackTooltipListener.onPlayerLeave(playerRef.getUuid());
            CosmeticListener.onPlayerLeave(uuidStr);

            if (helipackFlySystem != null) {
                helipackFlySystem.onPlayerLeave(playerRef.getUuid());
            }
        });

        LOGGER.atInfo().log("[TrueBackpack] Setup complete");
    }

    @Override
    protected void start() {
//        this.getEntityStoreRegistry().registerSystem(new MovementDebugSystem(
//                Player.getComponentType(),
//                MovementStatesComponent.getComponentType()
//        ));

        helipackFlySystem = new HelipackFlySystem(
                Player.getComponentType(),
                MovementStatesComponent.getComponentType()
        );
        this.getEntityStoreRegistry().registerSystem(helipackFlySystem);

        StateData.CODEC.register(
                "truebackpack:backpack_container",
                ItemContainerState.ItemContainerStateData.class,
                ItemContainerState.ItemContainerStateData.CODEC
        );

        this.getBlockStateRegistry().registerBlockState(
                BackpackContainerState.class,
                "truebackpack:backpack_container",
                BackpackContainerState.CODEC
        );

        LOGGER.atInfo().log("[TrueBackpack] Ready");
    }
}