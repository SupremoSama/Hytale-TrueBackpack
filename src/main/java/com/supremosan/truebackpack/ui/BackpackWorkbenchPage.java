package com.supremosan.truebackpack.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.supremosan.custominventory.CustomInventoryPlugin;
import com.supremosan.custominventory.api.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.supremosan.truebackpack.cosmetic.CosmeticPreferenceUtils;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.listener.BackpackArmorListener;
import com.supremosan.truebackpack.listener.CosmeticListener;
import com.supremosan.truebackpack.listener.HatArmorListener;
import com.supremosan.truebackpack.listener.QuiverListener;
import com.supremosan.truebackpack.registries.BackpackRegistry;
import com.supremosan.truebackpack.util.BlockPlacementUtil;
import com.supremosan.truebackpack.util.BackpackPaintService;
import com.supremosan.truebackpack.util.BackpackProgression;
import com.supremosan.truebackpack.util.I18nHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Backpack-specific content hosted by CustomInventory. Native grids, input and page lifetime belong to the host.
 */
public class BackpackWorkbenchPage implements InventoryContent {
    public static final String VIEW_ID = "truebackpack:workbench";
    private final PlayerRef playerRef;
    private InventoryContext activeContext;
    private String contentSelector;
    private String renderedTab;
    private List<String> mountedRecipes = List.of();
    private String mountedUpgrade;
    private static final Set<BackpackWorkbenchPage> ACTIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final class PageData {
        String action;
        String target;
        String skin;
        Boolean usePaint;
        String name;
        String color;
        PageData(InventoryContentEvent event) {
            action = event.action(); target = event.payload();
            name = event.values().get("@Text");
            color = event.values().get("@Color");
            skin = event.values().get("@Choice");
            String checked = event.values().get("@Checked");
            usePaint = checked == null ? null : Boolean.valueOf(checked);
        }
    }

    public record ActiveBackpack(
            ItemContainer container,
            short slot,
            ItemStack stack,
            boolean isEquippedArmor
    ) {}

    public record SkinOption(
            String id,
            String descKey,
            String iconItemId
    ) {}

    public record TierUpgrade(
            String fromItemId,
            String toItemId,
            short newCapacity,
            Map<String, Integer> costs
    ) {}

    public record LevelUpgrade(
            int targetLevel,
            short newCapacity,
            Map<String, Integer> costs
    ) {}

    private static final List<SkinOption> AVAILABLE_SKINS = List.of(
            new SkinOption("default", "server.truebackpack.workbench.skin.default.desc", ""),
            new SkinOption("Utility_Fibre_Side_Bag", "server.truebackpack.workbench.skin.Utility_Fibre_Side_Bag.desc", "Utility_Fibre_Side_Bag"),
            new SkinOption("Utility_Leather_Side_Backpack", "server.truebackpack.workbench.skin.Utility_Leather_Side_Backpack.desc", "Utility_Leather_Side_Backpack"),
            new SkinOption("Utility_Leather_Backpack", "server.truebackpack.workbench.skin.Utility_Leather_Backpack.desc", "Utility_Leather_Backpack"),
            new SkinOption("Utility_Leather_Medium_Backpack", "server.truebackpack.workbench.skin.Utility_Leather_Medium_Backpack.desc", "Utility_Leather_Medium_Backpack"),
            new SkinOption("Utility_Leather_Big_Backpack", "server.truebackpack.workbench.skin.Utility_Leather_Big_Backpack.desc", "Utility_Leather_Big_Backpack"),
            new SkinOption("Utility_Leather_Extra_Big_Backpack", "server.truebackpack.workbench.skin.Utility_Leather_Extra_Big_Backpack.desc", "Utility_Leather_Extra_Big_Backpack")
    );

    private static final Map<String, TierUpgrade> TIER_UPGRADES = new LinkedHashMap<>();
    static {
        TIER_UPGRADES.put("Utility_Fibre_Side_Bag", new TierUpgrade(
                "Utility_Fibre_Side_Bag",
                "Utility_Leather_Side_Backpack",
                (short) 6,
                Map.of("Ingredient_Leather_Medium", 4, "Ingredient_Bar_Iron", 1)
        ));
        TIER_UPGRADES.put("Utility_Leather_Side_Backpack", new TierUpgrade(
                "Utility_Leather_Side_Backpack",
                "Utility_Leather_Backpack",
                (short) 9,
                Map.of("Ingredient_Leather_Medium", 16, "Ingredient_Bar_Iron", 8)
        ));
        TIER_UPGRADES.put("Utility_Leather_Backpack", new TierUpgrade(
                "Utility_Leather_Backpack",
                "Utility_Leather_Medium_Backpack",
                (short) 18,
                Map.of("Ingredient_Fabric_Scrap_Cindercloth", 40, "Ingredient_Leather_Heavy", 24, "Ingredient_Bar_Cobalt", 8)
        ));
        TIER_UPGRADES.put("Utility_Leather_Medium_Backpack", new TierUpgrade(
                "Utility_Leather_Medium_Backpack",
                "Utility_Leather_Big_Backpack",
                (short) 27,
                Map.of("Ingredient_Leather_Storm", 16, "Ingredient_Bar_Adamantite", 8, "Ingredient_Voidheart", 1)
        ));
        TIER_UPGRADES.put("Utility_Leather_Big_Backpack", new TierUpgrade(
                "Utility_Leather_Big_Backpack",
                "Utility_Leather_Extra_Big_Backpack",
                (short) 36,
                Map.of("Ingredient_Fibre", 16, "Wood_Trunk", 8)
        ));
    }

    private static final Map<Integer, LevelUpgrade> LEVEL_UPGRADES = new LinkedHashMap<>();
    static {
        LEVEL_UPGRADES.put(1, new LevelUpgrade(1, (short) 45, Map.of("Ingredient_Fibre", 16, "Wood_Trunk", 8)));
        LEVEL_UPGRADES.put(2, new LevelUpgrade(2, (short) 54, Map.of("Ingredient_Leather_Heavy", 8, "Ingredient_Bar_Iron", 4)));
    }

    private BackpackCraftingWindow craftingWindow;
    private boolean dismissed;
    private java.util.concurrent.ScheduledFuture<?> pendingBenchCheck;
    private String selectedRecipeId;
    private boolean upgradeSelected;
    private String currentTab = "personalize";
    private String statusMessage = "";
    @Nullable
    private final org.joml.Vector3i benchPosition;

