package com.cappleapple.mastery.elemental;

/** Pure combat arithmetic shared by damage, editor previews and tests. */
public final class ElementalRules {
    private ElementalRules() {}
    public static double conversion(double requested,double total) {
        return Math.max(0,requested)/Math.max(1,total);
    }
    public static double physical(double total) {return Math.max(0,1-total);}
    public static double matchup(double modifier,double attunement,boolean healing) {
        double delta=healing?-modifier:modifier;
        return Math.max(0,1+delta*(1+Math.max(0,attunement)));
    }
    public static double matchupDelta(double delta,double attunement,double potency,double mitigation) {
        return delta*(1+Math.max(0,attunement))*(delta>=0?1+Math.max(0,potency):Math.max(0,1-mitigation));
    }
    public static double multiplier(double global,double school) {return Math.max(0,1+global)*Math.max(0,1+school);}
}
