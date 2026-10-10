package com.supremosan.truebackpack.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.listener.QuiverListener;
import com.supremosan.truebackpack.cosmetic.CosmeticPreferenceUtils;
import com.supremosan.truebackpack.util.I18nHelper;

import javax.annotation.Nonnull;

/**
 * Quiver visibility. The quiver follows carried arrows rather than an equipment slot; backpack and
 * hat visibility belong to the eye beside their CustomInventory Gear slot.
 */
public class ToggleCosmeticCommand extends AbstractPlayerCommand {

    private static final String KEY_QUIVER_VISIBLE = "server.truebackpack.toggle.quiver.visible";
    private static final String KEY_QUIVER_HIDDEN = "server.truebackpack.toggle.quiver.hidden";
    private static final String KEY_USE_GEAR_EYE = "server.truebackpack.toggle.use_gear_eye";
    private static final String KEY_UNKNOWN_TARGET = "server.truebackpack.toggle.unknown";

    private final RequiredArg<String> targetArg;

    public ToggleCosmeticCommand() {
        super("togglecosmetic", "Toggle quiver cosmetic visibility");
        this.targetArg = this.withRequiredArg("target", "quiver", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String target = this.targetArg.get(context).toLowerCase();

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        String playerUuid = uuidComp.getUuid().toString();

        String language = playerRef.getLanguage();

        switch (target) {
            case "quiver" -> {
                boolean nowVisible = CosmeticPreferenceUtils.toggleQuiver(store, ref);
                // Adds or removes the quiver attachment for the new preference.
                QuiverListener.syncQuiverAttachment(playerUuid, player, store, ref);
                context.sendMessage(Message.raw(I18nHelper.getOrFallback(language,
                        nowVisible ? KEY_QUIVER_VISIBLE : KEY_QUIVER_HIDDEN)));
            }
            case "backpack", "hat" -> context.sendMessage(Message.raw(I18nHelper.getOrFallback(language, KEY_USE_GEAR_EYE)));
            default -> context.sendMessage(Message.raw(I18nHelper.getOrFallback(language, KEY_UNKNOWN_TARGET)));
        }
    }
}
