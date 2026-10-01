package com.supremosan.truebackpack.util;

import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.supremosan.truebackpack.factory.BackpackItemFactory;
import com.supremosan.truebackpack.registries.BackpackRegistry;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.CompletableFuture;

/** Paints the leather layer only; neutral hardware and golden trim retain their original pixels. */
public final class BackpackPaintService {
    private BackpackPaintService() {}
    public static boolean canPaint(ItemStack stack) {
        var base = BackpackRegistry.getByItem(stack.getItemId());
        var visual = visualEntry(stack);
        return eligible(base) && eligible(visual);
    }
    private static boolean eligible(BackpackRegistry.BackpackEntry entry) {
        return entry != null && !entry.isHelipack() && !entry.itemId().equalsIgnoreCase("Utility_Fibre_Side_Bag");
    }
    public static BackpackRegistry.BackpackEntry visualEntry(ItemStack stack) {
        var base = BackpackRegistry.getByItem(stack.getItemId());
        if (base == null || base.isHelipack()) return base;
        var skin = BackpackRegistry.getByItem(BackpackItemFactory.getTransmogSkin(stack));
        return skin != null && !skin.isHelipack() ? skin : base;
    }
    public static synchronized String texture(ItemStack stack) {
        var entry = visualEntry(stack);
        if (entry == null) return null;
        String color = BackpackItemFactory.getPaintColor(stack);
        return color == null || !canPaint(stack) ? entry.texture() : texture(entry.texture(), color);
    }
    public static synchronized String texture(String original, String color) {
        String rgb = BackpackItemFactory.normalizeColor(color);
        if (rgb == null) return original;
        var source = CommonAssetRegistry.getByName(original);
        if (source == null) throw new IllegalStateException("Missing backpack texture: " + original);
        // Source hash invalidates painted textures automatically when an asset pack changes the atlas.
        String name = "Items/Backpacks/Painted/" + source.getHash() + "_v4_" + rgb.substring(1) + ".png";
        if (CommonAssetRegistry.getByName(name) != null) return name;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source.getBlob().join()));
            if (image == null) throw new IOException("Not a PNG: " + original);
            var layers = bundledLayers(original, image);
            BufferedImage painted = paint(image, layers[0], layers[1], Integer.parseInt(rgb.substring(1), 16));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(painted, "png", output);
            CommonAssetModule.get().addCommonAsset("TrueBackpack", new PaintedAsset(name, output.toByteArray()), false);
            com.hypixel.hytale.server.core.universe.Universe.get().broadcastPacketNoCache(
                    new com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild());
            return name;
        } catch (IOException e) { throw new IllegalStateException("Could not paint backpack", e); }
    }
    /** Page-local definitions and textures are immutable while asynchronous client renderers use them. */
    public static final class PreviewSession {
        // Common-assets rebuilds have no completion acknowledgement in the protocol.
        // Keep the UI renderer absent while the client repacks its entity texture atlas.
        private static final long ATLAS_SETTLE_MILLIS = 2500;
        private final String prefix = "TrueBackpack_UI_Preview_" + java.util.UUID.randomUUID().toString().replace("-", "") + "_";
        private final java.util.Map<String, com.hypixel.hytale.protocol.ItemBase> items = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, com.hypixel.hytale.protocol.Asset> assets = new java.util.LinkedHashMap<>();
        private long atlasReadyAfter;

        public String prepare(ItemStack stack, com.hypixel.hytale.server.core.universe.PlayerRef player) throws IOException {
            var entry = visualEntry(stack);
            if (entry == null) throw new IOException("Missing backpack appearance");
            var sourceItem = com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(entry.itemId());
            if (sourceItem == null) throw new IOException("Missing preview item " + entry.itemId());
            String texturePath = entry.texture();
            String color = BackpackItemFactory.getPaintColor(stack);
            // Native appearances require no private asset definition or atlas rebuild.
            if (!canPaint(stack) || color == null) return entry.itemId();
            boolean uploadedTexture = false;
            if (canPaint(stack) && color != null) {
                var source = CommonAssetRegistry.getByName(entry.texture());
                if (source == null) throw new IOException("Missing preview texture");
                BufferedImage original = ImageIO.read(new ByteArrayInputStream(source.getBlob().join()));
                if (original == null) throw new IOException("Invalid preview texture");
                var layers = bundledLayers(entry.texture(), original);
                var painted = paint(original, layers[0], layers[1], Integer.parseInt(color.substring(1), 16));
                var output = new ByteArrayOutputStream();
                ImageIO.write(painted, "png", output);
                byte[] bytes = output.toByteArray();
                String hash = new PaintedAsset("Preview.png", bytes).getHash();
                texturePath = "Items/Backpacks/Preview/" + prefix + hash + ".png";
                if (!assets.containsKey(texturePath)) {
                    player.getPacketHandler().write(livePreviewPackets(texturePath, bytes));
                    assets.put(texturePath, new PaintedAsset(texturePath, bytes).toPacket());
                    uploadedTexture = true;
                }
            }
            var source = sourceItem.toPacket();
            String id = prefix + previewKey(source, texturePath);
            if (!items.containsKey(id)) {
                // New PNGs also need the entity texture atlas rebuilt. Queue the definition
                // without rebuilding models, then rebuild common assets once both are present.
                var item = nativePreviewItem(source, id, texturePath);
                player.getPacketHandler().write(previewDefinitionPackets(item, uploadedTexture));
                items.put(id, item);
                if (uploadedTexture) waitForAtlasRebuild();
            }
            return id;
        }

        public long remainingDelayMillis() {
            return Math.max(0, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(atlasReadyAfter - System.nanoTime()));
        }

        public void waitForAtlasRebuild() {
            atlasReadyAfter = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(ATLAS_SETTLE_MILLIS);
        }

        /** Recreate native model data with the final atlas UVs before attaching a fresh UI renderer. */
        public void publish(String id, com.hypixel.hytale.server.core.universe.PlayerRef player) {
            var item = items.get(id);
            if (item != null) player.getPacketHandler().write(previewDefinitionPackets(item, false));
        }

        public void close(com.hypixel.hytale.server.core.universe.PlayerRef player) {
            if (!items.isEmpty()) player.getPacketHandler().write(new com.hypixel.hytale.protocol.packets.assets.UpdateItems(
                    com.hypixel.hytale.protocol.UpdateType.Remove, null, items.keySet().toArray(String[]::new), true, false));
            if (!assets.isEmpty()) player.getPacketHandler().write(new com.hypixel.hytale.protocol.packets.setup.RemoveAssets(
                    assets.values().toArray(com.hypixel.hytale.protocol.Asset[]::new)));
            items.clear();
            assets.clear();
        }
    }
    public static String previewKey(com.hypixel.hytale.protocol.ItemBase source, String texture) {
        String identity = source.id + "\n" + source.model + "\n" + source.scale + "\n" + texture;
        return java.util.UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "");
    }
    public static com.hypixel.hytale.protocol.ToClientPacket[] previewDefinitionPackets(
            com.hypixel.hytale.protocol.ItemBase item, boolean uploadedTexture) {
        var definition = new com.hypixel.hytale.protocol.packets.assets.UpdateItems(
                com.hypixel.hytale.protocol.UpdateType.AddOrUpdate, java.util.Map.of(item.id, item), null, !uploadedTexture, false);
        return uploadedTexture
                ? new com.hypixel.hytale.protocol.ToClientPacket[]{definition, new com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild()}
                : new com.hypixel.hytale.protocol.ToClientPacket[]{definition};
    }
    /** Copy the native item definition; never mutate its cached asset packet or create inventory items. */
    public static com.hypixel.hytale.protocol.ItemBase nativePreviewItem(com.hypixel.hytale.protocol.ItemBase source, String id, String texture) {
        var item = new com.hypixel.hytale.protocol.ItemBase(source);
        item.id = id;
        item.texture = texture;
        item.variant = true;
        item.categories = new String[0];
        return item;
    }
    /** The setup transfer helper emits WorldLoadProgress and must never be used while Playing. */
    public static com.hypixel.hytale.protocol.ToClientPacket[] livePreviewPackets(String name, byte[] bytes) {
        var asset = new PaintedAsset(name, bytes);
        byte[][] parts = com.hypixel.hytale.common.util.ArrayUtil.split(bytes, CommonAssetModule.MAX_FRAME);
        var packets = new com.hypixel.hytale.protocol.ToClientPacket[parts.length + 2];
        packets[0] = new com.hypixel.hytale.protocol.packets.setup.AssetInitialize(asset.toPacket(), bytes.length);
        for (int i = 0; i < parts.length; i++) packets[i + 1] = new com.hypixel.hytale.protocol.packets.setup.AssetPart(parts[i]);
        packets[parts.length + 1] = new com.hypixel.hytale.protocol.packets.setup.AssetFinalize();
        return packets;
    }
    private static BufferedImage[] bundledLayers(String texture, BufferedImage image) throws IOException {
        String stem = texture.substring(texture.lastIndexOf('/') + 1).replace(".png", "");
        String prefix = "/Common/Items/Backpacks/Paint/" + stem;
        try (InputStream grayFile = BackpackPaintService.class.getResourceAsStream(prefix + "_Gray.png");
             InputStream maskFile = BackpackPaintService.class.getResourceAsStream(prefix + "_Mask.png")) {
            if (grayFile != null && maskFile != null) {
                BufferedImage gray = ImageIO.read(grayFile), mask = ImageIO.read(maskFile);
                if (gray != null && mask != null && gray.getWidth() == image.getWidth() && gray.getHeight() == image.getHeight()
                        && mask.getWidth() == image.getWidth() && mask.getHeight() == image.getHeight()) return new BufferedImage[]{gray, mask};
            }
        }
        // Custom registered backpacks can use their own atlas without adding paint assets.
        return layers(image);
    }
    /** Grayscale leather base and binary paint mask. The original atlas remains the detail layer. */
    public static BufferedImage[] layers(BufferedImage original) {
        int w = original.getWidth(), h = original.getHeight();
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int pixel = original.getRGB(x, y), a = pixel >>> 24;
            int r = pixel >>> 16 & 255, g = pixel >>> 8 & 255, b = pixel & 255;
            // Leather is warm brown. Exclude achromatic hardware and bright yellow/gold stitching.
            boolean leather = a > 0 && r > g * 1.08 && g > b * 1.08 && !(r > 150 && g > r * .72 && b < g * .65);
            int shade = neutralShade(r, g, b);
            gray.setRGB(x, y, a << 24 | shade << 16 | shade << 8 | shade);
            mask.setRGB(x, y, 0xff000000 | (leather ? 0xffffff : 0));
        }
        return new BufferedImage[]{gray, mask};
    }
    /** Lift brown albedo without a brightness floor, so seams and folds retain their contrast. */
    public static int neutralShade(int r, int g, int b) {
        double luminance = r * .2126 + g * .7152 + b * .0722;
        return (int)Math.round(255 * Math.pow(Math.min(1, luminance / 160), .75));
    }
    public static BufferedImage paint(BufferedImage detail, BufferedImage gray, BufferedImage mask, int rgb) {
        int w = detail.getWidth(), h = detail.getHeight();
        if (gray.getWidth() != w || gray.getHeight() != h || mask.getWidth() != w || mask.getHeight() != h)
            throw new IllegalArgumentException("Paint layers must match the texture size");
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = detail.getRGB(x, y), shade = gray.getRGB(x, y) & 255, amount = mask.getRGB(x, y) & 255;
            int out = p & 0xff000000;
            for (int shift = 16; shift >= 0; shift -= 8) {
                int tinted = ((rgb >>> shift & 255) * shade + 127) / 255;
                int channel = ((p >>> shift & 255) * (255 - amount) + tinted * amount + 127) / 255;
                out |= channel << shift;
            }
            result.setRGB(x, y, out);
        }
        return result;
    }
    private static final class PaintedAsset extends CommonAsset {
        private final byte[] bytes;
        private PaintedAsset(String name, byte[] bytes) { super(name, bytes); this.bytes = bytes; }
        @Override protected CompletableFuture<byte[]> getBlob0() { return CompletableFuture.completedFuture(bytes); }
    }
}
