package com.cappleapple.mastery.client.gui.editor;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScriptSchemaTest {
    @Test void triggerAndKeywordFormsExposeEveryCompositionLayer() {
        JsonObject trigger=EditorSchema.fields("triggers","",new JsonObject());
        assertTrue(trigger.has("event"));assertTrue(trigger.get("actions").isJsonArray());
        assertTrue(trigger.get("conditions").isJsonArray());
        JsonObject keyword=EditorSchema.fields("keywords","",new JsonObject());
        assertTrue(keyword.has("tick_actions"));assertTrue(keyword.has("threshold_actions"));
        for(String action:ScriptSchema.ACTIONS) {
            JsonObject card=ScriptSchema.action(action);
            assertEquals(action,card.get("type").getAsString());
            JsonObject form=EditorSchema.fields("triggers","/actions",card);
            assertTrue(form.has("conditions"));assertTrue(form.has("chance"));assertTrue(form.has("radius"));
        }
    }
    @Test void nestedActionConditionsUsePredicateSchemaInsteadOfActionOrRequirementSchema() {
        JsonObject condition=ScriptSchema.condition("keyword");
        JsonObject form=EditorSchema.fields("keywords","/threshold_actions/conditions",condition);
        assertTrue(form.has("keyword"));assertTrue(form.has("min"));assertTrue(form.has("max"));
        assertFalse(form.has("radius"));assertFalse(form.has("tree"));
        assertEquals(List.of("health","keyword"),EditorSchema.choices("keywords","/threshold_actions/conditions/type","type"));
        assertEquals(ScriptSchema.ACTIONS,EditorSchema.choices("triggers","/actions/type","type"));
    }
    @Test void nestedCostLeavesUseCostKindsRatherThanRequirementKinds() {
        JsonObject cost=EditorSchema.entry("/costs/and/or").getAsJsonObject();
        assertEquals("points",cost.get("type").getAsString());
        cost.addProperty("type","experience");
        JsonObject fields=EditorSchema.fields("nodes","/costs/and/or",cost);
        assertTrue(fields.has("amount"));assertTrue(fields.has("depth_percent"));assertFalse(fields.has("spell"));
        assertEquals(List.of("points","experience","item"),EditorSchema.choices("nodes","/costs/and/or/type","type"));
        assertTrue(EditorSchema.fields("settings","",new JsonObject()).has("costs"));
    }
    @Test void typedFormsCoverCraftingAndDamageGroupAuthoring() {
        for(String type:List.of("mastery:crafting_attribute","mastery:crafting_food","mastery:crafting_potion","mastery:placed_comfort")) {
            JsonObject selected=new JsonObject();selected.addProperty("type",type);
            assertTrue(EditorSchema.fields("effects","",selected).has("chance"));
        }
        JsonObject element=EditorSchema.fields("elements","",new JsonObject());
        for(String field:List.of("school","damage_type","damage_modifiers","conversion_attribute","attunement_attribute","potency_attribute","mitigation_attribute"))assertTrue(element.has(field),field);
        assertTrue(EditorSchema.fields("mob_types","",new JsonObject()).get("entities").isJsonArray());
        assertTrue(EditorSchema.fields("weapon_types","",new JsonObject()).has("element"));
    }
}
