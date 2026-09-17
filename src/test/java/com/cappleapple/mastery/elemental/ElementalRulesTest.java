package com.cappleapple.mastery.elemental;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ElementalRulesTest {
    @Test void bonusAndConversionHaveDifferentBudgets() {
        assertEquals(0.8,ElementalRules.physical(0.2),1e-9);
        assertEquals(0.2,ElementalRules.conversion(0.2,0.2),1e-9);
        assertEquals(1.56,ElementalRules.multiplier(0.2,0.3),1e-9);
    }
    @Test void overAllocatedConversionNormalizesWithoutExtraBaseDamage() {
        assertEquals(0,ElementalRules.physical(1.5));
        assertEquals(2.0/3,ElementalRules.conversion(1,1.5),1e-9);
        assertEquals(1.0/3,ElementalRules.conversion(.5,1.5),1e-9);
        assertEquals(1,ElementalRules.conversion(1,1.5)+ElementalRules.conversion(.5,1.5),1e-9);
    }
    @Test void attunementAmplifiesBothSidesAndHealingInvertsThem() {
        assertEquals(1.3,ElementalRules.matchup(.25,.2,false),1e-9);
        assertEquals(.7,ElementalRules.matchup(-.25,.2,false),1e-9);
        assertEquals(1.3,ElementalRules.matchup(-.25,.2,true),1e-9);
        assertEquals(.7,ElementalRules.matchup(.25,.2,true),1e-9);
        assertEquals(0,ElementalRules.matchup(-.75,1,false),1e-9);
    }
    @Test void potencyAndMitigationOnlyModifyTheirOwnSide() {
        assertEquals(.6,ElementalRules.matchupDelta(.25,.2,1,.5),1e-9);
        assertEquals(-.15,ElementalRules.matchupDelta(-.25,.2,1,.5),1e-9);
        assertEquals(0,ElementalRules.matchupDelta(-.25,.2,0,1),1e-9);
    }
    @Test void reductionsCannotHealTheVictim() {assertEquals(0,ElementalRules.multiplier(-2,1));}
}
