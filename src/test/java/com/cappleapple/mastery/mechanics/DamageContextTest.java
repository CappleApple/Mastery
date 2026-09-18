package com.cappleapple.mastery.mechanics;
import com.cappleapple.mastery.data.DefinitionLoader;
import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DamageContextTest {
    private static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    @Test void filtersCombineAlternativesAndIndependentFields() {
        var context=new DamageContext(Set.of("melee","magic","elemental"),Set.of("mastery:fire"),Set.of("irons_spellbooks:fire_magic"),Set.of("minecraft:is_fire"));
        assertTrue(context.matches(json("{\"categories\":[\"ranged\",\"melee\"],\"elements\":[\"mastery:fire\"]}")));
        assertFalse(context.matches(json("{\"categories\":[\"ranged\"],\"elements\":[\"mastery:fire\"]}")));
        assertFalse(context.matches(json("{\"elements\":[\"mastery:piercing\"]}")));
        assertTrue(context.matches(json("{\"damage_tags\":[\"minecraft:is_fire\"]}")));
        assertFalse(DamageContext.EMPTY.matches(new JsonObject()));
        var mixed=context.merge(new DamageContext(Set.of("physical"),Set.of("mastery:slashing"),Set.of("mastery:slashing"),Set.of()));
        assertTrue(mixed.matches(json("{\"elements\":[\"mastery:slashing\"],\"categories\":[\"magic\"]}")));
        assertFalse(context.elements().contains("mastery:slashing"));
    }
    @Test void schemaRejectsEmptyUnknownAndMalformedFiltersAndKeepsValidFilters() {
        for(String condition:List.of("{\"type\":\"damage\"}","{\"type\":\"damage\",\"categories\":[\"mellee\"]}","{\"type\":\"damage\",\"elements\":[7]}","{\"type\":\"damage\",\"damage_tags\":\"minecraft:is_fire\"}"))assertFalse(load(condition).valid(),condition);
        assertTrue(load("{\"type\":\"damage\",\"categories\":[\"melee\"],\"elements\":[\"test:fire\"]}").valid());
    }
    private static DefinitionLoader.LoadResult load(String condition){return DefinitionLoader.load(Map.of("triggers",Map.of("test:damage",json("{\"event\":\"hit\",\"conditions\":["+condition+"],\"actions\":[{\"type\":\"heal\",\"target\":\"self\",\"amount\":1}]}"))));}
}
