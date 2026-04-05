package com.supremosan.truebackpack.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.supremosan.truebackpack.cosmetic.BackpackVisualOverride;
import com.supremosan.truebackpack.listener.BackpackArmorListener;
import com.supremosan.truebackpack.listener.CosmeticListener;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SetBackpackModelCommand extends AbstractCommand {

    private static final Logger LOGGER = Logger.getLogger("TrueBackpack");
    private static final String ATTACHMENT_SLOT_KEY = "truebackpack:backpack";

    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> modelArg;
    private final RequiredArg<String> textureArg;

    public SetBackpackModelCommand() {
        super("setbackpackmodel", "Override the backpack model shown for a specific player (admin only)");
        this.setPermissionGroup(GameMode.Creative);
        this.playerArg = this.withRequiredArg("player", "target player UUID or username", ArgTypes.STRING);
        this.modelArg = this.withRequiredArg("model", "model path", ArgTypes.STRING);
        this.textureArg = this.withRequiredArg("texture", "texture path", ArgTypes.STRING);
        this.addSubCommand(new ClearSub());
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        String playerInput = playerArg.get(context);
        String model = modelArg.get(context);
        String texture = textureArg.get(context);

        PlayerRef target = resolvePlayer(playerInput);
        if (target == null) {
            context.sendMessage(Message.raw("[TrueBackpack] Player not found: " + playerInput));
            return CompletableFuture.completedFuture(null);
        }

        UUID uuid = target.getUuid();
        String uuidStr = uuid.toString();

        BackpackVisualOverride.set(uuid, model, texture);

        if (BackpackArmorListener.hasEquippedBackpack(uuidStr)) {
            ModelAttachment attachment = new ModelAttachment(model, texture, "", "", 1.0);
            CosmeticListener.putAttachment(uuidStr, ATTACHMENT_SLOT_KEY, attachment);
            CosmeticListener.scheduleRebuildForUuid(uuidStr);
        }

        context.sendMessage(Message.raw("[TrueBackpack] Model override set for " + target.getUsername() + " (" + uuidStr + ")."));
        LOGGER.log(Level.INFO, "[TrueBackpack] Model override set for " + uuidStr + ": model=" + model + ", texture=" + texture);
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    private static PlayerRef resolvePlayer(@Nonnull String input) {
        try {
            return Universe.get().getPlayer(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
            return Universe.get().getPlayerByUsername(input, NameMatching.EXACT_IGNORE_CASE);
        }
    }

    private static final class ClearSub extends AbstractCommand {

        private final RequiredArg<String> playerArg;

        private ClearSub() {
            super("clear", "Remove the backpack model override for a player");
            this.setPermissionGroup(GameMode.Creative);
            this.playerArg = this.withRequiredArg("player", "target player UUID or username", ArgTypes.STRING);
        }

        @Override
        protected @NonNull CompletableFuture<Void> execute(@Nonnull CommandContext context) {
            String playerInput = playerArg.get(context);

            PlayerRef target = resolvePlayer(playerInput);
            if (target == null) {
                context.sendMessage(Message.raw("[TrueBackpack] Player not found: " + playerInput));
                return CompletableFuture.completedFuture(null);
            }

            UUID uuid = target.getUuid();
            String uuidStr = uuid.toString();

            BackpackVisualOverride.remove(uuid);

            if (BackpackArmorListener.hasEquippedBackpack(uuidStr)) {
                CosmeticListener.removeAttachment(uuidStr, ATTACHMENT_SLOT_KEY);
                CosmeticListener.scheduleRebuildForUuid(uuidStr);
            }

            context.sendMessage(Message.raw("[TrueBackpack] Model override cleared for " + target.getUsername() + " (" + uuidStr + ")."));
            return CompletableFuture.completedFuture(null);
        }

        @Nullable
        private static PlayerRef resolvePlayer(@Nonnull String input) {
            try {
                return Universe.get().getPlayer(UUID.fromString(input));
            } catch (IllegalArgumentException ignored) {
                return Universe.get().getPlayerByUsername(input, NameMatching.EXACT_IGNORE_CASE);
            }
        }
    }
}