package com.cappleapple.mastery.costs;

import java.util.*;

/** Global AND/OR backtracking and item/tag allocation; no resources change while planning. */
public final class CostPlanner {
    public record Stack(int slot,String item,Set<String> tags,int count) {
        public Stack {tags=Set.copyOf(tags);if(count<0)throw new IllegalArgumentException("Negative item count");}
    }
    public record Budget(Map<String,Integer> points,long experience,List<Stack> inventory) {
        public Budget {points=Map.copyOf(points);inventory=List.copyOf(inventory);}
    }
    public record ItemDemand(String selector,boolean tag,int amount) {}
    public record Plan(Map<String,Integer> points,long experience,List<ItemDemand> items,Map<Integer,Integer> slots) {
        public Plan {points=Map.copyOf(points);items=List.copyOf(items);slots=Map.copyOf(slots);}
        public static Plan empty(){return new Plan(Map.of(),0,List.of(),Map.of());}
    }
    private CostPlanner() {}
    public static Optional<Plan> plan(CostDefinition definition,String owningTree,int depth,double percent,Budget budget) {
        List<Plan> candidates=expand(definition,List.of(Plan.empty()),owningTree,depth,percent,budget,new int[]{0});
        for(Plan plan:candidates) {
            Map<Integer,Integer> slots=allocate(plan.items(),budget.inventory());
            if(slots!=null)return Optional.of(new Plan(plan.points(),plan.experience(),plan.items(),slots));
        }
        return Optional.empty();
    }
    private static List<Plan> expand(CostDefinition definition,List<Plan> incoming,String tree,int depth,double percent,Budget budget,int[] work) {
        if(++work[0]>4096)throw new IllegalArgumentException("Cost alternatives exceed the 4096-state planning limit");
        if(definition instanceof CostDefinition.Group group) {
            if(group.all()) {
                List<Plan> result=incoming;
                for(var child:group.children()){result=expand(child,result,tree,depth,percent,budget,work);if(result.isEmpty())break;}
                return result;
            }
            var result=new ArrayList<Plan>();
            for(var child:group.children()) {
                result.addAll(expand(child,incoming,tree,depth,percent,budget,work));
                if(result.size()>4096)throw new IllegalArgumentException("Cost alternatives exceed the 4096-state planning limit");
            }
            return result;
        }
        var leaf=(CostDefinition.Leaf)definition;int amount=CostDefinition.amount(leaf,depth,percent);var result=new ArrayList<Plan>();
        for(Plan previous:incoming) {
            if(++work[0]>4096)throw new IllegalArgumentException("Cost alternatives exceed the 4096-state planning limit");
            var points=new LinkedHashMap<>(previous.points());long experience=previous.experience();var items=new ArrayList<>(previous.items());
            switch(leaf.type()) {
                case "points" -> {String currency=leaf.selector().isEmpty()?tree:leaf.selector();points.merge(currency,amount,Math::addExact);}
                case "experience" -> experience=Math.addExact(experience,amount);
                case "item" -> {if(amount>0)items.add(new ItemDemand(leaf.selector(),leaf.tag(),amount));}
                default -> throw new IllegalArgumentException("Unknown cost type");
            }
            if(experience>budget.experience()||points.entrySet().stream().anyMatch(e->e.getValue()>budget.points().getOrDefault(e.getKey(),0)))continue;
            // Exact item allocation is independent of resource ordering and resolves overlapping selectors.
            if(!items.isEmpty()&&allocate(items,budget.inventory())==null)continue;
            result.add(new Plan(points,experience,items,Map.of()));
        }
        return result;
    }
    /** Small max-flow network ensures an early tag demand cannot consume a later exact-item requirement. */
    static Map<Integer,Integer> allocate(List<ItemDemand> demands,List<Stack> inventory) {
        if(demands.isEmpty())return Map.of();
        int source=0,firstDemand=1,firstStack=firstDemand+demands.size(),sink=firstStack+inventory.size(),size=sink+1;
        long[][] capacity=new long[size][size];long requested=0;
        for(int d=0;d<demands.size();d++) {
            ItemDemand demand=demands.get(d);capacity[source][firstDemand+d]=demand.amount();requested+=demand.amount();
            for(int s=0;s<inventory.size();s++) {
                Stack stack=inventory.get(s);boolean matches=demand.tag()?stack.tags().contains(demand.selector()):stack.item().equals(demand.selector());
                if(matches)capacity[firstDemand+d][firstStack+s]=stack.count();
            }
        }
        for(int s=0;s<inventory.size();s++)capacity[firstStack+s][sink]=inventory.get(s).count();
        long delivered=0;int[] parents=new int[size];
        while(delivered<requested) {
            Arrays.fill(parents,-1);parents[source]=source;var queue=new ArrayDeque<Integer>();queue.add(source);
            while(!queue.isEmpty()&&parents[sink]<0) {
                int from=queue.removeFirst();for(int to=0;to<size;to++)if(parents[to]<0&&capacity[from][to]>0){parents[to]=from;queue.add(to);}
            }
            if(parents[sink]<0)return null;
            long flow=Long.MAX_VALUE;for(int v=sink;v!=source;v=parents[v])flow=Math.min(flow,capacity[parents[v]][v]);
            for(int v=sink;v!=source;v=parents[v]){capacity[parents[v]][v]-=flow;capacity[v][parents[v]]+=flow;}
            delivered+=flow;
        }
        var slots=new LinkedHashMap<Integer,Integer>();
        for(int s=0;s<inventory.size();s++){int used=(int)capacity[sink][firstStack+s];if(used>0)slots.put(inventory.get(s).slot(),used);}
        return slots;
    }
}
