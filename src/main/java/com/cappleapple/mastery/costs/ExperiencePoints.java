package com.cappleapple.mastery.costs;

/** Vanilla's cumulative experience curve, independent from totalExperience bookkeeping. */
public final class ExperiencePoints {
    private ExperiencePoints() {}
    public static long atLevel(int level) {
        long n=Math.max(0,level);
        if(n<=16)return n*n+6*n;
        if(n<=31)return (5*n*n-81*n+720)/2;
        return java.math.BigInteger.valueOf(n).multiply(java.math.BigInteger.valueOf(n)).multiply(java.math.BigInteger.valueOf(9))
                .subtract(java.math.BigInteger.valueOf(325).multiply(java.math.BigInteger.valueOf(n))).add(java.math.BigInteger.valueOf(4440)).divide(java.math.BigInteger.TWO).longValueExact();
    }
    public static int nextLevelCost(int level) {
        long cost=level>=30?112L+(level-30L)*9:level>=15?37L+(level-15L)*5:7L+level*2L;
        return Math.toIntExact(cost);
    }
    public static long total(int level,float progress) {
        return Math.addExact(atLevel(level),Math.clamp(Math.round((double)progress*nextLevelCost(level)),0,nextLevelCost(level)-1));
    }
    public static int levelAt(long points) {
        if(points<0)throw new IllegalArgumentException("Experience cannot be negative");
        int low=0,high=100000000;
        while(low<high){int middle=low+(high-low+1)/2;if(atLevel(middle)<=points)low=middle;else high=middle-1;}
        return low;
    }
}
