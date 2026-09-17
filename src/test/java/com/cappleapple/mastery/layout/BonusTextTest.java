package com.cappleapple.mastery.layout;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BonusTextTest {
    @Test void nativePercentageAndFlatBonusesUseRanks(){
        var effect=JsonParser.parseString("{\"attribute\":\"test:power\",\"amount\":0.05}").getAsJsonObject();
        assertEquals(List.of("+15% Power"),BonusText.describe(effect,3,id->"Power",id->true));
        assertEquals(List.of("+0.15 Power"),BonusText.describe(effect,3,id->"Power",id->false));
    }
    @Test void masteryFractionAttributesDisplayPercentDespiteRangedRegistryType(){
        for(String id:List.of("mastery:elemental_damage","mastery:proc_chance","mastery:holy_weapon_damage","mastery:holy_conversion","mastery:holy_damage","mastery:holy_attunement","mastery:holy_potency","mastery:holy_mitigation")) {
            var effect=JsonParser.parseString("{\"attribute\":\""+id+"\",\"amount\":0.1}").getAsJsonObject();
            assertEquals(List.of("+20% Bonus"),BonusText.describe(effect,2,key->"Bonus",key->false));
        }
        assertFalse(BonusText.masteryFraction("minecraft:generic.attack_damage"));
    }
    @Test void spellModifiersShowCompoundedTotals(){
        var effect=JsonParser.parseString("{\"type\":\"mastery:spell_modifier\",\"mana_multiplier\":0.9,\"spell_level\":1}").getAsJsonObject();
        assertEquals(List.of("+3 Spell Level","-27.1% Mana Cost"),BonusText.describe(effect,3,id->id,id->false));
    }
}
