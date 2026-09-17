package com.cappleapple.mastery.data;

import java.util.List;
import java.util.Map;

/** Independent proficiency and point currency. XP is progress toward the next level. */
public record TreeDefinition(String id, String name, String description, String icon, String parent,
        int maxLevel, double xpBase, double xpGrowth, int pointEvery,
        Map<Integer, Integer> pointMilestones, String pointFormula, List<TierCap> tierCaps, String section, TreeTheme theme, UnlockPresentation unlock, int pointsPerAward) {
    public TreeDefinition(String id,String name,String description,String icon,String parent,
            int maxLevel,double xpBase,double xpGrowth,int pointEvery,Map<Integer,Integer> pointMilestones,
            String pointFormula,List<TierCap> tierCaps,String section,TreeTheme theme,UnlockPresentation unlock) {
        this(id,name,description,icon,parent,maxLevel,xpBase,xpGrowth,pointEvery,pointMilestones,pointFormula,tierCaps,section,theme,unlock,1);
    }
    public TreeDefinition withMaxLevel(int maximum) {
        return new TreeDefinition(id,name,description,icon,parent,maximum,xpBase,xpGrowth,pointEvery,pointMilestones,pointFormula,tierCaps,section,theme,unlock,pointsPerAward);
    }
    public TreeDefinition {
        pointMilestones = Map.copyOf(pointMilestones);
        tierCaps = List.copyOf(tierCaps);
        theme = theme == null ? TreeTheme.DEFAULT : theme;
        unlock = unlock == null ? UnlockPresentation.INHERIT : unlock;
    }

    public TreeDefinition(String id, String name, String description, String icon, String parent,
            int maxLevel, double xpBase, double xpGrowth, int pointEvery,
            Map<Integer, Integer> pointMilestones, String pointFormula, List<TierCap> tierCaps) {
        this(id, name, description, icon, parent, maxLevel, xpBase, xpGrowth, pointEvery,
                pointMilestones, pointFormula, tierCaps, "south");
    }

    public TreeDefinition(String id, String name, String description, String icon, String parent,
            int maxLevel, double xpBase, double xpGrowth, int pointEvery,
            Map<Integer, Integer> pointMilestones, String pointFormula, List<TierCap> tierCaps, String section) {
        this(id, name, description, icon, parent, maxLevel, xpBase, xpGrowth, pointEvery,
                pointMilestones, pointFormula, tierCaps, section, TreeTheme.DEFAULT);
    }

    public TreeDefinition(String id, String name, String description, String icon, String parent,
            int maxLevel, double xpBase, double xpGrowth, int pointEvery,
            Map<Integer, Integer> pointMilestones, String pointFormula, List<TierCap> tierCaps, String section, TreeTheme theme) {
        this(id,name,description,icon,parent,maxLevel,xpBase,xpGrowth,pointEvery,pointMilestones,pointFormula,tierCaps,section,theme,UnlockPresentation.INHERIT);
    }

    public double xpForLevel(int currentLevel) { return xpBase + xpGrowth * currentLevel; }

    /** Negative tier disables world caps, for the uncapped fallback provider. */
    public TierCap capAt(int worldTier) {
        if (worldTier < 0) return TierCap.UNLIMITED;
        TierCap selected = TierCap.UNLIMITED;
        int selectedTier = -1;
        for (TierCap cap : tierCaps) {
            if (cap.tier() <= worldTier && cap.tier() > selectedTier) {
                selected = cap;
                selectedTier = cap.tier();
            }
        }
        return selected;
    }

    public int levelCap(int worldTier) {
        int cap = capAt(worldTier).maxLevel();
        return cap < 0 ? maxLevel : Math.min(maxLevel, cap);
    }
}
