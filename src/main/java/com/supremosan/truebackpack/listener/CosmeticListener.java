package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Cosmetic;
import com.hypixel.hytale.protocol.ItemArmor;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.TrueBackpack;
import com.supremosan.truebackpack.cosmetic.CosmeticUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);
    private static final Query<EntityStore> QUERY = Query.any();

    private static final Map<String, Map<String, ModelAttachment>> PLAYER_ATTACHMENTS =
            new ConcurrentHashMap<>();

    private static final Map<String, List<ModelAttachment>> PREVIOUS_INJECTED =
            new ConcurrentHashMap<>();

    private static final Set<String> REBUILT_THIS_TICK = ConcurrentHashMap.newKeySet();

    private CosmeticListener() {
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        plugin.getEventRegistry()
                .registerGlobal(PlayerReadyEvent.class, CosmeticListener::onPlayerReady);

        plugin.getEntityStoreRegistry().registerSystem(new OnPlayerSettingsChange());
        plugin.getEntityStoreRegistry().registerSystem(new OnPlayerSkinChange());
        plugin.getEntityStoreRegistry().registerSystem(new OnModelChange());

        LOGGER.atInfo().log("[TrueBackpack] CosmeticListener registered");
    }

    public static void putAttachment(@Nonnull String playerUuid,
                                     @Nonnull String slotKey,
                                     @Nonnull ModelAttachment attachment) {
        PLAYER_ATTACHMENTS
                .computeIfAbsent(playerUuid, ignored -> new LinkedHashMap<>())
                .put(slotKey, attachment);
    }

    public static boolean hasAttachment(@Nonnull String playerUuid,
                                        @Nonnull String slotKey) {
        Map<String, ModelAttachment> slots = PLAYER_ATTACHMENTS.get(playerUuid);
        return slots != null && slots.containsKey(slotKey);
    }

    @Nullable
    public static ModelAttachment getAttachment(@Nonnull String playerUuid,
                                                @Nonnull String slotKey) {
        Map<String, ModelAttachment> slots = PLAYER_ATTACHMENTS.get(playerUuid);
        return slots == null ? null : slots.get(slotKey);
    }

    public static void removeAttachment(@Nonnull String playerUuid,
                                        @Nonnull String slotKey) {
        Map<String, ModelAttachment> slots = PLAYER_ATTACHMENTS.get(playerUuid);
        if (slots == null) {
            return;
        }

        slots.remove(slotKey);

        if (slots.isEmpty()) {
            PLAYER_ATTACHMENTS.remove(playerUuid);
        }

        scheduleRebuildForUuid(playerUuid);
    }

    public static void onPlayerLeave(@Nonnull String playerUuid) {
        PLAYER_ATTACHMENTS.remove(playerUuid);
        PREVIOUS_INJECTED.remove(playerUuid);
        REBUILT_THIS_TICK.remove(playerUuid);
    }

    public static boolean isProcessing() {
        return Boolean.TRUE.equals(PROCESSING.get());
    }

    public static boolean wasRebuiltSinceLastTick(@Nonnull String playerUuid) {
        return REBUILT_THIS_TICK.remove(playerUuid);
    }

    public static void scheduleRebuild(@Nonnull Player player,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> ref,
                                       @Nonnull String playerUuid) {
        World world = player.getWorld();
        Runnable task = () -> runProtectedRebuild(store, ref, playerUuid);

        if (world == null) {
            task.run();
            return;
        }

        world.execute(task);
    }

    public static void scheduleRebuildForUuid(@Nonnull String playerUuid) {
        UUID uuid = parseUuid(playerUuid);
        if (uuid == null) {
            return;
        }

        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef == null) {
            return;
        }

        UUID worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null) {
            return;
        }

        World world = Universe.get().getWorld(worldUuid);
        if (world == null) {
            return;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (isInvalid(ref)) {
            return;
        }

        Store<EntityStore> store = ref.getStore();

        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                scheduleRebuild(player, store, ref, playerUuid);
            }
        });
    }

    private static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        if (isProcessing()) {
            return;
        }

        Player player = event.getPlayer();
        Ref<EntityStore> ref = player.getReference();
        if (isInvalid(ref)) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        String playerUuid = resolveUuid(store, ref);
        if (playerUuid != null) {
            scheduleRebuild(player, store, ref, playerUuid);
        }
    }

    public static final class OnPlayerSettingsChange extends RebuildOnChangeSystem<PlayerSettings> {

        @Nonnull
        @Override
        public ComponentType<EntityStore, PlayerSettings> componentType() {
            return PlayerSettings.getComponentType();
        }
    }

    public static final class OnPlayerSkinChange extends RebuildOnChangeSystem<PlayerSkinComponent> {

        @Nonnull
        @Override
        public ComponentType<EntityStore, PlayerSkinComponent> componentType() {
            return PlayerSkinComponent.getComponentType();
        }
    }

    public static final class OnModelChange extends RebuildOnChangeSystem<ModelComponent> {

        @Nonnull
        @Override
        public ComponentType<EntityStore, ModelComponent> componentType() {
            return ModelComponent.getComponentType();
        }
    }

    private abstract static class RebuildOnChangeSystem<T extends Component<EntityStore>>
            extends RefChangeSystem<EntityStore, T> {

        @Override
        public @Nonnull Query<EntityStore> getQuery() {
            return QUERY;
        }

        @Override
        public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
                                     @Nonnull T component,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            handleChange(ref, store, commandBuffer);
        }

        @Override
        public void onComponentSet(@Nonnull Ref<EntityStore> ref,
                                   @Nullable T oldComponent,
                                   @Nonnull T newComponent,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            handleChange(ref, store, commandBuffer);
        }

        @Override
        public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
                                       @Nonnull T component,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        }

        private static void handleChange(@Nonnull Ref<EntityStore> ref,
                                         @Nonnull Store<EntityStore> store,
                                         @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            if (isProcessing()) {
                return;
            }

            Player player = commandBuffer.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }

            String playerUuid = resolveUuid(store, ref);
            if (playerUuid != null) {
                scheduleRebuild(player, store, ref, playerUuid);
            }
        }
    }

    private static void runProtectedRebuild(@Nonnull Store<EntityStore> store,
                                            @Nonnull Ref<EntityStore> ref,
                                            @Nonnull String playerUuid) {
        if (!ref.isValid()) {
            return;
        }

        PROCESSING.set(true);

        try {
            rebuildModel(store, ref, playerUuid);
            REBUILT_THIS_TICK.add(playerUuid);
        } finally {
            PROCESSING.set(false);
        }
    }

    private static void rebuildModel(@Nonnull Store<EntityStore> store,
                                     @Nonnull Ref<EntityStore> ref,
                                     @Nonnull String playerUuid) {
        ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
        if (modelComponent == null) {
            return;
        }

        PlayerSkinComponent skinComponent = store.getComponent(ref, PlayerSkinComponent.getComponentType());
        if (skinComponent == null) {
            return;
        }

        Model current = modelComponent.getModel();
        PlayerSkin skin = skinComponent.getPlayerSkin();
        List<ModelAttachment> attachments = current.getAttachments().length == 0
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(current.getAttachments()));

        List<ModelAttachment> previous = PREVIOUS_INJECTED.remove(playerUuid);
        if (previous != null) {
            attachments.removeAll(previous);
        }

        BodySkinData body = resolveBodySkinData(skin, current);
        List<ModelAttachment> injected = buildInjectedAttachments(store, ref, playerUuid, skin, body.gradientId, attachments);

        attachments.addAll(injected);

        if (!injected.isEmpty()) {
            PREVIOUS_INJECTED.put(playerUuid, injected);
        }

        if (previous == null && injected.isEmpty()) {
            return;
        }

        Model rebuilt = copyModelWithAttachments(current, attachments);
        store.replaceComponent(ref, ModelComponent.getComponentType(), new ModelComponent(rebuilt));
    }

    @Nonnull
    private static List<ModelAttachment> buildInjectedAttachments(@Nonnull Store<EntityStore> store,
                                                                  @Nonnull Ref<EntityStore> ref,
                                                                  @Nonnull String playerUuid,
                                                                  @Nonnull PlayerSkin skin,
                                                                  @Nonnull String bodyGradientId,
                                                                  @Nonnull List<ModelAttachment> currentAttachments) {
        PlayerSettings settings = store.getComponent(ref, PlayerSettings.getComponentType());
        Set<Cosmetic> hiddenCosmetics = resolveHiddenCosmetics(store, ref, settings);
        List<ModelAttachment> restored = new ArrayList<>();
        List<ModelAttachment> injected = new ArrayList<>();

        restoreSkinAttachments(restored, skin, hiddenCosmetics, bodyGradientId);

        for (ModelAttachment attachment : restored) {
            upsertAttachmentByModel(currentAttachments, attachment);
        }

        Map<String, ModelAttachment> extras = PLAYER_ATTACHMENTS.get(playerUuid);
        if (extras != null && !extras.isEmpty()) {
            injected.addAll(extras.values());
        }

        return injected;
    }

    @Nonnull
    private static Model copyModelWithAttachments(@Nonnull Model current,
                                                  @Nonnull List<ModelAttachment> attachments) {
        return new Model(
                current.getModelAssetId(),
                current.getScale(),
                current.getRandomAttachmentIds(),
                attachments.toArray(new ModelAttachment[0]),
                current.getBoundingBox(),
                current.getModel(),
                current.getTexture(),
                current.getGradientSet(),
                current.getGradientId(),
                current.getEyeHeight(),
                current.getCrouchOffset(),
                current.getSittingOffset(),
                current.getSleepingOffset(),
                current.getAnimationSetMap(),
                current.getCamera(),
                current.getLight(),
                current.getParticles(),
                current.getTrails(),
                current.getPhysicsValues(),
                current.getDetailBoxes(),
                current.getPhobia(),
                current.getPhobiaModelAssetId()
        );
    }

    @Nonnull
    private static BodySkinData resolveBodySkinData(@Nonnull PlayerSkin skin,
                                                    @Nonnull Model current) {
        if (skin.bodyCharacteristic == null) {
            return new BodySkinData("", null, null);
        }

        String[] parts = CosmeticUtils.splitId(skin.bodyCharacteristic);
        String assetId = CosmeticUtils.part(parts, 0);
        String gradientId = CosmeticUtils.fallback(CosmeticUtils.part(parts, 1), "");

        if (assetId == null) {
            return new BodySkinData(gradientId, null, null);
        }

        PlayerSkinPart bodyPart = CosmeticsModule.get().getRegistry().getBodyCharacteristics().get(assetId);
        if (bodyPart == null) {
            return new BodySkinData(gradientId, current.getGradientSet(), current.getTexture());
        }

        return new BodySkinData(
                gradientId,
                bodyPart.getGradientSet(),
                bodyPart.getGreyscaleTexture()
        );
    }

    @Nonnull
    private static Set<Cosmetic> resolveHiddenCosmetics(@Nonnull Store<EntityStore> store,
                                                        @Nonnull Ref<EntityStore> ref,
                                                        @Nullable PlayerSettings settings) {
        Set<Cosmetic> hidden = EnumSet.noneOf(Cosmetic.class);
        InventoryComponent.Armor armorComponent = store.getComponent(ref, InventoryComponent.Armor.getComponentType());

        if (armorComponent == null) {
            return hidden;
        }

        ItemContainer armor = armorComponent.getInventory();

        for (short slot = 0; slot < armor.getCapacity(); slot++) {
            ItemStack stack = armor.getItemStack(slot);
            if (stack == null || stack.isEmpty() || stack.getItem().getArmor() == null) {
                continue;
            }

            ItemArmor itemArmor = stack.getItem().getArmor().toPacket();
            if (isArmorHidden(itemArmor, settings)) {
                continue;
            }

            if (itemArmor.cosmeticsToHide != null) {
                Collections.addAll(hidden, itemArmor.cosmeticsToHide);
            }
        }

        return hidden;
    }

    private static boolean isArmorHidden(@Nonnull ItemArmor itemArmor,
                                         @Nullable PlayerSettings settings) {
        if (settings == null) {
            return false;
        }

        return switch (itemArmor.armorSlot) {
            case Head -> settings.hideHelmet();
            case Chest -> settings.hideCuirass();
            case Hands -> settings.hideGauntlets();
            case Legs -> settings.hidePants();
        };
    }

    private static void restoreSkinAttachments(@Nonnull List<ModelAttachment> attachments,
                                               @Nonnull PlayerSkin skin,
                                               @Nonnull Set<Cosmetic> hiddenCosmetics,
                                               @Nonnull String bodyGradientId) {
        if (skin.bodyCharacteristic == null) {
            return;
        }

        CosmeticRegistry registry = CosmeticsModule.get().getRegistry();

        addSkinPart(attachments, skin.skinFeature, registry.getSkinFeatures(), bodyGradientId);
        addFacePart(attachments, skin.face, registry.getFaces(), bodyGradientId);
        addFacePart(attachments, skin.ears, registry.getEars(), bodyGradientId);
        addFacePart(attachments, skin.mouth, registry.getMouths(), bodyGradientId);

        // BodyCharacteristics does not allow first person render the armor
//        addSkinPart(attachments, skin.bodyCharacteristic, registry.getBodyCharacteristics(), bodyGradientId);
        addSkinPart(attachments, skin.eyebrows, registry.getEyebrows(), bodyGradientId);
        addSkinPart(attachments, skin.eyes, registry.getEyes(), bodyGradientId);
        addSkinPart(attachments, skin.underwear, registry.getUnderwear(), bodyGradientId);

        addHaircutPart(attachments, skin, registry, bodyGradientId);

        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.FacialHair, skin.facialHair, registry.getFacialHairs(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Cape, skin.cape, registry.getCapes(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.FaceAccessory, skin.faceAccessory, registry.getFaceAccessories(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Gloves, skin.gloves, registry.getGloves(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.HeadAccessory, skin.headAccessory, registry.getHeadAccessories(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Overpants, skin.overpants, registry.getOverpants(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Overtop, skin.overtop, registry.getOvertops(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Pants, skin.pants, registry.getPants(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Shoes, skin.shoes, registry.getShoes(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Undertop, skin.undertop, registry.getUndertops(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.EarAccessory, skin.earAccessory, registry.getEarAccessories(), bodyGradientId);
    }

    private static void addHiddenAwarePart(@Nonnull List<ModelAttachment> attachments,
                                           @Nonnull Set<Cosmetic> hiddenCosmetics,
                                           @Nonnull Cosmetic cosmetic,
                                           @Nullable String rawId,
                                           @Nonnull Map<String, PlayerSkinPart> registry,
                                           @Nonnull String bodyGradientId) {
        if (!hiddenCosmetics.contains(cosmetic)) {
            addSkinPart(attachments, rawId, registry, bodyGradientId);
        }
    }

    private static void addHaircutPart(@Nonnull List<ModelAttachment> attachments,
                                       @Nonnull PlayerSkin skin,
                                       @Nonnull CosmeticRegistry registry,
                                       @Nonnull String bodyGradientId) {
        if (skin.haircut == null) {
            return;
        }

        String[] parts = CosmeticUtils.splitId(skin.haircut);
        String assetId = CosmeticUtils.part(parts, 0);
        if (assetId == null) {
            return;
        }

        PlayerSkinPart part = registry.getHaircuts().get(assetId);
        if (part == null) {
            return;
        }

        PlayerSkinPart.HeadAccessoryType headAccessoryType = resolveHeadAccessoryType(skin, registry);
        if (headAccessoryType == PlayerSkinPart.HeadAccessoryType.FullyCovering) {
            return;
        }

        String textureId = CosmeticUtils.part(parts, 1);
        String variantId = CosmeticUtils.part(parts, 2);

        if (headAccessoryType == PlayerSkinPart.HeadAccessoryType.HalfCovering
                && part.doesRequireGenericHaircut()
                && part.getHairType() != null) {
            PlayerSkinPart generic = registry.getHaircuts().get("Generic" + part.getHairType());
            if (generic != null) {
                attachments.add(CosmeticUtils.resolveAttachment(generic, textureId, variantId, bodyGradientId));
                return;
            }
        }

        attachments.add(CosmeticUtils.resolveAttachment(part, textureId, variantId, bodyGradientId));
    }

    @Nonnull
    private static PlayerSkinPart.HeadAccessoryType resolveHeadAccessoryType(@Nonnull PlayerSkin skin,
                                                                             @Nonnull CosmeticRegistry registry) {
        if (skin.headAccessory == null) {
            return PlayerSkinPart.HeadAccessoryType.Simple;
        }

        String assetId = CosmeticUtils.assetId(skin.headAccessory);
        if (assetId == null) {
            return PlayerSkinPart.HeadAccessoryType.Simple;
        }

        PlayerSkinPart headAccessory = registry.getHeadAccessories().get(assetId);
        return headAccessory == null ? PlayerSkinPart.HeadAccessoryType.Simple : headAccessory.getHeadAccessoryType();
    }

    private static void addSkinPart(@Nonnull List<ModelAttachment> attachments,
                                    @Nullable String rawId,
                                    @Nonnull Map<String, PlayerSkinPart> registry,
                                    @Nonnull String bodyGradientId) {
        addPart(attachments, rawId, registry, bodyGradientId, false);
    }

    private static void addFacePart(@Nonnull List<ModelAttachment> attachments,
                                    @Nullable String rawId,
                                    @Nonnull Map<String, PlayerSkinPart> registry,
                                    @Nonnull String bodyGradientId) {
        addPart(attachments, rawId, registry, bodyGradientId, true);
    }

    private static void addPart(@Nonnull List<ModelAttachment> attachments,
                                @Nullable String rawId,
                                @Nonnull Map<String, PlayerSkinPart> registry,
                                @Nonnull String bodyGradientId,
                                boolean useBodyGradientWhenMissingTexture) {
        if (rawId == null) {
            return;
        }

        String[] parts = CosmeticUtils.splitId(rawId);
        String assetId = CosmeticUtils.part(parts, 0);
        if (assetId == null) {
            return;
        }

        PlayerSkinPart part = registry.get(assetId);
        if (part == null) {
            return;
        }

        String textureId = CosmeticUtils.part(parts, 1);
        if (textureId == null && useBodyGradientWhenMissingTexture) {
            textureId = bodyGradientId;
        }

        attachments.add(CosmeticUtils.resolveAttachment(
                part,
                textureId,
                CosmeticUtils.part(parts, 2),
                bodyGradientId
        ));
    }

    private static void upsertAttachmentByModel(@Nonnull List<ModelAttachment> attachments,
                                                @Nonnull ModelAttachment updated) {
        String updatedModel = updated.getModel();
        if (updatedModel == null) {
            return;
        }

        for (int i = 0; i < attachments.size(); i++) {
            ModelAttachment existing = attachments.get(i);
            if (updatedModel.equals(existing.getModel())) {
                attachments.set(i, updated);
                return;
            }
        }

        attachments.add(updated);
    }

    @Nullable
    private static String resolveUuid(@Nonnull Store<EntityStore> store,
                                      @Nonnull Ref<EntityStore> ref) {
        UUIDComponent component = store.getComponent(ref, UUIDComponent.getComponentType());
        return component == null ? null : component.getUuid().toString();
    }

    @Nullable
    private static UUID parseUuid(@Nonnull String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isInvalid(@Nullable Ref<EntityStore> ref) {
        return ref == null || !ref.isValid();
    }

    private record BodySkinData(@Nonnull String gradientId,
                                @Nullable String gradientSet,
                                @Nullable String texture) {
    }
}