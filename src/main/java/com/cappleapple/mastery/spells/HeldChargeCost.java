package com.cappleapple.mastery.spells;

/** Whole base-spell charges consumed by a stronger held cast. */
public final class HeldChargeCost {
    private HeldChargeCost() {}
    public static int units(double baseDraw,double heldDraw) {
        if(!Double.isFinite(baseDraw)||!Double.isFinite(heldDraw)||heldDraw<=baseDraw)return 1;
        return (int)Math.clamp(Math.ceil(heldDraw/(baseDraw>0?baseDraw:1)-1e-9),1,10000);
    }
}
