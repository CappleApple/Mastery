package com.cappleapple.mastery.costs;

import com.google.gson.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;

/** Immutable nested purchase costs. Parsing has no Minecraft registry or player dependency. */
public sealed interface CostDefinition permits CostDefinition.Group, CostDefinition.Leaf {
    record Group(boolean all,List<CostDefinition> children) implements CostDefinition {
        public Group { children=List.copyOf(children); }
    }
    record Leaf(String type,String selector,boolean tag,int amount,Double depthPercent) implements CostDefinition {}
    static CostDefinition parse(JsonObject json) {return parse(json,0,new int[]{0});}
    private static CostDefinition parse(JsonObject json,int depth,int[] count) {
        if(depth>32||++count[0]>128)throw new IllegalArgumentException("Costs exceed 32 nested groups or 128 entries");
        if(json.has("and")||json.has("or")) {
            if(json.has("and")&&json.has("or")||json.has("type"))throw new IllegalArgumentException("A cost must be one AND group, OR group or typed leaf");
            String key=json.has("and")?"and":"or";
            if(!json.get(key).isJsonArray()||json.getAsJsonArray(key).isEmpty())throw new IllegalArgumentException("Cost groups need a nonempty array");
            List<CostDefinition> children=new ArrayList<>();
            for(var value:json.getAsJsonArray(key)) {
                if(!value.isJsonObject())throw new IllegalArgumentException("Cost group entries must be objects");
                children.add(parse(value.getAsJsonObject(),depth+1,count));
            }
            return new Group(key.equals("and"),children);
        }
        String type=text(json,"type","");
        if(!Set.of("points","experience","item").contains(type))throw new IllegalArgumentException("Cost type must be points, experience or item");
        if(!json.has("amount")||!json.get("amount").isJsonPrimitive()||!json.getAsJsonPrimitive("amount").isNumber())throw new IllegalArgumentException("Cost amount must be a nonnegative integer");
        int amount=json.get("amount").getAsBigDecimal().intValueExact();
        if(amount<0||amount>1000000000)throw new IllegalArgumentException("Cost amount must be between 0 and 1000000000");
        Double percent=json.has("depth_percent")?percent(json.get("depth_percent")):null;
        boolean tag=type.equals("item")&&json.has("item_tag");
        String selector=type.equals("points")?text(json,"tree",""):type.equals("item")?text(json,tag?"item_tag":"item",""):"";
        if(type.equals("item")&&(json.has("item")==json.has("item_tag")))throw new IllegalArgumentException("An item cost needs exactly one item or item_tag");
        if((!selector.isEmpty()||type.equals("item"))&&!selector.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException("Cost selector must be a resource ID");
        return new Leaf(type,selector,tag,amount,percent);
    }
    static double percent(JsonElement value) {
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Cost depth percentage must be numeric");
        double percent=value.getAsDouble();
        if(!Double.isFinite(percent)||percent<0||percent>100)throw new IllegalArgumentException("Cost depth percentage must be within 0..100");
        return percent;
    }
    static int amount(Leaf leaf,int depth,double inheritedPercent) {
        BigDecimal factor=BigDecimal.ONE.add(BigDecimal.valueOf(leaf.depthPercent()==null?inheritedPercent:leaf.depthPercent()).multiply(BigDecimal.valueOf(Math.max(0,depth))));
        return BigDecimal.valueOf(leaf.amount()).multiply(factor).setScale(0,RoundingMode.CEILING).intValueExact();
    }
    static String describe(CostDefinition definition,String owningTree,int depth,double percent,Function<String,String> names) {
        if(definition instanceof Group group)return "("+String.join(group.all()?" AND ":" OR ",group.children().stream().map(child->describe(child,owningTree,depth,percent,names)).toList())+")";
        Leaf leaf=(Leaf)definition;int amount=amount(leaf,depth,percent);
        return switch(leaf.type()) {
            case "points" -> amount+" "+names.apply(leaf.selector().isEmpty()?owningTree:leaf.selector())+" Points";
            case "experience" -> amount+" Minecraft XP";
            default -> amount+" × "+(leaf.tag()?"#"+leaf.selector():names.apply(leaf.selector()));
        };
    }
    private static String text(JsonObject json,String key,String fallback) {
        if(!json.has(key))return fallback;
        var value=json.get(key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException(key+" must be a string");
        return value.getAsString();
    }
}
