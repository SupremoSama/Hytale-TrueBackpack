package com.supremosan.custominventory.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.custominventory.api.*;

import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Supported legacy custom page; never intercepts or hides native pages. */
public final class InventoryShellPage extends InteractiveCustomUIPage<InventoryShellPage.Event> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String DEFAULT_PAGE = "inventory:native";
    private final InventoryRegistry registry;
    private final Consumer<InventoryShellPage> dismissedCallback;
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private InventoryPageDefinition activeDefinition;
    private InventoryRegistry.Entry<InventoryPageDefinition> activeRegistration;
    private final Map<String, InventoryRegistry.Entry<InventoryButtonDefinition>> mountedButtons = new HashMap<>();
    private final Map<String, InventoryRegistry.Entry<InventoryPageDefinition>> mountedPages = new HashMap<>();
    private final String pageInstanceId = UUID.randomUUID().toString();
    private InventoryContent activeContent;
    private volatile InventoryContext activeContext;
    private String pageId = DEFAULT_PAGE;
    private String sessionId;
    private long revision;
    private boolean dismissed;

    public InventoryShellPage(PlayerRef playerRef, InventoryRegistry registry,
                              Consumer<InventoryShellPage> dismissedCallback) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Event.CODEC);
        this.registry = Objects.requireNonNull(registry);
        this.dismissedCallback = Objects.requireNonNull(dismissedCallback);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands,
                      UIEventBuilder events, Store<EntityStore> store) {
        activeContext = new InventoryContext(ref, store, playerRef, this::requestRefresh);
        render(commands, events);
    }

    private void render(UICommandBuilder commands, UIEventBuilder events) {
        var pages = registry.pagesSnapshot();
        var selectedRegistration = registry.getPageRegistration(pageId);
        if (selectedRegistration == null && !pages.isEmpty()) selectedRegistration = registry.getPageRegistration(pages.getFirst().id());
        var selected = selectedRegistration == null ? null : selectedRegistration.definition();
        if (selectedRegistration != activeRegistration) {
            dismissContent();
            activeRegistration = selectedRegistration;
            activeDefinition = selected;
            activeContent = selected == null ? null : Objects.requireNonNull(selected.factory().apply(activeContext),
                    "Inventory page factory returned null: " + selected.id());
        }
        pageId = selected == null ? null : selected.id();
        sessionId = pageInstanceId + ":" + (++revision);
        mountedButtons.clear();
        mountedPages.clear();

        commands.append("Inventory/InventoryShell.ui");
        commands.set("#PageTitle.Text", selected == null ? "Custom inventory" : selected.title());
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", coreEvent("Close", ""));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshButton", coreEvent("Refresh", ""));

        for (int index = 0; index < pages.size(); index++) {
            var definition = pages.get(index);
            var registration = registry.getPageRegistration(definition.id());
            if (registration != null && registration.definition() == definition) mountedPages.put(definition.id(), registration);
            commands.append("#Navigation", "Inventory/NavigationButton.ui");
            String selector = "#Navigation[" + index + "] #EntryButton";
            commands.set(selector + ".Text", definition.title());
            commands.set(selector + ".Disabled", definition == selected);
            events.addEventBinding(CustomUIEventBindingType.Activating, selector, coreEvent("Navigate", definition.id()));
        }
        var buttons = registry.buttonsSnapshot();
        for (int index = 0; index < buttons.size(); index++) {
            var definition = buttons.get(index);
            var registration = registry.getButtonRegistration(definition.id());
            if (registration != null && registration.definition() == definition) mountedButtons.put(definition.id(), registration);
            commands.append("#ExtensionButtons", "Inventory/NavigationButton.ui");
            String selector = "#ExtensionButtons[" + index + "] #EntryButton";
            commands.set(selector + ".Text", definition.title());
            events.addEventBinding(CustomUIEventBindingType.Activating, selector, coreEvent("Button", definition.id()));
        }

        if (activeContent != null) {
            activeContent.build(activeContext, commands,
                    new InventoryEventBindings(events, "#ContentHost", pageId, sessionId), "#ContentHost");
        } else {
            commands.appendInline("#ContentHost", "Label { Text: \"No inventory pages registered.\"; Style: (TextColor: #c9d6df, FontSize: 18); }");
        }
    }

    private EventData coreEvent(String action, String target) {
        return new EventData().append("Action", action).append("Target", target).append("SessionId", sessionId);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Event event) {
        if (dismissed || !acceptsEvent(sessionId, event.sessionId) || !isActive(ref, store)) return;
        switch (event.action == null ? "" : event.action) {
            case "Close" -> store.getComponent(ref, Player.getComponentType()).getPageManager().setPage(ref, store, Page.None);
            case "Refresh" -> requestRefresh();
            case "Navigate" -> {
                var mounted = mountedPages.get(event.target);
                if (mounted != null && mounted == registry.getPageRegistration(event.target)) {
                    pageId = event.target;
                    requestRefresh();
                }
            }
            case "Button" -> {
                var mounted = mountedButtons.get(event.target);
                if (mounted != null && mounted == registry.getButtonRegistration(event.target)) {
                    mounted.definition().action().accept(activeContext);
                    requestRefresh();
                }
            }
            case "Content" -> {
                // A removed/replaced contribution cannot receive old UI events.
                if (activeContent != null && activeRegistration == registry.getPageRegistration(pageId)
                        && Objects.equals(pageId, event.pageId)) {
                    activeContent.handleEvent(activeContext,
                            new InventoryContentEvent(event.contentAction, event.payload, event.slotIndex));
                    requestRefresh();
                }
            }
            default -> { }
        }
    }

    /** Prevents old rendered events from applying to a newer selection or content session. */
    public static boolean acceptsEvent(String mountedSession, String eventSession) {
        return mountedSession != null && mountedSession.equals(eventSession);
    }

    private boolean isActive(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!ref.isValid() || ref.getStore() != store) return false;
        var player = store.getComponent(ref, Player.getComponentType());
        return player != null && player.getPageManager().getCustomPage() == this;
    }

    private void requestRefresh() {
        if (!refreshQueued.compareAndSet(false, true)) return;
        var ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            refreshQueued.set(false);
            return;
        }
        var store = ref.getStore();
        var retainedContext = activeContext;
        if (retainedContext != null && retainedContext.store() != store) {
            refreshQueued.set(false);
            closeForWorldRemoval(retainedContext.store().getExternalData().getWorld());
            return;
        }
        store.getExternalData().getWorld().execute(() -> {
            refreshQueued.set(false);
            if (dismissed || !isActive(ref, store)) return;
            activeContext = new InventoryContext(ref, store, playerRef, this::requestRefresh);
            var commands = new UICommandBuilder();
            var events = new UIEventBuilder();
            try {
                render(commands, events);
            } catch (RuntimeException failure) {
                LOGGER.atWarning().withCause(failure).log("Failed to refresh custom inventory");
                var player = store.getComponent(ref, Player.getComponentType());
                if (player != null && isActive(ref, store)) player.getPageManager().setPage(ref, store, Page.None);
                else onDismiss(ref, store);
                return;
            }
            // Already on the world thread; avoid delayed updates leaking into a different page.
            var player = store.getComponent(ref, Player.getComponentType());
            if (player != null && isActive(ref, store)) {
                player.getPageManager().updateLegacyCustomPage(new CustomPage(getClass().getName(),
                        false, true, getLifetime(), commands.getCommands(), events.getEvents()));
            }
        });
    }

    private void dismissContent() {
        var content = activeContent;
        activeContent = null;
        activeDefinition = null;
        activeRegistration = null;
        if (content != null) content.onDismiss(activeContext);
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (dismissed) return;
        dismissed = true;
        try {
            dismissContent();
        } finally {
            dismissedCallback.accept(this);
        }
    }

    /** Queues teardown on the owning world thread. */
    public void closeForShutdown() {
        var ref = playerRef.getReference();
        var retainedContext = activeContext;
        if (retainedContext == null) return;
        var store = retainedContext.store();
        var contextRef = retainedContext.ref();
        store.getExternalData().getWorld().execute(() -> {
            if (dismissed) return;
            if (ref != null && isActive(ref, store)) {
                store.getComponent(ref, Player.getComponentType()).getPageManager().setPage(ref, store, Page.None);
            } else onDismiss(contextRef, store);
        });
    }

    public boolean belongsTo(PlayerRef player) { return playerRef.getUuid().equals(player.getUuid()); }

    /** The old world owns listeners even after the player entity moves to another world. */
    public void closeForWorldRemoval(World world) {
        var retainedContext = activeContext;
        if (retainedContext == null || retainedContext.store().getExternalData().getWorld() != world) return;
        world.execute(() -> {
            if (!dismissed && activeContext == retainedContext) onDismiss(retainedContext.ref(), retainedContext.store());
        });
    }

    public static final class Event {
        public static final BuilderCodec<Event> CODEC = BuilderCodec.builder(Event.class, Event::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("Target", Codec.STRING), (d, v) -> d.target = v, d -> d.target).add()
                .append(new KeyedCodec<>("PageId", Codec.STRING), (d, v) -> d.pageId = v, d -> d.pageId).add()
                .append(new KeyedCodec<>("SessionId", Codec.STRING), (d, v) -> d.sessionId = v, d -> d.sessionId).add()
                .append(new KeyedCodec<>("ContentAction", Codec.STRING), (d, v) -> d.contentAction = v, d -> d.contentAction).add()
                .append(new KeyedCodec<>("Payload", Codec.STRING), (d, v) -> d.payload = v, d -> d.payload).add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER), (d, v) -> d.slotIndex = v, d -> d.slotIndex).add()
                .build();
        public String action;
        public String target;
        public String pageId;
        public String sessionId;
        public String contentAction;
        public String payload;
        public Integer slotIndex;
    }
}
