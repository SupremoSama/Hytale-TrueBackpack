package com.supremosan.truebackpack.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.supremosan.truebackpack.config.BackpackConfigService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SetHelipackFuelCommand extends AbstractCommand {

    private static final Logger LOGGER = Logger.getLogger("TrueBackpack");

    private final RequiredArg<String> itemIdArg;
    private final RequiredArg<String> fuelItemIdArg;
    private final RequiredArg<Integer> consumeAmountArg;
    private final RequiredArg<Float> consumeIntervalArg;

    public SetHelipackFuelCommand() {
        super("sethelipackfuel", "Update helipack fuel type and consumption (admin only)");
        this.setPermissionGroup(GameMode.Creative);
        this.itemIdArg = this.withRequiredArg("itemId", "helipack item ID", ArgTypes.STRING);
        this.fuelItemIdArg = this.withRequiredArg("fuelItemId", "fuel item ID", ArgTypes.STRING);
        this.consumeAmountArg = this.withRequiredArg("consumeAmount", "fuel units consumed per interval", ArgTypes.INTEGER);
        this.consumeIntervalArg = this.withRequiredArg("consumeInterval", "seconds between each fuel consumption", ArgTypes.FLOAT);
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        String itemId = itemIdArg.get(context);
        String fuelItemId = fuelItemIdArg.get(context);
        int consumeAmount = consumeAmountArg.get(context);
        float consumeInterval = consumeIntervalArg.get(context);

        if (consumeAmount < 1) {
            context.sendMessage(Message.raw("[TrueBackpack] consumeAmount must be at least 1."));
            return CompletableFuture.completedFuture(null);
        }

        if (consumeInterval <= 0) {
            context.sendMessage(Message.raw("[TrueBackpack] consumeInterval must be greater than 0."));
            return CompletableFuture.completedFuture(null);
        }

        try {
            boolean found = BackpackConfigService.updateHelipackFuel(itemId, fuelItemId, consumeAmount, consumeInterval, LOGGER);

            if (found) {
                context.sendMessage(Message.raw(
                        "[TrueBackpack] Updated '" + itemId + "': fuel=" + fuelItemId
                                + ", amount=" + consumeAmount + ", interval=" + consumeInterval + "s"));
            } else {
                context.sendMessage(Message.raw(
                        "[TrueBackpack] No helipack found with itemId '" + itemId + "'."));
            }
        } catch (Exception e) {
            context.sendMessage(Message.raw("[TrueBackpack] Failed to update helipack fuel — check console."));
            LOGGER.log(Level.SEVERE, "[TrueBackpack] Failed to update helipack fuel for '" + itemId + "'", e);
        }

        return CompletableFuture.completedFuture(null);
    }
}