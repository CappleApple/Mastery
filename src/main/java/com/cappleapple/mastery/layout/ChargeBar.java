package com.cappleapple.mastery.layout;
import java.util.List;
import java.util.ArrayList;

/** Segment positions include native preparation time before the first charged level. */
public record ChargeBar(int base,int ticksPerLevel,int levels,int total) {
    public ChargeBar {
        if(base<0||ticksPerLevel<1||levels<1||levels>64||total<1||(long)base+(long)ticksPerLevel*levels!=total)
            throw new IllegalArgumentException("Invalid charge window");
    }
    public double progress(int remaining){return Math.clamp(1-remaining/(double)total,0,1);}
    public int percentage(int remaining){return (int)Math.floor(progress(remaining)*100+1e-7);}
    public List<Double> markers(){
        var result=new ArrayList<Double>();
        for(int level=0;level<levels;level++){int tick=base+level*ticksPerLevel;if(tick>0)result.add(tick/(double)total);}
        return List.copyOf(result);
    }
}
