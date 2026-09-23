package com.supremosan.truebackpack.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
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
import com.supremosan.truebackpack.util.I18nHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Interactive custom UI page for transmogrification and backpack upgrades.
 */
public class BackpackWorkbenchPage extends InteractiveCustomUIPage<BackpackWorkbenchPage.PageData> {

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, s) -> d.action = s, d -> d.action).add()
                .append(new KeyedCodec<>("Target", Codec.STRING), (d, s) -> d.target = s, d -> d.target).add()
                .build();

        public String action;
        public String target;
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
    private final java.util.concurrent.atomic.AtomicBoolean refreshQueued = new java.util.concurrent.atomic.AtomicBoolean();
    private String selectedRecipeId;
    private String currentTab = "transmog";
    private String statusMessage = "";
    @Nullable
    private final org.joml.Vector3i benchPosition;

    public BackpackWorkbenchPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, null);
    }

    public BackpackWorkbenchPage(@Nonnull PlayerRef playerRef, @Nullable org.joml.Vector3i benchPosition) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.benchPosition = benchPosition == null ? null : new org.joml.Vector3i(benchPosition);
        if (benchPosition != null) currentTab = "crafting";
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/BackpackWorkbenchPage.ui");
        commandBuilder.set("#CraftingButton.Visible", benchPosition != null);
        commandBuilder.set("#BenchPanel.Visible", benchPosition != null);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BenchUpgradeButton",
                new EventData().append("Action", "UpgradeBench").append("Target", ""));
        commandBuilder.set("#CraftingButton.Text", I18nHelper.getOrFallback(playerRef.getLanguage(), "server.truebackpack.workbench.tab.crafting"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CraftingButton",
                new EventData().append("Action", "Crafting").append("Target", ""));

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                new EventData().append("Action", "Close").append("Target", "")
        );

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabTransmogButton",
                new EventData().append("Action", "TabTransmog").append("Target", "")
        );

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabUpgradeButton",
                new EventData().append("Action", "TabUpgrade").append("Target", "")
        );

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TabVisibilityButton",
                new EventData().append("Action", "TabVisibility").append("Target", "")
        );

        refreshPage(ref, store, commandBuilder, eventBuilder);
    }

    private void refreshPage(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder) {

        String lang = playerRef.getLanguage();

        updateBenchPanel(commandBuilder, lang);
        // Localize the shared workbench tabs.
        commandBuilder.set("#PageTitle.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.title"));

        String transmogLabel = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.tab.transmog");
        String upgradesLabel = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.tab.upgrades");
        String visibilityLabel = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.tab.visibility");

        commandBuilder.set("#TabTransmogButton.Text", transmogLabel);
        commandBuilder.set("#TabUpgradeButton.Text", upgradesLabel);
        commandBuilder.set("#TabVisibilityButton.Text", visibilityLabel);

        ActiveBackpack activeBp = findActiveBackpack(ref, store);
        commandBuilder.clear("#WorkbenchList");

        if ("crafting".equals(currentTab) && craftingWindow != null) {
            commandBuilder.set("#LoadingContainer.Visible", false);
            commandBuilder.set("#BackpackHeader.Visible", false);
            commandBuilder.set("#TabButtons.Visible", true);
            commandBuilder.set("#WorkbenchList.Visible", true);
            buildCraftingTab(ref, store, commandBuilder, eventBuilder, lang);
            return;
        }

        if ("visibility".equalsIgnoreCase(currentTab)) {
            commandBuilder.set("#LoadingContainer.Visible", false);
            commandBuilder.set("#BackpackHeader.Visible", true);
            commandBuilder.set("#TabButtons.Visible", true);
            commandBuilder.set("#WorkbenchList.Visible", true);
            updateHeader(activeBp, commandBuilder, lang);
            buildVisibilityTab(ref, store, activeBp, commandBuilder, eventBuilder, lang);
            return;
        }

        if (activeBp == null) {
            // Hide all content inside the UI and make only the text appear
            commandBuilder.set("#BackpackHeader.Visible", false);
            commandBuilder.set("#TabButtons.Visible", true);
            commandBuilder.set("#WorkbenchList.Visible", false);
            commandBuilder.set("#LoadingContainer.Visible", true);
            commandBuilder.set("#LoadingContainer #LoadingText.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.no_backpack.desc"));
            return;
        }

        commandBuilder.set("#LoadingContainer.Visible", false);
        commandBuilder.set("#BackpackHeader.Visible", true);
        commandBuilder.set("#TabButtons.Visible", true);
        commandBuilder.set("#WorkbenchList.Visible", true);
        updateHeader(activeBp, commandBuilder, lang);

        if ("transmog".equalsIgnoreCase(currentTab)) {
            buildTransmogTab(activeBp, commandBuilder, eventBuilder, lang);
        } else {
            buildUpgradeTab(ref, store, activeBp, commandBuilder, eventBuilder, lang);
        }
    }

    private void updateHeader(@Nullable ActiveBackpack activeBp, @Nonnull UICommandBuilder commandBuilder, @Nullable String lang) {
        if (activeBp == null) {
            commandBuilder.set("#BackpackHeader #CurrentIcon.ItemId", "");
            commandBuilder.set("#HeaderDetails #CurrentName.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.no_backpack.title"));
            commandBuilder.set("#HeaderDetails #CurrentInfo.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.no_backpack.desc"));
            commandBuilder.set("#HeaderDetails #StatusMessage.Text", "");
            return;
        }

        ItemStack stack = activeBp.stack();
        String itemId = stack.getItemId();
        commandBuilder.set("#BackpackHeader #CurrentIcon.ItemId", itemId);
        commandBuilder.set("#HeaderDetails #CurrentName.Text", I18nHelper.resolveItemName(itemId, lang));

        boolean isHelipack = "Utility_Heli_Backpack".equalsIgnoreCase(itemId);
        if (isHelipack) {
            commandBuilder.set("#HeaderDetails #CurrentInfo.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.helipack.ineligible"));
        } else {
            short capacity = BackpackItemFactory.getTotalCapacity(stack);
            String skin = BackpackItemFactory.getTransmogSkin(stack);
            String skinText = (skin != null && !skin.isBlank())
                    ? I18nHelper.resolveItemName(skin, lang)
                    : I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.original");
            int level = BackpackItemFactory.getUpgradeLevel(stack);

            StringBuilder info = new StringBuilder();
            info.append(capacity).append(" ").append(I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.slots"))
                .append(" | ").append(I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.skin")).append(": ").append(skinText);
            if ("Utility_Leather_Extra_Big_Backpack".equalsIgnoreCase(itemId)) {
                info.append(" | ").append(I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.level")).append(" ").append(level).append("/2");
            }
            commandBuilder.set("#HeaderDetails #CurrentInfo.Text", info.toString());
        }

        commandBuilder.set("#HeaderDetails #StatusMessage.Text", statusMessage);
    }

    private void buildTransmogTab(
            @Nonnull ActiveBackpack activeBp,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nullable String lang) {

        ItemStack stack = activeBp.stack();
        String currentItemId = stack.getItemId();
        boolean isHelipack = "Utility_Heli_Backpack".equalsIgnoreCase(currentItemId);

        if (isHelipack) {
            commandBuilder.set("#HeaderDetails #StatusMessage.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.helipack.cannot_transmog"));
            return;
        }

        String activeSkin = BackpackItemFactory.getTransmogSkin(stack);

        for (int i = 0; i < AVAILABLE_SKINS.size(); i++) {
            SkinOption skin = AVAILABLE_SKINS.get(i);
            String selector = "#WorkbenchList[" + i + "]";

            commandBuilder.append("#WorkbenchList", "Pages/BackpackSkinEntry.ui");
            commandBuilder.set(selector + " #SkinIcon.ItemId", skin.iconItemId());

            String skinName = "default".equals(skin.id())
                    ? I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.skin.default.name")
                    : I18nHelper.resolveItemName(skin.id(), lang);
            String skinDesc = I18nHelper.getOrFallback(lang, skin.descKey());

            commandBuilder.set(selector + " #SkinName.Text", skinName);
            commandBuilder.set(selector + " #SkinDesc.Text", skinDesc);

            boolean isCurrentActive;
            if ("default".equals(skin.id())) {
                isCurrentActive = (activeSkin == null || activeSkin.isBlank());
            } else {
                isCurrentActive = skin.id().equalsIgnoreCase(activeSkin)
                        || (activeSkin == null && skin.id().equalsIgnoreCase(currentItemId));
            }

            if (isCurrentActive) {
                commandBuilder.set(selector + " #ApplyButton.Visible", false);
                commandBuilder.set(selector + " #ActiveBadge.Visible", true);
                String activeLabel = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.btn.active");
                commandBuilder.set(selector + " #ActiveBadge #ActiveLabel.Text", activeLabel);
            } else {
                commandBuilder.set(selector + " #ApplyButton.Visible", true);
                commandBuilder.set(selector + " #ActiveBadge.Visible", false);
                commandBuilder.set(selector + " #ApplyButton.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.btn.apply"));
                eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        selector + " #ApplyButton",
                        new EventData().append("Action", "ApplySkin").append("Target", skin.id())
                );
            }
        }
    }

    private void buildUpgradeTab(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull ActiveBackpack activeBp,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nullable String lang) {

        ItemStack stack = activeBp.stack();
        String itemId = stack.getItemId();

        if ("Utility_Heli_Backpack".equalsIgnoreCase(itemId)) {
            commandBuilder.set("#HeaderDetails #StatusMessage.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.helipack.no_upgrades"));
            return;
        }

        int index = 0;

        // Tier Upgrade Option (for backpacks smaller than Extra Big)
        if (TIER_UPGRADES.containsKey(itemId)) {
            TierUpgrade tier = TIER_UPGRADES.get(itemId);
            String selector = "#WorkbenchList[" + index + "]";
            index++;

            commandBuilder.append("#WorkbenchList", "Pages/BackpackUpgradeEntry.ui");
            commandBuilder.set(selector + " #UpgradeIcon.ItemId", tier.toItemId());
            String targetName = I18nHelper.resolveItemName(tier.toItemId(), lang);
            commandBuilder.set(selector + " #UpgradeTitle.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.tier.title", targetName));
            commandBuilder.set(selector + " #UpgradeBenefits.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.tier.benefit", tier.newCapacity()));

            boolean canAfford = hasMaterials(ref, store, tier.costs());
            commandBuilder.set(selector + " #CostLabel.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.cost", formatCosts(tier.costs(), canAfford, lang)));

            if (canAfford) {
                commandBuilder.set(selector + " #UpgradeButton.Visible", true);
                commandBuilder.set(selector + " #NeedItemsBadge.Visible", false);
                commandBuilder.set(selector + " #MaxedBadge.Visible", false);
                commandBuilder.set(selector + " #UpgradeButton.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.btn.upgrade"));
                eventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        selector + " #UpgradeButton",
                        new EventData().append("Action", "UpgradeTier").append("Target", tier.toItemId())
                );
            } else {
                commandBuilder.set(selector + " #UpgradeButton.Visible", false);
                commandBuilder.set(selector + " #NeedItemsBadge.Visible", true);
                commandBuilder.set(selector + " #MaxedBadge.Visible", false);
                commandBuilder.set(selector + " #NeedItemsBadge #NeedItemsText.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.btn.need_items"));
            }
        }

        // Capacity Level Upgrade (exclusive to Extra Big Backpack, max lvl 2)
        if ("Utility_Leather_Extra_Big_Backpack".equalsIgnoreCase(itemId)) {
            int currentLevel = BackpackItemFactory.getUpgradeLevel(stack);
            String selector = "#WorkbenchList[" + index + "]";

            commandBuilder.append("#WorkbenchList", "Pages/BackpackUpgradeEntry.ui");
            commandBuilder.set(selector + " #UpgradeIcon.ItemId", itemId);

            if (currentLevel >= BackpackItemFactory.MAX_UPGRADE_LEVEL) {
                commandBuilder.set(selector + " #UpgradeTitle.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.level.max_title"));
                commandBuilder.set(selector + " #UpgradeBenefits.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.level.max_benefit"));
                commandBuilder.set(selector + " #CostLabel.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.level.max_cost"));
                commandBuilder.set(selector + " #UpgradeButton.Visible", false);
                commandBuilder.set(selector + " #NeedItemsBadge.Visible", false);
                commandBuilder.set(selector + " #MaxedBadge.Visible", true);
                commandBuilder.set(selector + " #MaxedBadge #MaxedText.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.btn.maxed"));
            } else {
                int nextLevel = currentLevel + 1;
                LevelUpgrade nextUpgrade = LEVEL_UPGRADES.get(nextLevel);
                short newCapacity = nextUpgrade != null ? nextUpgrade.newCapacity() : (short) (36 + nextLevel * 9);
                Map<String, Integer> costs = nextUpgrade != null ? nextUpgrade.costs() : Map.of();

                commandBuilder.set(selector + " #UpgradeTitle.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.level.title", nextLevel));
                commandBuilder.set(selector + " #UpgradeBenefits.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.level.benefit", 9, BackpackItemFactory.getTotalCapacity(stack), newCapacity));

                boolean canAfford = hasMaterials(ref, store, costs);
                commandBuilder.set(selector + " #CostLabel.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.cost", formatCosts(costs, canAfford, lang)));

                if (canAfford) {
                    commandBuilder.set(selector + " #UpgradeButton.Visible", true);
                    commandBuilder.set(selector + " #NeedItemsBadge.Visible", false);
                    commandBuilder.set(selector + " #MaxedBadge.Visible", false);
                    commandBuilder.set(selector + " #UpgradeButton.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.btn.upgrade"));
                    eventBuilder.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            selector + " #UpgradeButton",
                            new EventData().append("Action", "UpgradeLevel").append("Target", Integer.toString(nextLevel))
                    );
                } else {
                    commandBuilder.set(selector + " #UpgradeButton.Visible", false);
                    commandBuilder.set(selector + " #NeedItemsBadge.Visible", true);
                    commandBuilder.set(selector + " #MaxedBadge.Visible", false);
                    commandBuilder.set(selector + " #NeedItemsBadge #NeedItemsText.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.btn.need_items"));
                }
            }
        }
    }

    private void buildVisibilityTab(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nullable ActiveBackpack activeBp,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nullable String lang) {

        String visibleLabel = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.btn.visible");
        String hiddenLabel = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.btn.hidden");

        // 1. Backpack Visibility
        boolean bpVisible = CosmeticPreferenceUtils.isBackpackVisible(store, ref);
        String bpIcon = activeBp != null ? activeBp.stack().getItemId() : "Utility_Leather_Backpack";
        commandBuilder.append("#WorkbenchList", "Pages/BackpackVisibilityEntry.ui");
        String sel0 = "#WorkbenchList[0]";
        commandBuilder.set(sel0 + " #CosmeticIcon.ItemId", bpIcon);
        commandBuilder.set(sel0 + " #CosmeticName.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.visibility.backpack.title"));
        commandBuilder.set(sel0 + " #CosmeticDesc.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.visibility.backpack.desc"));
        if (bpVisible) {
            commandBuilder.set(sel0 + " #ToggleButton.Visible", true);
            commandBuilder.set(sel0 + " #ToggleHiddenButton.Visible", false);
            commandBuilder.set(sel0 + " #ToggleButton.Text", visibleLabel);
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sel0 + " #ToggleButton",
                    new EventData().append("Action", "ToggleCosmetic").append("Target", "backpack")
            );
        } else {
            commandBuilder.set(sel0 + " #ToggleButton.Visible", false);
            commandBuilder.set(sel0 + " #ToggleHiddenButton.Visible", true);
            commandBuilder.set(sel0 + " #ToggleHiddenButton.Text", hiddenLabel);
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sel0 + " #ToggleHiddenButton",
                    new EventData().append("Action", "ToggleCosmetic").append("Target", "backpack")
            );
        }

        // 2. Arrow Belt / Quiver Visibility
        boolean quiverVisible = CosmeticPreferenceUtils.isQuiverVisible(store, ref);
        commandBuilder.append("#WorkbenchList", "Pages/BackpackVisibilityEntry.ui");
        String sel1 = "#WorkbenchList[1]";
        commandBuilder.set(sel1 + " #CosmeticIcon.ItemId", "Weapon_Arrow_Iron");
        commandBuilder.set(sel1 + " #CosmeticName.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.visibility.quiver.title"));
        commandBuilder.set(sel1 + " #CosmeticDesc.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.visibility.quiver.desc"));
        if (quiverVisible) {
            commandBuilder.set(sel1 + " #ToggleButton.Visible", true);
            commandBuilder.set(sel1 + " #ToggleHiddenButton.Visible", false);
            commandBuilder.set(sel1 + " #ToggleButton.Text", visibleLabel);
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sel1 + " #ToggleButton",
                    new EventData().append("Action", "ToggleCosmetic").append("Target", "quiver")
            );
        } else {
            commandBuilder.set(sel1 + " #ToggleButton.Visible", false);
            commandBuilder.set(sel1 + " #ToggleHiddenButton.Visible", true);
            commandBuilder.set(sel1 + " #ToggleHiddenButton.Text", hiddenLabel);
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sel1 + " #ToggleHiddenButton",
                    new EventData().append("Action", "ToggleCosmetic").append("Target", "quiver")
            );
        }

        // 3. Hat / Bandana Visibility
        boolean hatVisible = CosmeticPreferenceUtils.isHatVisible(store, ref);
        commandBuilder.append("#WorkbenchList", "Pages/BackpackVisibilityEntry.ui");
        String sel2 = "#WorkbenchList[2]";
        commandBuilder.set(sel2 + " #CosmeticIcon.ItemId", "Utility_Torch_Bandana");
        commandBuilder.set(sel2 + " #CosmeticName.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.visibility.hat.title"));
        commandBuilder.set(sel2 + " #CosmeticDesc.Text", I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.visibility.hat.desc"));
        if (hatVisible) {
            commandBuilder.set(sel2 + " #ToggleButton.Visible", true);
            commandBuilder.set(sel2 + " #ToggleHiddenButton.Visible", false);
            commandBuilder.set(sel2 + " #ToggleButton.Text", visibleLabel);
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sel2 + " #ToggleButton",
                    new EventData().append("Action", "ToggleCosmetic").append("Target", "hat")
            );
        } else {
            commandBuilder.set(sel2 + " #ToggleButton.Visible", false);
            commandBuilder.set(sel2 + " #ToggleHiddenButton.Visible", true);
            commandBuilder.set(sel2 + " #ToggleHiddenButton.Text", hiddenLabel);
            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sel2 + " #ToggleHiddenButton",
                    new EventData().append("Action", "ToggleCosmetic").append("Target", "hat")
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) return;

        if (benchPosition != null && !isBenchAvailable(ref, store)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if ("Crafting".equals(data.action)) {
            if (craftingWindow != null) currentTab = "crafting";
            sendRefresh(ref, store);
            return;
        }

        if ("SelectRecipe".equals(data.action) && craftingWindow != null) {
            if (eligibleRecipes().stream().anyMatch(r -> r.getId().equals(data.target))) selectedRecipeId = data.target;
            sendRefresh(ref, store);
            return;
        }
        if ("CraftRecipe".equals(data.action) && craftingWindow != null) {
            var recipe = eligibleRecipes().stream().filter(r -> r.getId().equals(data.target)).findFirst().orElse(null);
            if (recipe != null) {
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
                player.getPageManager().setPage(ref, store, Page.None);
            }
            return;
        }

        if ("TabTransmog".equalsIgnoreCase(data.action)) {
            currentTab = "transmog";
            statusMessage = "";
            sendRefresh(ref, store);
            return;
        }

        if ("TabUpgrade".equalsIgnoreCase(data.action)) {
            currentTab = "upgrades";
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

        if ("ApplySkin".equalsIgnoreCase(data.action)) {
            handleApplySkin(ref, store, activeBp, data.target, lang);
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

    private void handleApplySkin(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull ActiveBackpack activeBp,
            @Nullable String skinId,
            @Nullable String lang) {

        ItemStack currentStack = activeBp.stack();
        if ("Utility_Heli_Backpack".equalsIgnoreCase(currentStack.getItemId())) {
            statusMessage = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.helipack.cannot_transmog");
            sendRefresh(ref, store);
            return;
        }

        if ("Utility_Heli_Backpack".equalsIgnoreCase(skinId)) {
            statusMessage = I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.helipack.cannot_be_skin");
            sendRefresh(ref, store);
            return;
        }

        String targetSkin = ("default".equalsIgnoreCase(skinId) || skinId == null) ? null : skinId;
        ItemStack updated = BackpackItemFactory.setTransmogSkin(currentStack, targetSkin);
        activeBp.container().setItemStackForSlot(activeBp.slot(), updated);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            String playerUuid = playerRef.getUuid().toString();
            BackpackArmorListener.syncBackpackAttachment(playerUuid, store, ref);
        }

        statusMessage = targetSkin == null
                ? I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.status.skin_reverted")
                : I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.status.skin_applied", I18nHelper.resolveItemName(targetSkin, lang));
        sendRefresh(ref, store);
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
        List<ItemStack> contents = BackpackItemFactory.loadContents(oldStack);
        upgraded = BackpackItemFactory.saveContents(upgraded, contents);

        // Preserve transmog skin if present
        if (BackpackItemFactory.hasTransmogSkin(oldStack)) {
            upgraded = BackpackItemFactory.setTransmogSkin(upgraded, BackpackItemFactory.getTransmogSkin(oldStack));
        }

        activeBp.container().setItemStackForSlot(activeBp.slot(), upgraded);

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
            if (nowVisible) {
                BackpackArmorListener.syncBackpackAttachment(playerUuid, store, ref);
            } else {
                CosmeticListener.removeAttachment(playerUuid, "truebackpack:backpack");
            }
            CosmeticListener.scheduleRebuild(player, store, ref, playerUuid);
            statusMessage = I18nHelper.getOrFallback(lang, nowVisible ? "server.truebackpack.toggle.backpack.visible" : "server.truebackpack.toggle.backpack.hidden");
        } else if ("quiver".equalsIgnoreCase(target)) {
            boolean nowVisible = CosmeticPreferenceUtils.toggleQuiver(store, ref);
            if (nowVisible) {
                QuiverListener.syncQuiverAttachment(playerUuid, player, store, ref);
            } else {
                CosmeticListener.removeAttachment(playerUuid, "truebackpack:quiver");
            }
            CosmeticListener.scheduleRebuild(player, store, ref, playerUuid);
            statusMessage = I18nHelper.getOrFallback(lang, nowVisible ? "server.truebackpack.toggle.quiver.visible" : "server.truebackpack.toggle.quiver.hidden");
        } else if ("hat".equalsIgnoreCase(target)) {
            boolean nowVisible = CosmeticPreferenceUtils.toggleHat(store, ref);
            if (nowVisible) {
                HatArmorListener.syncHatAttachment(playerUuid, store, ref);
            } else {
                CosmeticListener.removeAttachment(playerUuid, "truebackpack:hat");
            }
            CosmeticListener.scheduleRebuild(player, store, ref, playerUuid);
            statusMessage = I18nHelper.getOrFallback(lang, nowVisible ? "server.truebackpack.toggle.hat.visible" : "server.truebackpack.toggle.hat.hidden");
        }

        sendRefresh(ref, store);
    }

    private void sendRefresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cb = new UICommandBuilder();
        UIEventBuilder eb = new UIEventBuilder();
        refreshPage(ref, store, cb, eb);
        sendUpdate(cb, eb, false);
    }

    private boolean isBenchAvailable(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (benchPosition == null) return false;
        var transform = store.getComponent(ref,
                com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
        if (transform == null || transform.getPosition().distanceSquared(
                benchPosition.x + 0.5, benchPosition.y + 0.5, benchPosition.z + 0.5) > 64) return false;
        var world = store.getExternalData().getWorld();
        return com.supremosan.truebackpack.util.BackpackWorkbenchUtils.isBackpackWorkbench(
                world.getBlockType(benchPosition.x, benchPosition.y, benchPosition.z));
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
                if (!dismissed && ref.isValid() && player.getPageManager().getCustomPage() == this)
                    player.getPageManager().setPage(ref, store, Page.None);
            });
        });
        if (!player.getPageManager().openCustomPageWithWindows(ref, store, this, window)) {
            bench.getWindows().remove(uuid, window);
        }
    }

    private void scheduleRefresh(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (dismissed || !refreshQueued.compareAndSet(false, true)) return;
        store.getExternalData().getWorld().execute(() -> {
            refreshQueued.set(false);
            if (dismissed || !ref.isValid()) return;
            var player = store.getComponent(ref, Player.getComponentType());
            if (player != null && player.getPageManager().getCustomPage() == this) {
                if (!isBenchAvailable(ref, store)) player.getPageManager().setPage(ref, store, Page.None);
                else sendRefresh(ref, store);
            }
        });
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        dismissed = true;
        if (craftingWindow != null) {
            // The client owns closing windows attached through
            // openCustomPageWithWindows. Calling WindowManager.closeWindow here
            // races its CloseWindow packet and causes "Window id is invalid".
            craftingWindow.onChanged(null);
        }
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
        recipes.sort(Comparator.comparing(r -> r.getPrimaryOutput().getItemId()));
        return recipes;
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

    private void updateBenchPanel(UICommandBuilder cb, String lang) {
        if (craftingWindow == null) return;
        cb.set("#BenchName.Text", text(craftingWindow.isMaster() ? "bench.master_name" : "bench.apprentice"));
        cb.set("#BenchLevel.Text", text("level") + " " + (craftingWindow.isMaster() ? "2 / 2" : "1 / 2"));
        cb.set("#BenchPreview.ItemId", craftingWindow.isMaster()
                ? "Backpack_Workbench_Master" : "Backpack_Workbench_Apprentice");
        cb.set("#BenchUpgradeButton.Text", text("bench.upgrade"));
        var requirement = craftingWindow.upgradeRequirement();
        cb.set("#BenchUpgradeButton.Visible", requirement != null);
        cb.set("#BenchMax.Text", requirement == null ? text("bench.master") : text("bench.cost"));
        cb.clear("#BenchRequirements");
        if (requirement != null) {
            for (int i = 0; i < requirement.getInput().length && i < 4; i++) {
                cb.append("#BenchRequirements", "Pages/BackpackBenchMaterial.ui");
                String selector = "#BenchRequirements[" + i + "]";
                cb.set(selector + " #BenchMaterial.ItemId", materialIcon(requirement.getInput()[i]));
                cb.set(selector + " #BenchCost.Text", "× " + requirement.getInput()[i].getQuantity());
            }
        }
        var data = craftingWindow.getData();
        float progress = data.has("tierUpgradeProgress") ? data.get("tierUpgradeProgress").getAsFloat() : 0;
        cb.set("#BenchProgressBar.Visible", progress > 0);
        cb.set("#BenchProgress.Text", progress > 0 ? text("bench.upgrading") + " " + Math.round(progress * 100) + "%" : "");
        int queue = data.has("queueSize") ? data.get("queueSize").getAsInt() : 0;
        cb.set("#CraftingQueue.Text", text("craft.queue") + " " + queue);
    }

    private void buildCraftingTab(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder cb,
            UIEventBuilder eb, String lang) {
        var inventory = new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(
                InventoryComponent.getCombined(store, ref, InventoryComponent.BACKPACK_STORAGE_HOTBAR),
                craftingWindow.getExtraResourcesSection().getItemContainer());
        var player = store.getComponent(ref, Player.getComponentType());
        boolean creative = player != null && player.getGameMode() == com.hypixel.hytale.protocol.GameMode.Creative;
        int memories = com.hypixel.hytale.builtin.adventure.memories.MemoriesPlugin.get()
                .getMemoriesLevel(store.getExternalData().getWorld().getGameplayConfig());
        var recipes = eligibleRecipes();
        if (recipes.isEmpty()) return;
        if (selectedRecipeId == null || recipes.stream().noneMatch(r -> r.getId().equals(selectedRecipeId)))
            selectedRecipeId = recipes.getFirst().getId();
        cb.append("#WorkbenchList", "Pages/BackpackCraftingPanel.ui");
        String grid = "#WorkbenchList[0] #RecipeGrid";
        for (int i = 0; i < recipes.size(); i++) {
            if (i % 3 == 0) cb.append(grid, "Pages/BackpackRecipeGridRow.ui");
            String row = grid + "[" + (i / 3) + "]";
            int column = i % 3;
            var candidate = recipes.get(i);
            cb.set(row + " #RecipeIcon" + column + ".ItemId", candidate.getPrimaryOutput().getItemId());
            cb.set(row + " #Selected" + column + ".Visible", candidate.getId().equals(selectedRecipeId));
            eb.addEventBinding(CustomUIEventBindingType.Activating, row + " #SelectRecipe" + column,
                    new EventData().append("Action", "SelectRecipe").append("Target", candidate.getId()));
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
            String sel = "#WorkbenchList[0] #RecipeDetails";
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
                    label = (creative ? "*" : Integer.toString(count)) + " / " + materialLabel(inputs[i], lang);
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
            cb.set(sel + " #RecipeStatus.Text", !memory ? text("craft.memories") + " " + recipe.getRequiredMemoriesLevel()
                    : !known ? text("craft.unknown") : !materials && !creative ? text("craft.materials") : "");
            eb.addEventBinding(CustomUIEventBindingType.Activating, sel + " #CraftButton",
                    new EventData().append("Action", "CraftRecipe").append("Target", recipe.getId()));
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

        // 2. Equipped armor chest slot 1
        InventoryComponent.Armor armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
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

        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            String requiredId = entry.getKey();
            int requiredQty = entry.getValue();
            int count = countItem(hotbar != null ? hotbar.getInventory() : null, requiredId)
                    + countItem(storage != null ? storage.getInventory() : null, requiredId);
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

        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            String requiredId = entry.getKey();
            int toRemove = entry.getValue();

            if (hotbar != null) {
                toRemove = removeItem(hotbar.getInventory(), requiredId, toRemove);
            }
            if (toRemove > 0 && storage != null) {
                toRemove = removeItem(storage.getInventory(), requiredId, toRemove);
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

    private static String formatCosts(@Nonnull Map<String, Integer> costs, boolean canAfford, @Nullable String lang) {
        if (costs.isEmpty()) return I18nHelper.getOrFallback(lang, "server.truebackpack.workbench.cost.none");
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : costs.entrySet()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(e.getValue()).append("x ").append(I18nHelper.resolveItemName(e.getKey(), lang));
        }
        return sb.toString();
    }
}
