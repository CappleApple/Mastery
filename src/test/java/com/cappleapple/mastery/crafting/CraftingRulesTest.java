package com.cappleapple.mastery.crafting;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

final class CraftingRulesTest {
    @Test void invalidProbabilitiesAndUnboundedModifiersAreRejected() {
        var errors=new ArrayList<String>();
        CraftingRules.validate(JsonParser.parseString("{\"type\":\"mastery:crafting_food\",\"chance\":1.1,\"nutrition_bonus\":-1}").getAsJsonObject(),errors,"test");
        assertEquals(2,errors.size());
    }
    @Test void schemaRejectsInvalidSlotsIdsAndFractionalAmplifiers() {
        var errors=new ArrayList<String>();
        CraftingRules.validate(JsonParser.parseString("{\"type\":\"mastery:crafting_attribute\",\"attribute\":\"Bad ID\",\"slot\":\"shoes\"}").getAsJsonObject(),errors,"test");
        CraftingRules.validate(JsonParser.parseString("{\"type\":\"mastery:crafting_potion\",\"amplifier_bonus\":0.5}").getAsJsonObject(),errors,"test");
        assertEquals(3,errors.size());
    }
    @Test void chanceAndDurationBoundsAreExplicit() {
        assertEquals(1,CraftingRules.chance(.8,.5));
        assertEquals(0,CraftingRules.chance(.1,-.5));
        assertEquals(300,CraftingRules.duration(200,.5));
        assertEquals(-1,CraftingRules.duration(-1,1));
        assertEquals(1728000,CraftingRules.duration(Integer.MAX_VALUE,100));
    }
    @Test void fractionalFoodStrengthRoundsDownToWholeEffectTiers() {
        assertEquals(0,CraftingRules.strength(0,.25));
        assertEquals(1,CraftingRules.strength(0,1));
        assertEquals(2,CraftingRules.strength(1,.5));
    }
    @Test void malformedTypesAndNumericStringsBecomeValidationErrors() {
        var errors=new ArrayList<String>();
        assertDoesNotThrow(()->CraftingRules.validate(JsonParser.parseString("{\"type\":{}}").getAsJsonObject(),errors,"test"));
        assertFalse(errors.isEmpty());errors.clear();
        CraftingRules.validate(JsonParser.parseString("{\"type\":\"mastery:crafting_food\",\"chance\":\"0.5\",\"nutrition_bonus\":\"0.2\"}").getAsJsonObject(),errors,"test");
        assertEquals(2,errors.size());
    }
}
