package com.cappleapple.mastery.layout;

/** One second at the client tick rate. Releasing or dragging discards incomplete progress. */
public final class UnlockHold {
    public static final int DURATION = 20;
    public static final int DINGS = 8;
    private int ticks,delayTicks;
    private boolean active;
    public void start() {start(500);}
    public void start(int delayMs) {ticks=0;delayTicks=Math.max(0,(delayMs+49)/50);active=true;}
    public void cancel() {active=false;ticks=0;}
    public boolean active() {return active;}
    public int ticks() {return ticks;}
    public boolean presenting(){return active&&ticks>delayTicks;}
    public double progress() {return active?Math.clamp((ticks-delayTicks)/(double)DURATION,0,1):0;}
    public int advance() {
        if(!active||ticks>=delayTicks+DURATION)return -1;
        int tick=ticks++-delayTicks;
        if(tick<0)return -1;
        int ding=tick*DINGS/DURATION;
        return tick==0||ding!=(tick-1)*DINGS/DURATION?ding:-1;
    }
    public boolean ready() {return active&&ticks==delayTicks+DURATION;}
}
