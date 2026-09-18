package com.cappleapple.mastery.spells;

/** Native spell adjustments. No spell implementation, projectile or resource state lives in Mastery. */
public final class SpellModifiers {
    private int levels,extraCharges;
    private double mana=1,cooldown=1,castTime=1;
    public int extraCharges() { return extraCharges; }
    public void addCharges(int amount) { extraCharges=Math.clamp((long)extraCharges+Math.max(0,amount),0,10000); }
    public int withCharges(int current) { return (int)Math.clamp((long)current+extraCharges,1,Integer.MAX_VALUE); }
    public int levels() { return levels; }
    public double manaMultiplier() { return mana; }
    public double cooldownMultiplier() { return cooldown; }
    public double castTimeMultiplier() { return castTime; }
    public void addLevels(int amount) { levels=Math.clamp((long)levels+amount,-255,255); }
    public void multiplyMana(double multiplier) { mana=bounded(mana*multiplier); }
    public void multiplyCooldown(double multiplier) { cooldown=bounded(cooldown*multiplier); }
    public void multiplyCastTime(double multiplier) { castTime=bounded(castTime*multiplier); }
    private static double bounded(double value) { return Double.isNaN(value)?1:Math.clamp(value,0.01,100); }
    public static int scale(int original,double factor) { return (int)Math.clamp(Math.ceil(original*factor),0,Integer.MAX_VALUE); }
}