    public BackpackWorkbenchPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, null);
    }

    public BackpackWorkbenchPage(@Nonnull PlayerRef playerRef, @Nullable org.joml.Vector3i benchPosition) {
        this.playerRef = Objects.requireNonNull(playerRef);
        this.benchPosition = benchPosition == null ? null : new org.joml.Vector3i(benchPosition);
        if (benchPosition != null) currentTab = "crafting";
    }

    public static void closeAll() {
        for (var page : ACTIVE) {
            var context = page.activeContext;
            if (context != null) context.requestClose();
        }
    }

    private InventoryPageDefinition definition() {
        return new InventoryPageDefinition(VIEW_ID, text("page.personalize"), 0, context -> this);
    }

    /** One content/controller and extension are created for each player opening. */
    public static boolean open(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef) {
        var content = new BackpackWorkbenchPage(playerRef);
        return CustomInventoryPlugin.get().openView(ref, store, playerRef,
                content.definition(), content.extension());
    }

    private InventoryUiExtension extension() {
        return new InventoryUiExtension() {
            @Override public void onCreated(InventoryContext context, InventoryUiEditor editor) {
                editor.append(InventoryElementId.NAVIGATION, "Pages/BackpackWorkbenchNavigation.ui");
                editor.bind(CustomUIEventBindingType.Activating, InventoryElementId.NAVIGATION,
                        "#CharacterCraftingButton", "Crafting", "", true);
                editor.bind(CustomUIEventBindingType.Activating, InventoryElementId.NAVIGATION,
                        "#CharacterVisibilityButton", "TabVisibility", "", true);
                editor.bind(CustomUIEventBindingType.Activating, InventoryElementId.NAVIGATION,
                        "#CharacterPersonalizeButton", "TabPersonalize", "", true);
                if (craftingWindow != null) {
                    editor.append(InventoryElementId.AUXILIARY_HOST, "Pages/BackpackWorkbenchBench.ui");
                    editor.bind(CustomUIEventBindingType.Activating, InventoryElementId.AUXILIARY_HOST,
                            "#BenchUpgradeButton", "UpgradeBench", "", true);
                }
            }
            @Override public void onUpdate(InventoryContext context, InventoryUiEditor editor) {
                applyPresentation(context, editor);
            }
            @Override public void handleEvent(InventoryContext context, InventoryContentEvent event) {
                BackpackWorkbenchPage.this.handleEvent(context, event);
            }
        };
    }

    private void applyPresentation(InventoryContext context, InventoryUiEditor editor) {
        String title = switch (currentTab) {
            case "crafting" -> craftingWindow == null ? "page.upgrades" : "page.crafting";
            case "visibility" -> "page.visibility";
            default -> "page.personalize";
        };
        editor.text(InventoryElementId.TITLE, text(title));
        Anchor content = new Anchor();
        content.setHeight(Value.of(470));
        editor.anchor(InventoryElementId.CONTENT_PANEL, content);
        editor.padding(InventoryElementId.CONTENT_PANEL,
                Value.ref("Pages/BackpackWorkbenchPage.ui", "WorkbenchContentPadding"));
        editor.edit(InventoryElementId.NAVIGATION, cb -> {
            cb.set("#CharacterCraftingButton.TooltipText", text(craftingWindow == null ? "tab.upgrades" : "tab.production"));
            cb.set("#CharacterVisibilityButton.TooltipText", text("tab.visibility"));
            cb.set("#CharacterPersonalizeButton.TooltipText", text("tab.personalize"));
            cb.set("#CharacterCraftingActive.Visible", "crafting".equals(currentTab));
            cb.set("#CharacterVisibilityActive.Visible", "visibility".equals(currentTab));
            cb.set("#CharacterPersonalizeActive.Visible", "personalize".equals(currentTab));
        });
        if (craftingWindow != null) {
            Anchor auxiliary = new Anchor();
            auxiliary.setTop(Value.of(180));
            auxiliary.setWidth(Value.ref("Pages/BackpackWorkbenchBench.ui", "BenchPanelWidth"));
            auxiliary.setHeight(Value.ref("Pages/BackpackWorkbenchBench.ui", "BenchPanelHeight"));
            editor.anchor(InventoryElementId.AUXILIARY_HOST, auxiliary);
            editor.edit(InventoryElementId.AUXILIARY_HOST,
                    cb -> updateBenchPanel(context.ref(), context.store(), cb));
        }
    }

    private static ItemContainer inventory(Ref<EntityStore> ref, Store<EntityStore> store, boolean hotbar) {
        var component = hotbar ? store.getComponent(ref, InventoryComponent.Hotbar.getComponentType())
                : store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        return component == null ? null : component.getInventory();
    }

    private static ItemContainer backpackInventory(Ref<EntityStore> ref, Store<EntityStore> store) {
        var backpack = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());
        return backpack != null ? backpack.getInventory() : null;
    }

    private static UICommandBuilder scope(UICommandBuilder builder, String selector) {
        return new InventoryCommands(builder, selector);
    }

    @Override
    public void build(InventoryContext context, UICommandBuilder commands,
                      InventoryEventBindings events, String selector) {
        activeContext = context;
        contentSelector = selector;
        ACTIVE.add(this);
        renderedTab = null;
        mountedRecipes = List.of();
        mountedUpgrade = null;
        commands.append(selector, "Pages/BackpackWorkbenchPage.ui");
        refreshPage(context.ref(), context.store(), scope(commands, selector), events);
        startBenchCheck();
    }

    @Override
    public void refresh(InventoryContext context, UICommandBuilder commands,
                        InventoryEventBindings events, String selector) {
        activeContext = context;
        if (benchPosition != null && !isBenchAvailable(context.ref(), context.store())) {
            context.requestClose(); return;
        }
        var active = findActiveBackpack(context.ref(), context.store());
        if ("personalize".equals(currentTab) && currentTab.equals(renderedTab) && active != null && sameEditedBackpack(active)) {
            updateHeader(active, scope(commands, selector), playerRef.getLanguage());
            applyInputHints(scope(commands, selector));
        } else refreshPage(context.ref(), context.store(), scope(commands, selector), events);
    }

    private void refreshPage(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull InventoryEventBindings eventBuilder) {

        String lang = playerRef.getLanguage();
        boolean mount = !currentTab.equals(renderedTab) || "personalize".equals(currentTab);
        renderedTab = currentTab;

        ActiveBackpack activeBp = findActiveBackpack(ref, store);
        if (mount) {
            commandBuilder.clear("#WorkbenchList");
            mountedRecipes = List.of();
            mountedUpgrade = null;
        }
        updateHeader(activeBp, commandBuilder, lang);

        if ("crafting".equals(currentTab)) {
            commandBuilder.set("#LoadingContainer.Visible", false);
            commandBuilder.set("#WorkbenchList.Visible", true);
            commandBuilder.set("#BackpackHeader.Visible", false);
            if (mount) commandBuilder.append("#WorkbenchList", "Pages/BackpackProductionPanel.ui");
            buildCraftingTab(ref, store, commandBuilder, eventBuilder, lang, mount);
            boolean showUpgrade = upgradeSelected || craftingWindow == null;
            commandBuilder.set("#RecipeDetails.Visible", !showUpgrade && selectedRecipeId != null);
            commandBuilder.set("#UpgradeDetails.Visible", showUpgrade);
            commandBuilder.set("#UpgradeSelected.Visible", showUpgrade);
            commandBuilder.set("#ProductionEmpty.Visible", !showUpgrade && selectedRecipeId == null);
            commandBuilder.set("#ProductionEmptyText.Text", text("flow.select_recipe"));
            commandBuilder.set("#CraftNewTitle.Text", text("flow.craft_new"));
            commandBuilder.set("#CraftNewTitle.Visible", craftingWindow != null);
            commandBuilder.set("#RecipeGrid.Visible", craftingWindow != null);
            commandBuilder.set("#UpgradeSectionTitle.Text", text("flow.upgrade_existing"));
            commandBuilder.set("#UpgradeChoiceIcon.Visible", activeBp != null);
            commandBuilder.set("#SelectUpgrade.TooltipText", text("flow.hold_backpack"));
            commandBuilder.set("#UpgradeChoiceName.Text", activeBp == null ? text("flow.choose_backpack") : backpackName(activeBp.stack(), lang));
            commandBuilder.set("#UpgradeChoiceInfo.Text", activeBp == null ? text("flow.hold_backpack")
                    : I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.header.capacity", BackpackItemFactory.getTotalCapacity(activeBp.stack())));
            if (activeBp != null) commandBuilder.set("#UpgradeChoiceIcon.ItemId", activeBp.stack().getItemId());
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SelectUpgrade",
                    new EventData().append("Action", "SelectUpgrade"));
            if (showUpgrade) {
                String upgrade = activeBp == null ? "empty" : activeBp.stack().getItemId() + ":" + BackpackItemFactory.getUpgradeLevel(activeBp.stack());
                boolean mountUpgrade = !Objects.equals(mountedUpgrade, upgrade);
                if (mountUpgrade) commandBuilder.clear("#UpgradeDetails");
                buildUpgradeTab(ref, store, activeBp, commandBuilder, eventBuilder, lang, mountUpgrade);
                mountedUpgrade = upgrade;
            }
            if (craftingWindow != null) {
                var data = craftingWindow.getData();
                int queue = data.has("queueSize") ? data.get("queueSize").getAsInt() : 0;
                commandBuilder.set("#CraftingQueue.Visible", queue > 0);
                commandBuilder.set("#CraftingQueue.Text", text("craft.queue") + " " + queue);
            }
            return;
        }

        if ("visibility".equalsIgnoreCase(currentTab)) {
            commandBuilder.set("#LoadingContainer.Visible", false);
            commandBuilder.set("#BackpackHeader.Visible", false);
            commandBuilder.set("#WorkbenchList.Visible", true);
            if (mount) commandBuilder.append("#WorkbenchList", "Pages/BackpackVisibilityPanel.ui");
            buildVisibilityTab(ref, store, activeBp, commandBuilder, eventBuilder, lang, mount);
            return;
        }

        if (activeBp == null) {
            // Hide all content inside the UI and make only the text appear
            commandBuilder.set("#BackpackHeader.Visible", false);
            commandBuilder.set("#WorkbenchList.Visible", false);
            commandBuilder.set("#LoadingContainer.Visible", true);
            commandBuilder.set("#LoadingContainer #LoadingText.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.no_backpack.desc"));
            return;
        }

        commandBuilder.set("#LoadingContainer.Visible", false);
        commandBuilder.set("#BackpackHeader.Visible", true);
        commandBuilder.set("#WorkbenchList.Visible", true);

        if ("personalize".equalsIgnoreCase(currentTab)) {
            buildPersonalizeTab(ref, store, activeBp, commandBuilder, eventBuilder);
        }
    }

    private void updateHeader(@Nullable ActiveBackpack activeBp, @Nonnull UICommandBuilder commandBuilder, @Nullable String lang) {
        commandBuilder.set("#StatusMessage.Text", statusMessage);
        commandBuilder.set("#StatusMessage.Visible", !statusMessage.isBlank());
        if (activeBp == null) {
            commandBuilder.set("#BackpackHeader #CurrentIcon.ItemId", "");
            commandBuilder.set("#HeaderDetails #HeaderContext.Text", "");
            commandBuilder.set("#HeaderDetails #CurrentName.Text", "");
            commandBuilder.set("#HeaderDetails #CurrentInfo.Text", "");
            return;
        }

        ItemStack stack = activeBp.stack();
        String itemId = stack.getItemId();
        commandBuilder.set("#BackpackHeader #CurrentIcon.ItemId", itemId);
        commandBuilder.set("#HeaderDetails #HeaderContext.Text", text("crafting".equals(currentTab) ? "header.upgrade_target" : "header.selected"));
        commandBuilder.set("#HeaderDetails #CurrentName.Text", (BackpackItemFactory.getCustomName(stack) != null ? BackpackItemFactory.getCustomName(stack) : I18nHelper.resolveItemName(itemId, lang)));
        commandBuilder.set("#HeaderDetails #CurrentInfo.Text", "Utility_Heli_Backpack".equalsIgnoreCase(itemId) ? ""
                : I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.header.capacity", BackpackItemFactory.getTotalCapacity(stack)));
    }

    public static boolean hasAvailableUpgrade(ItemStack stack) {
        return TIER_UPGRADES.containsKey(stack.getItemId())
                || ("Utility_Leather_Extra_Big_Backpack".equalsIgnoreCase(stack.getItemId())
                && BackpackItemFactory.getUpgradeLevel(stack) < BackpackItemFactory.MAX_UPGRADE_LEVEL);
    }
    private boolean upgradeAllowed(String target, Store<EntityStore> store) {
        if (craftingWindow == null) return false;
        int memories = com.hypixel.hytale.builtin.adventure.memories.MemoriesPlugin.get()
                .getMemoriesLevel(store.getExternalData().getWorld().getGameplayConfig());
        return eligibleRecipes().stream().anyMatch(recipe -> target.equals(recipe.getPrimaryOutput().getItemId())
                && BackpackProgression.canAccessRecipe(recipe, craftingWindow.benchId(), craftingWindow.tier(), memories));
    }
    private boolean recipeUnlocked(com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe recipe, Store<EntityStore> store) {
        return BackpackProgression.gate(recipe.getRequiredMemoriesLevel(), store.getExternalData().getWorld().getGameplayConfig()).unlocked();
    }

    private static String backpackName(ItemStack stack, @Nullable String lang) {
        String name = BackpackItemFactory.getCustomName(stack);
        return name == null ? I18nHelper.resolveItemName(stack.getItemId(), lang) : name;
    }

    private void buildUpgradeTab(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nullable ActiveBackpack activeBp,
            @Nonnull UICommandBuilder cb,
            @Nonnull InventoryEventBindings eb,
            @Nullable String lang, boolean mount) {
        if (mount) cb.append("#UpgradeDetails", "Pages/BackpackUpgradeEntry.ui");
        cb.set("#UpgradeTitle.Text", text("flow.upgrade_existing"));
        cb.set("#UpgradeButton.Text", text("flow.apply_upgrade"));
        cb.set("#UpgradeButton.Disabled", true);
        cb.set("#UpgradeCurrentLabel.Text", text("flow.current"));
        cb.set("#UpgradeResultLabel.Text", text("flow.result"));
        cb.set("#UpgradeMaterialsTitle.Text", text("flow.inventory_materials"));
        cb.set("#UpgradePreservationNote.Text", text("flow.preserved"));

        if (activeBp == null) {
            showUpgradeEmpty(cb, text("flow.hold_backpack"));
            return;
        }
        ItemStack stack = activeBp.stack();
        String itemId = stack.getItemId();
        if ("Utility_Heli_Backpack".equalsIgnoreCase(itemId)) {
            showUpgradeEmpty(cb, text("helipack.no_upgrades"));
            return;
        }
        TierUpgrade tier = TIER_UPGRADES.get(itemId);
        int nextLevel = BackpackItemFactory.getUpgradeLevel(stack) + 1;
        LevelUpgrade level = "Utility_Leather_Extra_Big_Backpack".equalsIgnoreCase(itemId)
                ? LEVEL_UPGRADES.get(nextLevel) : null;
        if (tier == null && level == null) {
            showUpgradeEmpty(cb, text("Utility_Leather_Extra_Big_Backpack".equalsIgnoreCase(itemId)
                    ? "flow.max_capacity" : "flow.no_upgrades"));
            return;
        }
        String target = tier != null ? tier.toItemId() : itemId;
        short capacity = tier != null ? tier.newCapacity() : level.newCapacity();
        Map<String, Integer> costs = tier != null ? tier.costs() : level.costs();
        var gate = memoryGate(target, store);

        cb.set("#UpgradeCurrentIcon.ItemId", itemId);
        cb.set("#UpgradeCurrentName.Text", backpackName(stack, lang));
        cb.set("#UpgradeCurrentCapacity.Text", I18nHelper.getOrFallback(lang,
                "server.truebackpack.workbench.header.capacity", BackpackItemFactory.getTotalCapacity(stack)));
        cb.set("#UpgradeResultIcon.Visible", gate.unlocked());
        cb.set("#UpgradeResultLockedMarker.Visible", !gate.unlocked());
        if (gate.unlocked()) cb.set("#UpgradeResultIcon.ItemId", target);
        cb.set("#UpgradeResultName.Text", gate.unlocked() ? I18nHelper.resolveItemName(target, lang) : text("flow.locked_upgrade"));
        cb.set("#UpgradeResultCapacity.Text", I18nHelper.getOrFallback(lang,
                "server.truebackpack.workbench.header.capacity", capacity));
        if (tier == null) cb.set("#UpgradeResultLabel.Text", text("flow.result") + " · " + text("level") + " " + nextLevel + "/2");

        var hotbar = inventory(ref, store, true);
        var storage = inventory(ref, store, false);
        var backpack = backpackInventory(ref, store);
        var materials = costs.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        for (int i = 0; i < 3; i++) {
            cb.set("#UpgradeMaterialRow" + i + ".Visible", i < materials.size());
            if (i >= materials.size()) continue;
            var material = materials.get(i);
            int count = (hotbar == null ? 0 : countItem(hotbar, material.getKey()))
                    + (storage == null ? 0 : countItem(storage, material.getKey()))
                    + (backpack == null ? 0 : countItem(backpack, material.getKey()));
            cb.set("#UpgradeMaterialIcon" + i + ".ItemId", "Wood_Trunk".equals(material.getKey()) ? "Wood_Oak_Trunk" : material.getKey());
            cb.set("#UpgradeMaterial" + i + ".Text", count + " / " + material.getValue() + " × "
                    + ("Wood_Trunk".equals(material.getKey()) ? text("flow.any_trunk") : I18nHelper.resolveItemName(material.getKey(), lang)));
            cb.set("#UpgradeMaterial" + i + ".Style.TextColor", count >= material.getValue() ? "#62b78d" : "#d78b82");
        }
        boolean materialsReady = hasMaterials(ref, store, costs);
        boolean benchReady = upgradeAllowed(target, store);
        boolean ready = benchReady && gate.unlocked() && materialsReady;
        String reason = craftingWindow == null ? text("flow.requires_bench")
                : !gate.unlocked() ? memoryLabel(gate)
                : !benchReady ? text(craftingWindow.isMaster() ? "flow.upgrade_unavailable" : "flow.requires_master")
                : !materialsReady ? text("craft.materials") : "";
        cb.set("#UpgradeStatus.Text", reason);
        cb.set("#UpgradeButton.Disabled", !ready);
        if (!gate.unlocked()) {
            cb.set("#UpgradeResultLockedMarker.TooltipTextSpans", memoryTooltip(gate));
            cb.set("#UpgradeStatus.TooltipTextSpans", memoryTooltip(gate));
        } else {
            cb.set("#UpgradeResultLockedMarker.TooltipTextSpans", Message.raw(""));
            cb.set("#UpgradeStatus.TooltipTextSpans", Message.raw(""));
        }
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#UpgradeButton",
                new EventData().append("Action", tier != null ? "UpgradeTier" : "UpgradeLevel")
                        .append("Target", tier != null ? target : Integer.toString(nextLevel)));
    }

    private void showUpgradeEmpty(UICommandBuilder cb, String explanation) {
        cb.set("#UpgradeComparison.Visible", false);
        cb.set("#UpgradeMaterials.Visible", false);
        cb.set("#UpgradeEmpty.Visible", true);
        cb.set("#UpgradeEmptyText.Text", explanation);
        cb.set("#UpgradePreservationNote.Visible", false);
        cb.set("#UpgradeStatus.Text", "");
    }

    private void buildVisibilityTab(Ref<EntityStore> ref, Store<EntityStore> store,
            @Nullable ActiveBackpack activeBp, UICommandBuilder cb, InventoryEventBindings events,
            @Nullable String lang, boolean mount) {
        var activeEntry = activeBp == null ? null : BackpackRegistry.getByItem(activeBp.stack().getItemId());
        String backpackIcon = activeEntry != null && !activeEntry.isHelipack()
                ? activeBp.stack().getItemId() : "Utility_Leather_Backpack";
        String[] ids = {"backpack", "quiver", "hat"};
        String[] icons = {backpackIcon, "Weapon_Arrow_Iron", "Utility_Torch_Bandana"};
        boolean[] visible = {CosmeticPreferenceUtils.isBackpackVisible(store, ref),
                CosmeticPreferenceUtils.isQuiverVisible(store, ref), CosmeticPreferenceUtils.isHatVisible(store, ref)};
        for (int index = 0; index < ids.length; index++) {
            if (mount) cb.append("#VisibilityEntries", "Pages/BackpackVisibilityEntry.ui");
            String selector = "#VisibilityEntries[" + index + "]";
            cb.set(selector + " #CosmeticIcon.ItemId", icons[index]);
            cb.set(selector + " #CosmeticName.Text", I18nHelper.getOrFallback(lang,
                    "server.truebackpack.workbench.visibility." + ids[index] + ".title"));
            cb.set(selector + " #CosmeticDesc.Text", I18nHelper.getOrFallback(lang,
                    "server.truebackpack.workbench.visibility." + ids[index] + ".desc"));
            updateVisibilityState(cb, selector, visible[index]);
            cb.set(selector + " #ToggleButton.Text", text(visible[index] ? "visibility.hide" : "visibility.show"));
            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #ToggleButton",
                    new EventData().append("Action", "ToggleCosmetic").append("Target", ids[index]));
        }
    }

    private void updateVisibilityState(UICommandBuilder cb, String selector, boolean visible) {
        cb.set(selector + " #VisibilityState.Text", text(visible ? "btn.visible" : "btn.hidden"));
        cb.set(selector + " #VisibilityState.Style.TextColor", visible ? "#91e9c2" : "#c5d1dd");
    }

    @Override
    public void handleEvent(InventoryContext context, InventoryContentEvent event) {
        var ref = context.ref(); var store = context.store();
        handleDataEvent(ref, store, new PageData(event));
    }

    private void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageData data) {
        if (dismissed || data.action == null) return;

        if (benchPosition != null && !isBenchAvailable(ref, store)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) activeContext.requestClose();
            return;
        }
        if ("Crafting".equals(data.action)) {
            currentTab = "crafting";
            statusMessage = "";
            sendRefresh(ref, store);
            return;
        }

        if ("SelectRecipe".equals(data.action) && craftingWindow != null) {
            if (eligibleRecipes().stream().anyMatch(r -> r.getId().equals(data.target) && recipeUnlocked(r, store))) {
                selectedRecipeId = data.target;
                upgradeSelected = false;
                statusMessage = "";
            }
            sendRefresh(ref, store);
            return;
        }
        if ("SelectUpgrade".equals(data.action)) {
            currentTab = "crafting";
            upgradeSelected = true;
            statusMessage = "";
            sendRefresh(ref, store);
            return;
        }
        if ("CraftRecipe".equals(data.action) && craftingWindow != null) {
            var recipe = eligibleRecipes().stream().filter(r -> r.getId().equals(selectedRecipeId)).findFirst().orElse(null);
            if (recipe != null && recipeUnlocked(recipe, store)) {
                var action = new com.hypixel.hytale.protocol.packets.window.CraftRecipeAction();
                action.recipeId = recipe.getId();
                action.quantity = 1;
                craftingWindow.handleAction(ref, store, action);
            }
            sendRefresh(ref, store);
            return;
        }
        if ("UpgradeBench".equals(data.action) && craftingWindow != null) {
            if (craftingWindow.upgradeRequirement() != null) {
                craftingWindow.handleAction(ref, store, new com.hypixel.hytale.protocol.packets.window.TierUpgradeAction());
            }
            sendRefresh(ref, store);
            return;
        }

        if ("Close".equalsIgnoreCase(data.action)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                activeContext.requestClose();
            }
            return;
        }

        if ("TabPersonalize".equalsIgnoreCase(data.action)) {
            currentTab = "personalize";
            statusMessage = "";
            sendRefresh(ref, store);
            return;
        }
        if ("TabVisibility".equalsIgnoreCase(data.action)) {
            currentTab = "visibility";
            statusMessage = "";
            sendRefresh(ref, store);
            return;
        }

        String lang = playerRef.getLanguage();

        if ("ToggleCosmetic".equalsIgnoreCase(data.action)) {
            handleToggleCosmetic(ref, store, data.target, lang);
            return;
        }

        ActiveBackpack activeBp = findActiveBackpack(ref, store);
        if (activeBp == null) {
            statusMessage = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.status.no_backpack");
            sendRefresh(ref, store);
            return;
        }

        if (Set.of("DraftAppearance", "DraftColor", "DraftPaint", "DraftName", "SaveCustomization").contains(data.action)) {
            handlePersonalize(ref, store, activeBp, data);
            return;
        }
        if ("UpgradeTier".equalsIgnoreCase(data.action)) {
            handleUpgradeTier(ref, store, activeBp, data.target, lang);
            return;
        }

        if ("UpgradeLevel".equalsIgnoreCase(data.action)) {
            handleUpgradeLevel(ref, store, activeBp, lang);
            return;
        }

        sendRefresh(ref, store);
    }

    private static final java.util.concurrent.ScheduledExecutorService PREVIEW_TIMER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "TrueBackpack-preview"); thread.setDaemon(true); return thread;
    });
    private java.util.concurrent.ScheduledFuture<?> pendingPreview;
    private long previewRevision;
    private String editedBackpackId;
    private ItemStack editedStack;
    private ItemContainer editedContainer;
    private short editedSlot;
    private String draftName;
    private String draftSkin;
    private String draftColor;
    private boolean draftUsePaint;
    private final BackpackPaintService.PreviewSession preview = new BackpackPaintService.PreviewSession();

    private boolean sameEditedBackpack(ActiveBackpack bp) {
        return bp.container() == editedContainer && bp.slot() == editedSlot
                && Objects.equals(editedBackpackId, BackpackItemFactory.getInstanceId(bp.stack()))
                && (editedBackpackId != null || Objects.equals(editedStack, bp.stack()));
    }
    private EventData customizationEvent(String action) {
        return new EventData().append("Action", action).append("@Text", "#BackpackName.Value")
                .append("@Choice", "#AppearanceSelect.Value").append("@Color", "#PaintPicker.Value")
                .append("@Checked", "#UsePaint.Value");
    }
    private void buildPersonalizeTab(Ref<EntityStore> ref, Store<EntityStore> store, ActiveBackpack bp, UICommandBuilder cb, InventoryEventBindings eb) {
        previewRevision++;
        if (pendingPreview != null) pendingPreview.cancel(false);
        if (!sameEditedBackpack(bp)) {
            editedBackpackId = BackpackItemFactory.getInstanceId(bp.stack());
            editedStack = bp.stack(); editedContainer = bp.container(); editedSlot = bp.slot();
            draftName = BackpackItemFactory.getCustomName(bp.stack());
            draftSkin = BackpackItemFactory.getTransmogSkin(bp.stack());
            if (draftSkin == null || BackpackRegistry.getByItem(draftSkin) == null) draftSkin = "default";
            draftColor = BackpackItemFactory.getPaintColor(bp.stack());
            draftUsePaint = draftColor != null;
            if (draftColor == null) draftColor = "#FFFFFF";
        }
        cb.append("#WorkbenchList", "Pages/BackpackPersonalizePanel.ui");
        for (var entry : Map.of("#NameTitle.Text", "personalize.name", "#BackpackName.PlaceholderText", "personalize.name_hint",
                "#AppearanceTitle.Text", "personalize.appearance", "#UsePaintLabel.Text", "personalize.use_paint",
                "#PaintExcluded.Text", "personalize.excluded", "#SaveCustomization.Text", "personalize.save_all",
                "#PreviewTitle.Text", "personalize.preview", "#PreviewHint.Text", "personalize.native_hint").entrySet())
            cb.set(entry.getKey(), text(entry.getValue()));
        applyInputHints(cb);
        var entries = new ArrayList<com.hypixel.hytale.server.core.ui.DropdownEntryInfo>();
        boolean heli = BackpackRegistry.getByItem(bp.stack().getItemId()).isHelipack();
        for (SkinOption skin : AVAILABLE_SKINS) {
            if (heli && !skin.id().equals("default")) continue;
            String label = skin.id().equals("default") ? text("skin.default.name") : I18nHelper.resolveItemName(skin.id(), playerRef.getLanguage());
            entries.add(new com.hypixel.hytale.server.core.ui.DropdownEntryInfo(
                    com.hypixel.hytale.server.core.ui.LocalizableString.fromString(label), skin.id()));
        }
        cb.set("#AppearanceSelect.Entries", entries);
        cb.set("#AppearanceSelect.Value", draftSkin);
        cb.set("#BackpackName.Value", draftName == null ? "" : draftName);
        cb.set("#PaintPicker.Value", draftColor);
        cb.set("#UsePaint.Value", draftUsePaint);
        updateNativePreview(ref, store, bp.stack(), cb);
        eb.addEventBinding(CustomUIEventBindingType.ValueChanged, "#AppearanceSelect", customizationEvent("DraftAppearance"), false);
        eb.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PaintPicker", customizationEvent("DraftColor"), false);
        eb.addEventBinding(CustomUIEventBindingType.ValueChanged, "#UsePaint", customizationEvent("DraftPaint"), false);
        eb.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BackpackName", customizationEvent("DraftName"), false);
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#SaveCustomization", customizationEvent("SaveCustomization"));
    }
    private ItemStack customizationDraft(ItemStack stack) {
        return com.supremosan.truebackpack.util.BackpackCustomization.draft(stack, draftName, draftSkin, draftColor, draftUsePaint);
    }
    private void updateNativePreview(Ref<EntityStore> ref, Store<EntityStore> store, ItemStack original, UICommandBuilder cb) {
        var draft = customizationDraft(original);
        boolean paintable = BackpackPaintService.canPaint(draft);
        cb.set("#PaintControls.Visible", paintable);
        cb.set("#PaintExcluded.Visible", !paintable);
        cb.set("#PreviewColor.Text", paintable && draftUsePaint ? draftColor : text("original"));
        // Deliver the empty host before asset packets, including during the initial page
        // build. Debouncing also avoids rebuilding the atlas for every picker movement.
        cb.clear("#PaintPreviewHost");
        cb.set("#PreviewHint.Text", text("personalize.preview_loading"));
        long revision = ++previewRevision;
        if (pendingPreview != null) pendingPreview.cancel(false);
        pendingPreview = PREVIEW_TIMER.schedule(() -> store.getExternalData().getWorld().execute(() -> {
            if (!previewIsCurrent(ref, store, revision)) return;
            try {
                String id = preview.prepare(draft, playerRef);
                long delay = preview.remainingDelayMillis();
                if (delay == 0) {
                    var ready = new InventoryCommands(contentSelector);
                    attachNativePreview(id, ready);
                    activeContext.update(ready);
                } else {
                    pendingPreview = PREVIEW_TIMER.schedule(() -> store.getExternalData().getWorld().execute(() -> {
                        if (!previewIsCurrent(ref, store, revision)) return;
                        var ready = new InventoryCommands(contentSelector);
                        attachNativePreview(id, ready);
                        activeContext.update(ready);
                    }), delay, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            } catch (java.io.IOException | RuntimeException e) {
                var fallback = new InventoryCommands(contentSelector);
                fallback.clear("#PaintPreviewHost");
                fallback.append("#PaintPreviewHost", "Pages/BackpackNativePreview.ui");
                fallback.set("#PaintPreview.ItemId", BackpackPaintService.visualEntry(draft).itemId());
                fallback.set("#PreviewHint.Text", text("personalize.preview_failed"));
                activeContext.update(fallback);
                java.util.logging.Logger.getLogger("TrueBackpack").warning("Native backpack preview failed: " + e.getMessage());
            }
        }), 500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    private boolean previewIsCurrent(Ref<EntityStore> ref, Store<EntityStore> store, long revision) {
        if (dismissed || activeContext == null || !activeContext.isActive() || !ref.isValid()
                || revision != previewRevision || !"personalize".equals(currentTab)) return false;
        var active = findActiveBackpack(ref, store);
        return active != null && sameEditedBackpack(active);
    }
    private void attachNativePreview(String id, UICommandBuilder cb) {
        preview.publish(id, playerRef);
        cb.clear("#PaintPreviewHost");
        cb.append("#PaintPreviewHost", "Pages/BackpackNativePreview.ui");
        cb.set("#PaintPreview.ItemId", id);
        cb.set("#PreviewHint.Text", text(hintKey()));
    }

    private String hintKey() {
        return "personalize.native_hint";
    }

    /** Personalize prompts follow CustomInventory's keyboard/controller mode. */
    private void applyInputHints(UICommandBuilder cb) {
        if (!"personalize".equals(renderedTab)) return;
        cb.set("#PreviewHint.Text", text(hintKey()));
    }
    private static String pickerColor(String value) {
        if (value != null && value.matches("#[0-9a-fA-F]{8}")) value = value.substring(0, 7);
        return BackpackItemFactory.normalizeColor(value);
    }
    private void handlePersonalize(Ref<EntityStore> ref, Store<EntityStore> store, ActiveBackpack bp, PageData data) {
        if (!"personalize".equals(currentTab) || !sameEditedBackpack(bp)) {
            statusMessage = text("personalize.changed"); sendRefresh(ref, store); return;
        }
        try {
            draftName = data.name;
            draftSkin = data.skin;
            draftColor = pickerColor(data.color);
            draftUsePaint = "DraftColor".equals(data.action) || Boolean.TRUE.equals(data.usePaint);
            var draft = customizationDraft(bp.stack());
            if ("SaveCustomization".equals(data.action)) {
                previewRevision++;
                if (pendingPreview != null) pendingPreview.cancel(false);
                BackpackPaintService.texture(draft);
                preview.waitForAtlasRebuild();
                if (!BackpackItemFactory.hasInstanceId(draft)) draft = BackpackItemFactory.createBackpackInstance(draft);
                bp.container().setItemStackForSlot(bp.slot(), draft);
                rebuildBackpack(ref, store);
                editedContainer = null;
                statusMessage = text("personalize.updated");
                sendRefresh(ref, store);
                return;
            }
            if ("DraftName".equals(data.action)) return;
            var selection = new InventoryCommands(contentSelector);
            selection.set("#UsePaint.Value", draftUsePaint);
            updateNativePreview(ref, store, bp.stack(), selection);
            activeContext.update(selection);
        } catch (RuntimeException e) {
            editedContainer = null;
            statusMessage = text("personalize.failed");
            sendRefresh(ref, store);
        }
    }
    private void rebuildBackpack(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        String uuid = playerRef.getUuid().toString();
        BackpackArmorListener.syncBackpackAttachment(uuid, store, ref);
        CosmeticListener.scheduleAttachmentRebuild(player, store, ref, uuid);
    }
    private BackpackProgression.Gate memoryGate(String itemId, Store<EntityStore> store) {
        return BackpackProgression.gate(itemId, store.getExternalData().getWorld().getGameplayConfig());
    }
    private String memoryLabel(BackpackProgression.Gate gate) {
        return gate.required() < 0 ? I18nHelper.getOrFallback(playerRef.getLanguage(), "server.truebackpack.workbench.memories.level", gate.level())
                : I18nHelper.getOrFallback(playerRef.getLanguage(), "server.truebackpack.workbench.memories.count", gate.recorded(), gate.required());
    }
    private com.hypixel.hytale.server.core.Message memoryTooltip(BackpackProgression.Gate gate) {
        var instruction = gate.required() < 0
                ? com.hypixel.hytale.server.core.Message.raw(memoryLabel(gate))
                : com.hypixel.hytale.server.core.Message.join(
                        com.hypixel.hytale.server.core.Message.raw(text("memories.find") + " "),
                        com.hypixel.hytale.server.core.Message.raw(text("memories.temple")).color("#f1ba50"),
                        com.hypixel.hytale.server.core.Message.raw(" " + I18nHelper.getOrFallback(playerRef.getLanguage(),
                                "server.truebackpack.workbench.memories.restore", gate.required())));
        return com.hypixel.hytale.server.core.Message.join(
                com.hypixel.hytale.server.core.Message.translation("client.inventory.crafting.unknownItem").color("#2c86d6").bold(true),
                com.hypixel.hytale.server.core.Message.raw("\n\n"), instruction.color("#969daa"));
    }
    private boolean requireMemories(String itemId, Ref<EntityStore> ref, Store<EntityStore> store) {
        var gate = memoryGate(itemId, store);
        if (gate.unlocked()) return true;
        statusMessage = memoryLabel(gate); sendRefresh(ref, store); return false;
    }

    private void handleUpgradeTier(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull ActiveBackpack activeBp,
            @Nullable String targetItemId,
            @Nullable String lang) {

        if (targetItemId == null) return;
        TierUpgrade tier = TIER_UPGRADES.get(activeBp.stack().getItemId());
        if (tier == null || !tier.toItemId().equalsIgnoreCase(targetItemId)) {
            statusMessage = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.status.invalid_tier");
            sendRefresh(ref, store);
            return;
        }

        if (!upgradeAllowed(tier.toItemId(), store)) { sendRefresh(ref, store); return; }
        if (!requireMemories(tier.toItemId(), ref, store)) return;
        if (!consumeMaterials(ref, store, tier.costs())) {
            statusMessage = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.status.missing_tier_materials");
            sendRefresh(ref, store);
            return;
        }

        ItemStack oldStack = activeBp.stack();
        ItemStack upgraded = new ItemStack(tier.toItemId());
        if (BackpackItemFactory.hasInstanceId(oldStack)) {
            upgraded = upgraded.withMetadata(BackpackItemFactory.INSTANCE_ID_CODEC, BackpackItemFactory.getInstanceId(oldStack));
        } else {
            upgraded = BackpackItemFactory.createBackpackInstance(upgraded);
        }

        // Preserve all saved contents!
        List<ItemStack> contents;
        InventoryComponent.Backpack bpComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());
        if (activeBp.isEquippedArmor() && bpComp != null) {
            contents = new ArrayList<>();
            var bp = bpComp.getInventory();
            for (short s = 0; s < bp.getCapacity(); s++) contents.add(bp.getItemStack(s));
        } else {
            contents = BackpackItemFactory.loadContents(oldStack);
        }
        upgraded = BackpackItemFactory.saveContents(upgraded, contents);

        // Preserve transmog skin if present
        if (BackpackItemFactory.hasTransmogSkin(oldStack)) {
            upgraded = BackpackItemFactory.setTransmogSkin(upgraded, BackpackItemFactory.getTransmogSkin(oldStack));
        }

        upgraded = BackpackItemFactory.setCustomName(upgraded, BackpackItemFactory.getCustomName(oldStack));
        upgraded = BackpackItemFactory.setPaintColor(upgraded, BackpackItemFactory.getPaintColor(oldStack));
        upgraded = BackpackItemFactory.setEquipped(upgraded, BackpackItemFactory.isEquipped(oldStack));
        activeBp.container().setItemStackForSlot(activeBp.slot(), upgraded);
        rebuildBackpack(ref, store);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            BackpackUIUpdater.updateBackpackUI(player, ref, store);
        }

        String targetName = I18nHelper.resolveItemName(tier.toItemId(), lang);
        statusMessage = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.status.tier_upgraded", targetName);
        sendRefresh(ref, store);
    }

    private void handleUpgradeLevel(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull ActiveBackpack activeBp,
            @Nullable String lang) {

        ItemStack stack = activeBp.stack();
        if (!"Utility_Leather_Extra_Big_Backpack".equalsIgnoreCase(stack.getItemId())) {
            statusMessage = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.status.only_extra_big_level");
            sendRefresh(ref, store);
            return;
        }

        int currentLevel = BackpackItemFactory.getUpgradeLevel(stack);
        if (currentLevel >= BackpackItemFactory.MAX_UPGRADE_LEVEL) {
            statusMessage = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.status.already_max_level");
            sendRefresh(ref, store);
            return;
        }

        int nextLevel = currentLevel + 1;
        LevelUpgrade upgrade = LEVEL_UPGRADES.get(nextLevel);
        if (upgrade == null) return;

        if (!upgradeAllowed(stack.getItemId(), store)) { sendRefresh(ref, store); return; }
        if (!requireMemories(stack.getItemId(), ref, store)) return;
        if (!consumeMaterials(ref, store, upgrade.costs())) {
            statusMessage = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.status.missing_level_materials", nextLevel);
            sendRefresh(ref, store);
            return;
        }

        ItemStack upgraded = BackpackItemFactory.setUpgradeLevel(stack, nextLevel);
        activeBp.container().setItemStackForSlot(activeBp.slot(), upgraded);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            BackpackUIUpdater.updateBackpackUI(player, ref, store);
        }

        statusMessage = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.status.level_upgraded", nextLevel, upgrade.newCapacity());
        sendRefresh(ref, store);
    }

    private void handleToggleCosmetic(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nullable String target,
            @Nullable String lang) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        String playerUuid = uuidComp.getUuid().toString();

        if ("backpack".equalsIgnoreCase(target)) {
            boolean nowVisible = CosmeticPreferenceUtils.toggleBackpack(store, ref);
            BackpackArmorListener.syncBackpackAttachment(playerUuid, store, ref);
            CosmeticListener.scheduleAttachmentRebuild(player, store, ref, playerUuid);
            statusMessage = I18nHelper.getOrFallback(lang, nowVisible ? "server.truebackpack.toggle.backpack.visible" : "server.truebackpack.toggle.backpack.hidden");
        } else if ("quiver".equalsIgnoreCase(target)) {
            boolean nowVisible = CosmeticPreferenceUtils.toggleQuiver(store, ref);
            if (nowVisible) {
                QuiverListener.syncQuiverAttachment(playerUuid, player, store, ref);
            } else {
                CosmeticListener.removeAttachment(playerUuid, "truebackpack:quiver");
            }
            CosmeticListener.scheduleAttachmentRebuild(player, store, ref, playerUuid);
            statusMessage = I18nHelper.getOrFallback(lang, nowVisible ? "server.truebackpack.toggle.quiver.visible" : "server.truebackpack.toggle.quiver.hidden");
        } else if ("hat".equalsIgnoreCase(target)) {
            boolean nowVisible = CosmeticPreferenceUtils.toggleHat(store, ref);
            if (nowVisible) {
                HatArmorListener.syncHatAttachment(playerUuid, store, ref);
            } else {
                CosmeticListener.removeAttachment(playerUuid, "truebackpack:hat");
            }
            CosmeticListener.scheduleAttachmentRebuild(player, store, ref, playerUuid);
            statusMessage = I18nHelper.getOrFallback(lang, nowVisible ? "server.truebackpack.toggle.hat.visible" : "server.truebackpack.toggle.hat.hidden");
        }

        sendRefresh(ref, store);
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (activeContext != null) activeContext.requestRefresh();
    }

    private void startBenchCheck() {
        if (benchPosition == null || pendingBenchCheck != null) return;
        var context = activeContext;
        var world = context.store().getExternalData().getWorld();
        pendingBenchCheck = PREVIEW_TIMER.scheduleAtFixedRate(() -> {
            try { world.execute(() -> {
                if (dismissed || !context.isActive()) return;
                if (!isBenchAvailable(context.ref(), context.store())) context.requestClose();
            }); } catch (RuntimeException worldStopped) {
                if (pendingBenchCheck != null) pendingBenchCheck.cancel(false);
            }
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
    }

    private boolean isBenchAvailable(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (benchPosition == null) return false;
        var transform = store.getComponent(ref,
                com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
        if (transform == null || transform.getPosition().distanceSquared(
                benchPosition.x + 0.5, benchPosition.y + 0.5, benchPosition.z + 0.5) > 64) return false;
        var world = store.getExternalData().getWorld();
        return com.supremosan.truebackpack.util.BackpackWorkbenchUtils.isBackpackWorkbench(
                BlockPlacementUtil.getBlockType(world, benchPosition.x, benchPosition.y, benchPosition.z));
    }

    /** Keep native recipe validation, queues, memories and material transactions. */
    public static void openAt(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef, org.joml.Vector3i pos) {
        var page = new BackpackWorkbenchPage(playerRef, pos);
        page.openCraftingSession(ref, store);
    }

    private void openCraftingSession(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!isBenchAvailable(ref, store)) return;
        var player = store.getComponent(ref, Player.getComponentType());
        var manager = store.getComponent(ref, com.hypixel.hytale.builtin.crafting.component.CraftingManager.getComponentType());
        if (player == null || manager == null || manager.hasBenchSet()) return;
        var world = store.getExternalData().getWorld();
        var chunks = world.getChunkStore();
        var pos = benchPosition;
        var sectionRef = chunks.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        if (sectionRef == null || !sectionRef.isValid()) return;
        var chunkStore = chunks.getStore();
        var benchRef = com.hypixel.hytale.server.core.modules.block.BlockModule.getBlockEntity(
                chunkStore, sectionRef, pos.x, pos.y, pos.z);
        if (benchRef == null || !benchRef.isValid()) return;
        var bench = chunkStore.getComponent(benchRef, com.hypixel.hytale.builtin.crafting.component.BenchBlock.getComponentType());
        var section = chunkStore.getComponent(sectionRef,
                com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection.getComponentType());
        if (bench == null || section == null) return;
        var block = com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.getAssetMap()
                .getAsset(section.get(pos.x, pos.y, pos.z));
        if (!com.supremosan.truebackpack.util.BackpackWorkbenchUtils.isBackpackWorkbench(block)) return;
        com.hypixel.hytale.builtin.crafting.component.BenchBlock.refreshGrantedAugmentTags(chunkStore, benchRef, pos);
        var window = new BackpackCraftingWindow(
                pos.x, pos.y, pos.z, section.getRotationIndex(pos.x, pos.y, pos.z), block, bench);
        var uuid = playerRef.getUuid();
        if (bench.getWindows().putIfAbsent(uuid, window) != null) return;
        window.registerCloseEvent(event -> bench.getWindows().remove(uuid, window));
        craftingWindow = window;
        window.onChanged(() -> scheduleRefresh(ref, store));
        window.registerCloseEvent(event -> {
            world.execute(() -> {
                if (!dismissed && activeContext != null && activeContext.isActive())
                    activeContext.requestClose();
            });
        });
        try {
            if (!CustomInventoryPlugin.get().openView(ref, store, playerRef, definition(), extension(), window)) {
                abortCraftingSession(ref, store, player, bench, uuid, window);
            }
        } catch (RuntimeException | Error failure) {
            abortCraftingSession(ref, store, player, bench, uuid, window);
            throw failure;
        }
    }

    private void abortCraftingSession(Ref<EntityStore> ref, Store<EntityStore> store, Player player,
            com.hypixel.hytale.builtin.crafting.component.BenchBlock bench, UUID uuid, BackpackCraftingWindow window) {
        window.onChanged(null);
        bench.getWindows().remove(uuid, window);
        var windows = player.getWindowManager();
        if (window.getId() > 0 && windows.getWindow(window.getId()) == window) {
            windows.closeWindow(ref, window.getId(), store);
        }
        if (activeContext != null) onDismiss(activeContext);
    }

    private void scheduleRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!dismissed && activeContext != null) activeContext.requestRefresh();
    }

    @Override
    public void onDismiss(InventoryContext context) {
        if (dismissed) return;
        dismissed = true;
        ACTIVE.remove(this);
        if (pendingBenchCheck != null) pendingBenchCheck.cancel(false);
        preview.close(playerRef);
        previewRevision++;
        if (pendingPreview != null) pendingPreview.cancel(false);
        if (craftingWindow != null) {
            // The client owns closing windows attached through
            // openCustomPageWithWindows. Calling WindowManager.closeWindow here
            // races its CloseWindow packet and causes "Window id is invalid".
            craftingWindow.onChanged(null);
        }
        activeContext = null;
        contentSelector = null;
        editedContainer = null;
        editedStack = null;
    }

    private List<com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe> eligibleRecipes() {
        if (craftingWindow == null) return List.of();
        var ids = com.hypixel.hytale.builtin.crafting.CraftingPlugin.getAvailableRecipesForCategory(
                craftingWindow.benchId(), "Backpack");
        if (ids == null) return List.of();
        var recipes = new ArrayList<com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe>();
        for (String id : ids) {
            var recipe = com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe.getAssetMap().getAsset(id);
            if (recipe == null || recipe.getPrimaryOutput() == null || recipe.getBenchRequirement() == null) continue;
            for (var requirement : recipe.getBenchRequirement()) {
                if (craftingWindow.benchId().equals(requirement.id) && requirement.requiredTierLevel <= craftingWindow.tier()) {
                    recipes.add(recipe);
                    break;
                }
            }
        }
        recipes.sort(Comparator
                .comparingInt((com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe r) ->
                        craftingRarityOrder(r.getPrimaryOutput().getItemId()))
                .thenComparing(r -> r.getPrimaryOutput().getItemId())
                .thenComparing(r -> r.getId()));
        return recipes;
    }

    private static int craftingRarityOrder(String itemId) {
        var item = com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(itemId);
        if (item == null) return Integer.MAX_VALUE;
        var quality = com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality
                .getAssetMap().getAsset(item.getQualityIndex());
        if (quality == null || quality.getId() == null) return Integer.MAX_VALUE;
        return switch (quality.getId()) {
            case "Common" -> 0;
            case "Uncommon" -> 1;
            case "Rare" -> 2;
            case "Epic" -> 3;
            case "Legendary" -> 4;
            default -> Integer.MAX_VALUE;
        };
    }

    private String text(String key) {
        return I18nHelper.getOrFallback(playerRef.getLanguage(), "server.truebackpack.workbench." + key);
    }

    private String materialIcon(com.hypixel.hytale.server.core.inventory.MaterialQuantity material) {
        if (material.getItemId() != null) return material.getItemId();
        return "Wood_Trunk".equals(material.getResourceTypeId()) ? "Wood_Oak_Trunk" : "";
    }

    private String materialLabel(com.hypixel.hytale.server.core.inventory.MaterialQuantity material, String lang) {
        String id = material.getItemId() != null ? material.getItemId() : material.getResourceTypeId();
        return material.getQuantity() + " × " + I18nHelper.resolveItemName(id, lang);
    }

    private void updateBenchPanel(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder cb) {
        cb.set("#BenchPanel.Visible", craftingWindow != null);
        if (craftingWindow == null) return;
        cb.set("#BenchName.Text", text(craftingWindow.isMaster() ? "bench.master_name" : "bench.apprentice"));
        cb.set("#BenchLevel.TextSpans", Message.translation("client.inventory.bench.tierLevelX")
                .param("level", craftingWindow.tier()));
        String benchItemId = craftingWindow.isMaster()
                ? "Backpack_Workbench_Master" : "Backpack_Workbench_Apprentice";
        cb.set("#BenchPreview.ItemId", benchItemId);
        var benchItem = com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(benchItemId);
        cb.set("#BenchInfoButton.TooltipTextSpans", benchItem == null
                ? Message.raw("") : benchItem.getDescriptionTranslationMessage());
        cb.set("#BenchInfoButton.Visible", benchItem != null);
        var requirement = craftingWindow.upgradeRequirement();
        cb.set("#BenchTierUpgrade.Visible", requirement != null);
        cb.set("#BenchMax.Visible", requirement == null);
        cb.set("#BenchNextRequirements.TextSpans",
                Message.translation("client.inventory.bench.tierLevelX.requirements")
                        .param("level", craftingWindow.tier() + 1));
        cb.clear("#BenchRequirements");
        boolean materialsReady = false;
        if (requirement != null) {
            var inventory = new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(
                    InventoryComponent.getCombined(store, ref, InventoryComponent.BACKPACK_STORAGE_HOTBAR),
                    craftingWindow.getExtraResourcesSection().getItemContainer());
            materialsReady = inventory.canRemoveMaterials(Arrays.asList(requirement.getInput()));
            for (int i = 0; i < requirement.getInput().length && i < 4; i++) {
                cb.append("#BenchRequirements", "Pages/BackpackBenchMaterial.ui");
                String selector = "#BenchRequirements[" + i + "]";
                var material = requirement.getInput()[i];
                int owned = inventory.countRemovableMaterial(material);
                cb.set(selector + " #BenchMaterial.ItemId", materialIcon(material));
                cb.set(selector + " #BenchCost.Text", owned + "/" + material.getQuantity());
                cb.set(selector + " #BenchCost.Style.TextColor",
                        owned >= material.getQuantity() ? "#ccd3dd" : "#e54545");
            }
        }
        var data = craftingWindow.getData();
        int chestCount = data.has("nearbyChestCount") ? data.get("nearbyChestCount").getAsInt() : 0;
        int chestLimit = data.has("maxChestCount") ? data.get("maxChestCount").getAsInt() : 0;
        cb.set("#BenchNearbyChests.Text", "× " + chestCount + "/" + chestLimit);
        cb.set("#BenchChestInfoButton.TooltipTextSpans", Message.translation("client.inventory.bench.chestInfo")
                .param("horizontalRadius", data.has("chestHorizontalRadius") ? data.get("chestHorizontalRadius").getAsInt() : 0)
                .param("verticalRadius", data.has("chestVerticalRadius") ? data.get("chestVerticalRadius").getAsInt() : 0)
                .param("maxChests", chestLimit));
        float progress = data.has("tierUpgradeProgress") ? data.get("tierUpgradeProgress").getAsFloat() : 0;
        cb.set("#BenchProgressBar.Value", Math.clamp(progress, 0f, 1f));
        var player = store.getComponent(ref, Player.getComponentType());
        boolean creative = player != null && player.getGameMode() == com.hypixel.hytale.protocol.GameMode.Creative;
        cb.set("#BenchUpgradeButton.Disabled", requirement == null || progress > 0 || !(creative || materialsReady));
    }

    private void buildCraftingTab(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder cb,
            InventoryEventBindings eb, String lang, boolean mount) {
        var combined = InventoryComponent.getCombined(store, ref, InventoryComponent.BACKPACK_STORAGE_HOTBAR);
        var extra = craftingWindow != null && craftingWindow.getExtraResourcesSection() != null
                ? craftingWindow.getExtraResourcesSection().getItemContainer() : null;
        var inventory = extra != null
                ? new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(combined, extra)
                : combined;
        var player = store.getComponent(ref, Player.getComponentType());
        boolean creative = player != null && player.getGameMode() == com.hypixel.hytale.protocol.GameMode.Creative;
        int memories = com.hypixel.hytale.builtin.adventure.memories.MemoriesPlugin.get()
                .getMemoriesLevel(store.getExternalData().getWorld().getGameplayConfig());
        var recipes = eligibleRecipes();
        if (selectedRecipeId == null || recipes.stream().noneMatch(r -> r.getId().equals(selectedRecipeId) && recipeUnlocked(r, store)))
            selectedRecipeId = recipes.stream().filter(r -> recipeUnlocked(r, store)).map(r -> r.getId()).findFirst().orElse(null);
        if (mount) cb.append("#CraftingContent", "Pages/BackpackCraftingPanel.ui");
        // Rebind every mounted control together when the host advances its content event generation.
        eb.addEventBinding(CustomUIEventBindingType.Activating, "#CraftButton",
                new EventData().append("Action", "CraftRecipe"));
        cb.set("#RecipeDetails.Visible", selectedRecipeId != null);
        String grid = "#CraftingContent[0] #RecipeGrid";
        var recipeIds = recipes.stream().map(recipe -> recipe.getId()).toList();
        boolean mountRecipes = mount || !recipeIds.equals(mountedRecipes);
        if (mountRecipes && !mount) cb.clear(grid);
        mountedRecipes = recipeIds;
        for (int i = 0; i < recipes.size(); i++) {
            if (mountRecipes && i % 3 == 0) cb.append(grid, "Pages/BackpackRecipeGridRow.ui");
            String row = grid + "[" + (i / 3) + "]";
            int column = i % 3;
            var candidate = recipes.get(i);
            boolean memory = candidate.getRequiredMemoriesLevel() <= memories;
            cb.set(row + " #RecipeIcon" + column + ".ItemId", memory ? candidate.getPrimaryOutput().getItemId() : "");
            cb.set(row + " #Selected" + column + ".Visible", !upgradeSelected && candidate.getId().equals(selectedRecipeId));
            boolean materials = true;
            var inputs = candidate.getInput();
            if (inputs != null) {
                for (var input : inputs) {
                    materials &= inventory.countRemovableMaterial(input) >= input.getQuantity();
                }
            }
            boolean known = !candidate.isKnowledgeRequired()
                    || (player != null && player.getPlayerConfigData().getKnownRecipes()
                    .contains(candidate.getPrimaryOutput().getItemId()));
            boolean allowed = (creative || materials) && known && memory;
            // Match the game's memory lock: hide the item behind the rune and
            // explain the unlock condition in its native localized tooltip.
            cb.set(row + " #RecipeIcon" + column + ".Visible", memory);
            cb.set(row + " #MemoryLocked" + column + ".Visible", !memory);
            cb.set(row + " #Unavailable" + column + ".Visible", memory && !allowed);
            if (!memory) {
                var tooltip = memoryTooltip(BackpackProgression.gate(candidate.getRequiredMemoriesLevel(),
                        store.getExternalData().getWorld().getGameplayConfig()));
                cb.set(row + " #MemoryLocked" + column + ".TooltipTextSpans", tooltip);
            } else {
                // Text-span resets must use the formatted-message payload, not BSON null.
                cb.set(row + " #MemoryLocked" + column + ".TooltipTextSpans", Message.raw(""));
            }
            if (memory) {
                eb.addEventBinding(CustomUIEventBindingType.Activating, row + " #SelectRecipe" + column,
                        new EventData().append("Action", "SelectRecipe").append("Target", candidate.getId()));
            }
        }
        int remainder = recipes.size() % 3;
        if (remainder != 0) {
            String lastRow = grid + "[" + ((recipes.size() - 1) / 3) + "]";
            for (int column = remainder; column < 3; column++) {
                cb.set(lastRow + " #SelectRecipe" + column + ".Visible", false);
            }
        }
        for (var recipe : recipes) {
            if (!recipe.getId().equals(selectedRecipeId)) continue;
            String sel = "#CraftingContent[0] #RecipeDetails";
            String itemId = recipe.getPrimaryOutput().getItemId();
            cb.set(sel + " #RecipePreview.ItemId", itemId);
            cb.set(sel + " #RecipeName.Text", I18nHelper.resolveItemName(itemId, lang));
            var inputs = recipe.getInput();
            boolean materials = true;
            for (int i = 0; i < 4; i++) {
                String label = "";
                if (inputs != null && i < inputs.length) {
                    int count = inventory.countRemovableMaterial(inputs[i]);
                    materials &= count >= inputs[i].getQuantity();
                    label = (creative ? "" : count + " / ") + materialLabel(inputs[i], lang);
                    cb.set(sel + " #MaterialIcon" + i + ".ItemId", materialIcon(inputs[i]));
                    cb.set(sel + " #Material" + i + ".Style.TextColor",
                            creative || count >= inputs[i].getQuantity() ? "#62b78d" : "#d78b82");
                }
                cb.set(sel + " #MaterialRow" + i + ".Visible", inputs != null && i < inputs.length);
                cb.set(sel + " #Material" + i + ".Text", label);
            }
            if (inputs != null) for (var input : inputs) materials &= inventory.countRemovableMaterial(input) >= input.getQuantity();
            boolean known = !recipe.isKnowledgeRequired() || (player != null && player.getPlayerConfigData().getKnownRecipes().contains(itemId));
            boolean memory = recipe.getRequiredMemoriesLevel() <= memories;
            boolean allowed = (creative || materials) && known && memory;
            cb.set(sel + " #CraftButton.Disabled", !allowed);
            cb.set(sel + " #CraftButton.Text", text("craft.action"));
            cb.set(sel + " #RecipeStatus.Visible", memory);
            cb.set(sel + " #RecipeMemoryLock.Visible", !memory);
            if (!memory) {
                var gate = BackpackProgression.gate(recipe.getRequiredMemoriesLevel(), store.getExternalData().getWorld().getGameplayConfig());
                cb.set(sel + " #RecipeMemoryLock.TooltipTextSpans", memoryTooltip(gate));
                cb.set(sel + " #MemoryLockLabel.Text", memoryLabel(gate));
            }
            cb.set(sel + " #RecipeStatus.Text", !known ? text("craft.unknown") : !materials && !creative ? text("craft.materials") : "");
        }
    }

    @Nullable
    public static ActiveBackpack findActiveBackpack(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        // 1. Hand item
        ItemStack handItem = InventoryComponent.getItemInHand(store, ref);
        if (handItem != null && BackpackRegistry.isBackpack(handItem.getItemId())) {
            InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
            if (hotbar != null) {
                byte activeSlot = hotbar.getActiveSlot();
                if (activeSlot >= 0) {
                    return new ActiveBackpack(hotbar.getInventory(), activeSlot, handItem, false);
                }
            }
        }

        // 2. Dedicated backpack equipment slot
        var armor = store.getComponent(ref, com.supremosan.custominventory.api.ExtraEquipment.TYPE);
        if (armor != null) {
            ItemStack chest = armor.getInventory().getItemStack((short) 1);
            if (chest != null && BackpackRegistry.isBackpack(chest.getItemId())) {
                return new ActiveBackpack(armor.getInventory(), (short) 1, chest, true);
            }
        }

        // 3. Hotbar
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar != null) {
            ItemContainer inv = hotbar.getInventory();
            for (short s = 0; s < inv.getCapacity(); s++) {
                ItemStack item = inv.getItemStack(s);
                if (item != null && BackpackRegistry.isBackpack(item.getItemId())) {
                    return new ActiveBackpack(inv, s, item, false);
                }
            }
        }

        // 4. Storage
        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        if (storage != null) {
            ItemContainer inv = storage.getInventory();
            for (short s = 0; s < inv.getCapacity(); s++) {
                ItemStack item = inv.getItemStack(s);
                if (item != null && BackpackRegistry.isBackpack(item.getItemId())) {
                    return new ActiveBackpack(inv, s, item, false);
                }
            }
        }

        return null;
    }

    private static boolean hasMaterials(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull Map<String, Integer> costs) {

        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());
        ItemContainer backpack = backpackComp != null ? backpackComp.getInventory() : null;

        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            String requiredId = entry.getKey();
            int requiredQty = entry.getValue();
            int count = countItem(hotbar != null ? hotbar.getInventory() : null, requiredId)
                    + countItem(storage != null ? storage.getInventory() : null, requiredId)
                    + countItem(backpack, requiredId);
            if (count < requiredQty) return false;
        }
        return true;
    }

    private static boolean consumeMaterials(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull Map<String, Integer> costs) {

        if (!hasMaterials(ref, store, costs)) return false;

        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());
        ItemContainer backpack = backpackComp != null ? backpackComp.getInventory() : null;

        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            String requiredId = entry.getKey();
            int toRemove = entry.getValue();

            if (hotbar != null) {
                toRemove = removeItem(hotbar.getInventory(), requiredId, toRemove);
            }
            if (toRemove > 0 && storage != null) {
                toRemove = removeItem(storage.getInventory(), requiredId, toRemove);
            }
            if (toRemove > 0 && backpack != null) {
                toRemove = removeItem(backpack, requiredId, toRemove);
            }
        }
        return true;
    }

    private static boolean matchesRequirement(@Nonnull ItemStack stack, @Nonnull String requiredId) {
        if (stack.isEmpty()) return false;
        String itemId = stack.getItemId();
        if (itemId == null) return false;
        if (requiredId.equalsIgnoreCase(itemId)) return true;

        if ("Wood_Trunk".equalsIgnoreCase(requiredId)) {
            if (itemId.contains("Trunk") || itemId.contains("trunk")) {
                return true;
            }
        }

        if (itemId.toLowerCase().startsWith(requiredId.toLowerCase() + "_")) {
            return true;
        }

        try {
            com.hypixel.hytale.server.core.asset.type.item.config.Item asset =
                    com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(itemId);
            if (asset != null && asset.getResourceTypes() != null) {
                for (var rt : asset.getResourceTypes()) {
                    if (rt != null && requiredId.equalsIgnoreCase(rt.id)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private static int countItem(@Nullable ItemContainer container, @Nonnull String requiredId) {
        if (container == null) return 0;
        int total = 0;
        for (short s = 0; s < container.getCapacity(); s++) {
            ItemStack stack = container.getItemStack(s);
            if (stack != null && !stack.isEmpty() && matchesRequirement(stack, requiredId)) {
                total += stack.getQuantity();
            }
        }
        return total;
    }

    private static int removeItem(@Nonnull ItemContainer container, @Nonnull String requiredId, int amount) {
        for (short s = 0; s < container.getCapacity() && amount > 0; s++) {
            ItemStack stack = container.getItemStack(s);
            if (stack != null && !stack.isEmpty() && matchesRequirement(stack, requiredId)) {
                int qty = stack.getQuantity();
                if (qty <= amount) {
                    container.removeItemStackFromSlot(s, qty);
                    amount -= qty;
                } else {
                    container.setItemStackForSlot(s, stack.withQuantity(qty - amount));
                    amount = 0;
                }
            }
        }
        return amount;
    }

}
