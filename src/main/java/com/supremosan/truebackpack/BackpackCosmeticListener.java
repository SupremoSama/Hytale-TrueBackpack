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
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.Cosmetic;
import com.hypixel.hytale.protocol.PlayerSkin;
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
    private static final short CHEST_SLOT = 1;

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
            copy.originalModelPath = this.originalModelPath;
            return copy;
        }

        @Nullable
        public String getActiveBackpackItemId() {
            return activeBackpackItemId;
        }

        public void setActiveBackpackItemId(@Nullable String id) {
            this.activeBackpackItemId = id;
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

    private BackpackCosmeticListener() {
    }

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
                BackpackCosmeticListener::onPlayerReady);

        plugin.getEventRegistry().registerGlobal(
                LivingEntityInventoryChangeEvent.class,
                BackpackCosmeticListener::onInventoryChange);

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
        ItemContainer armor = inv.getArmor();

        ItemContainer changed = event.getItemContainer();
        Transaction tx = event.getTransaction();

        if (changed == storage && tx.wasSlotModified(STORAGE_SLOT)) {
            ItemStack newItem = resolveNewItemInSlot(tx, storage, STORAGE_SLOT);
            String newItemId = itemId(newItem);
            scheduleUpdate(player, store, ref, playerUuid, newItemId);
            return;
        }

        if (changed == armor) {
            if (tx.wasSlotModified(CHEST_SLOT)) {
                ItemStack newItem = resolveNewItemInSlot(tx, armor, CHEST_SLOT);
                ItemStack oldItem = resolveOldItemInSlot(tx);
                String newItemId = itemId(newItem);
                String oldItemId = itemId(oldItem);

                boolean oldWasBackpack = oldItemId != null && resolveVisual(oldItemId) != null;
                boolean newIsBackpack = newItemId != null && resolveVisual(newItemId) != null;

                if (oldWasBackpack || newIsBackpack) {
                    scheduleUpdate(player, store, ref, playerUuid, newIsBackpack ? newItemId : null);
                } else {
                    String currentBackpackItemId = readActiveBackpackItemId(player);
                    if (currentBackpackItemId == null) {
                        BackpackData persisted = store.getComponent(ref, BackpackData.TYPE);
                        if (persisted != null) currentBackpackItemId = persisted.getActiveBackpackItemId();
                    }
                    scheduleArmorReapply(player, store, ref, playerUuid, currentBackpackItemId);
                }
                return;
            }

            String currentBackpackItemId = readActiveBackpackItemId(player);
            if (currentBackpackItemId == null) {
                BackpackData persisted = store.getComponent(ref, BackpackData.TYPE);
                if (persisted != null) currentBackpackItemId = persisted.getActiveBackpackItemId();
            }
            scheduleArmorReapply(player, store, ref, playerUuid, currentBackpackItemId);
        }
    }

    private static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        if (PROCESSING.get()) return;

        Player player = event.getPlayer();
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        String playerUuid = resolveUuid(store, ref);
        if (playerUuid == null) return;

        String itemId = readActiveBackpackItemId(player);
        if (itemId == null) {
            BackpackData persisted = store.getComponent(ref, BackpackData.TYPE);
            if (persisted != null) itemId = persisted.getActiveBackpackItemId();
        }

        scheduleUpdate(player, store, ref, playerUuid, itemId);
    }

    private static void scheduleUpdate(@Nonnull Player player,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> ref,
                                       @Nonnull String playerUuid,
                                       @Nullable String itemId) {
        World world = player.getWorld();
        if (world == null) {
            updateBackpackState(store, ref, player, playerUuid, itemId);
            return;
        }
        final String captured = itemId;
        world.execute(() -> {
            if (!ref.isValid()) return;
            updateBackpackState(store, ref, player, playerUuid, captured);
        });
    }

    private static void scheduleArmorReapply(@Nonnull Player player,
                                             @Nonnull Store<EntityStore> store,
                                             @Nonnull Ref<EntityStore> ref,
                                             @Nonnull String playerUuid,
                                             @Nullable String currentBackpackItemId) {
        World world = player.getWorld();

        Runnable task = () -> {
            if (!ref.isValid()) return;

            ModelAttachment visual = resolveVisual(currentBackpackItemId);
            if (visual != null) {
                ACTIVE_BACKPACK.put(playerUuid, visual);
            } else {
                ACTIVE_BACKPACK.remove(playerUuid);
            }

            PROCESSING.set(true);
            try {
                applyBackpack(store, ref, player, playerUuid);
            } finally {
                PROCESSING.set(false);
            }
        };

        if (world == null) {
            task.run();
        } else {
            world.execute(task);
        }
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
        PlayerSkin skin = skinComp.getPlayerSkin();

        BackpackData data = getOrCreateBackpackData(store, ref);
        data.captureOriginalModelPath(current.getModel());
        store.replaceComponent(ref, BackpackData.TYPE, data);

        String baseModelPath = data.getOriginalModelPath() != null
                ? data.getOriginalModelPath()
                : current.getModel();

        Set<Cosmetic> hiddenCosmetics = resolveHiddenCosmetics(store, ref, player);

        List<ModelAttachment> attachments = new ArrayList<>();
        restoreSkinAttachments(attachments, skin, hiddenCosmetics);

        ModelAttachment backpack = ACTIVE_BACKPACK.get(playerUuid);
        if (backpack != null) attachments.add(backpack);

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
                for (short slot = 0; slot < armor.getCapacity(); slot++) {
                    ItemStack stack = armor.getItemStack(slot);
                    if (stack == null || stack.isEmpty()) continue;
                    Cosmetic[] toHide = stack.getItem().getArmor().toPacket().cosmeticsToHide;
                    if (toHide == null) continue;
                    Collections.addAll(hidden, toHide);
                }
            }
        }

        PlayerSettings settings = store.getComponent(ref, PlayerSettings.getComponentType());
        if (settings != null) {
            if (settings.hideHelmet()) {
                hidden.remove(Cosmetic.Ear);
                hidden.remove(Cosmetic.HeadAccessory);
                hidden.remove(Cosmetic.Haircut);
                hidden.remove(Cosmetic.EarAccessory);
            }
            if (settings.hideCuirass()) hidden.remove(Cosmetic.Overtop);
            if (settings.hideGauntlets()) {
                hidden.remove(Cosmetic.Undertop);
                hidden.remove(Cosmetic.Gloves);
            }
            if (settings.hidePants()) {
                hidden.remove(Cosmetic.Pants);
                hidden.remove(Cosmetic.Shoes);
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
        String gradientId = bodyParts[1];

        PlayerSkinPart body = registry.getBodyCharacteristics().get(bodyParts[0]);
        if (body != null) attachments.add(BackpackUtils.resolveAttachment(body, bodyParts, gradientId));

        addSkinPart(attachments, skin.eyebrows, registry.getEyebrows(), gradientId);
        addSkinPart(attachments, skin.eyes, registry.getEyes(), gradientId);
        addSkinPart(attachments, skin.face, registry.getFaces(), gradientId);
        addSkinPart(attachments, skin.mouth, registry.getMouths(), gradientId);
        addSkinPart(attachments, skin.underwear, registry.getUnderwear(), gradientId);
        addSkinPart(attachments, skin.skinFeature, registry.getSkinFeatures(), gradientId);

        if (!hiddenCosmetics.contains(Cosmetic.FacialHair))
            addSkinPart(attachments, skin.facialHair, registry.getFacialHairs(), gradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Ear))
            addSkinPart(attachments, skin.ears, registry.getEars(), gradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Haircut))
            addSkinPart(attachments, skin.haircut, registry.getHaircuts(), gradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Cape))
            addSkinPart(attachments, skin.cape, registry.getCapes(), gradientId);
        if (!hiddenCosmetics.contains(Cosmetic.FaceAccessory))
            addSkinPart(attachments, skin.faceAccessory, registry.getFaceAccessories(), gradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Gloves))
            addSkinPart(attachments, skin.gloves, registry.getGloves(), gradientId);
        if (!hiddenCosmetics.contains(Cosmetic.HeadAccessory))
            addSkinPart(attachments, skin.headAccessory, registry.getHeadAccessories(), gradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Overpants))
            addSkinPart(attachments, skin.overpants, registry.getOverpants(), gradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Overtop))
            addSkinPart(attachments, skin.overtop, registry.getOvertops(), gradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Pants))
            addSkinPart(attachments, skin.pants, registry.getPants(), gradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Shoes))
            addSkinPart(attachments, skin.shoes, registry.getShoes(), gradientId);
        if (!hiddenCosmetics.contains(Cosmetic.Undertop))
            addSkinPart(attachments, skin.undertop, registry.getUndertops(), gradientId);
        if (!hiddenCosmetics.contains(Cosmetic.EarAccessory))
            addSkinPart(attachments, skin.earAccessory, registry.getEarAccessories(), gradientId);
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

    private static boolean isOurBackpack(@Nonnull ModelAttachment attachment) {
        String model = attachment.getModel();
        if (model == null) return false;
        for (String[] v : BACKPACK_VISUALS.values()) {
            if (model.equals(v[0])) return true;
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
        UUIDComponent c = store.getComponent(ref, UUIDComponent.getComponentType());
        return c == null ? null : c.getUuid().toString();
    }

    @Nullable
    private static String readActiveBackpackItemId(@Nonnull Player player) {
        Inventory inv = player.getInventory();
        if (inv == null) return null;

        ItemContainer armor = inv.getArmor();
        if (armor != null) {
            String id = itemId(armor.getItemStack(CHEST_SLOT));
            if (id != null && resolveVisual(id) != null) return id;
        }

        ItemContainer storage = inv.getStorage();
        if (storage != null) {
            return itemId(storage.getItemStack(STORAGE_SLOT));
        }

        return null;
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
                                                  @Nonnull ItemContainer container,
                                                  short targetSlot) {
        if (tx instanceof SlotTransaction slotTx && slotTx.getSlot() == targetSlot)
            return slotTx.getSlotAfter();

        if (tx instanceof MoveTransaction<?> moveTx) {
            if (moveTx.getMoveType() == MoveType.MOVE_TO_SELF) {
                Object addTx = moveTx.getAddTransaction();
                if (addTx instanceof SlotTransaction addSlot && addSlot.getSlot() == targetSlot)
                    return addSlot.getSlotAfter();
                if (addTx instanceof ItemStackTransaction addStack) {
                    for (ItemStackSlotTransaction s : addStack.getSlotTransactions())
                        if (s.getSlot() == targetSlot) return s.getSlotAfter();
                }
            } else {
                SlotTransaction removeTx = moveTx.getRemoveTransaction();
                if (removeTx.getSlot() == targetSlot) return removeTx.getSlotAfter();
            }
        }

        if (tx instanceof ItemStackTransaction stackTx) {
            for (ItemStackSlotTransaction s : stackTx.getSlotTransactions())
                if (s.getSlot() == targetSlot) return s.getSlotAfter();
        }

        return container.getItemStack(targetSlot);
    }

    @Nullable
    private static ItemStack resolveOldItemInSlot(@Nonnull Transaction tx) {
        if (tx instanceof SlotTransaction slotTx && slotTx.getSlot() == BackpackCosmeticListener.CHEST_SLOT)
            return slotTx.getSlotBefore();

        if (tx instanceof MoveTransaction<?> moveTx) {
            if (moveTx.getMoveType() == MoveType.MOVE_TO_SELF) {
                Object addTx = moveTx.getAddTransaction();
                if (addTx instanceof SlotTransaction addSlot && addSlot.getSlot() == BackpackCosmeticListener.CHEST_SLOT)
                    return addSlot.getSlotBefore();
                if (addTx instanceof ItemStackTransaction addStack) {
                    for (ItemStackSlotTransaction s : addStack.getSlotTransactions())
                        if (s.getSlot() == BackpackCosmeticListener.CHEST_SLOT) return s.getSlotBefore();
                }
            } else {
                SlotTransaction removeTx = moveTx.getRemoveTransaction();
                if (removeTx.getSlot() == BackpackCosmeticListener.CHEST_SLOT) return removeTx.getSlotBefore();
            }
        }

        if (tx instanceof ItemStackTransaction stackTx) {
            for (ItemStackSlotTransaction s : stackTx.getSlotTransactions())
                if (s.getSlot() == BackpackCosmeticListener.CHEST_SLOT) return s.getSlotBefore();
        }

        return null;
    }

    @Nullable
    private static String itemId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.getItemId();
    }
}