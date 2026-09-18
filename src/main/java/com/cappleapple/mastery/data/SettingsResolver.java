package com.cappleapple.mastery.data;
import com.google.gson.*;
import java.util.*;

/** Missing fields and literal default inherit; explicit leaf values override their parent scope. */
public final class SettingsResolver {
    public static final List<String> FIELDS=List.of("inherit_subtrees","skill_damage_xp","damage_filter","connections","theme","unlock","charge","modifier_slots","appearance","effect_context","costs","cost_depth_percent");
    private SettingsResolver(){}
    public static JsonObject merge(JsonObject base,JsonObject overrides) {
        JsonObject result=base.deepCopy();
        overrides.entrySet().forEach(entry->{
            var value=entry.getValue();
            if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()&&value.getAsString().equals("default"))return;
            if(!entry.getKey().equals("costs")&&value.isJsonObject()&&result.has(entry.getKey())&&result.get(entry.getKey()).isJsonObject())
                result.add(entry.getKey(),merge(result.getAsJsonObject(entry.getKey()),value.getAsJsonObject()));
            else result.add(entry.getKey(),value.deepCopy());
        });return result;
    }
    public static JsonObject extract(JsonObject json) {
        JsonObject result=new JsonObject();
        for(String field:FIELDS)if(json.has(field)) {
            if(field.equals("modifier_slots")&&json.get(field).isJsonPrimitive()&&json.getAsJsonPrimitive(field).isNumber()) {
                JsonObject value=new JsonObject();value.add("base",json.get(field));result.add(field,value);
            } else result.add(field,json.get(field).deepCopy());
        }
        return result;
    }
    public static JsonObject builtins(String spell) {
        JsonObject result=new JsonObject();result.addProperty("inherit_subtrees",true);result.addProperty("skill_damage_xp",1);result.add("damage_filter",new JsonObject());result.add("costs",JsonNull.INSTANCE);result.addProperty("cost_depth_percent",0);result.addProperty("effect_context","");result.add("connections",ConnectionPresentation.DEFAULT.toJson());result.add("appearance",NodeAppearance.DEFAULT.toJson());result.add("theme",TreeTheme.DEFAULT.toJson());result.add("unlock",UnlockPresentation.DEFAULTS.toJson());
        result.add("charge",new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().toJsonTree(ChargeDefinition.defaults(spell)));
        result.add("modifier_slots",JsonParser.parseString("{\"base\":2,\"per_level\":1,\"max\":64}"));return result;
    }
    public static JsonObject resolved(DefinitionSet definitions,String tree,String spell,String node) {
        JsonObject base=builtins(spell);
        var definition=definitions.nodes().get(node);
        var shape=definition==null&&!tree.isBlank()||definition!=null&&definition.rootTree()!=null?NodeAppearance.Shape.PENTAGON:
                definition!=null&&definition.type()==NodeType.ACTIVE?NodeAppearance.Shape.SQUARE:
                definition!=null&&definition.type()==NodeType.MODIFIER?NodeAppearance.Shape.TRIANGLE:NodeAppearance.Shape.CIRCLE;
        base.getAsJsonObject("appearance").addProperty("shape",shape.name().toLowerCase(Locale.ROOT));
        JsonObject result=merge(base,definitions.settingOverrides().getOrDefault("settings/mastery:defaults",new JsonObject()));
        for(String key:List.of("trees/"+tree,"spells/"+spell,"nodes/"+node))result=merge(result,definitions.settingOverrides().getOrDefault(key,new JsonObject()));
        return result;
    }
    public static JsonObject forNode(DefinitionSet definitions,String id) {
        var node=definitions.nodes().get(id);return resolved(definitions,node==null?id:node.tree(),node==null?"":node.spell(),node==null?"":id);
    }
    public static int modifierSlots(JsonObject settings,int level) {
        var slots=settings.getAsJsonObject("modifier_slots");
        int base=slots.get("base").getAsBigDecimal().intValueExact(),max=slots.get("max").getAsBigDecimal().intValueExact();
        double per=slots.get("per_level").getAsDouble();
        if(base<0||base>4096||max<0||max>4096||!Double.isFinite(per)||per<0||per>4096)throw new IllegalArgumentException("modifier_slots values must be within 0..4096");
        return (int)Math.min(max,Math.floor(base+Math.max(0,level-1)*per));
    }
    public static void validate(JsonObject settings,String spell) {
        if(settings.has("costs")&&!settings.get("costs").isJsonNull())com.cappleapple.mastery.costs.CostDefinition.parse(settings.getAsJsonObject("costs"));
        if(settings.has("cost_depth_percent"))com.cappleapple.mastery.costs.CostDefinition.percent(settings.get("cost_depth_percent"));
        var context=settings.get("effect_context");if(!context.isJsonPrimitive()||!context.getAsJsonPrimitive().isString()||!context.getAsString().isEmpty()&&!context.getAsString().matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException("effect_context must be empty or a context ID");
        ConnectionPresentation.parse(settings.getAsJsonObject("connections"));NodeAppearance.parse(settings.getAsJsonObject("appearance"));TreeTheme.parse(settings.getAsJsonObject("theme"));UnlockPresentation.parse(settings.getAsJsonObject("unlock"));
        com.cappleapple.mastery.mechanics.TreeModifiers.validate(settings.getAsJsonObject("damage_filter"));
        if(!settings.get("inherit_subtrees").isJsonPrimitive()||!settings.getAsJsonPrimitive("inherit_subtrees").isBoolean())throw new IllegalArgumentException("inherit_subtrees must be boolean");
        if(!settings.get("skill_damage_xp").isJsonPrimitive()||!settings.getAsJsonPrimitive("skill_damage_xp").isNumber())throw new IllegalArgumentException("skill_damage_xp must be numeric");
        double skillXp=settings.get("skill_damage_xp").getAsDouble();
        if(!Double.isFinite(skillXp)||skillXp<0||skillXp>1000000)throw new IllegalArgumentException("skill_damage_xp must be within 0..1000000");
        ChargeDefinition.parse(spell,settings.getAsJsonObject("charge"));modifierSlots(settings,1);
    }
}
