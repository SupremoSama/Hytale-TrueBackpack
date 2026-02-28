package com.supremosan.truebackpack.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.builtin.asseteditor.AssetEditorPlugin;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockyModelWrapper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record SlotTransform(
            double px, double py, double pz,
            double ox, double oy, double oz, double ow
    ) {}

    private static final Map<String, SlotTransform> SLOTS = Map.of(
            "BACK",      new SlotTransform(-9.16025, 4.11474, -20.0, -0.59637, -0.37993, -0.59637, 0.37993),
            "HIP_LEFT",  new SlotTransform(28.68653, 0.56218, 13.0, -0.88191, -0.21972, -0.12431, 0.39813),
            "HIP_RIGHT", new SlotTransform(-21.54294, -28.43782, 13.0, -0.85666, -0.21303, 0.32088, 0.34321)
    );

    private static Path generatedDir;
    private static String generatedPrefix;

    private static final ConcurrentHashMap<String, Path> ACTIVE_FILES = new ConcurrentHashMap<>();

    private BlockyModelWrapper() {}

    public static void init(@Nonnull Path outputDir,
                            @Nonnull String assetPrefix) throws IOException {

        generatedDir = outputDir;
        generatedPrefix = assetPrefix.endsWith("/")
                ? assetPrefix.substring(0, assetPrefix.length() - 1)
                : assetPrefix;

        Files.createDirectories(generatedDir);
    }

    @Nullable
    public static String wrap(@Nonnull String playerUuid,
                              @Nonnull String slotKey,
                              @Nonnull String sourceAssetPath) {

        if (generatedDir == null) return null;

        String slotName = extractSlotName(slotKey);
        if (slotName == null) return null;

        SlotTransform transform = SLOTS.get(slotName);
        if (transform == null) return null;

        JsonObject source;
        try {
            source = loadSourceModel(sourceAssetPath);
        } catch (Exception e) {
            return null;
        }

        JsonObject wrapped = buildWrapped(transform, source);

        String fileName = UUID.randomUUID() + ".blockymodel";
        Path outPath = generatedDir.resolve(fileName);

        try {
            Files.writeString(outPath, GSON.toJson(wrapped), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }

        ACTIVE_FILES.put(playerUuid + ":" + slotKey, outPath);

        triggerModelReload();

        return generatedPrefix + "/" + fileName;
    }

    private static void triggerModelReload() {
        AssetUpdateQuery.RebuildCache rebuild = AssetUpdateQuery.RebuildCache.builder().build();
        AssetUpdateQuery query = new AssetUpdateQuery(rebuild);
//        AssetEditorPlugin.get().handleAssetUpdate(query);
    }

    private static JsonObject loadSourceModel(String assetPath) throws IOException {

        InputStream stream = BlockyModelWrapper.class
                .getClassLoader()
                .getResourceAsStream(assetPath);

        if (stream == null) {
            throw new IOException("Asset not found: " + assetPath);
        }

        String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

        try {
            return GSON.fromJson(json, JsonObject.class);
        } catch (JsonParseException e) {
            throw new IOException("Invalid JSON");
        }
    }

    private static JsonObject buildWrapped(SlotTransform t,
                                           JsonObject source) {

        JsonArray sourceNodes = source.getAsJsonArray("nodes").deepCopy();

        JsonObject attachment = new JsonObject();
        attachment.addProperty("id", "1");
        attachment.addProperty("name", "Chest");
        attachment.add("position", vec3(t.px(), t.py(), t.pz()));
        attachment.add("orientation", quat(t.ox(), t.oy(), t.oz(), t.ow()));
        attachment.add("children", sourceNodes);

        JsonArray nodes = new JsonArray();
        nodes.add(attachment);

        JsonObject root = new JsonObject();
        root.add("nodes", nodes);
        root.addProperty("format", "character");
        root.addProperty("lod", "auto");

        return root;
    }

    public static void delete(@Nonnull String playerUuid,
                              @Nonnull String slotKey) {

        Path path = ACTIVE_FILES.remove(playerUuid + ":" + slotKey);
        if (path == null) return;

        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {}
    }

    private static String extractSlotName(String slotKey) {
        if (slotKey.endsWith("BACK")) return "BACK";
        if (slotKey.endsWith("HIP_LEFT")) return "HIP_LEFT";
        if (slotKey.endsWith("HIP_RIGHT")) return "HIP_RIGHT";
        return null;
    }

    private static JsonObject vec3(double x, double y, double z) {
        JsonObject o = new JsonObject();
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        return o;
    }

    private static JsonObject quat(double x, double y, double z, double w) {
        JsonObject o = new JsonObject();
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("w", w);
        return o;
    }
}