package com.supremosan.truebackpack;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.helpers.BackpackConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BackpackArmorListener extends RefSystem<EntityStore> {

    private static CommandBuffer<EntityStore> BUFFER;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final short CHEST_SLOT = 1;

    private static final Map<UUID, Integer> appliedBonus = new ConcurrentHashMap<>();
    private static final Map<String, Short> BACKPACK_SIZES = new ConcurrentHashMap<>();

    private static BackpackArmorListener INSTANCE;

    public BackpackArmorListener() {
        INSTANCE = this;
    }

    public static void register(@Nonnull TrueBackpack plugin) {
        INSTANCE = new BackpackArmorListener();
        plugin.getEntityStoreRegistry().registerSystem(INSTANCE);

        BackpackConfig.registerDefaults();

        plugin.getEventRegistry().registerGlobal(
                LivingEntityInventoryChangeEvent.class,
                event -> INSTANCE.handle(event)
        );
    }

    public static void registerBackpack(@Nonnull String itemId, short sizeBonus) {
        BACKPACK_SIZES.put(itemId, sizeBonus);
    }

    private void handle(LivingEntityInventoryChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;

        Ref<EntityStore> ref = entity.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;

        UUID uuid = uuidComponent.getUuid();

        ItemContainer changed = event.getItemContainer();
        if (changed == null) return;

        Inventory inv = entity.getInventory();
        ItemContainer armor = inv.getArmor();
        if (changed != armor) return;
        if (!event.getTransaction().wasSlotModified(CHEST_SLOT)) return;

        ItemStack stack = armor.getItemStack(CHEST_SLOT);
        short newBonus = getBonus(stack);
        int oldBonus = appliedBonus.getOrDefault(uuid, 0);

        if (newBonus == oldBonus) return;

        appliedBonus.put(uuid, (int) newBonus);

        short currentCap = inv.getBackpack().getCapacity();
        short targetCap = (short) Math.max(0, currentCap - oldBonus + newBonus);

        List<ItemStack> overflow = new ObjectArrayList<>();
        inv.resizeBackpack(targetCap, overflow);

        if (!overflow.isEmpty()) {
            dropOverflow(ref, store, overflow);
        }

        sync(ref, store);
    }

    private short getBonus(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        String id = stack.getItemId();
        Short exact = BACKPACK_SIZES.get(id);
        if (exact != null) return exact;

        for (Map.Entry<String, Short> e : BACKPACK_SIZES.entrySet()) {
            if (id.contains(e.getKey())) return e.getValue();
        }
        return 0;
    }

    private void dropOverflow(Ref<EntityStore> ref, Store<EntityStore> store, List<ItemStack> overflow) {
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation r = store.getComponent(ref, HeadRotation.getComponentType());
        if (t == null || r == null) return;

        Vector3d pos = t.getPosition().clone().add(0, 1, 0);
        Vector3f rot = r.getRotation().clone();

        Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(store, overflow, pos, rot);
        if (BUFFER != null) BUFFER.addEntities(drops, AddReason.SPAWN);
    }

    private void sync(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        player.sendMessage(
                Message.translation("server.commands.inventory.backpack.size")
                        .param("capacity", player.getInventory().getBackpack().getCapacity())
        );
    }

    public static void cleanup(UUID uuid) {
        appliedBonus.remove(uuid);
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull CommandBuffer<EntityStore> buffer) {
        BUFFER = buffer;
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull CommandBuffer<EntityStore> buffer) {
        UUIDComponent c = store.getComponent(ref, UUIDComponent.getComponentType());
        if (c != null) cleanup(c.getUuid());
    }

    private static final Query<EntityStore> QUERY = Query.any();

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return QUERY;
    }
}
