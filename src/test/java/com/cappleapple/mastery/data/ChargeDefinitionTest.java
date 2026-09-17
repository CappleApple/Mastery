package com.cappleapple.mastery.data;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChargeDefinitionTest {
    @Test void chargeWindowsIncreaseLinearlyAndClampToTheConfiguredRankLimit() {
        var d=ChargeDefinition.defaults("irons_spellbooks:fireball");
        assertEquals(20,d.extraTicks(1));assertEquals(60,d.extraTicks(3));assertEquals(320,d.extraTicks(1000));
        assertEquals(0,d.stages(39,40,3));assertEquals(1.5,d.stages(70,40,3));assertEquals(3,d.stages(200,40,3));
        assertEquals(0,d.spellLevelsPerStage(),"Base native levels are independent from charge ranks");
    }
    @Test void instantSpellTimingAndBurstScalingAreDatapackFields() {
        var json=new JsonObject();json.addProperty("instant_base_ticks",15);json.addProperty("extra_casts_per_stage",2);
        var d=ChargeDefinition.parse("irons_spellbooks:firebolt",json);
        assertTrue(d.enabled());assertEquals(15,d.instantBaseTicks());assertEquals(4,d.repeats(2));
        assertEquals(64,d.repeats(10000));
        json.addProperty("ticks_per_level",0);assertThrows(IllegalArgumentException.class,()->ChargeDefinition.parse("irons_spellbooks:firebolt",json));
    }
}
