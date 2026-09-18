package com.cappleapple.mastery.costs;

import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.progression.PlayerProgress;
import com.cappleapple.mastery.progression.ProgressionService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.*;
import java.util.stream.Collectors;

/** Server purchase transaction plus a shared read-only client affordability calculation. */
public final class CostService {
    private CostService() {}
    public static CostPlanner.Budget budget(PlayerProgress progress,Player player) {
        var points=CostResolver.pointsBudget(progress).points();
        var inventory=new ArrayList<CostPlanner.Stack>();
        if(player!=null)for(int slot=0;slot<player.getInventory().getContainerSize();slot++) {
            if(slot>=36&&slot<40)continue; // Worn armor is never consumed by a skill purchase.
            var stack=player.getInventory().getItem(slot);if(stack.isEmpty())continue;
            inventory.add(new CostPlanner.Stack(slot,BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getTags().map(tag->tag.location().toString()).collect(Collectors.toSet()),stack.getCount()));
        }
        return new CostPlanner.Budget(points,player==null?0:ExperiencePoints.total(player.experienceLevel,player.experienceProgress),inventory);
    }
    public static boolean affordable(DefinitionSet definitions,PlayerProgress progress,String node,Player player) {
        try{return CostResolver.forNode(definitions,node).plan(CostResolver.gateBudget(definitions,progress,-1,budget(progress,player))).isPresent();}catch(RuntimeException ex){return false;}
    }
    public static void validateRuntime(DefinitionSet definitions,List<String> errors) {
        for(String node:definitions.nodes().keySet())try {validateItems(CostResolver.forNode(definitions,node).definition());}
        catch(RuntimeException ex){errors.add("nodes/"+node+": "+ex.getMessage());}
    }
    private static void validateItems(CostDefinition definition) {
        if(definition instanceof CostDefinition.Group group){group.children().forEach(CostService::validateItems);return;}
        var leaf=(CostDefinition.Leaf)definition;
        if(leaf.type().equals("item")&&!leaf.tag()&&!BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.parse(leaf.selector())))
            throw new IllegalArgumentException("Unknown cost item "+leaf.selector());
    }
    public static ProgressionService.Change purchase(DefinitionSet definitions,PlayerProgress progress,String node,int tier,ProgressionService.RequirementEvaluator requirements,ServerPlayer player) {
        return ProgressionService.purchase(definitions,progress,node,tier,requirements,definition->{
            try {
                var resolved=CostResolver.forNode(definitions,node);validateItems(resolved.definition());var balances=CostResolver.gateBudget(definitions,progress,tier,budget(progress,player),requirements);var selected=resolved.plan(balances);
                if(selected.isEmpty())return ProgressionService.Change.failure("Not enough resources: "+resolved.describe(id->id));
                var plan=selected.get();
                // Calculate every potentially failing conversion before touching any resource.
                long remaining=balances.experience()-plan.experience();int level=player.experienceLevel,within=0;
                if(plan.experience()>0){level=ExperiencePoints.levelAt(remaining);within=Math.toIntExact(remaining-ExperiencePoints.atLevel(level));}
                int pointsSpent=(int)Math.min(Integer.MAX_VALUE,plan.points().values().stream().mapToLong(Integer::longValue).sum());
                for(var entry:plan.points().entrySet())progress.tree(entry.getKey()).points(progress.tree(entry.getKey()).points()-entry.getValue());
                for(var entry:plan.slots().entrySet())player.getInventory().getItem(entry.getKey()).shrink(entry.getValue());
                if(plan.experience()>0) {
                    player.setExperienceLevels(level);player.setExperiencePoints(within);
                    player.totalExperience=(int)Math.min(Integer.MAX_VALUE,remaining);
                }
                player.getInventory().setChanged();
                return new ProgressionService.Change(true,"Paid purchase costs",0,-pointsSpent);
            }catch(RuntimeException ex){return ProgressionService.Change.failure("Could not resolve purchase costs: "+ex.getMessage());}
        });
    }
}
