package com.supremosan.truebackpack.events;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.truebackpack.config.ConfigHelper;
import com.supremosan.truebackpack.util.BackpackReturnUtil;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds backpacks taken from a dying player (keepBackpacksOnDeath) until they respawn. Saved to
 * disk so a restart or disconnect while dead does not lose them.
 */
public final class BackpackDeathKeeper {

    private static final Logger LOGGER = Logger.getLogger("TrueBackpack");
    private static final String DIR = "kept_backpacks";

    public record Kept(int containerIndex, short slot, ItemStack item) {}

    private BackpackDeathKeeper() {
    }

    public static synchronized void stash(@Nonnull String playerUuid, @Nonnull List<Kept> kept) {
        List<Kept> all = new ArrayList<>(load(playerUuid));
        all.addAll(kept);
        BsonArray array = new BsonArray();
        for (Kept k : all) {
            BsonDocument doc = new BsonDocument();
            doc.put("container", new BsonInt32(k.containerIndex()));
            doc.put("slot", new BsonInt32(k.slot()));
            doc.put("item", ItemStack.CODEC.encode(k.item(), ExtraInfo.THREAD_LOCAL.get()));
            array.add(doc);
        }
        try {
            Path file = file(playerUuid);
            Files.createDirectories(file.getParent());
            Files.writeString(file, new BsonDocument("kept", array).toJson(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[TrueBackpack] Failed to save kept backpacks for " + playerUuid, e);
        }
    }

    /** Puts kept backpacks back into their original slots, or the inventory, or at the player's feet. */
    public static synchronized void restore(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        UUIDComponent uuidComp = accessor.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        String playerUuid = uuidComp.getUuid().toString();

        List<Kept> kept = load(playerUuid);
        if (kept.isEmpty()) return;
        try {
            Files.deleteIfExists(file(playerUuid));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[TrueBackpack] Failed to clear kept backpacks for " + playerUuid, e);
            return; // keep the file rather than risk restoring twice
        }

        ItemContainer[] containers = BackpackDeathEvent.playerContainers(ref, accessor);
        for (Kept k : kept) {
            ItemContainer container = k.containerIndex() >= 0 && k.containerIndex() < containers.length
                    ? containers[k.containerIndex()] : null;
            if (container != null && k.slot() >= 0 && k.slot() < container.getCapacity()
                    && ItemStack.isEmpty(container.getItemStack(k.slot()))
                    && container.setItemStackForSlot(k.slot(), k.item()).succeeded()) {
                continue;
            }
            BackpackReturnUtil.giveBack(ref, k.item(), accessor);
        }
    }

    @Nonnull
    private static List<Kept> load(@Nonnull String playerUuid) {
        Path file = file(playerUuid);
        if (!Files.exists(file)) return List.of();
        List<Kept> result = new ArrayList<>();
        try {
            BsonArray array = BsonDocument.parse(Files.readString(file, StandardCharsets.UTF_8)).getArray("kept");
            for (BsonValue value : array) {
                BsonDocument doc = value.asDocument();
                ItemStack item = ItemStack.CODEC.decode(doc.get("item"), ExtraInfo.THREAD_LOCAL.get());
                if (ItemStack.isEmpty(item)) continue;
                result.add(new Kept(doc.getInt32("container").getValue(), (short) doc.getInt32("slot").getValue(), item));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[TrueBackpack] Failed to read kept backpacks for " + playerUuid, e);
        }
        return result;
    }

    @Nonnull
    private static Path file(@Nonnull String playerUuid) {
        return ConfigHelper.getModDirectory().resolve(DIR).resolve(playerUuid + ".json");
    }
}
