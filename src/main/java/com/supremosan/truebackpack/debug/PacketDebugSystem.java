package com.supremosan.truebackpack.debug;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InventorySection;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory;
import com.hypixel.hytale.protocol.packets.window.UpdateWindow;
import com.hypixel.hytale.server.core.auth.PlayerAuthentication;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;

import java.util.Map;
import java.util.UUID;

public class PacketDebugSystem implements PacketWatcher {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public void accept(PacketHandler handler, Packet packet) {
        PlayerAuthentication auth = handler.getAuth();
        UUID uuid = (auth != null) ? auth.getUuid() : null;
        String playerInfo = (uuid != null) ? uuid.toString() : "Unknown";

        int id = packet.getId();

        if (id == 160 || id == 161 || id == 162 || id == 241 || id == 131 || id == 136) return;

        LOGGER.atInfo().log("[PacketDebug] ID=%d Type=%s Player=%s", id, packet.getClass().getSimpleName(), playerInfo);

        if (packet instanceof UpdatePlayerInventory p) {
            logSection("armor", p.armor);
            logSection("hotbar", p.hotbar);
            logSection("storage", p.storage);
            logSection("backpack", p.backpack);
        }

        if (packet instanceof UpdateWindow p) {
            LOGGER.atInfo().log("  [UpdateWindow] windowId=%d windowData=%s", p.id, p.windowData);
            logSection("inventory", p.inventory);
        }
    }

    private static void logSection(String name, InventorySection section) {
        if (section == null) {
            LOGGER.atInfo().log("  [%s] null", name);
            return;
        }
        LOGGER.atInfo().log("  [%s] capacity=%d itemCount=%d", name, section.capacity, section.items != null ? section.items.size() : 0);
        if (section.items != null) {
            for (Map.Entry<Integer, ItemWithAllMetadata> entry : section.items.entrySet()) {
                ItemWithAllMetadata item = entry.getValue();
                LOGGER.atInfo().log("    slot=%d itemId=%s qty=%d durability=%.2f maxDurability=%.2f metadata=%s",
                        entry.getKey(),
                        item.itemId,
                        item.quantity,
                        item.durability,
                        item.maxDurability,
                        item.metadata);
            }
        }
    }
}