package com.cappleapple.mastery.progression;

/** Mutable server-thread state for one independent currency. */
public final class TreeProgress {
    private double xp;
    private double lifetimeXp;
    private int level;
    private int points;
    private int highestLevel;
    private boolean discovered;

    public double xp() { return xp; }
    public void xp(double value) { xp = finiteNonnegative(value); }
    public double lifetimeXp() { return lifetimeXp; }
    public void lifetimeXp(double value) { lifetimeXp = finiteNonnegative(value); }
    public int level() { return level; }
    public void level(int value) { level = Math.max(0, value); }
    public int points() { return points; }
    public void points(int value) { points = Math.max(0, value); if (points > 0) discovered = true; }
    /** Highest level whose milestone points have already been awarded. */
    public int highestLevel() { return highestLevel; }
    public void highestLevel(int value) { highestLevel = Math.max(0, value); }
    /** Once discovered, spending the last point does not hide the specialization. */
    public boolean discovered() { return discovered; }
    public void discovered(boolean value) { discovered = value; }

    private static double finiteNonnegative(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0;
    }
}
