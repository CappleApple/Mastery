package com.cappleapple.mastery.progression;
import com.cappleapple.mastery.data.*;
import java.util.Collection;

/** Loaded tree caps use the first level that funds all defined ranks, including hidden branches. */
public final class TreeLevelBudget {
    public static final int MAX_SUPPORTED_LEVEL=100000;
    private TreeLevelBudget(){}
    public static long pointsAt(TreeDefinition tree,int level) {
        long points=tree.pointEvery()==0?0:(long)(level/tree.pointEvery())*tree.pointsPerAward();
        for(var milestone:tree.pointMilestones().entrySet())if(milestone.getKey()>0&&milestone.getKey()<=level)points+=milestone.getValue();
        return points+PointFormula.total(tree.pointFormula(),level);
    }
    public static int maximum(TreeDefinition tree,Collection<NodeDefinition> nodes) {
        long budget=0;
        for(var node:nodes)if(node.tree().equals(tree.id()))budget=Math.addExact(budget,(long)Math.max(0,node.cost())*Math.max(0,node.maxRank()));
        return maximum(tree,budget);
    }
    public static int maximum(TreeDefinition tree,DefinitionSet definitions) {
        long actual=0,legacy=0;int requiredLevel=0;
        for(var node:definitions.nodes().values()) {
            var resolved=com.cappleapple.mastery.costs.CostResolver.forNode(definitions,node.id());
            actual=Math.addExact(actual,Math.multiplyExact(com.cappleapple.mastery.costs.CostResolver.maximumPoints(resolved.definition(),tree.id(),resolved),node.maxRank()));
            if(node.tree().equals(tree.id())) {
                legacy=Math.addExact(legacy,(long)Math.max(0,node.cost())*Math.max(0,node.maxRank()));
                requiredLevel=Math.max(requiredLevel,node.level());
            }
        }
        return Math.max(requiredLevel,maximum(tree,Math.max(legacy,actual)));
    }
    private static int maximum(TreeDefinition tree,long budget) {
        if(tree.pointEvery()<0||tree.pointsPerAward()<0)throw new IllegalArgumentException("Point interval and award must be nonnegative");
        if(PointFormula.total(tree.pointFormula(),0)!=0)throw new IllegalArgumentException("point_formula must give zero cumulative points at level 0");
        if(budget==0)return 0;
        // A tree funded entirely by external grants does not earn proficiency levels.
        if((tree.pointEvery()==0||tree.pointsPerAward()==0)&&tree.pointMilestones().values().stream().allMatch(v->v==0)&&tree.pointFormula().isBlank())return 0;
        if(tree.pointFormula().isBlank()) {
            if(pointsAt(tree,Integer.MAX_VALUE)<budget)throw new IllegalArgumentException("Point schedule cannot fund all ranks within the supported level range");
            int low=0,high=Integer.MAX_VALUE;
            while(low<high){int middle=low+(high-low)/2;if(pointsAt(tree,middle)>=budget)high=middle;else low=middle+1;}
            return low;
        }
        long previous=0;
        for(int level=1;level<=MAX_SUPPORTED_LEVEL;level++) {
            long total=pointsAt(tree,level);
            if(total<previous)throw new IllegalArgumentException("Point schedule decreases at level "+level);
            if(total>Integer.MAX_VALUE)throw new IllegalArgumentException("Point schedule exceeds the supported point balance");
            if(total>=budget)return level;
            previous=total;
        }
        throw new IllegalArgumentException("Point schedule cannot fund "+budget+" points within "+MAX_SUPPORTED_LEVEL+" levels");
    }
}
