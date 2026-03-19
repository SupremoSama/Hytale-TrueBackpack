package com.supremosan.truebackpack.factory;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.listener.BackpackArmorListener;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HelipackFlySystem extends EntityTickingSystem<EntityStore> {

    private static final float DOUBLE_JUMP_WINDOW = 0.65f;
    private static final String HELIPACK_ITEM_ID = "Utility_Heli_Backpack";
    private static final String FUEL_ITEM_ID = "Ingredient_Charcoal";

    private static final float VERTICAL_FLY_SPEED = 6.0f;
    private static final float HORIZONTAL_FLY_SPEED = 7.0f;

    private static final float FUEL_CONSUME_INTERVAL = 10.0f;
    private static final int FUEL_CONSUME_AMOUNT = 5;

    private final ComponentType<EntityStore, Player> playerComponentType;
    private final ComponentType<EntityStore, MovementStatesComponent> movementStatesComponentType;
    private Query<EntityStore> query;

    private final Map<UUID, JumpState> jumpStates = new HashMap<>();

    public HelipackFlySystem(ComponentType<EntityStore, Player> playerComponentType, ComponentType<EntityStore, MovementStatesComponent> movementStatesComponentType) {
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
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Player player = archetypeChunk.getComponent(index, playerComponentType);
        MovementStatesComponent movementStatesComponent = archetypeChunk.getComponent(index, movementStatesComponentType);

        if (player == null || movementStatesComponent == null) return;

        UUID uuid = player.getUuid();

        if (!BackpackArmorListener.hasEquippedHelipack(uuid.toString(), HELIPACK_ITEM_ID)) {
            jumpStates.remove(uuid);
            return;
        }

        MovementStates current = movementStatesComponent.getMovementStates();
        MovementStates sent = movementStatesComponent.getSentMovementStates();

        JumpState jumpState = jumpStates.computeIfAbsent(uuid, k -> new JumpState());

        jumpState.timeSinceLastTrigger += dt;

        if (current.flying) {
            jumpState.fuelTimer += dt;
            if (jumpState.fuelTimer >= FUEL_CONSUME_INTERVAL) {
                jumpState.fuelTimer = 0f;
                if (!consumeFuel(player.getInventory(), FUEL_CONSUME_AMOUNT)) {
                    disableFlight(uuid, store, archetypeChunk.getReferenceTo(index), movementStatesComponent, jumpState);
                    return;
                }
            }
        }

        boolean wasJumping = sent.jumping;
        boolean isJumping = current.jumping;
        boolean justStartedJump = !wasJumping && isJumping;

        boolean wasOnGround = sent.onGround;
        boolean isOnGround = current.onGround;
        boolean justLeftGround = wasOnGround && !isOnGround;
        boolean justLanded = !wasOnGround && isOnGround;

        if (justLeftGround && !current.flying && isJumping) {
            if (!hasFuel(player.getInventory())) return;

            boolean withinWindow = jumpState.timeSinceLastTrigger <= DOUBLE_JUMP_WINDOW;

            if (jumpState.windowOpen && withinWindow) {
                enableFlight(uuid, store, archetypeChunk.getReferenceTo(index));
                jumpState.windowOpen = false;
                jumpState.timeSinceLastTrigger = Float.MAX_VALUE;
                jumpState.fuelTimer = 0f;
                return;
            } else {
                jumpState.windowOpen = true;
                jumpState.timeSinceLastTrigger = 0f;
            }
        }

        if (justLanded && jumpState.windowOpen) {
            if (jumpState.timeSinceLastTrigger > DOUBLE_JUMP_WINDOW) {
                jumpState.windowOpen = false;
                jumpState.timeSinceLastTrigger = Float.MAX_VALUE;
            }
        }

        if (justStartedJump && current.flying) {
            boolean withinWindow = jumpState.timeSinceLastTrigger <= DOUBLE_JUMP_WINDOW;

            if (withinWindow) {
                disableFlight(uuid, store, archetypeChunk.getReferenceTo(index), movementStatesComponent, jumpState);
            } else {
                jumpState.timeSinceLastTrigger = 0f;
            }
        }
    }

    private boolean hasFuel(@Nonnull Inventory inventory) {
        ItemContainer backpack = inventory.getBackpack();
        if (backpack == null) return false;
        for (short slot = 0; slot < backpack.getCapacity(); slot++) {
            ItemStack stack = backpack.getItemStack(slot);
            if (stack != null && !stack.isEmpty() && FUEL_ITEM_ID.equals(stack.getItemId()) && stack.getQuantity() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean consumeFuel(@Nonnull Inventory inventory, int amount) {
        ItemContainer backpack = inventory.getBackpack();
        if (backpack == null) return false;

        int remaining = amount;

        for (short slot = 0; slot < backpack.getCapacity() && remaining > 0; slot++) {
            ItemStack stack = backpack.getItemStack(slot);
            if (stack == null || stack.isEmpty() || !FUEL_ITEM_ID.equals(stack.getItemId())) continue;

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

    private void enableFlight(UUID uuid, Store<EntityStore> store, Ref<EntityStore> ref) {
        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef == null) return;

        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;

        movementManager.getSettings().canFly = true;
        movementManager.getSettings().verticalFlySpeed = VERTICAL_FLY_SPEED;
        movementManager.getSettings().horizontalFlySpeed = HORIZONTAL_FLY_SPEED;
        movementManager.update(playerRef.getPacketHandler());

        MovementStatesComponent movementStatesComponent = store.getComponent(ref, movementStatesComponentType);
        if (movementStatesComponent == null) return;

        movementStatesComponent.getMovementStates().flying = true;
        playerRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(true)));
    }

    private void disableFlight(UUID uuid, Store<EntityStore> store, Ref<EntityStore> ref, MovementStatesComponent movementStatesComponent, JumpState jumpState) {
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
    }

    public void onPlayerLeave(UUID uuid) {
        jumpStates.remove(uuid);
    }

    private static class JumpState {
        float timeSinceLastTrigger = Float.MAX_VALUE;
        float fuelTimer = 0f;
        boolean windowOpen = false;
    }
}