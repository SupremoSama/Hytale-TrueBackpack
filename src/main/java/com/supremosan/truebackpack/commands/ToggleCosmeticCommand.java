package com.supremosan.truebackpack.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.listener.BackpackArmorListener;
import com.supremosan.truebackpack.listener.QuiverListener;
import com.supremosan.truebackpack.listener.CosmeticListener;
import com.supremosan.truebackpack.cosmetic.CosmeticPreferenceUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ToggleCosmeticCommand extends AbstractPlayerCommand {

    private static final String KEY_BACKPACK_VISIBLE = "server.truebackpack.toggle.backpack.visible";
    private static final String KEY_BACKPACK_HIDDEN  = "server.truebackpack.toggle.backpack.hidden";
    private static final String KEY_QUIVER_VISIBLE   = "server.truebackpack.toggle.quiver.visible";
    private static final String KEY_QUIVER_HIDDEN    = "server.truebackpack.toggle.quiver.hidden";
    private static final String KEY_UNKNOWN_TARGET   = "server.truebackpack.toggle.unknown";

    private final RequiredArg<String> targetArg;

    public ToggleCosmeticCommand() {
        super("togglecosmetic", "Toggle backpack or quiver cosmetic visibility");
        this.setPermissionGroup(GameMode.Adventure);
        this.targetArg = this.withRequiredArg("target", "backpack or quiver", ArgTypes.STRING);
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
            case "backpack" -> {
                boolean nowVisible = CosmeticPreferenceUtils.toggleBackpack(store, ref);
                if (nowVisible) {
                    BackpackArmorListener.syncBackpackAttachment(playerUuid, player, store, ref);
                } else {
                    CosmeticListener.removeAttachment(playerUuid, "truebackpack:backpack");
                }
                CosmeticListener.scheduleRebuild(player, store, ref, playerUuid);
                context.sendMessage(Message.raw(resolve(language,
                        nowVisible ? KEY_BACKPACK_VISIBLE : KEY_BACKPACK_HIDDEN)));
            }
            case "quiver" -> {
                boolean nowVisible = CosmeticPreferenceUtils.toggleQuiver(store, ref);
                if (nowVisible) {
                    QuiverListener.syncQuiverAttachment(playerUuid, player, store, ref);
                } else {
                    CosmeticListener.removeAttachment(playerUuid, "truebackpack:quiver");
                }
                CosmeticListener.scheduleRebuild(player, store, ref, playerUuid);
                context.sendMessage(Message.raw(resolve(language,
                        nowVisible ? KEY_QUIVER_VISIBLE : KEY_QUIVER_HIDDEN)));
            }
            default -> context.sendMessage(Message.raw(resolve(language, KEY_UNKNOWN_TARGET)));
        }
    }

    @Nonnull
    private static String resolve(@Nullable String language, @Nonnull String key) {
        I18nModule i18n = I18nModule.get();
        if (i18n != null) {
            try {
                String value = i18n.getMessage(language, key);
                if (value != null && !value.isBlank() && !value.equals(key)) {
                    return value;
                }
            } catch (Exception ignored) {}
        }
        return fallback(key);
    }

    @Nonnull
    private static String fallback(@Nonnull String key) {
        String[] parts = key.split("\\.");
        String last = parts[parts.length - 1];
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }
}