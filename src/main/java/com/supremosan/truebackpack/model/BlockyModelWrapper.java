package com.supremosan.truebackpack.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockyModelWrapper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final double CHEST_POS_X = 6.0;
    private static final double CHEST_POS_Y = 26.0;
    private static final double CHEST_POS_Z = 0.0;
    private static final double CHEST_ORI_X = -0.18301;
    private static final double CHEST_ORI_Y = 0.68301;
    private static final double CHEST_ORI_Z = -0.18301;
    private static final double CHEST_ORI_W = 0.68301;

    private static final Map<String, double[]> SLOT_POSITIONS = Map.of(
            "R-Attachment", new double[]{-21.54294, -28.43782, 13.0},
            "B-Attachment", new double[]{-9.16025,   4.11474, -20.0},
            "L-Attachment", new double[]{ 28.68653,   0.56218,  13.0}
    );

    private static final Map<String, double[]> SLOT_ORIENTATIONS = Map.of(
            "R-Attachment", new double[]{-0.85666, -0.21303,  0.32088, 0.34321},
            "B-Attachment", new double[]{-0.59637, -0.37993, -0.59637, 0.37993},
            "L-Attachment", new double[]{-0.88191, -0.21972, -0.12431, 0.39813}
    );

    private static Path tempDir;
    private static final ConcurrentHashMap<String, Path> ACTIVE_FILES = new ConcurrentHashMap<>();

    private BlockyModelWrapper() {
    }

    public static void init(@Nonnull Path baseTempDir) throws IOException {
        tempDir = baseTempDir.resolve("truebackpack_models");
        Files.createDirectories(tempDir);
    }

    @Nullable
    public static String wrap(@Nonnull String playerUuid,
                              @Nonnull String slotKey,
                              @Nonnull String sourceModelPath) {
        String fileKey = playerUuid + ":" + slotKey;
        Path existing = ACTIVE_FILES.get(fileKey);
        if (existing != null) delete(playerUuid, slotKey);

        String boneName = extractBoneName(slotKey);
        if (boneName == null) {
            LOGGER.atWarning().log("[TrueBackpack] Unknown slot key: %s", slotKey);
            return null;
        }

        JsonObject wrapped = buildWrapped(boneName, sourceModelPath);
        String fileName = UUID.randomUUID() + ".blockymodel";
        Path outPath = tempDir.resolve(fileName);

        try {
            Files.writeString(outPath, GSON.toJson(wrapped), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.atWarning().log("[TrueBackpack] Failed to write temp model: %s", e.getMessage());
            return null;
        }

        ACTIVE_FILES.put(fileKey, outPath);
        LOGGER.atInfo().log("[TrueBackpack] Created temp model %s for player=%s slot=%s bone=%s",
                fileName, playerUuid, slotKey, boneName);
        return outPath.toString();
    }

    public static void delete(@Nonnull String playerUuid, @Nonnull String slotKey) {
        String fileKey = playerUuid + ":" + slotKey;
        Path path = ACTIVE_FILES.remove(fileKey);
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
            LOGGER.atInfo().log("[TrueBackpack] Deleted temp model %s", path.getFileName());
        } catch (IOException e) {
            LOGGER.atWarning().log("[TrueBackpack] Failed to delete temp model %s: %s", path, e.getMessage());
        }
    }

    public static void deleteAll(@Nonnull String playerUuid) {
        ACTIVE_FILES.forEach((key, path) -> {
            if (key.startsWith(playerUuid + ":")) {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            }
        });
        ACTIVE_FILES.entrySet().removeIf(e -> e.getKey().startsWith(playerUuid + ":"));
    }

    public static void deleteAllTemp() {
        ACTIVE_FILES.forEach((key, path) -> {
            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
        });
        ACTIVE_FILES.clear();
    }

    @Nonnull
    private static JsonObject buildWrapped(@Nonnull String boneName,
                                           @Nonnull String sourceModelPath) {
        JsonObject chestNode = buildChestNode(boneName, sourceModelPath);

        JsonArray nodes = new JsonArray();
        nodes.add(chestNode);

        JsonObject root = new JsonObject();
        root.add("nodes", nodes);
        root.addProperty("format", "character");
        root.addProperty("lod", "auto");
        return root;
    }

    @Nonnull
    private static JsonObject buildChestNode(@Nonnull String boneName,
                                             @Nonnull String sourceModelPath) {
        JsonObject chest = new JsonObject();
        chest.addProperty("id", "wrapper_chest");
        chest.addProperty("name", "Chest");
        chest.add("position", vec3(CHEST_POS_X, CHEST_POS_Y, CHEST_POS_Z));
        chest.add("orientation", quat(CHEST_ORI_X, CHEST_ORI_Y, CHEST_ORI_Z, CHEST_ORI_W));
        chest.add("shape", emptyShape());

        JsonArray children = new JsonArray();

        for (Map.Entry<String, double[]> entry : SLOT_POSITIONS.entrySet()) {
            String name = entry.getKey();
            double[] pos = entry.getValue();
            double[] ori = SLOT_ORIENTATIONS.get(name);

            JsonObject slotNode = new JsonObject();
            slotNode.addProperty("id", "wrapper_" + name.toLowerCase().replace("-", "_"));
            slotNode.addProperty("name", name);
            slotNode.add("position", vec3(pos[0], pos[1], pos[2]));
            slotNode.add("orientation", quat(ori[0], ori[1], ori[2], ori[3]));
            slotNode.add("shape", emptyShape());

            if (name.equals(boneName)) {
                JsonObject toolNode = buildToolReferenceNode(sourceModelPath);
                JsonArray slotChildren = new JsonArray();
                slotChildren.add(toolNode);
                slotNode.add("children", slotChildren);
            }

            children.add(slotNode);
        }

        chest.add("children", children);
        return chest;
    }

    @Nonnull
    private static JsonObject buildToolReferenceNode(@Nonnull String sourceModelPath) {
        JsonObject node = new JsonObject();
        node.addProperty("id", "tool_mesh");
        node.addProperty("name", "ToolMesh");
        node.add("position", vec3(0, 0, 0));
        node.add("orientation", quat(0, 0, 0, 1));

        JsonObject shape = new JsonObject();
        shape.addProperty("type", "model");
        shape.addProperty("model", sourceModelPath);
        shape.add("offset", vec3(0, 0, 0));
        shape.add("stretch", vec3(1, 1, 1));
        JsonObject settings = new JsonObject();
        settings.addProperty("isPiece", true);
        shape.add("settings", settings);
        shape.add("textureLayout", new JsonObject());
        shape.addProperty("unwrapMode", "custom");
        shape.addProperty("visible", true);
        shape.addProperty("doubleSided", false);
        shape.addProperty("shadingMode", "flat");
        node.add("shape", shape);

        return node;
    }

    @Nullable
    private static String extractBoneName(@Nonnull String slotKey) {
        if (slotKey.endsWith("BACK"))      return "B-Attachment";
        if (slotKey.endsWith("HIP_LEFT"))  return "L-Attachment";
        if (slotKey.endsWith("HIP_RIGHT")) return "R-Attachment";
        return null;
    }

    @Nonnull
    private static JsonObject vec3(double x, double y, double z) {
        JsonObject o = new JsonObject();
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        return o;
    }

    @Nonnull
    private static JsonObject quat(double x, double y, double z, double w) {
        JsonObject o = new JsonObject();
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("w", w);
        return o;
    }

    @Nonnull
    private static JsonObject emptyShape() {
        JsonObject shape = new JsonObject();
        shape.addProperty("type", "none");
        shape.add("offset", vec3(0, 0, 0));
        shape.add("stretch", vec3(1, 1, 1));
        JsonObject settings = new JsonObject();
        settings.addProperty("isPiece", true);
        shape.add("settings", settings);
        shape.add("textureLayout", new JsonObject());
        shape.addProperty("unwrapMode", "custom");
        shape.addProperty("visible", true);
        shape.addProperty("doubleSided", false);
        shape.addProperty("shadingMode", "flat");
        return shape;
    }
}