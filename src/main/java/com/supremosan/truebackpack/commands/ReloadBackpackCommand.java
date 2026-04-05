package com.supremosan.truebackpack.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.supremosan.truebackpack.config.BackpackConfigService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReloadBackpackCommand extends AbstractCommand {

    private static final Logger LOGGER = Logger.getLogger("TrueBackpack");

    public ReloadBackpackCommand() {
        super("reloadbackpack", "Reload the backpack configuration from file");
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        try {
            BackpackConfigService.reloadAndRegister(LOGGER);
            context.sendMessage(Message.raw("[TrueBackpack] Config reloaded successfully."));
            LOGGER.log(Level.INFO, "[TrueBackpack] Config reloaded via command.");
        } catch (Exception e) {
            context.sendMessage(Message.raw("[TrueBackpack] Reload failed — check console for details."));
            LOGGER.log(Level.SEVERE, "[TrueBackpack] Reload failed", e);
        }
        return CompletableFuture.completedFuture(null);
    }
}