package com.supremosan.custominventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.custominventory.api.InventoryPageDefinition;
import com.supremosan.custominventory.api.InventoryRegistry;
import com.supremosan.custominventory.command.CustomInventoryCommand;
import com.supremosan.custominventory.inventory.NativeInventoryContent;
import com.supremosan.custominventory.ui.InventoryShellPage;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomInventoryPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile CustomInventoryPlugin instance;
    private final InventoryRegistry registry = new InventoryRegistry();
    private final Set<InventoryShellPage> sessions = ConcurrentHashMap.newKeySet();
    private volatile boolean running;

    public CustomInventoryPlugin(JavaPluginInit init) { super(init); }

    public static CustomInventoryPlugin get() {
        var plugin = instance;
        if (plugin == null) throw new IllegalStateException("CustomInventory is not loaded");
        return plugin;
    }

    public InventoryRegistry getInventoryRegistry() { return registry; }

    @Override
    protected void setup() {
        instance = this;
        running = true;
        registry.registerInventoryPage(new InventoryPageDefinition(InventoryShellPage.DEFAULT_PAGE,
                "Inventory", 0, context -> new NativeInventoryContent()));
        getCommandRegistry().registerCommand(new CustomInventoryCommand(this));
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            for (var page : sessions) if (page.belongsTo(event.getPlayerRef())) page.closeForShutdown();
        });
        getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, event -> {
            var playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());
            if (playerRef != null) {
                for (var page : sessions) if (page.belongsTo(playerRef)) page.closeForWorldRemoval(event.getWorld());
            }
        });
        LOGGER.atInfo().log("CustomInventory registered /custominventory and native inventory content");
    }

    @Override
    protected void start() {
        LOGGER.atInfo().log("CustomInventory ready: %s pages, %s extension buttons", registry.pagesSnapshot().size(), registry.buttonsSnapshot().size());
    }

    /** Invoke on the player's world thread. */
    public void open(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        if (!running || !ref.isValid()) return;
        var player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        var page = new InventoryShellPage(playerRef, registry, sessions::remove);
        sessions.add(page);
        try {
            player.getPageManager().openCustomPage(ref, store, page);
        } catch (RuntimeException failure) {
            page.onDismiss(ref, store);
            throw failure;
        }
    }

    @Override
    protected void shutdown() {
        running = false;
        for (var page : sessions) page.closeForShutdown();
        sessions.clear();
        registry.clear();
        if (instance == this) instance = null;
    }
}
