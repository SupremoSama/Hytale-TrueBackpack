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
import com.supremosan.truebackpack.config.HatConfigService;
import com.supremosan.truebackpack.cosmetic.CosmeticPreference;
import com.supremosan.truebackpack.data.BackpackContainerState;
import com.supremosan.truebackpack.events.BackpackDeathEvent;
import com.supremosan.truebackpack.system.BackpackContainerSystem;
import com.supremosan.truebackpack.system.HatDurabilitySystem;
import com.supremosan.truebackpack.system.HelipackFlySystem;
import com.supremosan.truebackpack.interactions.BackpackInteraction;
import com.supremosan.truebackpack.listener.*;

import java.util.UUID;
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
            HatConfigService.reloadAndRegister(JUL);
        } catch (Exception e) {
            JUL.log(Level.SEVERE, "[TrueBackpack] Failed to load backpack config", e);
        }

        this.getCodecRegistry(Interaction.CODEC).register(
                "TrueBackpack_BackpackInteraction",
                BackpackInteraction.class,
                BackpackInteraction.CODEC);

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
            UUID uuid = playerRef.getUuid();
            String uuidStr = uuid.toString();

            BackpackTooltipListener.onPlayerLeave(uuid);
            BackpackArmorListener.onPlayerRemove(uuidStr, null, null, null, null);
            CosmeticListener.onPlayerLeave(uuidStr);
            HatArmorListener.onPlayerRemove(uuidStr);
            HatDurabilitySystem.onPlayerRemove(uuidStr);
        });

        LOGGER.atInfo().log("[TrueBackpack] Setup complete");
    }

    @Override
    protected void start() {
        this.getEntityStoreRegistry().registerSystem(new HelipackFlySystem(
                Player.getComponentType(),
                MovementStatesComponent.getComponentType()
        ));
        this.getEntityStoreRegistry().registerSystem(new HatDurabilitySystem());
        this.getEntityStoreRegistry().registerSystem(new BackpackDeathEvent());

        this.getChunkStoreRegistry().registerSystem(new BackpackContainerSystem());

        BackpackArmorListener.register(this);
        QuiverListener.register(this);
        BackpackNestingListener.register(this);
        HatArmorListener.register(this);

        LOGGER.atInfo().log("[TrueBackpack] Ready");
    }
}