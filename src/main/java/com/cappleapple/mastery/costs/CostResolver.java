package com.cappleapple.mastery.costs;

import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.graph.GraphValidator;
import com.cappleapple.mastery.progression.PlayerProgress;
import java.util.*;
import java.util.function.Function;

/** Resolves inherited cost expressions while retaining legacy single-tree prices. */
public final class CostResolver {
    public record Resolved(CostDefinition definition,String owningTree,int depth,double depthPercent) {
        public Optional<CostPlanner.Plan> plan(CostPlanner.Budget budget){return CostPlanner.plan(definition,owningTree,depth,depthPercent,budget);}
        public String describe(Function<String,String> names){return CostDefinition.describe(definition,owningTree,depth,depthPercent,names);}
    }
    private CostResolver() {}
    public static Resolved forNode(DefinitionSet definitions,String nodeId) {
        var node=definitions.nodes().get(nodeId);if(node==null)throw new IllegalArgumentException("Unknown node: "+nodeId);
        var settings=SettingsResolver.forNode(definitions,nodeId);
        var raw=settings.get("costs");
        CostDefinition cost=raw==null||raw.isJsonNull()?new CostDefinition.Leaf("points",node.tree(),false,node.cost(),null):CostDefinition.parse(raw.getAsJsonObject());
        double percent=settings.has("cost_depth_percent")?CostDefinition.percent(settings.get("cost_depth_percent")):0;
        return new Resolved(cost,node.tree(),GraphValidator.depthOf(definitions,nodeId),percent);
    }
    public static CostPlanner.Budget pointsBudget(PlayerProgress player) {
        var points=new LinkedHashMap<String,Integer>();player.trees().forEach((id,tree)->points.put(id,tree.points()));
        return new CostPlanner.Budget(points,0,List.of());
    }
    /** Worst point demand for one currency; OR chooses a maximum, AND accumulates. */
    public static long maximumPoints(CostDefinition definition,String currency,Resolved resolved) {
        if(definition instanceof CostDefinition.Group group) {
            long total=0;for(var child:group.children()) {long value=maximumPoints(child,currency,resolved);total=group.all()?Math.addExact(total,value):Math.max(total,value);}return total;
        }
        var leaf=(CostDefinition.Leaf)definition;
        return leaf.type().equals("points")&&(leaf.selector().isEmpty()?resolved.owningTree():leaf.selector()).equals(currency)?CostDefinition.amount(leaf,resolved.depth(),resolved.depthPercent()):0;
    }
    public static void validate(DefinitionSet definitions,List<String> errors) {
        for(String node:definitions.nodes().keySet())try {
            var cost=forNode(definitions,node);validate(cost.definition(),cost,definitions);
        }catch(RuntimeException ex){errors.add("nodes/"+node+": invalid costs: "+ex.getMessage());}
    }
    private static void validate(CostDefinition definition,Resolved resolved,DefinitionSet definitions) {
        if(definition instanceof CostDefinition.Group group){group.children().forEach(child->validate(child,resolved,definitions));return;}
        var leaf=(CostDefinition.Leaf)definition;
        if(leaf.type().equals("points")&&!definitions.trees().containsKey(leaf.selector().isEmpty()?resolved.owningTree():leaf.selector()))throw new IllegalArgumentException("Unknown points tree "+leaf.selector());
        CostDefinition.amount(leaf,resolved.depth(),resolved.depthPercent());
    }
}
