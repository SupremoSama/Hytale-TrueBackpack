package com.supremosan.truebackpack;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
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

    private HelipackFlySystem helipackFlySystem;

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

            if (helipackFlySystem != null) {
                InventoryComponent.Armor armorComp = null;
                InventoryComponent.Storage storageComp = null;
                BackpackRegistry.HelipackConfig helipackConfig = null;

                World world = Universe.get().getDefaultWorld();
                if (world != null) {
                    Ref<EntityStore> ref = world.getEntityRef(playerRef.getUuid());
                    if (ref != null) {
                        Store<EntityStore> store = ref.getStore();
                        armorComp = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
                        storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());

                        String equippedItemId = BackpackArmorListener.getEquippedItemId(uuidStr);
                        if (equippedItemId != null) {
                            BackpackRegistry.BackpackEntry entry = BackpackRegistry.getByItem(equippedItemId);
                            if (entry != null && entry.isHelipack()) {
                                helipackConfig = entry.helipackConfig();
                            }
                        }
                    }
                }

                helipackFlySystem.onPlayerLeave(playerRef.getUuid(), armorComp, storageComp, helipackConfig);
            }
        });

        LOGGER.atInfo().log("[TrueBackpack] Setup complete");
    }

    @Override
    protected void start() {
        helipackFlySystem = new HelipackFlySystem(
                Player.getComponentType(),
                MovementStatesComponent.getComponentType()
        );

        this.getEntityStoreRegistry().registerSystem(helipackFlySystem);
        this.getChunkStoreRegistry().registerSystem(new BackpackContainerSystem());

        BackpackArmorListener.register(this);
        QuiverListener.register(this);

        LOGGER.atInfo().log("[TrueBackpack] Ready");
    }
}