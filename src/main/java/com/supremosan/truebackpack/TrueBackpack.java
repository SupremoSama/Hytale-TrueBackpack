package com.supremosan.truebackpack;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.supremosan.truebackpack.commands.ToggleCosmeticCommand;
import com.supremosan.truebackpack.cosmetic.CosmeticPreference;
import com.supremosan.truebackpack.data.BackpackContainerState;
import com.supremosan.truebackpack.system.BackpackContainerSystem;
import com.supremosan.truebackpack.system.HelipackFlySystem;
import com.supremosan.truebackpack.interactions.BackpackPlaceInteraction;
import com.supremosan.truebackpack.listener.*;
import com.supremosan.truebackpack.registries.BackpackRegistry;

public class TrueBackpack extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public TrueBackpack(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        BackpackRegistry.registerDefaults();

        this.getCodecRegistry(Interaction.CODEC).register(
                "TrueBackpack_PlaceBackpack",
                BackpackPlaceInteraction.class,
                BackpackPlaceInteraction.CODEC);

        ComponentType<ChunkStore, BackpackContainerState> type =
                this.getChunkStoreRegistry().registerComponent(
                        BackpackContainerState.class,
                        "BackpackContainerState",
                        BackpackContainerState.CODEC
                );

        BackpackContainerState.setComponentType(type);

        CosmeticListener.register(this);
        CosmeticPreference.register(this);

        BackpackTooltipListener.register();
        BackpackNestingListener.register(this);

        this.getCommandRegistry().registerCommand(new ToggleCosmeticCommand());

        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            String uuidStr = playerRef.getUuid().toString();

            BackpackTooltipListener.onPlayerLeave(playerRef.getUuid());
            BackpackArmorListener.onPlayerRemove(playerRef.getUuid().toString(), null, null, null, null);
            CosmeticListener.onPlayerLeave(uuidStr);
        });

        LOGGER.atInfo().log("[TrueBackpack] Setup complete");
    }

    @Override
    protected void start() {
        this.getEntityStoreRegistry().registerSystem(new HelipackFlySystem(
                Player.getComponentType(),
                MovementStatesComponent.getComponentType()
        ));
        this.getChunkStoreRegistry().registerSystem(new BackpackContainerSystem());

        BackpackArmorListener.register(this);
        QuiverListener.register(this);

        LOGGER.atInfo().log("[TrueBackpack] Ready");
    }
}