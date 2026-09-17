package com.cappleapple.mastery.spells;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpellRulesTest {
    @Test void slotsValidateOwnershipContextAndGlobalCapacity() {
        var loadouts=Map.of("one",List.of("a","","",""),"two",List.of("b","","",""));
        assertFalse(LoadoutRules.validate(loadouts,"one",4096,"c",10,true,true).isEmpty());
        assertFalse(LoadoutRules.validate(loadouts,"one",1,"c",10,false,true).isEmpty());
        assertFalse(LoadoutRules.validate(loadouts,"one",1,"c",10,true,false).isEmpty());
        assertFalse(LoadoutRules.validate(loadouts,"one",1,"a",10,true,true).isEmpty());
        assertFalse(LoadoutRules.validate(loadouts,"one",1,"c",2,true,true).isEmpty());
        assertEquals("",LoadoutRules.validate(loadouts,"one",0,"c",2,true,true));
        assertEquals("",LoadoutRules.validate(loadouts,"one",0,"",0,false,false));
    }
    @Test void purchasedModifierStillNeedsRelationAndSpace() {
        assertFalse(LoadoutRules.modifier(0,2,false,true,false).isEmpty());
        assertFalse(LoadoutRules.modifier(0,2,true,false,false).isEmpty());
        assertFalse(LoadoutRules.modifier(2,2,true,true,false).isEmpty());
        assertEquals("",LoadoutRules.modifier(2,2,true,true,true));
    }
    @Test void nativeModifiersComposeAndRoundCostsUp() {
        var result=new SpellModifiers();
        result.addLevels(2); result.addLevels(1);
        result.multiplyMana(Math.pow(.9,3)); result.multiplyMana(.5);
        result.multiplyCooldown(.8); result.multiplyCastTime(.7);
        assertEquals(3,result.levels()); assertEquals(.3645,result.manaMultiplier(),1e-8);
        assertEquals(22,SpellModifiers.scale(60,result.manaMultiplier()));
        assertEquals(400,SpellModifiers.scale(500,result.cooldownMultiplier()));
        assertEquals(28,SpellModifiers.scale(40,result.castTimeMultiplier()));
    }
    @Test void adjustmentsCannotOverflowOrInvertNativeParameters() {
        var result=new SpellModifiers(); result.addLevels(Integer.MAX_VALUE); result.addLevels(Integer.MAX_VALUE);
        assertEquals(255,result.levels()); result.addLevels(Integer.MIN_VALUE); assertEquals(-255,result.levels());
        result.multiplyMana(0); assertEquals(.01,result.manaMultiplier());
        result.multiplyCooldown(Double.POSITIVE_INFINITY); assertEquals(100,result.cooldownMultiplier());
        assertEquals(Integer.MAX_VALUE,SpellModifiers.scale(Integer.MAX_VALUE,100));
        assertEquals(0,SpellModifiers.scale(0,100));
    }
}
