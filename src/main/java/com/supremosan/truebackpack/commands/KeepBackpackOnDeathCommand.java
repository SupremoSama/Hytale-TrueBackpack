package com.supremosan.truebackpack.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.supremosan.truebackpack.config.settings.TrueBackpackSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/** /backpackkeepondeath [true|false] — shows or sets whether backpacks are kept on death. */
public class KeepBackpackOnDeathCommand extends AbstractCommand {

    private final OptionalArg<Boolean> enabledArg;

    public KeepBackpackOnDeathCommand() {
        super("backpackkeepondeath", "Keep backpacks on death instead of dropping them (admin only)");
        this.requirePermission("truebackpack.command.backpackkeepondeath");
        this.enabledArg = this.withOptionalArg("enabled", "true or false", ArgTypes.BOOLEAN);
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (enabledArg.provided(context)) {
            TrueBackpackSettings.setKeepBackpacksOnDeath(enabledArg.get(context));
        }
        boolean enabled = TrueBackpackSettings.get().keepBackpacksOnDeath;
        context.sendMessage(Message.raw("[TrueBackpack] Keep backpacks on death: " + (enabled ? "ON" : "OFF")));
        return CompletableFuture.completedFuture(null);
    }
}
