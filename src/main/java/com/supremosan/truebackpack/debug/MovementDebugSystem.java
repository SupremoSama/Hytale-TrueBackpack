package com.supremosan.truebackpack.debug;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class MovementDebugSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<EntityStore, Player> playerComponentType;
    private final ComponentType<EntityStore, MovementStatesComponent> movementStatesComponentType;
    private Query<EntityStore> query;

    public MovementDebugSystem(
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

        if (player == null || movementStatesComponent == null) {
            return;
        }

        MovementStates current = movementStatesComponent.getMovementStates();
        MovementStates sent = movementStatesComponent.getSentMovementStates();

        if (current.equals(sent)) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        if (sent.idle != current.idle) sb.append("idle=").append(current.idle).append(" ");
        if (sent.horizontalIdle != current.horizontalIdle)
            sb.append("horizontalIdle=").append(current.horizontalIdle).append(" ");
        if (sent.jumping != current.jumping) sb.append("jumping=").append(current.jumping).append(" ");
        if (sent.flying != current.flying) sb.append("flying=").append(current.flying).append(" ");
        if (sent.walking != current.walking) sb.append("walking=").append(current.walking).append(" ");
        if (sent.running != current.running) sb.append("running=").append(current.running).append(" ");
        if (sent.sprinting != current.sprinting) sb.append("sprinting=").append(current.sprinting).append(" ");
        if (sent.crouching != current.crouching) sb.append("crouching=").append(current.crouching).append(" ");
        if (sent.forcedCrouching != current.forcedCrouching)
            sb.append("forcedCrouching=").append(current.forcedCrouching).append(" ");
        if (sent.falling != current.falling) sb.append("falling=").append(current.falling).append(" ");
        if (sent.climbing != current.climbing) sb.append("climbing=").append(current.climbing).append(" ");
        if (sent.inFluid != current.inFluid) sb.append("inFluid=").append(current.inFluid).append(" ");
        if (sent.swimming != current.swimming) sb.append("swimming=").append(current.swimming).append(" ");
        if (sent.swimJumping != current.swimJumping) sb.append("swimJumping=").append(current.swimJumping).append(" ");
        if (sent.onGround != current.onGround) sb.append("onGround=").append(current.onGround).append(" ");
        if (sent.mantling != current.mantling) sb.append("mantling=").append(current.mantling).append(" ");
        if (sent.sliding != current.sliding) sb.append("sliding=").append(current.sliding).append(" ");
        if (sent.mounting != current.mounting) sb.append("mounting=").append(current.mounting).append(" ");
        if (sent.rolling != current.rolling) sb.append("rolling=").append(current.rolling).append(" ");
        if (sent.sitting != current.sitting) sb.append("sitting=").append(current.sitting).append(" ");
        if (sent.gliding != current.gliding) sb.append("gliding=").append(current.gliding).append(" ");
        if (sent.sleeping != current.sleeping) sb.append("sleeping=").append(current.sleeping).append(" ");

        if (!sb.isEmpty()) {
            LOGGER.atInfo().log("[MovementDebug] " + player.getUuid() + " changed: " + sb.toString().trim());
        }
    }
}