package com.cappleapple.mastery.client.gui.editor;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EditorSchemaTest {
    @Test void formsCoverChargeInheritanceRanksAndNestedLogic(){
        var empty=new JsonObject();
        for(String kind:java.util.List.of("trees","nodes","spells","settings"))for(String field:java.util.List.of("theme","unlock","charge","modifier_slots"))assertTrue(EditorSchema.fields(kind,"",empty).has(field));
        var bonus=EditorSchema.fields("effects","",JsonParser.parseString("{\"type\":\"mastery:bonus\"}").getAsJsonObject());assertTrue(bonus.has("key"));assertTrue(bonus.has("spell"));assertFalse(bonus.has("stat"));
        var node=EditorSchema.fields("nodes","",empty);assertTrue(node.has("dependencies"));assertTrue(node.has("book_token"));assertTrue(node.has("max_rank"));
        assertTrue(EditorSchema.fields("nodes","/requirements/and",empty).has("not"));
        assertTrue(EditorSchema.fields("nodes","/effects/condition",empty).has("min_damage"));
        assertEquals(1,EditorSchema.entry("dependencies").getAsJsonObject().get("rank").getAsInt());
        assertTrue(EditorSchema.fields("spells","/charge",empty).has("extra_casts_per_stage"));
    }
    @Test void jsonColorsSeparateKeysEscapedStringsNumbersAndBooleans(){
        String json="{\"name\":\"a\\\"b\",\"amount\":1.5,\"enabled\":true}";
        var tokens=JsonSyntax.tokenize(json);
        assertTrue(tokens.stream().anyMatch(t->json.substring(t.start(),t.end()).equals("\"name\"")&&t.color()==0x9CDCFE));
        assertTrue(tokens.stream().anyMatch(t->json.substring(t.start(),t.end()).equals("\"a\\\"b\"")&&t.color()==0xCE9178));
        assertTrue(tokens.stream().anyMatch(t->json.substring(t.start(),t.end()).equals("1.5")&&t.color()==0xB5CEA8));
        assertTrue(tokens.stream().anyMatch(t->json.substring(t.start(),t.end()).equals("true")&&t.color()==0x569CD6));
    }
}
