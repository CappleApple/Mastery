package com.cappleapple.mastery.spells;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HeldChargeCostTest {
    @Test void wholeBaseChargesRoundUpAtTheSameGameplayBoundaries() {
        assertEquals(1,HeldChargeCost.units(60,60));assertEquals(2,HeldChargeCost.units(60,70));
        assertEquals(2,HeldChargeCost.units(60,120));assertEquals(3,HeldChargeCost.units(60,121));
        assertEquals(2,HeldChargeCost.units(60,120.000000001));
        assertEquals(2,HeldChargeCost.units(.5,.8));assertEquals(1,HeldChargeCost.units(0,0));assertEquals(10000,HeldChargeCost.units(1,Double.MAX_VALUE));
        assertEquals(1,HeldChargeCost.units(Double.NaN,100));
    }
}
