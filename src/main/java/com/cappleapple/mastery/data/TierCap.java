package com.cappleapple.mastery.data;

/** Caps effective at this tier and above, until another entry replaces them. -1 means unlimited. */
public record TierCap(int tier, int maxLevel, int maxRank, int maxDepth, int modifierSlots, int activeCapacity) {
    public static final TierCap UNLIMITED = new TierCap(0, -1, -1, -1, -1, -1);
}
