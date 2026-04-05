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
import com.supremosan.truebackpack.commands.ReloadBackpackCommand;
import com.supremosan.truebackpack.commands.SetBackpackModelCommand;
import com.supremosan.truebackpack.commands.SetHelipackFuelCommand;
import com.supremosan.truebackpack.commands.ToggleCosmeticCommand;
import com.supremosan.truebackpack.config.BackpackConfigService;
import com.supremosan.truebackpack.cosmetic.CosmeticPreference;
import com.supremosan.truebackpack.data.BackpackContainerState;
import com.supremosan.truebackpack.events.BackpackDeathEvent;
import com.supremosan.truebackpack.interactions.BackpackChestTransferInteraction;
import com.supremosan.truebackpack.system.BackpackContainerSystem;
import com.supremosan.truebackpack.system.HelipackFlySystem;
import com.supremosan.truebackpack.interactions.BackpackPlaceInteraction;
import com.supremosan.truebackpack.listener.*;

import java.util.logging.Level;
import java.util.logging.Logger;

public class TrueBackpack extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Logger JUL = Logger.getLogger("TrueBackpack");

    public TrueBackpack(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        try {
            BackpackConfigService.reloadAndRegister(JUL);
        } catch (Exception e) {
            JUL.log(Level.SEVERE, "[TrueBackpack] Failed to load backpack config", e);
        }

        this.getCodecRegistry(Interaction.CODEC).register(
                "TrueBackpack_PlaceBackpack",
                BackpackPlaceInteraction.class,
                BackpackPlaceInteraction.CODEC);

        this.getCodecRegistry(Interaction.CODEC).register(
                "TrueBackpack_ChestTransfer",
                BackpackChestTransferInteraction.class,
                BackpackChestTransferInteraction.CODEC);

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

        this.getCommandRegistry().registerCommand(new ToggleCosmeticCommand());
        this.getCommandRegistry().registerCommand(new ReloadBackpackCommand());
        this.getCommandRegistry().registerCommand(new SetHelipackFuelCommand());
        this.getCommandRegistry().registerCommand(new SetBackpackModelCommand());

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
        BackpackNestingListener.register(this);

        this.getEntityStoreRegistry().registerSystem(new BackpackDeathEvent());

        LOGGER.atInfo().log("[TrueBackpack] Ready");
    }
}