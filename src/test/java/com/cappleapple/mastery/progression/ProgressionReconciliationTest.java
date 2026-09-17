package com.cappleapple.mastery.progression;

import com.cappleapple.mastery.data.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionReconciliationTest {
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static TreeDefinition tree(String id, List<TierCap> caps) {
        return new TreeDefinition(id, id, "", "", "", 100, 10, 0, 5, Map.of(), "", caps);
    }
    private static NodeDefinition node(String id, String tree, List<Dependency> dependencies, List<JsonObject> effects) {
        return new NodeDefinition(id, tree, id, "", "", NodeType.PASSIVE, dependencies, 3, 1, 0, 0,
                List.of(), effects, "discovered", List.of(), false, "", "");
    }

    @Test void sourceTreesRankCapAppliesToCrossTreeDependency() {
        var bow = tree("test:bow", List.of(new TierCap(0, 100, 1, -1, -1, -1), new TierCap(1, 100, 3, -1, -1, -1)));
        var fire = tree("test:fire", List.of());
        var parent = node("test:parent", bow.id(), List.of(), List.of());
        var child = node("test:child", fire.id(), List.of(new Dependency(parent.id(), 2)), List.of());
        var definitions = new DefinitionSet(Map.of(), Map.of(bow.id(), bow, fire.id(), fire),
                Map.of(parent.id(), parent, child.id(), child), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        PlayerProgress player = new PlayerProgress();
        ProgressionService.setRank(definitions, player, parent.id(), 2, 1);
        ProgressionService.grantPoints(definitions, player, fire.id(), 1);
        assertFalse(ProgressionService.purchase(definitions, player, child.id(), 0, requirement -> true).success());
        assertEquals(1, player.tree(fire.id()).points());
        assertTrue(ProgressionService.purchase(definitions, player, child.id(), 1, requirement -> true).success());
    }

    @Test void reusableUnlockEffectSurvivesReloadReconciliation() {
        var fire = tree("test:fire", List.of());
        var node = node("test:node", fire.id(), List.of(), List.of(json("{\"ref\":\"test:unlock\"}")));
        var spell = new SpellDefinition("irons_spellbooks:fireball", fire.id(), "Fireball", "", "", List.of("test:context"),
                2, 1);
        var definitions = new DefinitionSet(Map.of(), Map.of(fire.id(), fire), Map.of(node.id(), node),
                Map.of(spell.id(), spell), Map.of("test:context", new ContextDefinition("test:context", "Context", 0, new JsonObject())),
                Map.of(), Map.of(), Map.of("test:unlock", json("{\"type\":\"mastery:unlock_spell\",\"spell\":\"irons_spellbooks:fireball\"}")));
        PlayerProgress player = new PlayerProgress();
        player.node(node.id()).rank(1);
        player.loadout("test:context").set(2, spell.id());
        ProgressionService.reconcile(definitions, player);
        assertTrue(ProgressionService.ownsSpell(definitions, player, spell.id()));
        assertEquals(spell.id(), player.loadout("mastery:quick_cast").getFirst());
        assertTrue(player.tree(fire.id()).discovered());
        ProgressionService.reconcile(DefinitionSet.EMPTY, player);
        assertFalse(ProgressionService.ownsSpell(DefinitionSet.EMPTY, player, spell.id()));
        assertTrue(player.loadouts().isEmpty());
        assertTrue(player.nodes().isEmpty());
    }

    @Test void discardedMasteryCooldownsAreNotRestoredOrSaved() {
        var player = PlayerProgress.fromJson(json("{\"version\":1,\"cooldowns\":{\"mastery:fireball\":999},\"loadouts\":{\"test:context\":[\"irons_spellbooks:fireball\"]}}"));
        assertFalse(player.toJson().has("cooldowns"), "Native Iron player data is the sole cooldown owner");
        assertEquals("irons_spellbooks:fireball", player.loadout("test:context").getFirst());
        assertEquals(3, player.toJson().get("version").getAsInt());
    }
}
