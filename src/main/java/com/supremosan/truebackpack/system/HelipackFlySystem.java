package com.supremosan.truebackpack.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.connection.PongType;
import com.hypixel.hytale.server.core.io.PacketHandler;
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

public class HelipackFlySystem extends EntityTickingSystem<EntityStore> {

    private static final float DOUBLE_JUMP_WINDOW = 0.65f;
    private static final float MAX_PING_COMPENSATION = 0.6f;
    private static final float PING_COMPENSATION_FACTOR = 2.0f;

    private static final String ANIM_DEPLOY = "Deploy";
    private static final String ANIM_ACTIVE = "Active";
    private static final String ANIM_RETRACT = "Retract";
    private static final AnimationSlot ANIM_SLOT = AnimationSlot.Action;
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
                stopHelipackAnimation(ref, store);
                jumpStates.remove(uuid);
            }
            return;
        }

        BackpackEntry entry = BackpackRegistry.getByItem(equippedItemId);

        if (entry == null || !entry.isHelipack()) {
            if (hadState) {
                stopHelipackAnimation(ref, store);
                jumpStates.remove(uuid);
            }
            return;
        }

        HelipackConfig config = entry.helipackConfig();
        if (config == null) return;

        InventoryComponent.Armor armorComp = archetypeChunk.getComponent(index, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Storage storageComp = archetypeChunk.getComponent(index, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Backpack backpackComp = archetypeChunk.getComponent(index, InventoryComponent.Backpack.getComponentType());

        MovementStates current = movementStatesComponent.getMovementStates();
        MovementStates sent = movementStatesComponent.getSentMovementStates();

        jumpState = jumpStates.computeIfAbsent(uuid, _ -> new JumpState());

        tickAnimationSequence(dt, jumpState, store, ref, config);
        restoreAnimationAfterRebuild(uuid, jumpState, store, ref, config);

        float pingSeconds = resolvePingSeconds(uuid);
        float effectiveWindow = DOUBLE_JUMP_WINDOW + Math.min(pingSeconds * PING_COMPENSATION_FACTOR, MAX_PING_COMPENSATION);

        jumpState.timeSinceLastTrigger += dt;

        if (current.flying) {
            jumpState.fuelTimer += dt;
            if (jumpState.fuelTimer >= config.fuelConsumeInterval()) {
                jumpState.fuelTimer = 0f;
                if (config.requiresFuel() && consumeFuel(backpackComp, config.fuelItemId(), config.fuelConsumeAmount())) {
                    disableFlight(uuid, store, archetypeChunk.getReferenceTo(index), movementStatesComponent, jumpState, armorComp, storageComp, config);
                    return;
                }
            }
        }

        boolean wasJumping = sent.jumping;
        boolean isJumping = current.jumping;
        boolean justStartedJump = !wasJumping && isJumping;

        boolean wasOnGround = sent.onGround;
        boolean isOnGround = current.onGround;
        boolean justLanded = !wasOnGround && isOnGround;

        if (justStartedJump && !current.flying && !jumpState.isFlying) {
            if (config.requiresFuel()) {
                boolean hasFuelItem = hasFuel(backpackComp, config.fuelItemId());
                float savedTime = readSavedFuelTime(armorComp, storageComp, uuid.toString());
                if (!hasFuelItem && savedTime <= 0f) return;
            }

            boolean withinWindow = jumpState.timeSinceLastTrigger <= effectiveWindow;
            if (jumpState.windowOpen && withinWindow) {
                enableFlight(uuid, store, archetypeChunk.getReferenceTo(index), jumpState, armorComp, storageComp, backpackComp, config);
                jumpState.windowOpen = false;
                jumpState.timeSinceLastTrigger = Float.MAX_VALUE;
                return;
            }

            jumpState.windowOpen = true;
            jumpState.timeSinceLastTrigger = 0f;
        }

        if (justLanded) {
            if (isFlying(uuid)) {
                disableFlight(uuid, store, archetypeChunk.getReferenceTo(index), movementStatesComponent, jumpState, armorComp, storageComp, config);
                return;
            }

            if (jumpState.timeSinceLastTrigger > effectiveWindow) {
                jumpState.windowOpen = false;
                jumpState.timeSinceLastTrigger = Float.MAX_VALUE;
            }
        }

        if (jumpState.isFlying && !current.flying && !justLanded) {
            PlayerRef reassertRef = Universe.get().getPlayer(uuid);
            if (reassertRef != null) {
                movementStatesComponent.getMovementStates().flying = true;
                reassertRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(true)));

                if (jumpState.animState != AnimState.ACTIVE) {
                    playHelipackAnimation(ref, config, ANIM_ACTIVE, store);
                    jumpState.animState = AnimState.ACTIVE;
                    jumpState.animTimer = 0f;
                }
            }
        }

        if (justStartedJump && jumpState.isFlying) {
            boolean withinWindow = jumpState.timeSinceLastTrigger <= effectiveWindow;
            if (withinWindow) {
                disableFlight(uuid, store, archetypeChunk.getReferenceTo(index), movementStatesComponent, jumpState, armorComp, storageComp, config);
            } else {
                jumpState.timeSinceLastTrigger = 0f;
            }
        }
    }

    private float resolvePingSeconds(@Nonnull UUID uuid) {
        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef == null) return 0f;
        double pingMicros = playerRef.getPacketHandler()
                .getPingInfo(PongType.Tick)
                .getPingMetricSet()
                .getAverage(PacketHandler.PingInfo.ONE_SECOND_INDEX);
        if (pingMicros <= 0) return 0f;
        return (float) (pingMicros / 1_000_000.0);
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

    private boolean hasFuel(@Nullable InventoryComponent.Backpack backpackComp, @Nonnull String fuelItemId) {
        if (backpackComp == null) return false;
        ItemContainer backpack = backpackComp.getInventory();
        for (short slot = 0; slot < backpack.getCapacity(); slot++) {
            ItemStack stack = backpack.getItemStack(slot);
            if (stack != null && !stack.isEmpty() && fuelItemId.equals(stack.getItemId()) && stack.getQuantity() > 0) {
                return true;
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
            if (stack == null || stack.isEmpty() || !fuelItemId.equals(stack.getItemId())) continue;

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

    private void enableFlight(UUID uuid, Store<EntityStore> store, Ref<EntityStore> ref, JumpState jumpState, @Nullable InventoryComponent.Armor armorComp, @Nullable InventoryComponent.Storage storageComp, @Nullable InventoryComponent.Backpack backpackComp, HelipackConfig config) {
        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef == null) return;

        if (config.requiresFuel()) {
            float savedTime = readSavedFuelTime(armorComp, storageComp, uuid.toString());
            if (savedTime > 0f) {
                jumpState.fuelTimer = config.fuelConsumeInterval() - savedTime;
                writeSavedFuelTime(armorComp, storageComp, uuid.toString(), 0f);
            } else {
                if (consumeFuel(backpackComp, config.fuelItemId(), config.fuelConsumeAmount())) return;
                jumpState.fuelTimer = 0f;
            }
        }

        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;

        movementManager.getSettings().canFly = true;
        movementManager.getSettings().verticalFlySpeed = config.verticalFlySpeed();
        movementManager.getSettings().horizontalFlySpeed = config.horizontalFlySpeed();
        movementManager.update(playerRef.getPacketHandler());

        MovementStatesComponent movementStatesComponent = store.getComponent(ref, movementStatesComponentType);
        if (movementStatesComponent == null) return;

        movementStatesComponent.getMovementStates().flying = true;
        playerRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(true)));

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
            HelipackConfig config
    ) {
        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef == null) return;

        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;

        float defaultVertical = movementManager.getDefaultSettings().verticalFlySpeed;
        float defaultHorizontal = movementManager.getDefaultSettings().horizontalFlySpeed;

        movementManager.getSettings().canFly = false;
        movementManager.getSettings().verticalFlySpeed = defaultVertical;
        movementManager.getSettings().horizontalFlySpeed = defaultHorizontal;
        movementManager.update(playerRef.getPacketHandler());

        movementStatesComponent.getMovementStates().flying = false;
        playerRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(false)));

        if (config.requiresFuel() && jumpState.fuelTimer > 0f) {
            float remainingTime = config.fuelConsumeInterval() - jumpState.fuelTimer;
            if (remainingTime > 0f) {
                writeSavedFuelTime(armorComp, storageComp, uuid.toString(), remainingTime);
            }
        }

        jumpState.isFlying = false;
        jumpState.timeSinceLastTrigger = Float.MAX_VALUE;
        jumpState.fuelTimer = 0f;
        jumpState.windowOpen = false;

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
        float timeSinceLastTrigger = Float.MAX_VALUE;
        float fuelTimer = 0f;
        boolean windowOpen = false;
        boolean isFlying = false;
        AnimState animState = AnimState.IDLE;
        float animTimer = 0f;
        float deployDuration = FALLBACK_ANIM_DURATION;
        float retractDuration = FALLBACK_ANIM_DURATION;
    }

    private record EquipLocation(ItemContainer container, short slot, ItemStack stack) {
    }
}
