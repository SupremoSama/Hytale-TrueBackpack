package com.supremosan.truebackpack.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.ItemAnimation;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.Rangef;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.listener.BackpackArmorListener;
import com.supremosan.truebackpack.listener.CosmeticListener;
import com.supremosan.truebackpack.registries.BackpackRegistry;
import com.supremosan.truebackpack.registries.BackpackRegistry.BackpackEntry;
import com.supremosan.truebackpack.registries.BackpackRegistry.HelipackConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HelipackFlySystem extends EntityTickingSystem<EntityStore> implements BackpackArmorListener.EquipChangeListener {

    private static final String ANIM_IDLE = "Idle";
    private static final String ANIM_DEPLOY = "Deploy";
    private static final String ANIM_ACTIVE = "Active";
    private static final String ANIM_RETRACT = "Retract";
    private static final AnimationSlot ANIM_SLOT = AnimationSlot.ServerAction;
    private static final float FALLBACK_ANIM_DURATION = 0.5f;

    private static final short CHEST_SLOT = 1;
    private static final short STORAGE_SLOT = 0;

    private final ComponentType<EntityStore, Player> playerComponentType;
    private final ComponentType<EntityStore, MovementStatesComponent> movementStatesComponentType;
    private Query<EntityStore> query;

    private final Map<UUID, JumpState> jumpStates = new HashMap<>();

    public HelipackFlySystem(
            ComponentType<EntityStore, Player> playerComponentType,
            ComponentType<EntityStore, MovementStatesComponent> movementStatesComponentType
    ) {
        this.playerComponentType = playerComponentType;
        this.movementStatesComponentType = movementStatesComponentType;
        registerHelipackAnimations();
        BackpackArmorListener.addEquipChangeListener(this);
    }

    private static void registerHelipackAnimations() {
        ModelAsset.Animation idle = new ModelAsset.Animation(
                ANIM_IDLE, "Characters/Animations/Helipack/helipack_idle.blockyanim",
                1f, 0.2f, true, 1f, new int[0], null);
        ModelAsset.Animation deploy = new ModelAsset.Animation(
                ANIM_DEPLOY, "Characters/Animations/Helipack/helipack_deploy.blockyanim",
                2f, 0.2f, false, 1f, new int[0], null);
        ModelAsset.Animation active = new ModelAsset.Animation(
                ANIM_ACTIVE, "Characters/Animations/Helipack/helipack_working.blockyanim",
                2f, 0.2f, true, 1f, new int[0], null);
        ModelAsset.Animation retract = new ModelAsset.Animation(
                ANIM_RETRACT, "Characters/Animations/Helipack/helipack_deploy.blockyanim",
                -2f, 0.2f, false, 1f, new int[0], null);

        Rangef delay = new Rangef(0, 0);
        CosmeticListener.registerExtraAnimations(ANIM_IDLE,
                new ModelAsset.AnimationSet(new ModelAsset.Animation[]{idle}, delay));
        CosmeticListener.registerExtraAnimations(ANIM_DEPLOY,
                new ModelAsset.AnimationSet(new ModelAsset.Animation[]{deploy}, delay));
        CosmeticListener.registerExtraAnimations(ANIM_ACTIVE,
                new ModelAsset.AnimationSet(new ModelAsset.Animation[]{active}, delay));
        CosmeticListener.registerExtraAnimations(ANIM_RETRACT,
                new ModelAsset.AnimationSet(new ModelAsset.Animation[]{retract}, delay));
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Query.and(playerComponentType, movementStatesComponentType);
        }
        return query;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Player player = archetypeChunk.getComponent(index, playerComponentType);
        MovementStatesComponent movementStatesComponent = archetypeChunk.getComponent(index, movementStatesComponentType);

        if (player == null || movementStatesComponent == null) return;

        Ref<EntityStore> ref = player.getReference();
        if (ref == null) return;

        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;

        UUID uuid = uuidComp.getUuid();
        String equippedItemId = BackpackArmorListener.getEquippedItemId(uuid.toString());

        JumpState jumpState = jumpStates.get(uuid);
        boolean hadState = jumpState != null;

        if (equippedItemId == null) {
            if (hadState) {
                cleanupPlayerFlight(uuid, ref, store, movementStatesComponent, jumpState);
                jumpStates.remove(uuid);
            }
            return;
        }

        BackpackEntry entry = BackpackRegistry.getByItem(equippedItemId);

        if (entry == null || !entry.isHelipack()) {
            if (hadState) {
                cleanupPlayerFlight(uuid, ref, store, movementStatesComponent, jumpState);
                jumpStates.remove(uuid);
            }
            return;
        }

        HelipackConfig config = entry.helipackConfig();
        if (config == null) return;

        InventoryComponent.Armor armorComp = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

        MovementStates current = movementStatesComponent.getMovementStates();

        jumpState = jumpStates.computeIfAbsent(uuid, _ -> new JumpState());

        tickAnimationSequence(dt, jumpState, store, ref, config);
        restoreAnimationAfterRebuild(uuid, jumpState, store, ref, config);

        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        PlayerRef playerRef = Universe.get().getPlayer(uuid);

        // Synchronize FlyMode strictly based on fuel availability
        if (movementManager != null && playerRef != null) {
            syncFlyMode(uuid, playerRef, movementManager, config, armorComp, storageComp, backpackComp);
        }

        boolean hasFuelInBackpack = hasFuel(backpackComp, config.fuelItemId(), config.fuelConsumeAmount());
        boolean hasFuelAvailable = !config.requiresFuel() || hasFuelInBackpack;

        boolean isFlyingNow = current.flying;
        boolean wasFlying = jumpState.isFlying;

        // Player started flying (client detected double-tap Space natively)
        if (isFlyingNow && !wasFlying) {
            if (!hasFuelAvailable) {
                current.flying = false;
                if (playerRef != null) {
                    playerRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(false)));
                    if (movementManager != null) {
                        movementManager.getSettings().fly = movementManager.getDefaultSettings().fly;
                        movementManager.update(playerRef.getPacketHandler());
                    }
                }
                return;
            }

            enableFlight(uuid, store, ref, jumpState, armorComp, storageComp, backpackComp, config);
            return;
        }

        // Player stopped flying (client detected double-tap Space or landed)
        if (!isFlyingNow && wasFlying) {
            disableFlight(uuid, store, ref, movementStatesComponent, jumpState, armorComp, storageComp, backpackComp, config);
            return;
        }

        if (wasFlying && current.onGround) {
            disableFlight(uuid, store, ref, movementStatesComponent, jumpState, armorComp, storageComp, backpackComp, config);
            return;
        }

        // Active flight: tick fuel consumption
        if (jumpState.isFlying) {
            jumpState.fuelTimer += dt;
            if (jumpState.fuelTimer >= config.fuelConsumeInterval()) {
                jumpState.fuelTimer = 0f;
                if (config.requiresFuel() && consumeFuel(backpackComp, config.fuelItemId(), config.fuelConsumeAmount())) {
                    // Ran out of fuel while flying
                    clearSavedFuelTime(armorComp, storageComp, uuid.toString());
                    disableFlight(uuid, store, ref, movementStatesComponent, jumpState, armorComp, storageComp, backpackComp, config);
                    if (movementManager != null && playerRef != null) {
                        movementManager.getSettings().fly = movementManager.getDefaultSettings().fly;
                        movementManager.getSettings().verticalFlySpeed = movementManager.getDefaultSettings().verticalFlySpeed;
                        movementManager.getSettings().horizontalFlySpeed = movementManager.getDefaultSettings().horizontalFlySpeed;
                        movementManager.update(playerRef.getPacketHandler());
                    }
                }
            }
        }
    }

    private void cleanupPlayerFlight(
            UUID uuid,
            Ref<EntityStore> ref,
            Store<EntityStore> store,
            MovementStatesComponent movementStatesComponent,
            JumpState jumpState
    ) {
        stopHelipackAnimation(ref, store);
        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef == null) return;

        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager != null) {
            movementManager.getSettings().fly = movementManager.getDefaultSettings().fly;
            movementManager.getSettings().verticalFlySpeed = movementManager.getDefaultSettings().verticalFlySpeed;
            movementManager.getSettings().horizontalFlySpeed = movementManager.getDefaultSettings().horizontalFlySpeed;
            movementManager.update(playerRef.getPacketHandler());
        }

        if (jumpState.isFlying || movementStatesComponent.getMovementStates().flying) {
            movementStatesComponent.getMovementStates().flying = false;
            playerRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(false)));
        }
    }

    @Override
    public void onEquipChange(
            @Nonnull String playerUuid,
            @Nonnull Player player,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref
    ) {
        UUID uuid;
        try {
            uuid = UUID.fromString(playerUuid);
        } catch (IllegalArgumentException e) {
            return;
        }

        MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
        if (mm == null) return;

        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef == null) return;

        String equippedItemId = BackpackArmorListener.getEquippedItemId(playerUuid);
        BackpackEntry entry = equippedItemId != null ? BackpackRegistry.getByItem(equippedItemId) : null;

        if (entry == null || !entry.isHelipack()) {
            if (mm.getSettings().fly != mm.getDefaultSettings().fly) {
                mm.getSettings().fly = mm.getDefaultSettings().fly;
                mm.getSettings().verticalFlySpeed = mm.getDefaultSettings().verticalFlySpeed;
                mm.getSettings().horizontalFlySpeed = mm.getDefaultSettings().horizontalFlySpeed;
                mm.update(playerRef.getPacketHandler());
            }
            return;
        }

        HelipackConfig config = entry.helipackConfig();
        if (config == null) return;

        InventoryComponent.Armor armorComp = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

        syncFlyMode(uuid, playerRef, mm, config, armorComp, storageComp, backpackComp);
    }

    private void syncFlyMode(
            @Nonnull UUID uuid,
            @Nonnull PlayerRef playerRef,
            @Nonnull MovementManager movementManager,
            @Nonnull HelipackConfig config,
            @Nullable InventoryComponent.Armor armorComp,
            @Nullable InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Backpack backpackComp
    ) {
        float savedFuelTime = readSavedFuelTime(armorComp, storageComp, uuid.toString());
        boolean hasFuelInBackpack = hasFuel(backpackComp, config.fuelItemId(), config.fuelConsumeAmount());

        // A helipack without fuel in its inventory cannot fly under any circumstances.
        if (config.requiresFuel() && !hasFuelInBackpack) {
            if (savedFuelTime > 0f) {
                clearSavedFuelTime(armorComp, storageComp, uuid.toString());
                savedFuelTime = 0f;
            }
        }

        boolean hasFuelAvailable = !config.requiresFuel() || hasFuelInBackpack;

        FlyMode defaultFly = movementManager.getDefaultSettings().fly;
        FlyMode targetFly = hasFuelAvailable ? FlyMode.Allowed : defaultFly;

        if (movementManager.getSettings().fly != targetFly) {
            movementManager.getSettings().fly = targetFly;
            if (targetFly == FlyMode.Allowed) {
                movementManager.getSettings().verticalFlySpeed = config.verticalFlySpeed();
                movementManager.getSettings().horizontalFlySpeed = config.horizontalFlySpeed();
            } else {
                movementManager.getSettings().verticalFlySpeed = movementManager.getDefaultSettings().verticalFlySpeed;
                movementManager.getSettings().horizontalFlySpeed = movementManager.getDefaultSettings().horizontalFlySpeed;
            }
            movementManager.update(playerRef.getPacketHandler());
        }
    }

    private void restoreAnimationAfterRebuild(
            @Nonnull UUID uuid,
            @Nonnull JumpState jumpState,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull HelipackConfig config) {

        if (!CosmeticListener.wasRebuiltSinceLastTick(uuid.toString())) return;
        if (jumpState.animState == AnimState.IDLE) return;

        String animId = switch (jumpState.animState) {
            case DEPLOYING -> ANIM_DEPLOY;
            case ACTIVE -> ANIM_ACTIVE;
            case RETRACTING -> ANIM_RETRACT;
            default -> throw new IllegalStateException("Unexpected value: " + jumpState.animState);
        };

        playHelipackAnimation(ref, config, animId, store);
    }

    private void tickAnimationSequence(float dt, JumpState jumpState, Store<EntityStore> store, Ref<EntityStore> ref, HelipackConfig config) {
        if (jumpState.animState == AnimState.IDLE) return;

        jumpState.animTimer += dt;

        if (jumpState.animState == AnimState.DEPLOYING && jumpState.animTimer >= jumpState.deployDuration) {
            jumpState.animState = AnimState.ACTIVE;
            jumpState.animTimer = 0f;
            playHelipackAnimation(ref, config, ANIM_ACTIVE, store);
        } else if (jumpState.animState == AnimState.RETRACTING && jumpState.animTimer >= jumpState.retractDuration) {
            jumpState.animState = AnimState.IDLE;
            jumpState.animTimer = 0f;
            stopHelipackAnimation(ref, store);
        }
    }

    private void playHelipackAnimation(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull HelipackConfig config,
            @Nonnull String animationId,
            @Nonnull Store<EntityStore> store) {
        String itemAnimationsId = config.itemAnimationsId();
        if (itemAnimationsId != null) {
            AnimationUtils.playAnimation(ref, ANIM_SLOT, itemAnimationsId, animationId, true, store);
        } else {
            AnimationUtils.playAnimation(ref, ANIM_SLOT, animationId, true, store);
        }
    }

    private void stopHelipackAnimation(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store) {
        AnimationUtils.stopAnimation(ref, ANIM_SLOT, true, store);
    }

    private float resolveAnimationDuration(HelipackConfig config, String animationId) {
        if (config.itemAnimationsId() == null) return FALLBACK_ANIM_DURATION;

        ItemPlayerAnimations itemAnimations = ItemPlayerAnimations.getAssetMap().getAsset(config.itemAnimationsId());
        if (itemAnimations == null) return FALLBACK_ANIM_DURATION;

        Map<String, ItemAnimation> animations = itemAnimations.getAnimations();
        if (animations == null) return FALLBACK_ANIM_DURATION;

        ItemAnimation anim = animations.get(animationId);
        if (anim == null || anim.speed == 0f) return FALLBACK_ANIM_DURATION;

        return 1f / Math.abs(anim.speed);
    }

    private boolean hasFuel(@Nullable InventoryComponent.Backpack backpackComp, @Nonnull String fuelItemId, int requiredAmount) {
        if (backpackComp == null) return false;
        ItemContainer backpack = backpackComp.getInventory();
        int total = 0;
        for (short slot = 0; slot < backpack.getCapacity(); slot++) {
            ItemStack stack = backpack.getItemStack(slot);
            if (stack != null && !stack.isEmpty() && isMatchingFuel(stack.getItemId(), fuelItemId)) {
                total += stack.getQuantity();
                if (total >= requiredAmount) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean consumeFuel(@Nullable InventoryComponent.Backpack backpackComp, @Nonnull String fuelItemId, int amount) {
        if (backpackComp == null) return true;
        ItemContainer backpack = backpackComp.getInventory();

        int remaining = amount;

        for (short slot = 0; slot < backpack.getCapacity() && remaining > 0; slot++) {
            ItemStack stack = backpack.getItemStack(slot);
            if (stack == null || stack.isEmpty() || !isMatchingFuel(stack.getItemId(), fuelItemId)) continue;

            int available = stack.getQuantity();
            if (available <= 0) continue;

            if (available >= remaining) {
                backpack.setItemStackForSlot(slot, stack.withQuantity(available - remaining));
                remaining = 0;
            } else {
                remaining -= available;
                backpack.setItemStackForSlot(slot, ItemStack.EMPTY);
            }
        }

        return remaining != 0;
    }

    private static boolean isMatchingFuel(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) return false;
        if (a.equalsIgnoreCase(b)) return true;
        String cleanA = a.contains(":") ? a.substring(a.indexOf(':') + 1) : a;
        String cleanB = b.contains(":") ? b.substring(b.indexOf(':') + 1) : b;
        return cleanA.equalsIgnoreCase(cleanB);
    }

    @Nullable
    private EquipLocation findEquipLocation(
            @Nullable InventoryComponent.Armor armorComp,
            @Nullable InventoryComponent.Storage storageComp,
            @Nonnull String playerUuid) {
        String equippedItemId = BackpackArmorListener.getEquippedItemId(playerUuid);
        if (equippedItemId == null) return null;

        if (armorComp != null) {
            ItemStack stack = armorComp.getInventory().getItemStack(CHEST_SLOT);
            if (stack != null && !stack.isEmpty() && equippedItemId.equals(stack.getItemId())) {
                return new EquipLocation(armorComp.getInventory(), CHEST_SLOT, stack);
            }
        }

        if (storageComp != null) {
            ItemStack stack = storageComp.getInventory().getItemStack(STORAGE_SLOT);
            if (stack != null && !stack.isEmpty() && equippedItemId.equals(stack.getItemId())) {
                return new EquipLocation(storageComp.getInventory(), STORAGE_SLOT, stack);
            }
        }

        return null;
    }

    private float readSavedFuelTime(
            @Nullable InventoryComponent.Armor armorComp,
            @Nullable InventoryComponent.Storage storageComp,
            @Nonnull String playerUuid) {
        EquipLocation loc = findEquipLocation(armorComp, storageComp, playerUuid);
        if (loc == null) return 0f;
        return BackpackItemFactory.getRemainingFuelTime(loc.stack);
    }

    private void writeSavedFuelTime(
            @Nullable InventoryComponent.Armor armorComp,
            @Nullable InventoryComponent.Storage storageComp,
            @Nonnull String playerUuid,
            float seconds) {
        EquipLocation loc = findEquipLocation(armorComp, storageComp, playerUuid);
        if (loc == null) return;
        loc.container.setItemStackForSlot(loc.slot, BackpackItemFactory.setRemainingFuelTime(loc.stack, seconds));
    }

    private void clearSavedFuelTime(
            @Nullable InventoryComponent.Armor armorComp,
            @Nullable InventoryComponent.Storage storageComp,
            @Nonnull String playerUuid) {
        writeSavedFuelTime(armorComp, storageComp, playerUuid, 0f);
    }

    private void enableFlight(
            UUID uuid,
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            JumpState jumpState,
            @Nullable InventoryComponent.Armor armorComp,
            @Nullable InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Backpack backpackComp,
            HelipackConfig config
    ) {
        if (config.requiresFuel()) {
            float savedTime = readSavedFuelTime(armorComp, storageComp, uuid.toString());
            if (savedTime > 0.01f && hasFuel(backpackComp, config.fuelItemId(), 1)) {
                jumpState.fuelTimer = config.fuelConsumeInterval() - savedTime;
                writeSavedFuelTime(armorComp, storageComp, uuid.toString(), 0f);
            } else {
                if (consumeFuel(backpackComp, config.fuelItemId(), config.fuelConsumeAmount())) {
                    clearSavedFuelTime(armorComp, storageComp, uuid.toString());
                    PlayerRef playerRef = Universe.get().getPlayer(uuid);
                    if (playerRef != null) {
                        playerRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(false)));
                        MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
                        if (mm != null) {
                            mm.getSettings().fly = mm.getDefaultSettings().fly;
                            mm.update(playerRef.getPacketHandler());
                        }
                    }
                    return;
                }
                jumpState.fuelTimer = 0f;
            }
        }

        jumpState.isFlying = true;
        jumpState.deployDuration = resolveAnimationDuration(config, ANIM_DEPLOY);
        jumpState.animState = AnimState.DEPLOYING;
        jumpState.animTimer = 0f;

        playHelipackAnimation(ref, config, ANIM_DEPLOY, store);
    }

    private void disableFlight(
            UUID uuid,
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            MovementStatesComponent movementStatesComponent,
            JumpState jumpState,
            @Nullable InventoryComponent.Armor armorComp,
            @Nullable InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Backpack backpackComp,
            HelipackConfig config
    ) {
        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef != null) {
            movementStatesComponent.getMovementStates().flying = false;
            playerRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(false)));
        }

        if (config.requiresFuel() && jumpState.fuelTimer > 0f) {
            float remainingTime = config.fuelConsumeInterval() - jumpState.fuelTimer;
            if (remainingTime > 0.1f && hasFuel(backpackComp, config.fuelItemId(), 1)) {
                writeSavedFuelTime(armorComp, storageComp, uuid.toString(), remainingTime);
            } else {
                clearSavedFuelTime(armorComp, storageComp, uuid.toString());
            }
        } else {
            clearSavedFuelTime(armorComp, storageComp, uuid.toString());
        }

        jumpState.isFlying = false;
        jumpState.fuelTimer = 0f;

        jumpState.retractDuration = resolveAnimationDuration(config, ANIM_RETRACT);
        jumpState.animState = AnimState.RETRACTING;
        jumpState.animTimer = 0f;

        playHelipackAnimation(ref, config, ANIM_RETRACT, store);
    }

    public boolean isFlying(UUID uuid) {
        JumpState jumpState = jumpStates.get(uuid);
        return jumpState != null && jumpState.isFlying;
    }

    private enum AnimState {
        IDLE,
        DEPLOYING,
        ACTIVE,
        RETRACTING
    }

    private static class JumpState {
        float fuelTimer = 0f;
        boolean isFlying = false;
        AnimState animState = AnimState.IDLE;
        float animTimer = 0f;
        float deployDuration = FALLBACK_ANIM_DURATION;
        float retractDuration = FALLBACK_ANIM_DURATION;
    }

    private record EquipLocation(ItemContainer container, short slot, ItemStack stack) {
    }
}
