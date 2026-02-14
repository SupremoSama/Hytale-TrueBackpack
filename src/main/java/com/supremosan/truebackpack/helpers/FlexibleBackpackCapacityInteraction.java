package com.supremosan.truebackpack.helpers;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class FlexibleBackpackCapacityInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<FlexibleBackpackCapacityInteraction> CODEC =
            BuilderCodec.builder(
                            FlexibleBackpackCapacityInteraction.class,
                            FlexibleBackpackCapacityInteraction::new,
                            SimpleInstantInteraction.CODEC
                    )
                    .<Short>appendInherited(
                            new KeyedCodec<>("Capacity", Codec.SHORT),
                            (i, s) -> i.capacity = s,
                            i -> i.capacity,
                            (i, parent) -> i.capacity = parent.capacity
                    )
                    .add()
                    .build();

    private short capacity = 1;

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {

        Ref<EntityStore> ref = context.getEntity();
        Player player = context.getCommandBuffer().getComponent(ref, Player.getComponentType());
        if (player == null) return;

        Inventory inventory = player.getInventory();
        short current = inventory.getBackpack().getCapacity();
        short next = (short) (current + capacity);
        if (next < 0) next = 0;

        inventory.resizeBackpack(next, null);

        player.sendMessage(
                Message.translation("server.commands.inventory.backpack.size")
                        .param("capacity", inventory.getBackpack().getCapacity())
        );
    }

    @Override
    public String toString() {
        return "FlexibleBackpackCapacityInteraction{capacity=" + capacity + "} " + super.toString();
    }
}