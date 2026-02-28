package com.supremosan.truebackpack.listener;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.Cosmetic;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.supremosan.truebackpack.TrueBackpack;
import com.supremosan.truebackpack.cosmetic.CosmeticUtils;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CosmeticListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);

    private static final Query<EntityStore> QUERY = Query.any();

    private static final Map<String, Map<String, ModelAttachment>> PLAYER_ATTACHMENTS =
            new ConcurrentHashMap<>();

    public static class CosmeticData implements Component<EntityStore> {

        public static ComponentType<EntityStore, CosmeticData> TYPE;

        public static final BuilderCodec<CosmeticData> CODEC =
                BuilderCodec.builder(CosmeticData.class, CosmeticData::new)
                        .append(
                                new KeyedCodec<>("OriginalModelPath", Codec.STRING),
                                (data, value) -> data.originalModelPath = value,
                                (data) -> data.originalModelPath
                        ).add()
                        .build();

        @Nullable
        private String originalModelPath = null;

        @NullableDecl
        @Override
        public Component<EntityStore> clone() {
            CosmeticData copy = new CosmeticData();
            copy.originalModelPath = this.originalModelPath;
            return copy;
        }

        @Nullable
        public String getOriginalModelPath() {
            return originalModelPath;
        }

        public void captureOriginalModelPath(@Nonnull String path) {
            if (this.originalModelPath != null) return;
            if (isOurCustomModelName(path)) return;
            this.originalModelPath = path;
        }
    }

    private CosmeticListener() {
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        CosmeticData.TYPE = plugin.getEntityStoreRegistry()
                .registerComponent(CosmeticData.class, "TrueBackpack_CosmeticData", CosmeticData.CODEC);

        plugin.getEventRegistry().registerGlobal(
                PlayerReadyEvent.class,
                CosmeticListener::onPlayerReady);

        plugin.getEntityStoreRegistry()
                .registerSystem(new CosmeticListener.OnPlayerSettingsChange());

        LOGGER.atInfo().log("[TrueBackpack] CosmeticListener registered");
    }

    public static void putAttachment(@Nonnull String playerUuid,
                                     @Nonnull String slotKey,
                                     @Nonnull ModelAttachment attachment) {
        PLAYER_ATTACHMENTS
                .computeIfAbsent(playerUuid, _ -> new LinkedHashMap<>())
                .put(slotKey, attachment);
    }

    public static boolean hasAttachment(@Nonnull String playerUuid, @Nonnull String slotKey) {
        Map<String, ModelAttachment> slots = PLAYER_ATTACHMENTS.get(playerUuid);
        return slots != null && slots.containsKey(slotKey);
    }

    public static void removeAttachment(@Nonnull String playerUuid,
                                        @Nonnull String slotKey) {
        Map<String, ModelAttachment> slots = PLAYER_ATTACHMENTS.get(playerUuid);
        if (slots != null) {
            slots.remove(slotKey);
            if (slots.isEmpty()) PLAYER_ATTACHMENTS.remove(playerUuid);
        }
    }

    public static void scheduleRebuild(@Nonnull Player player,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> ref,
                                       @Nonnull String playerUuid) {
        World world = player.getWorld();
        Runnable task = () -> {
            if (!ref.isValid()) return;
            PROCESSING.set(true);
            try {
                rebuildModel(store, ref, player, playerUuid);
            } finally {
                PROCESSING.set(false);
            }
        };

        if (world == null) task.run();
        else world.execute(task);
    }

    public static void onPlayerLeave(@Nonnull String playerUuid) {
        PLAYER_ATTACHMENTS.remove(playerUuid);
    }

    public static boolean isProcessing() {
        return Boolean.TRUE.equals(PROCESSING.get());
    }

    private static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        if (PROCESSING.get()) return;

        Player player = event.getPlayer();
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        String playerUuid = resolveUuid(store, ref);
        if (playerUuid == null) return;

        scheduleRebuild(player, store, ref, playerUuid);
    }

    public static class OnPlayerSettingsChange extends RefChangeSystem<EntityStore, PlayerSettings> {

        @Nonnull
        @Override
        public ComponentType<EntityStore, PlayerSettings> componentType() {
            return PlayerSettings.getComponentType();
        }

        @Override
        public @Nonnull Query<EntityStore> getQuery() {
            return QUERY;
        }

        @Override
        public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull PlayerSettings component,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            handleSettingsChange(ref, store, commandBuffer);
        }

        @Override
        public void onComponentSet(@Nonnull Ref<EntityStore> ref, @Nullable PlayerSettings oldComponent,
                                   @Nonnull PlayerSettings newComponent, @Nonnull Store<EntityStore> store,
                                   @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            handleSettingsChange(ref, store, commandBuffer);
        }

        @Override
        public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, @Nonnull PlayerSettings component,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        }

        private static void handleSettingsChange(@Nonnull Ref<EntityStore> ref,
                                                 @Nonnull Store<EntityStore> store,
                                                 @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            if (PROCESSING.get()) return;

            Player player = commandBuffer.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            String playerUuid = resolveUuid(store, ref);
            if (playerUuid == null) return;

            scheduleRebuild(player, store, ref, playerUuid);
        }
    }

    private static void rebuildModel(@Nonnull Store<EntityStore> store,
                                     @Nonnull Ref<EntityStore> ref,
                                     @Nonnull Player player,
                                     @Nonnull String playerUuid) {

        ModelComponent modelComp = store.getComponent(ref, ModelComponent.getComponentType());
        if (modelComp == null) return;
        Model current = modelComp.getModel();

        PlayerSkinComponent skinComp = store.getComponent(ref, PlayerSkinComponent.getComponentType());
        if (skinComp == null) return;
        PlayerSkin skin = skinComp.getPlayerSkin();

        CosmeticData data = getOrCreateCosmeticData(store, ref);
        data.captureOriginalModelPath(current.getModel());
        store.replaceComponent(ref, CosmeticData.TYPE, data);

        String baseModelPath = data.getOriginalModelPath() != null
                ? data.getOriginalModelPath()
                : current.getModel();

        Set<Cosmetic> hiddenCosmetics = resolveHiddenCosmetics(store, ref, player);

        List<ModelAttachment> attachments = new ArrayList<>();
        restoreSkinAttachments(attachments, skin, hiddenCosmetics);

        Map<String, ModelAttachment> extras = PLAYER_ATTACHMENTS.get(playerUuid);
        if (extras != null) attachments.addAll(extras.values());

        Model newModel = new Model(
                player.getDisplayName() + "_CustomModel",
                current.getScale(),
                current.getRandomAttachmentIds(),
                attachments.toArray(new ModelAttachment[0]),
                current.getBoundingBox(),
                baseModelPath,
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

        store.replaceComponent(ref, ModelComponent.getComponentType(), new ModelComponent(newModel));
    }

    @Nonnull
    private static Set<Cosmetic> resolveHiddenCosmetics(@Nonnull Store<EntityStore> store,
                                                        @Nonnull Ref<EntityStore> ref,
                                                        @Nonnull Player player) {
        Set<Cosmetic> hidden = EnumSet.noneOf(Cosmetic.class);

        Inventory inv = player.getInventory();
        if (inv != null) {
            ItemContainer armor = inv.getArmor();
            if (armor != null) {
                PlayerSettings settings = store.getComponent(ref, PlayerSettings.getComponentType());

                for (short slot = 0; slot < armor.getCapacity(); slot++) {
                    ItemStack stack = armor.getItemStack(slot);
                    if (stack == null || stack.isEmpty()) continue;

                    if (stack.getItem().getArmor() == null) continue;
                    com.hypixel.hytale.protocol.ItemArmor protocolArmor =
                            stack.getItem().getArmor().toPacket();
                    if (protocolArmor == null || protocolArmor.cosmeticsToHide == null) continue;

                    boolean armorIsHidden = settings != null && switch (protocolArmor.armorSlot) {
                        case Head  -> settings.hideHelmet();
                        case Chest -> settings.hideCuirass();
                        case Hands -> settings.hideGauntlets();
                        case Legs  -> settings.hidePants();
                        default    -> false;
                    };

                    if (!armorIsHidden) {
                        Collections.addAll(hidden, protocolArmor.cosmeticsToHide);
                    }
                }
            }
        }

        return hidden;
    }

    private static void restoreSkinAttachments(@Nonnull List<ModelAttachment> attachments,
                                               @Nonnull PlayerSkin skin,
                                               @Nonnull Set<Cosmetic> hiddenCosmetics) {
        if (skin.bodyCharacteristic == null) return;

        CosmeticRegistry registry = CosmeticsModule.get().getRegistry();

        String[] bodyParts = skin.bodyCharacteristic.split("\\.");
        String bodyGradientId = bodyParts.length > 1 ? bodyParts[1] : "";

        PlayerSkinPart body = registry.getBodyCharacteristics().get(bodyParts[0]);
        if (body != null) attachments.add(CosmeticUtils.resolveAttachment(body, bodyParts, bodyGradientId));

        addSkinPart(attachments, skin.haircut,    registry.getHaircuts(),      bodyGradientId);
        addSkinPart(attachments, skin.eyebrows,   registry.getEyebrows(),      bodyGradientId);
        addSkinPart(attachments, skin.eyes,        registry.getEyes(),          bodyGradientId);
        addSkinPart(attachments, skin.face,        registry.getFaces(),         bodyGradientId);
        addSkinPart(attachments, skin.mouth,       registry.getMouths(),        bodyGradientId);
        addSkinPart(attachments, skin.underwear,   registry.getUnderwear(),     bodyGradientId);
        addSkinPart(attachments, skin.skinFeature, registry.getSkinFeatures(),  bodyGradientId);

        if (!hiddenCosmetics.contains(Cosmetic.FacialHair))
            addSkinPart(attachments, skin.facialHair,   registry.getFacialHairs(),    bodyGradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Ear))
            addSkinPart(attachments, skin.ears,          registry.getEars(),           bodyGradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Cape))
            addSkinPart(attachments, skin.cape,          registry.getCapes(),          bodyGradientId);
        if (!hiddenCosmetics.contains(Cosmetic.FaceAccessory))
            addSkinPart(attachments, skin.faceAccessory, registry.getFaceAccessories(), bodyGradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Gloves))
            addSkinPart(attachments, skin.gloves,        registry.getGloves(),         bodyGradientId);
        if (!hiddenCosmetics.contains(Cosmetic.HeadAccessory))
            addSkinPart(attachments, skin.headAccessory, registry.getHeadAccessories(), bodyGradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Overpants))
            addSkinPart(attachments, skin.overpants,     registry.getOverpants(),      bodyGradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Overtop))
            addSkinPart(attachments, skin.overtop,       registry.getOvertops(),       bodyGradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Pants))
            addSkinPart(attachments, skin.pants,         registry.getPants(),          bodyGradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Shoes))
            addSkinPart(attachments, skin.shoes,         registry.getShoes(),          bodyGradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Undertop))
            addSkinPart(attachments, skin.undertop,      registry.getUndertops(),      bodyGradientId);
        if (!hiddenCosmetics.contains(Cosmetic.EarAccessory))
            addSkinPart(attachments, skin.earAccessory,  registry.getEarAccessories(), bodyGradientId);
    }

    private static void addSkinPart(@Nonnull List<ModelAttachment> attachments,
                                    @Nullable String skinValue,
                                    @Nonnull Map<String, PlayerSkinPart> registry,
                                    @Nonnull String bodyGradientId) {
        if (skinValue == null) return;
        String[] parts = skinValue.split("\\.");
        PlayerSkinPart part = registry.get(parts[0]);
        if (part == null) return;
        attachments.add(CosmeticUtils.resolveAttachment(part, parts, bodyGradientId));
    }

    @Nonnull
    private static CosmeticData getOrCreateCosmeticData(@Nonnull Store<EntityStore> store,
                                                        @Nonnull Ref<EntityStore> ref) {
        CosmeticData data = store.getComponent(ref, CosmeticData.TYPE);
        if (data == null) {
            data = new CosmeticData();
            store.addComponent(ref, CosmeticData.TYPE, data);
        }
        return data;
    }

    @Nullable
    private static String resolveUuid(@Nonnull Store<EntityStore> store,
                                      @Nonnull Ref<EntityStore> ref) {
        UUIDComponent c = store.getComponent(ref, UUIDComponent.getComponentType());
        return c == null ? null : c.getUuid().toString();
    }

    private static boolean isOurCustomModelName(@Nonnull String name) {
        return name.endsWith("_CustomModel");
    }
}