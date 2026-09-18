package com.cappleapple.mastery.effects;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.NodeDefinition;
import com.cappleapple.mastery.requirements.RequirementContext;
import com.cappleapple.mastery.requirements.RequirementRegistry;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import java.util.*;

/** Derived effects are rebuilt from purchased nodes; they are never used as the persistence source. */
public final class EffectService {
    private record Applied(String key, JsonObject effect, EffectRegistry.Handler handler) {}
    private static final Map<UUID,List<Applied>> APPLIED = new HashMap<>();
    private static final Map<UUID,String> WEAPON_CONTEXTS=new HashMap<>();
    private static final Map<UUID,Long> SIGNATURES = new HashMap<>();
    static {
        EffectRegistry.register("mastery:attribute", new EffectRegistry.Handler() {
            @Override public void apply(ServerPlayer p,String node,int rank,JsonObject effect) {
                var holder=BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(text(effect,"attribute","minecraft:generic.attack_damage")));
                if(holder.isEmpty())return;
                var attribute=p.getAttribute(holder.get()); if(attribute==null)return;
                var id=modifierId(node);
                attribute.removeModifier(id);
                var operation=switch(text(effect,"operation","add_value")) {
                    case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                    case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                    default -> AttributeModifier.Operation.ADD_VALUE;
                };
                attribute.addTransientModifier(new AttributeModifier(id,number(effect,"amount",0)*rank,operation));
            }
            @Override public void remove(ServerPlayer p,String node,JsonObject effect) {
                var holder=BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(text(effect,"attribute","minecraft:generic.attack_damage")));
                if(holder.isPresent() && p.getAttribute(holder.get())!=null)p.getAttribute(holder.get()).removeModifier(modifierId(node));
            }
        });
        EffectRegistry.Handler declarative=(p,n,r,e)->{};
        for(String type:List.of("mastery:keyword_modifier","mastery:trigger","mastery:experience_gain","mastery:bonus","mastery:spell_modifier","mastery:unlock_spell","mastery:unlock_context"))EffectRegistry.register(type,declarative);
        EffectRegistry.register("mastery:on_usage",new EffectRegistry.Handler() {
            @Override public void apply(ServerPlayer p,String n,int r,JsonObject e) {}
            @Override public void onUsage(ServerPlayer p,String n,int r,JsonObject e,String event,JsonObject context) {
                if(!event.equals(text(e,"event","")))return;
                if(e.has("condition") && !RequirementRegistry.test(new RequirementContext(p,context),e.getAsJsonObject("condition")))return;
                if(!context.has("target_id"))return;
                var entity=p.serverLevel().getEntity(context.get("target_id").getAsInt());
                if(!(entity instanceof net.minecraft.world.entity.LivingEntity target) || target==p || target.isAlliedTo(p))return;
                if(e.has("ignite_ticks"))target.igniteForSeconds((float)(number(e,"ignite_ticks",0)*r/20.0));
                if(e.has("effect")) {
                    var effect=BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(text(e,"effect","")));
                    effect.ifPresent(h->target.addEffect(new net.minecraft.world.effect.MobEffectInstance(h,(int)number(e,"duration",60),(int)number(e,"amplifier",0)),p));
                }
            }
        });
    }
    private EffectService() {}
    public static void initialize() {}
    public static boolean active(ServerPlayer player,NodeDefinition node) { return effectiveRank(player,node)>0; }
    public static int effectiveRank(ServerPlayer player,NodeDefinition node) {
        var state=MasteryRuntime.progress(player).nodes().get(node.id());
        if(state==null||!state.toggled()) return 0;
        if(node.spellModifier()&&MasteryRuntime.progress(player).activeModifiers().values().stream().noneMatch(ids->ids.contains(node.id()))) return 0;
        return eligibleRank(player,node);
    }
    /** Rank prerequisites depend on investment and gates, never on ancestor activation switches. */
    public static int eligibleRank(ServerPlayer player,NodeDefinition node) { return eligibleRank(player,node,new HashMap<>(),new HashSet<>()); }
    private static int eligibleRank(ServerPlayer player,NodeDefinition node,Map<String,Integer> memo,Set<String> visiting) {
        if(memo.containsKey(node.id()))return memo.get(node.id());
        if(!visiting.add(node.id()))return 0;
        var progress=MasteryRuntime.progress(player); int rank=progress.rank(node.id());
        if(!com.cappleapple.mastery.progression.ProgressionService.bookUnlocked(MasteryRuntime.definitions(),progress,node.id())){visiting.remove(node.id());return 0;}
        var tree=MasteryRuntime.definitions().trees().get(node.tree()); int tier=MasteryRuntime.worldTier(player);
        if(rank<=0 || tree==null){visiting.remove(node.id());return 0;}
        if(!com.cappleapple.mastery.data.PromotedTrees.unlocked(MasteryRuntime.definitions(),progress,node.tree(),tier,r->RequirementRegistry.test(new RequirementContext(player,new JsonObject()),r))){visiting.remove(node.id());return 0;}
        var cap=tree.capAt(tier);
        if(tier>=0 && tier<node.worldTier() || Math.min(progress.tree(node.tree()).level(),tree.levelCap(tier))<node.level()){visiting.remove(node.id());return 0;}
        if(cap.maxRank()>=0)rank=Math.min(rank,cap.maxRank());
        if(cap.maxDepth()>=0 && com.cappleapple.mastery.graph.GraphValidator.depthOf(MasteryRuntime.definitions(),node.id())>cap.maxDepth()){visiting.remove(node.id());return 0;}
        if(!node.dependenciesMet(dep->{
            var parent=MasteryRuntime.definitions().nodes().get(dep.node());
            return parent!=null&&eligibleRank(player,parent,memo,visiting)>=dep.rank();
        })){visiting.remove(node.id());memo.put(node.id(),0);return 0;}
        for(var requirement:node.requirements())if(!RequirementRegistry.test(new RequirementContext(player,new JsonObject()),requirement)){visiting.remove(node.id());return 0;}
        visiting.remove(node.id());memo.put(node.id(),rank);return rank;
    }
    public static void rebuild(ServerPlayer player) {
        clear(player);
        List<Applied> applied=new ArrayList<>();
        for(var node:MasteryRuntime.definitions().nodes().values())if(active(player,node)) {
            int rank=effectiveRank(player,node); int i=0;
            for(var raw:node.effects()) {
                var effect=resolve(raw,0); if(effect==null||!applies(player,node,effect))continue;
                var handler=EffectRegistry.get(text(effect,"type","")); if(handler==null)continue;
                String key=node.id()+"/"+i++;
                handler.apply(player,key,rank,effect); applied.add(new Applied(key,effect,handler));
            }
        }
        APPLIED.put(player.getUUID(),applied);
        SIGNATURES.put(player.getUUID(),signature(player));WEAPON_CONTEXTS.put(player.getUUID(),weaponContext(player));
    }
    public static void clear(ServerPlayer player) {
        SIGNATURES.remove(player.getUUID());WEAPON_CONTEXTS.remove(player.getUUID());
        var old=APPLIED.remove(player.getUUID());
        if(old!=null)for(var effect:old)effect.handler.remove(player,effect.key,effect.effect);
    }
    private static String weaponContext(ServerPlayer player){return com.cappleapple.mastery.spells.ContextResolver.resolve(player,MasteryRuntime.definitions());}
    public static void refreshWeaponContext(ServerPlayer player){if(!weaponContext(player).equals(WEAPON_CONTEXTS.get(player.getUUID())))rebuild(player);}
    private static boolean applies(ServerPlayer player,NodeDefinition node,JsonObject effect){
        if(!text(effect,"type","").equals("mastery:attribute"))return true;
        String required=effect.has("context")?text(effect,"context",""):com.cappleapple.mastery.data.SettingsResolver.forNode(MasteryRuntime.definitions(),node.id()).get("effect_context").getAsString();
        return required.isEmpty()||required.equals(weaponContext(player));
    }
    /** Rechecks external predicates once per second without reapplying unchanged attributes. */
    public static void refresh(ServerPlayer player) {
        long next=signature(player);
        if(SIGNATURES.getOrDefault(player.getUUID(),Long.MIN_VALUE)!=next)rebuild(player);
    }
    private static long signature(ServerPlayer player) {
        long result=weaponContext(player).hashCode();
        for(var entry:MasteryRuntime.progress(player).nodes().entrySet())if(entry.getValue().rank()>0) {
            var node=MasteryRuntime.definitions().nodes().get(entry.getKey());
            if(node!=null)result+=(31L*node.id().hashCode()+7)*effectiveRank(player,node);
        }
        return result;
    }
    public static void onUsage(ServerPlayer player,String event,JsonObject context) {
        for(var node:MasteryRuntime.definitions().nodes().values())if(active(player,node))for(var raw:node.effects()) {
            var effect=resolve(raw,0); if(effect==null||!applies(player,node,effect))continue;
            var handler=EffectRegistry.get(text(effect,"type",""));
            if(handler!=null)handler.onUsage(player,node.id(),effectiveRank(player,node),effect,event,context);
        }
    }
    /** Additive rank-scaled bonus; empty spell filters apply to every spell. */
    public static double bonus(ServerPlayer player,String key,String spell) {
        double amount=0;
        for(var node:MasteryRuntime.definitions().nodes().values())if(active(player,node))for(var raw:node.effects()) {
            var effect=resolve(raw,0); if(effect==null||!applies(player,node,effect))continue;
            if(text(effect,"type","").equals("mastery:bonus") && text(effect,"key","").equals(key) && (!effect.has("spell")||text(effect,"spell","").equals(spell)))
                amount+=number(effect,"amount",0)*effectiveRank(player,node);
        }
        return amount;
    }
    private static JsonObject resolve(JsonObject effect,int depth) {
        if(effect==null||depth>16)return null;
        return effect.has("ref")?resolve(MasteryRuntime.definitions().effects().get(effect.get("ref").getAsString()),depth+1):effect;
    }
    private static ResourceLocation modifierId(String node) { return ResourceLocation.fromNamespaceAndPath("mastery","node/"+node.replace(':','/')); }
    private static String text(JsonObject j,String key,String fallback) { return RequirementRegistry.string(j,key,fallback); }
    private static double number(JsonObject j,String key,double fallback) { return RequirementRegistry.number(j,key,fallback); }
}
