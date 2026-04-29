package com.supremosan.truebackpack.system;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.factory.HatItemFactory;
import com.supremosan.truebackpack.listener.CosmeticListener;
import com.supremosan.truebackpack.listener.HatArmorListener;
import com.supremosan.truebackpack.registries.HatRegistry;
import com.supremosan.truebackpack.registries.HatRegistry.HatEntry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HatDurabilitySystem extends EntityTickingSystem<EntityStore> {

    private static final short HEAD_SLOT = 1;

    private static final Map<String, Integer> TICK_COUNTERS = new ConcurrentHashMap<>();

    private static volatile Query<EntityStore> QUERY;

    public HatDurabilitySystem() {
        super();
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        if (QUERY == null) {
            QUERY = Query.or(InventoryComponent.Storage.getComponentType());
        }
        return QUERY;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUIDComponent uuidComp = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null) return;

        String playerUuid = uuidComp.getUuid().toString();
        if (!HatArmorListener.hasEquippedHat(playerUuid)) {
            TICK_COUNTERS.remove(playerUuid);
            return;
        }

        InventoryComponent.Storage storageComp = archetypeChunk.getComponent(index, InventoryComponent.Storage.getComponentType());
        if (storageComp == null) return;

        ItemStack hat = storageComp.getInventory().getItemStack(HEAD_SLOT);
        if (hat == null || hat.isEmpty()) return;

        if (hat.isUnbreakable()) return;

        HatEntry entry = HatRegistry.getByItem(hat.getItemId());
        if (entry == null) return;

        int counter = TICK_COUNTERS.merge(playerUuid, 1, Integer::sum);
        if (counter < entry.drainIntervalTicks()) return;

        TICK_COUNTERS.put(playerUuid, 0);

        if (hat.isBroken()) {
            onHatBreak(storageComp, playerUuid, archetypeChunk, index, store, commandBuffer);
            return;
        }

        ItemStack drained = HatItemFactory.setDurability(hat, HatItemFactory.getDurability(hat) - 1);
        storageComp.getInventory().setItemStackForSlot(HEAD_SLOT, drained);

        if (drained.isBroken()) {
            onHatBreak(storageComp, playerUuid, archetypeChunk, index, store, commandBuffer);
        }
    }

    private static void onHatBreak(
            @Nonnull InventoryComponent.Storage storageComp,
            @Nonnull String playerUuid,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            int index,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        storageComp.getInventory().setItemStackForSlot(HEAD_SLOT, ItemStack.EMPTY);
        TICK_COUNTERS.remove(playerUuid);

        Player entity = archetypeChunk.getComponent(index, Player.getComponentType());
        if (entity != null) {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            HatArmorListener.onPlayerRemove(playerUuid);
            CosmeticListener.removeAttachment(playerUuid, "truebackpack:hat");
            CosmeticListener.scheduleRebuild(entity, store, ref, playerUuid);

            if (commandBuffer.getComponent(ref, DynamicLight.getComponentType()) != null) {
                commandBuffer.removeComponent(ref, DynamicLight.getComponentType());
            }
        }
    }

    public static void onPlayerRemove(@Nonnull String playerUuid) {
        TICK_COUNTERS.remove(playerUuid);
    }
}