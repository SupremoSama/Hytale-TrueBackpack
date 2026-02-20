package com.supremosan.truebackpack;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.*;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.helpers.BackpackUtils;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BackpackCosmeticListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);

    private static final Map<String, String[]> BACKPACK_VISUALS = new LinkedHashMap<>();
    private static final Map<String, ModelAttachment> ACTIVE_BACKPACK = new ConcurrentHashMap<>();
    private static final short STORAGE_SLOT = 0;

    public static class BackpackData implements Component<EntityStore> {

        public static ComponentType<EntityStore, BackpackData> TYPE;

        public static final BuilderCodec<BackpackData> CODEC =
                BuilderCodec.builder(BackpackData.class, BackpackData::new)
                        .append(
                                new KeyedCodec<>("ActiveBackpack", Codec.STRING),
                                (data, value) -> data.activeBackpackItemId = value,
                                (data) -> data.activeBackpackItemId
                        ).add()
                        .append(
                                new KeyedCodec<>("OriginalModelPath", Codec.STRING),
                                (data, value) -> data.originalModelPath = value,
                                (data) -> data.originalModelPath
                        ).add()
                        .build();

        @Nullable
        private String activeBackpackItemId = null;
        
        @Nullable
        private String originalModelPath = null;

        @NullableDecl
        @Override
        public Component<EntityStore> clone() {
            BackpackData copy = new BackpackData();
            copy.activeBackpackItemId = this.activeBackpackItemId;
            copy.originalModelPath    = this.originalModelPath;
            return copy;
        }

        @Nullable
        public String getActiveBackpackItemId() { return activeBackpackItemId; }

        public void setActiveBackpackItemId(@Nullable String id) { this.activeBackpackItemId = id; }

        @Nullable
        public String getOriginalModelPath() { return originalModelPath; }

        public void captureOriginalModelPath(@Nonnull String path) {
            if (this.originalModelPath != null) return;
            if (isOurCustomModelName(path))     return;

            this.originalModelPath = path;
        }
    }

    private BackpackCosmeticListener() {}

    public static void registerBackpackVisual(@Nonnull String baseItemId,
                                              @Nonnull String modelPath,
                                              @Nonnull String texturePath) {
        BACKPACK_VISUALS.put(baseItemId, new String[]{modelPath, texturePath, "", ""});
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        BackpackData.TYPE = plugin.getEntityStoreRegistry()
                .registerComponent(BackpackData.class, "TrueBackpack_BackpackData", BackpackData.CODEC);

        plugin.getEventRegistry().registerGlobal(
                PlayerReadyEvent.class,
                BackpackCosmeticListener::onPlayerReady
        );

        plugin.getEventRegistry().registerGlobal(
                LivingEntityInventoryChangeEvent.class,
                BackpackCosmeticListener::onInventoryChange
        );

        LOGGER.atInfo().log("[TrueBackpack] BackpackCosmeticListener registered");
    }

    public static void onPlayerLeave(@Nonnull String playerUuid) {
        ACTIVE_BACKPACK.remove(playerUuid);
    }

    private static void onInventoryChange(@Nonnull LivingEntityInventoryChangeEvent event) {
        if (PROCESSING.get()) return;

        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        Ref<EntityStore> ref = entity.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        String playerUuid = resolveUuid(store, ref);
        if (playerUuid == null) return;

        Inventory inv = entity.getInventory();
        ItemContainer storage = inv.getStorage();
        if (event.getItemContainer() != storage) return;

        Transaction tx = event.getTransaction();
        if (!tx.wasSlotModified(STORAGE_SLOT)) return;

        ItemStack newItem  = resolveNewItemInSlot(tx, storage);
        String    newItemId = itemId(newItem);

        updateBackpackState(store, ref, player, playerUuid, newItemId);
    }

    private static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        if (PROCESSING.get()) return;

        Player player = event.getPlayer();
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        String playerUuid = resolveUuid(store, ref);
        if (playerUuid == null) return;

        String itemId = readSlotItemId(player);
        if (itemId == null) {
            BackpackData persisted = store.getComponent(ref, BackpackData.TYPE);
            if (persisted != null) itemId = persisted.getActiveBackpackItemId();
        }

        updateBackpackState(store, ref, player, playerUuid, itemId);
    }

    private static void updateBackpackState(@Nonnull Store<EntityStore> store,
                                            @Nonnull Ref<EntityStore> ref,
                                            @Nonnull Player player,
                                            @Nonnull String playerUuid,
                                            @Nullable String itemId) {

        ModelAttachment visual = resolveVisual(itemId);
        if (visual != null) {
            ACTIVE_BACKPACK.put(playerUuid, visual);
        } else {
            ACTIVE_BACKPACK.remove(playerUuid);
        }

        BackpackData data = getOrCreateBackpackData(store, ref);
        data.setActiveBackpackItemId(itemId);
        store.replaceComponent(ref, BackpackData.TYPE, data);

        if (visual == null && !hadActiveBackpack(store, ref)) return;

        PROCESSING.set(true);
        try {
            applyBackpack(store, ref, player, playerUuid);
        } finally {
            PROCESSING.set(false);
        }
    }


    private static void applyBackpack(@Nonnull Store<EntityStore> store,
                                      @Nonnull Ref<EntityStore> ref,
                                      @Nonnull Player player,
                                      @Nonnull String playerUuid) {

        ModelComponent modelComp = store.getComponent(ref, ModelComponent.getComponentType());
        if (modelComp == null) return;
        Model current = modelComp.getModel();

        PlayerSkinComponent skinComp = store.getComponent(ref, PlayerSkinComponent.getComponentType());
        if (skinComp == null) return;
        com.hypixel.hytale.protocol.PlayerSkin playerSkin = skinComp.getPlayerSkin();

        BackpackData data = getOrCreateBackpackData(store, ref);
        data.captureOriginalModelPath(current.getModel());
        store.replaceComponent(ref, BackpackData.TYPE, data);

        String baseModelPath = data.getOriginalModelPath() != null
                ? data.getOriginalModelPath()
                : current.getModel();

        List<ModelAttachment> attachments = new ArrayList<>();
        restoreSkinAttachments(attachments, playerSkin);

        ModelAttachment backpack = ACTIVE_BACKPACK.get(playerUuid);
        if (backpack != null) {
            attachments.add(backpack);
        }

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


    private static void restoreSkinAttachments(@Nonnull List<ModelAttachment> attachments,
                                               @Nonnull com.hypixel.hytale.protocol.PlayerSkin playerSkin) {
        CosmeticRegistry registry = CosmeticsModule.get().getRegistry();
        assert playerSkin.bodyCharacteristic != null;
        String gradientId = playerSkin.bodyCharacteristic.split("\\.")[1];

        String[] bodyParts = playerSkin.bodyCharacteristic.split("\\.");
        PlayerSkinPart body = registry.getBodyCharacteristics().get(bodyParts[0]);
        if (body != null) attachments.add(BackpackUtils.resolveAttachment(body, bodyParts, gradientId));

        addSkinPart(attachments, playerSkin.facialHair,     registry.getFacialHairs(),     gradientId);
        addSkinPart(attachments, playerSkin.ears,           registry.getEars(),            gradientId);
        addSkinPart(attachments, playerSkin.eyebrows,       registry.getEyebrows(),        gradientId);
        addSkinPart(attachments, playerSkin.eyes,           registry.getEyes(),            gradientId);
        addSkinPart(attachments, playerSkin.face,           registry.getFaces(),           gradientId);
        addSkinPart(attachments, playerSkin.mouth,          registry.getMouths(),          gradientId);
        addSkinPart(attachments, playerSkin.haircut,        registry.getHaircuts(),        gradientId);
        addSkinPart(attachments, playerSkin.cape,           registry.getCapes(),           gradientId);
        addSkinPart(attachments, playerSkin.faceAccessory,  registry.getFaceAccessories(), gradientId);
        addSkinPart(attachments, playerSkin.gloves,         registry.getGloves(),          gradientId);
        addSkinPart(attachments, playerSkin.headAccessory,  registry.getHeadAccessories(), gradientId);
        addSkinPart(attachments, playerSkin.overpants,      registry.getOverpants(),       gradientId);
        addSkinPart(attachments, playerSkin.overtop,        registry.getOvertops(),        gradientId);
        addSkinPart(attachments, playerSkin.pants,          registry.getPants(),           gradientId);
        addSkinPart(attachments, playerSkin.shoes,          registry.getShoes(),           gradientId);
        addSkinPart(attachments, playerSkin.undertop,       registry.getUndertops(),       gradientId);
        addSkinPart(attachments, playerSkin.underwear,      registry.getUnderwear(),       gradientId);
        addSkinPart(attachments, playerSkin.earAccessory,   registry.getEarAccessories(),  gradientId);
        addSkinPart(attachments, playerSkin.skinFeature,    registry.getSkinFeatures(),    gradientId);
    }

    private static void addSkinPart(@Nonnull List<ModelAttachment> attachments,
                                    @Nullable String skinValue,
                                    @Nonnull Map<String, PlayerSkinPart> registry,
                                    @Nonnull String gradientId) {
        if (skinValue == null) return;
        String[] parts = skinValue.split("\\.");
        PlayerSkinPart part = registry.get(parts[0]);
        if (part != null) attachments.add(BackpackUtils.resolveAttachment(part, parts, gradientId));
    }

    private static boolean hadActiveBackpack(@Nonnull Store<EntityStore> store,
                                             @Nonnull Ref<EntityStore> ref) {
        ModelComponent comp = store.getComponent(ref, ModelComponent.getComponentType());
        if (comp == null) return false;
        ModelAttachment[] attachments = comp.getModel().getAttachments();
        if (attachments == null) return false;
        for (ModelAttachment a : attachments) {
            if (isOurBackpack(a)) return true;
        }
        return false;
    }

    private static boolean isOurCustomModelName(@Nonnull String name) {
        return name.endsWith("_CustomModel");
    }

    @Nonnull
    private static BackpackData getOrCreateBackpackData(@Nonnull Store<EntityStore> store,
                                                        @Nonnull Ref<EntityStore> ref) {
        BackpackData data = store.getComponent(ref, BackpackData.TYPE);
        if (data == null) {
            data = new BackpackData();
            store.addComponent(ref, BackpackData.TYPE, data);
        }
        return data;
    }

    @Nullable
    private static String resolveUuid(@Nonnull Store<EntityStore> store,
                                      @Nonnull Ref<EntityStore> ref) {
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComp == null ? null : uuidComp.getUuid().toString();
    }

    @Nullable
    private static String readSlotItemId(@Nonnull Player player) {
        Inventory inv = player.getInventory();
        if (inv == null) return null;
        ItemContainer storage = inv.getStorage();
        if (storage == null) return null;
        return itemId(storage.getItemStack(STORAGE_SLOT));
    }

    private static boolean isOurBackpack(@Nonnull ModelAttachment attachment) {
        String model = attachment.getModel();
        if (model == null) return false;
        for (String[] v : BACKPACK_VISUALS.values()) {
            if (model.equals(v[0])) return true;
        }
        return false;
    }

    @Nullable
    private static ModelAttachment resolveVisual(@Nullable String itemId) {
        if (itemId == null) return null;
        String[] exact = BACKPACK_VISUALS.get(itemId);
        if (exact != null) return toAttachment(exact);
        for (Map.Entry<String, String[]> e : BACKPACK_VISUALS.entrySet()) {
            if (itemId.contains(e.getKey())) return toAttachment(e.getValue());
        }
        return null;
    }

    @Nonnull
    private static ModelAttachment toAttachment(@Nonnull String[] v) {
        return new ModelAttachment(v[0], v[1], v[2], v[3], 1.0);
    }

    @Nullable
    private static ItemStack resolveNewItemInSlot(@Nonnull Transaction tx,
                                                  @Nonnull ItemContainer container) {
        if (tx instanceof SlotTransaction slotTx && slotTx.getSlot() == BackpackCosmeticListener.STORAGE_SLOT)
            return slotTx.getSlotAfter();

        if (tx instanceof MoveTransaction<?> moveTx) {
            if (moveTx.getMoveType() == MoveType.MOVE_TO_SELF) {
                Object addTx = moveTx.getAddTransaction();
                if (addTx instanceof SlotTransaction addSlot && addSlot.getSlot() == BackpackCosmeticListener.STORAGE_SLOT)
                    return addSlot.getSlotAfter();
                if (addTx instanceof ItemStackTransaction addStack) {
                    for (ItemStackSlotTransaction s : addStack.getSlotTransactions())
                        if (s.getSlot() == BackpackCosmeticListener.STORAGE_SLOT) return s.getSlotAfter();
                }
            } else {
                SlotTransaction removeTx = moveTx.getRemoveTransaction();
                if (removeTx.getSlot() == BackpackCosmeticListener.STORAGE_SLOT) return removeTx.getSlotAfter();
            }
        }

        if (tx instanceof ItemStackTransaction stackTx) {
            for (ItemStackSlotTransaction s : stackTx.getSlotTransactions())
                if (s.getSlot() == BackpackCosmeticListener.STORAGE_SLOT) return s.getSlotAfter();
        }

        return container.getItemStack(BackpackCosmeticListener.STORAGE_SLOT);
    }

    @Nullable
    private static String itemId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.getItemId();
    }
}