package com.supremosan.custominventory.example;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.supremosan.custominventory.CustomInventoryPlugin;
import com.supremosan.custominventory.api.*;

import java.util.ArrayList;
import java.util.List;

/** Optional companion; its only integration point is the public registry API. */
public final class ExampleInventoryPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final List<InventoryRegistry.Registration> registrations = new ArrayList<>();

    public ExampleInventoryPlugin(JavaPluginInit init) { super(init); }

    @Override
    protected void setup() {
        var registry = CustomInventoryPlugin.get().getInventoryRegistry();
        registrations.add(registry.registerInventoryPage(new InventoryPageDefinition(
                "example:appearance", "Appearance example", 100, context -> new ExampleContent())));
        registrations.add(registry.registerInventoryButton(new InventoryButtonDefinition(
                "example:hello", "Extension greeting", 100,
                context -> context.playerRef().sendMessage(Message.raw("Hello from the optional inventory extension.")))));
        LOGGER.atInfo().log("CustomInventoryExample registered example:appearance and example:hello through the public API");
    }

    @Override
    protected void shutdown() {
        registrations.forEach(InventoryRegistry.Registration::close);
        registrations.clear();
    }

    private static final class ExampleContent implements InventoryContent {
        private int greetings;

        @Override
        public void build(InventoryContext context, UICommandBuilder commands,
                          InventoryEventBindings bindings, String selector) {
            commands.append(selector, "CustomInventoryExample/AppearanceExample.ui");
            commands.set(selector + " #PlayerName.Text", context.playerRef().getUsername());
            commands.set(selector + " #GreetingCount.Text", "Greetings this session: " + greetings);
            bindings.bind(CustomUIEventBindingType.Activating, "#GreetButton", "Greet", "", true);
        }

        @Override
        public void handleEvent(InventoryContext context, InventoryContentEvent event) {
            if (!"Greet".equals(event.action())) return;
            greetings++;
            context.playerRef().sendMessage(Message.raw("The example tab belongs to its companion mod."));
            context.requestRefresh();
        }
    }
}
