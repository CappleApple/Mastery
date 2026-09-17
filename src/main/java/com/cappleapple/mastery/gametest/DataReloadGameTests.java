package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.MasteryReloadListener;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Files;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class DataReloadGameTests {
    private DataReloadGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void freshEditorFilesOverrideBaseAndInvalidChangesStayAtomic(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var directory = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("mastery-editor/data/mastery/masteryedits/nodes/fire");
        var file = directory.resolve("foundation.json");
        byte[] previous = null;
        boolean inspected = false;
        try {
            previous = Files.exists(file) ? Files.readAllBytes(file) : null;
            inspected = true;
            Files.createDirectories(directory);
            var definition = MasteryRuntime.definitions().toJson().getAsJsonObject("nodes")
                    .getAsJsonObject("mastery:fire/foundation").deepCopy();
            definition.addProperty("name", "Mastery reload fixture");
            Files.writeString(file, definition.toString());
            var resources = server.getResourceManager();
            var recipes = server.getRecipeManager();
            var result = MasteryReloadListener.reloadOnly(server);
            helper.assertTrue(result.valid(), "Live masteryedits override was not accepted: " + result.errors());
            helper.assertTrue(MasteryRuntime.definitions().nodes().get("mastery:fire/foundation").name().equals("Mastery reload fixture"),
                    "Fresh editor file did not override bundled base data");
            helper.assertTrue(resources == server.getResourceManager() && recipes == server.getRecipeManager(),
                    "Mastery-only edit replaced unrelated Minecraft data managers");
            helper.assertTrue(MasteryReloadListener.nodeResourceKind("mastery:fire/foundation").equals("nodes")
                    && MasteryReloadListener.nodeResourceKind("mastery:spellblade_practice").equals("synergies"),
                    "Accepted resource origin kinds were lost");
            var accepted = MasteryRuntime.definitions();
            Files.writeString(file, "{not valid JSON");
            result = MasteryReloadListener.reloadOnly(server);
            helper.assertTrue(!result.valid(), "Malformed live edit was accepted");
            helper.assertTrue(MasteryRuntime.definitions() == accepted, "Failed live edit replaced the accepted snapshot");
        } catch (Exception exception) {
            helper.fail("Live editor reload fixture failed: " + exception.getMessage());
            return;
        } finally {
            if (inspected) try {
                if (previous == null) Files.deleteIfExists(file); else Files.write(file, previous);
                var restored = MasteryReloadListener.reloadOnly(server);
                if (!restored.valid()) helper.fail("Could not restore fixture override: " + restored.errors());
            } catch (Exception exception) { helper.fail("Could not restore fixture file: " + exception.getMessage()); }
        }
        helper.succeed();
    }
}
