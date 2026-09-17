package com.cappleapple.mastery.layout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChargeBarTest {
    @Test void percentageRisesAndSegmentsFollowActualChargeStages(){
        var bar=new ChargeBar(10,20,3,70);
        assertEquals(0,bar.percentage(70));assertEquals(50,bar.percentage(35));assertEquals(100,bar.percentage(0));
        assertEquals(java.util.List.of(10/70.0,30/70.0,50/70.0),bar.markers());
        assertEquals(100,bar.percentage(-10));assertEquals(0,bar.percentage(100));
    }
    @Test void zeroPreparationHasEvenLevelSegments(){assertEquals(java.util.List.of(.25,.5,.75),new ChargeBar(0,20,4,80).markers());}
    @Test void rejectsMismatchedServerWindow(){assertThrows(IllegalArgumentException.class,()->new ChargeBar(10,20,3,60));}
}
