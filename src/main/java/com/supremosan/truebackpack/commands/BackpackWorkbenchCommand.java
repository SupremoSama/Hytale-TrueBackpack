package com.supremosan.truebackpack.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.ui.BackpackWorkbenchPage;

import javax.annotation.Nonnull;

/**
 * Command to open the backpack transmog and upgrade workbench menu (/transmog or /backpackworkbench).
 */
public class BackpackWorkbenchCommand extends AbstractPlayerCommand {

    public BackpackWorkbenchCommand() {
        super("transmog", "Open the backpack transmog and upgrade workbench");
        addAliases("backpackworkbench", "bpworkbench", "backpackupgrade", "bptransmog");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        player.getPageManager().openCustomPage(ref, store, new BackpackWorkbenchPage(playerRef));
    }
}
