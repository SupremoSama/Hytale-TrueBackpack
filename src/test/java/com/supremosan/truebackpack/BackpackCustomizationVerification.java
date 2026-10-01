package com.supremosan.truebackpack;

import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.protocol.packets.setup.*;
import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.supremosan.truebackpack.util.BackpackPaintService;
import com.supremosan.truebackpack.util.BackpackProgression;
import org.bson.Document;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

/** Standalone checks against the engine protocol and the shipped backpack assets. */
public final class BackpackCustomizationVerification {
    private static int checks;
    private static final Path RESOURCES = Path.of("src/main/resources");

    public static void main(String[] args) throws Exception {
        try {
            Options.parse(new String[0]);
            verifyProgression();
            verifyPreviewPackets();
            verifyPaintMasks();
            verifyTranslations();
            Files.deleteIfExists(Path.of("build/customization-verification-error.txt"));
            System.out.println("Backpack verification passed: " + checks + " checks.");
        } catch (Throwable failure) {
            var diagnostic = new java.io.StringWriter();
            failure.printStackTrace(new java.io.PrintWriter(diagnostic));
            Files.writeString(Path.of("build/customization-verification-error.txt"), diagnostic.toString());
            throw failure;
        }
    }

    private static void verifyProgression() throws Exception {
        for (String id : new String[]{"Utility_Leather_Big_Backpack", "Utility_Leather_Extra_Big_Backpack"}) {
            var definition = Document.parse(Files.readString(RESOURCES.resolve("Server/Item/Items/" + id + ".json")));
            var config = definition.get("Recipe", Document.class);
            var requirements = config.getList("BenchRequirement", Document.class).stream().map(value -> {
                var requirement = new BenchRequirement();
                requirement.id = value.getString("Id");
                requirement.requiredTierLevel = value.getInteger("RequiredTierLevel");
                return requirement;
            }).toArray(BenchRequirement[]::new);
            var recipe = new CraftingRecipe(new MaterialQuantity[0], null, new MaterialQuantity[0], 1,
                    requirements, 0, false, config.getInteger("RequiredMemoriesLevel"));
            check(!BackpackProgression.canAccessRecipe(recipe, "Backpack_Workbench_Apprentice", 1, 5), id + ": apprentice tier 1");
            check(!BackpackProgression.canAccessRecipe(recipe, "Backpack_Workbench_Apprentice", 2, 4), id + ": memory lock");
            check(BackpackProgression.canAccessRecipe(recipe, "Backpack_Workbench_Apprentice", 2, 5), id + ": upgraded apprentice");
            check(BackpackProgression.canAccessRecipe(recipe, "Backpack_Workbench_Master", 1, 5), id + ": master alternative");
            check(!BackpackProgression.canAccessRecipe(recipe, "Backpack_Workbench_Master", 1, 4), id + ": master memory lock");
            check(!BackpackProgression.canAccessRecipe(recipe, "WrongBench", 9, 9), id + ": unrelated bench");
            check(!BackpackProgression.canAccessRecipe(recipe, null, 9, 9), id + ": absent bench");
        }
        check(BackpackProgression.threshold(5, new int[]{10, 25, 50, 100}) == 100, "native memory threshold");
        check(BackpackProgression.threshold(5, new int[]{20, 40, 80, 160}) == 160, "server memory override");
        check(BackpackProgression.threshold(5, new int[]{10}) == -1, "missing memory threshold fails closed");
    }

    private static void verifyPreviewPackets() {
        var source = new ItemBase();
        source.id = "Utility_Leather_Big_Backpack";
        source.model = "Items/Blocks/Big_Backpack_Block.blockymodel";
        source.texture = "Items/Backpacks/Big_Backpack_Texture.png";
        source.scale = 1;
        var copy = BackpackPaintService.nativePreviewItem(source, "Preview", "Items/Backpacks/Preview/red.png");
        check(copy.model.equals(source.model) && copy.scale == source.scale, "native model and scale retained");
        check(!source.texture.equals(copy.texture) && source.id.equals("Utility_Leather_Big_Backpack"), "source item is immutable");
        check(copy.variant, "preview does not become a separate recipe");
        check(!BackpackPaintService.previewKey(source, "red.png").equals(BackpackPaintService.previewKey(source, "blue.png")), "colors have distinct immutable identities");
        var transfer = BackpackPaintService.livePreviewPackets("Items/Backpacks/Preview/test.png", new byte[]{1, 2, 3});
        check(transfer[0] instanceof AssetInitialize && transfer[transfer.length - 1] instanceof AssetFinalize, "runtime asset framing");
        for (var packet : transfer) check(!(packet instanceof WorldLoadProgress), "no login-only world progress while Playing");
        var prepare = BackpackPaintService.previewDefinitionPackets(copy, true);
        check(prepare.length == 2 && prepare[1] instanceof RequestCommonAssetsRebuild, "single atlas rebuild after private definition");
        check(!((UpdateItems)prepare[0]).updateModels && !((UpdateItems)prepare[0]).updateIcons, "do not prepare meshes before atlas rebuild");
        var publish = BackpackPaintService.previewDefinitionPackets(copy, false);
        check(publish.length == 1 && ((UpdateItems)publish[0]).updateModels, "refresh model data after atlas rebuild without repacking again");
        check(new BackpackPaintService.PreviewSession().remainingDelayMillis() == 0, "resident textures need no atlas delay");
    }

    private static void verifyPaintMasks() throws Exception {
        for (String stem : new String[]{"Side_Backpack_Texture", "Big_Backpack_Texture", "Extra_Big_Backpack_Texture"}) {
            var dir = RESOURCES.resolve("Common/Items/Backpacks");
            var original = ImageIO.read(dir.resolve(stem + ".png").toFile());
            var gray = ImageIO.read(dir.resolve("Paint/" + stem + "_Gray.png").toFile());
            var mask = ImageIO.read(dir.resolve("Paint/" + stem + "_Mask.png").toFile());
            var painted = BackpackPaintService.paint(original, gray, mask, 0x00ffff);
            for (int y = 0; y < original.getHeight(); y++) for (int x = 0; x < original.getWidth(); x++) {
                int expected = original.getRGB(x, y), actual = painted.getRGB(x, y);
                check((expected >>> 24) == (actual >>> 24), stem + ": alpha");
                if ((mask.getRGB(x, y) & 255) == 0) check(expected == actual, stem + ": protected details");
                if (stem.equals("Extra_Big_Backpack_Texture") && x >= original.getWidth() / 2)
                    check(expected == actual, "extra big chest keeps original colors");
            }
        }
        check(BackpackPaintService.neutralShade(30, 20, 10) < BackpackPaintService.neutralShade(80, 50, 30), "leather shadows preserve contrast");
    }

    private static void verifyTranslations() throws Exception {
        try (var locales = Files.list(RESOURCES.resolve("Server/Languages"))) {
            for (var locale : locales.toList()) {
                String text = Files.readString(locale.resolve("server.lang"));
                for (String key : new String[]{"memories.find", "memories.temple", "memories.restore", "personalize.preview_loading"}) {
                    var lines = text.lines().filter(line -> line.startsWith("truebackpack.workbench." + key + "=")).toList();
                    check(lines.size() == 1 && !lines.getFirst().contains("<color"), locale + ": rich tooltip uses plain translated segments");
                }
            }
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
}
