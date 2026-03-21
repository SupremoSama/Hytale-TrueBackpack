package com.supremosan.truebackpack.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.ItemAnimation;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
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
import com.supremosan.truebackpack.listener.BackpackArmorListener;
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

    private static final String ANIM_IDLE = "Idle";
    private static final String ANIM_DEPLOY = "Deploy";
    private static final String ANIM_RETRACT = "Retract";
    private static final String ANIM_ACTIVE = "Active";
    private static final float FALLBACK_ANIM_DURATION = 0.5f;

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
        if (equippedItemId == null) {
            jumpStates.remove(uuid);
            return;
        }

        BackpackEntry entry = BackpackRegistry.getByItem(equippedItemId);

        if (entry == null || !entry.isHelipack()) {
            jumpStates.remove(uuid);
            return;
        }

        HelipackConfig config = entry.helipackConfig();
        if (config == null) return;

        InventoryComponent.Backpack backpackComp = archetypeChunk.getComponent(index, InventoryComponent.Backpack.getComponentType());

        MovementStates current = movementStatesComponent.getMovementStates();
        MovementStates sent = movementStatesComponent.getSentMovementStates();

        JumpState jumpState = jumpStates.computeIfAbsent(uuid, _ -> new JumpState());

        tickAnimationSequence(dt, jumpState, archetypeChunk.getReferenceTo(index), commandBuffer, config);

        jumpState.timeSinceLastTrigger += dt;

        if (current.flying) {
            jumpState.fuelTimer += dt;
            if (jumpState.fuelTimer >= config.fuelConsumeInterval()) {
                jumpState.fuelTimer = 0f;
                if (config.requiresFuel() && !consumeFuel(backpackComp, config.fuelItemId(), config.fuelConsumeAmount())) {
                    disableFlight(uuid, store, archetypeChunk.getReferenceTo(index), movementStatesComponent, jumpState, commandBuffer, config);
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

        if (justStartedJump && !current.flying) {
            if (config.requiresFuel() && !hasFuel(backpackComp, config.fuelItemId())) return;

            boolean withinWindow = jumpState.timeSinceLastTrigger <= DOUBLE_JUMP_WINDOW;

            if (jumpState.windowOpen && withinWindow) {
                enableFlight(uuid, store, archetypeChunk.getReferenceTo(index), jumpState, commandBuffer, config);
                jumpState.windowOpen = false;
                jumpState.timeSinceLastTrigger = Float.MAX_VALUE;
                jumpState.fuelTimer = 0f;
                return;
            }

            jumpState.windowOpen = true;
            jumpState.timeSinceLastTrigger = 0f;
        }

        if (justLanded) {
            if (current.flying) {
                disableFlight(uuid, store, archetypeChunk.getReferenceTo(index), movementStatesComponent, jumpState, commandBuffer, config);
                return;
            }

            if (jumpState.timeSinceLastTrigger > DOUBLE_JUMP_WINDOW) {
                jumpState.windowOpen = false;
                jumpState.timeSinceLastTrigger = Float.MAX_VALUE;
            }
        }

        if (justStartedJump && current.flying) {
            boolean withinWindow = jumpState.timeSinceLastTrigger <= DOUBLE_JUMP_WINDOW;

            if (withinWindow) {
                disableFlight(uuid, store, archetypeChunk.getReferenceTo(index), movementStatesComponent, jumpState, commandBuffer, config);
            } else {
                jumpState.timeSinceLastTrigger = 0f;
            }
        }
    }

    private void tickAnimationSequence(float dt, JumpState jumpState, Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer, HelipackConfig config) {
        if (jumpState.animState == AnimState.IDLE) return;

        jumpState.animTimer += dt;

        if (jumpState.animState == AnimState.DEPLOYING && jumpState.animTimer >= jumpState.deployDuration) {
            jumpState.animState = AnimState.ACTIVE;
            jumpState.animTimer = 0f;
            playAnimation(ref, commandBuffer, config, ANIM_ACTIVE);
        } else if (jumpState.animState == AnimState.RETRACTING && jumpState.animTimer >= jumpState.retractDuration) {
            jumpState.animState = AnimState.IDLE;
            jumpState.animTimer = 0f;
            playAnimation(ref, commandBuffer, config, ANIM_IDLE);
        }
    }

    private void playAnimation(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer, HelipackConfig config, String animationId) {
        if (config.itemAnimationsId() == null) return;
        AnimationUtils.playAnimation(ref, AnimationSlot.Action, config.itemAnimationsId(), animationId, true, commandBuffer);
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
        if (backpackComp == null) return false;
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

        return remaining == 0;
    }

    private void enableFlight(UUID uuid, Store<EntityStore> store, Ref<EntityStore> ref, JumpState jumpState, CommandBuffer<EntityStore> commandBuffer, HelipackConfig config) {
        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef == null) return;

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

        jumpState.deployDuration = resolveAnimationDuration(config, ANIM_DEPLOY);
        jumpState.animState = AnimState.DEPLOYING;
        jumpState.animTimer = 0f;
        playAnimation(ref, commandBuffer, config, ANIM_DEPLOY);
    }

    private void disableFlight(
            UUID uuid,
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            MovementStatesComponent movementStatesComponent,
            JumpState jumpState,
            CommandBuffer<EntityStore> commandBuffer,
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

        jumpState.timeSinceLastTrigger = Float.MAX_VALUE;
        jumpState.fuelTimer = 0f;
        jumpState.windowOpen = false;

        jumpState.retractDuration = resolveAnimationDuration(config, ANIM_RETRACT);
        jumpState.animState = AnimState.RETRACTING;
        jumpState.animTimer = 0f;
        playAnimation(ref, commandBuffer, config, ANIM_RETRACT);
    }

    public void onPlayerLeave(UUID uuid) {
        jumpStates.remove(uuid);
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
        AnimState animState = AnimState.IDLE;
        float animTimer = 0f;
        float deployDuration = FALLBACK_ANIM_DURATION;
        float retractDuration = FALLBACK_ANIM_DURATION;
    }
}