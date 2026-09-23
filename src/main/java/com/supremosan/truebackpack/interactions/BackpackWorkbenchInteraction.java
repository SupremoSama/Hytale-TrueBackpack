package com.supremosan.truebackpack.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.ui.BackpackWorkbenchPage;
import org.joml.Vector3i;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The normal Use action for both backpack benches. */
public class BackpackWorkbenchInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<BackpackWorkbenchInteraction> CODEC = BuilderCodec.builder(
            BackpackWorkbenchInteraction.class, BackpackWorkbenchInteraction::new, SimpleBlockInteraction.CODEC).build();

    @Override
    protected void interactWithBlock(@Nonnull World world, @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull InteractionType type, @Nonnull InteractionContext context, @Nullable ItemStack held,
            @Nonnull Vector3i target, @Nonnull CooldownHandler cooldown) {
        var ref = context.getEntity();
        var player = buffer.getComponent(ref, Player.getComponentType());
        var playerRef = buffer.getComponent(ref, PlayerRef.getComponentType());
        if (player != null && playerRef != null) {
            var benchRef = com.hypixel.hytale.server.core.modules.block.BlockModule.getBlockEntity(
                    world, target.x, target.y, target.z);
            if (benchRef == null || !benchRef.isValid()
                    || !com.hypixel.hytale.builtin.crafting.component.BenchBlock.tryOpen(ref, buffer, benchRef, target)) return;
            BackpackWorkbenchPage.openAt(ref, ref.getStore(), playerRef, target);
        }
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type, @Nonnull InteractionContext context,
            @Nullable ItemStack held, @Nonnull World world, @Nonnull Vector3i target) {}
}
