package com.cappleapple.mastery.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefinitionLoaderTest {
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }

    @Test void malformedFilesProduceIndependentPathDiagnostics() {
        var result = DefinitionLoader.load(Map.of("trees", Map.of(
                "test:bad_integer", json("{\"max_level\":1.5}"),
                "test:bad_number", json("{\"xp_base\":\"many\"}"),
                "test:good", json("{}"))));
        assertFalse(result.valid());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("trees/test:bad_integer: max_level")));
        assertTrue(result.definitions().trees().containsKey("test:good"));
    }

    @Test void snapshotRoundTripKeepsSchemaAndPlacementSection() {
        var result = DefinitionLoader.load(Map.of(
                "trees", Map.of("test:t", json("{\"section\":\"east\",\"point_every\":0,\"point_formula\":\"floor(level/3)\",\"point_milestones\":{\"2\":3}}")),
                "nodes", Map.of("test:n", json("{\"tree\":\"test:t\",\"type\":\"keystone\",\"cost\":2}"))));
        assertTrue(result.valid(), result.errors().toString());
        DefinitionSet copy = DefinitionLoader.fromJson(DefinitionLoader.toJson(result.definitions()));
        assertEquals(DefinitionLoader.toJson(result.definitions()), DefinitionLoader.toJson(copy));
        assertEquals("east", copy.trees().get("test:t").section());
        assertEquals(NodeType.KEYSTONE, copy.nodes().get("test:n").type());
    }

    @Test void stringDependencyAndReusableRequirementShorthandDecode() {
        NodeDefinition node = (NodeDefinition) DefinitionLoader.parse("nodes", "test:n",
                json("{\"tree\":\"test:t\",\"dependencies\":[\"test:parent\"],\"requirements\":[\"test:requirement\"]}"));
        assertEquals(new Dependency("test:parent", 1), node.dependencies().getFirst());
        assertEquals("test:requirement", node.requirements().getFirst().get("ref").getAsString());
    }

    @Test void duplicateSynergyNodeIdsAreRejected() {
        JsonObject node = json("{\"tree\":\"test:t\"}");
        var result = DefinitionLoader.load(Map.of("trees", Map.of("test:t", json("{}")),
                "nodes", Map.of("test:n", node), "synergies", Map.of("test:n", node)));
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("duplicate node ID")));
    }

    @Test void allEightPlacementSectionsAreAccepted() {
        for (String section : java.util.List.of("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")) {
            JsonObject tree = new JsonObject(); tree.addProperty("section", section);
            var result = DefinitionLoader.load(Map.of("trees", Map.of("test:t", tree)));
            assertTrue(result.valid(), result.errors().toString());
            assertEquals(section, result.definitions().trees().get("test:t").section());
        }
    }

    @Test void invalidPlacementSectionIsRejected() {
        var result = DefinitionLoader.load(Map.of("trees", Map.of("test:t", json("{\"section\":\"nowhere\"}"))));
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("section must be")));
    }

    @Test void defaultTreeAwardsOnePointAtEveryNewLevel() {
        var result = DefinitionLoader.load(Map.of("trees", Map.of("test:tree", json("{}")),"nodes",Map.of("test:ranks",json("{\"tree\":\"test:tree\",\"max_rank\":2}"))));
        var tree = result.definitions().trees().get("test:tree");
        assertEquals(1, tree.pointEvery());
        var player = new com.cappleapple.mastery.progression.PlayerProgress();
        var first = com.cappleapple.mastery.progression.ProgressionService.addXp(result.definitions(), player, tree.id(), 100, -1);
        assertEquals(1, first.points());
        assertEquals(1, first.levels());
        assertTrue(player.tree(tree.id()).discovered());
        var second = com.cappleapple.mastery.progression.ProgressionService.addXp(result.definitions(), player, tree.id(), 120, -1);
        assertEquals(1, second.points());
        assertEquals(2, player.tree(tree.id()).points());
    }

    @Test void nativeSpellBindingRequiresExactNativeResourceId() {
        JsonObject binding = json("{\"spell\":\"irons_spellbooks:fireball\",\"tree\":\"test:fire\"}");
        var spell = (SpellDefinition) DefinitionLoader.parse("spells", "irons_spellbooks:fireball", binding);
        assertEquals("irons_spellbooks:fireball", spell.id());
        assertEquals(1, spell.level());
        assertThrows(com.google.gson.JsonParseException.class, () -> DefinitionLoader.parse("spells", "mastery:new_fireball", binding));
        binding.remove("spell");
        assertThrows(com.google.gson.JsonParseException.class, () -> DefinitionLoader.parse("spells", "irons_spellbooks:fireball", binding));
    }

    @Test void customCastingFieldsAndFormerAbilitySchemaAreRejected() {
        for (String field : java.util.List.of("behavior", "charge", "cooldown", "parameters")) {
            JsonObject binding = json("{\"spell\":\"irons_spellbooks:fireball\",\"tree\":\"test:fire\"}");
            binding.addProperty(field, "unsupported");
            var failure = assertThrows(com.google.gson.JsonParseException.class,
                    () -> DefinitionLoader.parse("spells", "irons_spellbooks:fireball", binding));
            assertTrue(failure.getMessage().contains(field));
        }
        assertThrows(com.google.gson.JsonParseException.class, () -> DefinitionLoader.parse("abilities", "test:old", json("{}")));
        assertThrows(com.google.gson.JsonParseException.class, () -> DefinitionLoader.parse("nodes", "test:old",
                json("{\"tree\":\"test:fire\",\"ability\":\"mastery:custom\"}")));
    }

    @Test void disabledResourceOverridesOmitDefinitionsButStillValidateReferences() {
        var disabled = json("{\"disabled\":true}");
        var empty = DefinitionLoader.load(Map.of("nodes", Map.of("test:removed", disabled)));
        assertTrue(empty.valid(), empty.errors().toString());
        assertTrue(empty.definitions().nodes().isEmpty());
        var referenced = DefinitionLoader.load(Map.of("trees", Map.of("test:tree", json("{}")), "nodes", Map.of(
                "test:removed", disabled, "test:child", json("{\"tree\":\"test:tree\",\"dependencies\":[\"test:removed\"]}"))));
        assertFalse(referenced.valid());
        assertTrue(referenced.errors().stream().anyMatch(error -> error.contains("missing dependency node test:removed")));
    }

    @Test void bundledDemonstrationDatapackValidatesAndRoundTrips() throws IOException {
        Map<String, Map<String, JsonObject>> resources = new LinkedHashMap<>();
        Path root = Path.of(System.getProperty("mastery.projectDir", "."), "src/main/resources/data");
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                Path relative = root.relativize(file);
                if (relative.getNameCount() < 4 || !relative.getName(1).toString().equals("mastery")) continue;
                String kind = relative.getName(2).toString();
                if (!DefinitionLoader.KINDS.contains(kind)) continue;
                String local = relative.subpath(3, relative.getNameCount()).toString().replace('\\', '/');
                String id = relative.getName(0) + ":" + local.substring(0, local.length() - 5);
                resources.computeIfAbsent(kind, ignored -> new LinkedHashMap<>()).put(id, json(Files.readString(file)));
            }
        }
        var result = DefinitionLoader.load(resources);
        assertTrue(result.valid(), String.join("\n", result.errors()));
        assertEquals(22, result.definitions().trees().size());
        assertEquals(resources.get("nodes").size()+resources.get("synergies").size(), result.definitions().nodes().size());
        for(String id:java.util.List.of("mastery:fire/scorch","mastery:lightning/thunder")) {
            assertEquals(NodeType.MODIFIER,result.definitions().nodes().get(id).type());
            assertEquals("mastery:native_spell",result.definitions().nodes().get(id).modifier());
            assertFalse(result.definitions().nodes().get(id).spell().isBlank());
            assertTrue(result.definitions().nodes().get(id).dependencyLeaves().stream().anyMatch(dep->result.definitions().nodes().get(dep.node()).type()==NodeType.ACTIVE));
            assertEquals(NodeAppearance.Shape.TRIANGLE,NodeAppearance.parse(SettingsResolver.forNode(result.definitions(),id).getAsJsonObject("appearance")).shape());
        }
        assertEquals(100,UnlockPresentation.DEFAULTS.holdDelayMs());
        assertEquals(100,UnlockPresentation.parse(SettingsResolver.forNode(result.definitions(),"mastery:fire/foundation").getAsJsonObject("unlock")).holdDelayMs());
        assertTrue(result.definitions().nodes().containsKey("mastery:holy/attunement"));
        assertEquals(10, result.definitions().spells().size());
        assertEquals("mastery:flame_blade",result.definitions().nodes().get("mastery:flame_blade/flaming_strike").tree());
        assertTrue(PromotedTrees.inherits(result.definitions(),"mastery:flame_blade","mastery:fire"));
        assertTrue(PromotedTrees.inherits(result.definitions(),"mastery:flame_blade","mastery:two_handed"));
        assertFalse(PromotedTrees.inherits(result.definitions(),"mastery:flame_blade","mastery:lightning"));
        assertTrue(result.definitions().groups().isEmpty(), "Fresh trees are independent and discovered through point grants");
        assertTrue(result.definitions().trees().values().stream().allMatch(tree -> tree.parent().isEmpty()));
        assertTrue(result.definitions().trees().values().stream().allMatch(tree -> tree.pointEvery() == 1));
        assertTrue(result.definitions().spells().keySet().stream().allMatch(id -> id.startsWith("irons_spellbooks:")));
        assertEquals(10, result.definitions().xpSources().values().stream().filter(source -> source.condition().has("school")
                && source.condition().get("school").getAsString().startsWith("irons_spellbooks:")).count());
        for (String school : java.util.List.of("fire", "ice", "lightning", "holy", "ender", "blood", "evocation", "nature", "eldritch")) {
            var source = result.definitions().xpSources().get("mastery:" + school + "_damage");
            assertEquals("damage", source.event());
            assertEquals("damage", source.scale());
            assertEquals("irons_spellbooks:" + school, source.condition().get("school").getAsString());
            assertEquals(1, source.condition().size(), "Schools classify only native damage school, never held items or generic fire tags");
        }
        assertTrue(result.definitions().contexts().get("mastery:two_handed").condition().get("provider_only").getAsBoolean());
        assertFalse(Files.exists(root.resolve("mastery/tags/item/two_handed_weapons.json")));
        assertFalse(Files.exists(root.resolve("mastery/tags/item/wands.json")));
        assertEquals(DefinitionLoader.toJson(result.definitions()), DefinitionLoader.toJson(
                DefinitionLoader.fromJson(DefinitionLoader.toJson(result.definitions()))));
    }
}
