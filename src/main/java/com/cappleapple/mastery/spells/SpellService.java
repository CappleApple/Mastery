package com.cappleapple.mastery.spells;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.api.SpellEquipmentRegistry;
import com.cappleapple.mastery.api.spell.SpellModifierRegistry;
import com.cappleapple.mastery.config.MasteryConfig;
import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.integration.IronSpellsIntegration;
import com.google.gson.*;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.compat.Curios;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;

/** Stores Mastery assignments and delegates casting entirely to Iron's existing spell implementation. */
public final class SpellService {
    private record Started(String spell,int slot) {}
    private record Intent(UUID player,String spell) {}
    private static final ThreadLocal<Intent> INITIATING=new ThreadLocal<>();
    private static final Map<UUID,Started> STARTED=new HashMap<>();
    private static final Map<UUID,String> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Integer> CAPACITIES=new HashMap<>();
    private static final Map<UUID,Boolean> BUSY=new HashMap<>();
    private static final Map<UUID,JsonObject> CHARGE_STATS=new HashMap<>();
    private static boolean initialized;
    private SpellService() {}
    public static void initialize() {
        if(initialized) return; initialized=true;
        SpellModifierRegistry.register("mastery:native_spell",(player,spell,rank,data,result) -> {
            result.addCharges((int)Math.clamp(number(data,"extra_charges",0)*rank,0,10000));
            result.addLevels((int)Math.clamp(number(data,"spell_level",0)*rank,-255,255));
            result.multiplyMana(Math.pow(number(data,"mana_multiplier",1),rank));
            result.multiplyCooldown(Math.pow(number(data,"cooldown_multiplier",1),rank));
            result.multiplyCastTime(Math.pow(number(data,"cast_time_multiplier",1),rank));
        });
        com.cappleapple.mastery.integration.TempoChargeIntegration.initialize();
        NeoForge.EVENT_BUS.register(IronSpellsIntegration.class);NeoForge.EVENT_BUS.register(ChargeService.class);
    }
    public static String context(Player player) {
        if(!(player instanceof ServerPlayer serverPlayer))return BindingSlots.QUICK;
        return MasteryRuntime.progress(serverPlayer).bindingMode().equals("quick_cast")?BindingSlots.QUICK:BindingSlots.hotbar(player.getInventory().selected);
    }
    public static String bindingMode(ServerPlayer player,String mode) {
        if(!mode.equals("hotbar")&&!mode.equals("quick_cast"))return "Unknown binding mode";
        interrupt(player,"binding mode changed");MasteryRuntime.progress(player).bindingMode(mode);reconcile(player);return "";
    }
    /** Combat classification remains independent from the player's binding layout. */
    public static String combatContext(Player player) {
        return ContextResolver.resolve(player,MasteryRuntime.definitions(),id->{
            var definition=MasteryRuntime.definitions().contexts().get(id);if(definition==null)return false;
            if(!definition.condition().has("requires_unlock")||!definition.condition().get("requires_unlock").getAsBoolean())return true;
            return player instanceof ServerPlayer server&&MasteryRuntime.definitions().nodes().values().stream()
                    .anyMatch(node->EffectService.active(server,node)&&grants(node,"mastery:unlock_context","context",id));
        });
    }
    public static int capacity(ServerPlayer player) {
        double result=MasteryConfig.ACTIVE_CAPACITY.get()+IronSpellsIntegration.equipmentCapacity(player)
                +EffectService.bonus(player,"active_capacity","")+EffectService.bonus(player,"spell_slots","");
        for(var source:SpellEquipmentRegistry.CAPACITY.values()) result+=Math.max(0,source.capacity(player));
        return (int)Math.clamp(result,0,4096);
    }
    public static boolean owned(ServerPlayer player,String spell) {
        if(!MasteryRuntime.definitions().spells().containsKey(spell)) return false;
        return MasteryRuntime.definitions().nodes().values().stream().anyMatch(node -> !node.spellModifier()
                &&(node.spell().equals(spell)||grants(node,"mastery:unlock_spell","spell",spell))&&EffectService.active(player,node));
    }
    public static boolean unlocked(ServerPlayer player,String spell) {
        if(!MasteryRuntime.definitions().spells().containsKey(spell)) return false;
        return MasteryRuntime.definitions().nodes().values().stream().anyMatch(node -> !node.spellModifier()
                &&(node.spell().equals(spell)||grants(node,"mastery:unlock_spell","spell",spell))&&EffectService.eligibleRank(player,node)>0);
    }
    public static String toggleNode(ServerPlayer player,NodeDefinition node,boolean enabled) {
        if(node.spellModifier()) {
            var targets=MasteryRuntime.definitions().spells().keySet().stream().sorted().filter(id->related(node,id,new HashSet<>())).toList();
            if(targets.isEmpty())return "No target spell for this modifier";
            if(enabled)for(String spell:targets) {
                var equipped=MasteryRuntime.progress(player).modifiers(spell);
                String error=LoadoutRules.modifier(equipped.size(),modifierLimit(player,MasteryRuntime.definitions().spells().get(spell)),EffectService.eligibleRank(player,node)>0,true,equipped.contains(node.id()));
                if(!error.isBlank())return error;
            }
            for(String spell:targets)setModifier(player,spell,node.id(),enabled);
            return "";
        }
        if(!enabled&&(!node.spell().isBlank()||node.effects().stream().map(SpellService::resolve).filter(Objects::nonNull).anyMatch(e->text(e,"type","").equals("mastery:unlock_spell"))))interrupt(player,"node disabled");
        MasteryRuntime.progress(player).node(node.id()).toggled(enabled);
        return "";
    }
    private static JsonObject resolve(JsonObject effect) {
        for(int depth=0;effect!=null&&effect.has("ref")&&depth<32;depth++) effect=MasteryRuntime.definitions().effects().get(effect.get("ref").getAsString());
        return effect!=null&&!effect.has("ref")?effect:null;
    }
    private static boolean grants(NodeDefinition node,String type,String field,String id) {
        return node.effects().stream().map(SpellService::resolve).filter(Objects::nonNull)
                .anyMatch(effect -> text(effect,"type","").equals(type)&&text(effect,field,"").equals(id));
    }
    public static String assign(ServerPlayer player,String context,int slot,String spell) {
        var progress=MasteryRuntime.progress(player); var definitions=MasteryRuntime.definitions();
        if(!BindingSlots.inMode(context,progress.bindingMode()))return "Choose a slot in the active binding mode";
        var definition=definitions.spells().get(spell);
        String error=LoadoutRules.validate(BindingSlots.modeLoadouts(progress.loadouts(),progress.bindingMode()),context,slot,spell,capacity(player),unlocked(player,spell),
                definition!=null);
        if(!error.isBlank()) return error;
        if(definition!=null) {
            int cap=definitions.trees().get(definition.tree()).capAt(MasteryRuntime.worldTier(player)).activeCapacity();
            long existing=BindingSlots.modeLoadouts(progress.loadouts(),progress.bindingMode()).values().stream().flatMap(Collection::stream).map(definitions.spells()::get)
                    .filter(Objects::nonNull).filter(value -> value.tree().equals(definition.tree())).count();
            var previous=definitions.spells().get(BindingSlots.get(progress.loadouts(),context,slot));
            if(previous!=null&&previous.tree().equals(definition.tree())) existing--;
            if(cap>=0&&existing+1>cap) return "World tier limits prepared spells in this specialization";
        }
        interrupt(player,"loadout changed"); progress.bind(context,slot,spell); MasteryRuntime.sync(player); return "";
    }
    public static String grantingNode(ServerPlayer player,String spell) {
        return MasteryRuntime.definitions().nodes().values().stream().filter(n->!n.spellModifier()&&(n.spell().equals(spell)||grants(n,"mastery:unlock_spell","spell",spell)))
                .filter(n->EffectService.eligibleRank(player,n)>0).sorted(Comparator.<NodeDefinition>comparingInt(n->EffectService.eligibleRank(player,n)).reversed().thenComparing(NodeDefinition::id))
                .map(NodeDefinition::id).findFirst().orElse("");
    }
    /** Highest active granting-node rank supplies charge progression for singular and array-based spell grants. */
    public static int grantingRank(ServerPlayer player,String spell) {
        return MasteryRuntime.definitions().nodes().values().stream().filter(n->!n.spellModifier()&&(n.spell().equals(spell)||grants(n,"mastery:unlock_spell","spell",spell)))
                .mapToInt(n->EffectService.effectiveRank(player,n)).max().orElse(1);
    }
    /** Array grants may upgrade the native starting level without changing legacy charge-only node ranks. */
    public static int baseLevel(ServerPlayer player,String spell) {
        var definition=MasteryRuntime.definitions().spells().get(spell);
        if(definition==null)return 1;
        int level=definition.level();
        for(var node:MasteryRuntime.definitions().nodes().values()) {
            int rank=EffectService.effectiveRank(player,node);if(rank<=0||node.spellModifier())continue;
            for(var raw:node.effects()) {
                var effect=resolve(raw);
                if(effect!=null&&text(effect,"type","").equals("mastery:unlock_spell")&&text(effect,"spell","").equals(spell))
                    level=Math.max(level,(int)Math.clamp((long)number(effect,"level",1)+(long)number(effect,"levels_per_rank",0)*(rank-1),1,255));
            }
        }
        return level;
    }
    /** Native effective level, evaluated once for a prepared spell (including affinity and enabled modifiers). */
    public static int effectiveLevel(ServerPlayer player,String spell) {
        return Math.max(1,SpellRegistry.getSpell(spell).getLevelFor(baseLevel(player,spell),player));
    }
    public static List<io.redspace.ironsspellbooks.api.spells.SpellData> preparedSpells(ServerPlayer player) {
        String context=context(player);
        return MasteryRuntime.progress(player).loadouts().getOrDefault(context,List.of()).stream()
                .limit(Math.min(capacity(player),BindingSlots.limit(context))).filter(id->owned(player,id))
                .map(id->new io.redspace.ironsspellbooks.api.spells.SpellData(SpellRegistry.getSpell(id),effectiveLevel(player,id))).toList();
    }
    private static List<JsonObject> modifierEffects(NodeDefinition node,String spell) {
        return node.effects().stream().map(SpellService::resolve).filter(Objects::nonNull)
                .filter(e->text(e,"type","").equals("mastery:spell_modifier")&&(!e.has("spell")||text(e,"spell","").equals(spell))).toList();
    }
    public static JsonObject spellSettings(ServerPlayer player,String spell) {
        var definition=MasteryRuntime.definitions().spells().get(spell);
        return SettingsResolver.resolved(MasteryRuntime.definitions(),definition==null?"":definition.tree(),spell,grantingNode(player,spell));
    }
    public static int modifierLimit(ServerPlayer player,SpellDefinition spell) {
        int level=Math.max(baseLevel(player,spell.id()),grantingRank(player,spell.id()));
        // All active passive upgrades and selected modifier upgrades contribute to slot growth.
        for(var node:MasteryRuntime.definitions().nodes().values()) {
            if(!EffectService.active(player,node))continue;
            if(node.treeModifier()&&!com.cappleapple.mastery.mechanics.TreeModifiers.matches(node,com.cappleapple.mastery.mechanics.DamageContexts.capture(SpellRegistry.getSpell(spell.id()).getDamageSource(player))))continue;
            if(node.spellModifier()&&(!MasteryRuntime.progress(player).modifiers(spell.id()).contains(node.id())||!related(node,spell.id(),new HashSet<>())))continue;
            for(var effect:modifierEffects(node,spell.id()))if(effect.has("spell_level"))
                level=(int)Math.clamp((long)level+(long)number(effect,"spell_level",0)*EffectService.effectiveRank(player,node),1,Integer.MAX_VALUE);
        }
        int result=(int)Math.clamp(SettingsResolver.modifierSlots(spellSettings(player,spell.id()),level)+EffectService.bonus(player,"modifier_slots",spell.id()),0,4096);
        var tree=MasteryRuntime.definitions().trees().get(spell.tree());
        int cap=tree==null?-1:tree.capAt(MasteryRuntime.worldTier(player)).modifierSlots();
        return cap<0?result:Math.min(result,cap);
    }
    public static String setModifier(ServerPlayer player,String spell,String nodeId,boolean enabled) {
        var definitions=MasteryRuntime.definitions(); var progress=MasteryRuntime.progress(player);
        var definition=definitions.spells().get(spell); var node=definitions.nodes().get(nodeId);
        if(definition==null||node==null||!node.spellModifier()) return "Unknown spell or modifier";
        Set<String> modifiers=progress.modifiers(spell);
        if(enabled) {
            String error=LoadoutRules.modifier(modifiers.size(),modifierLimit(player,definition),EffectService.eligibleRank(player,node)>0,
                    related(node,spell,new HashSet<>()),modifiers.contains(nodeId));
            if(!error.isBlank()) return error;
        }
        interrupt(player,"modifiers changed");
        if(enabled) modifiers.add(nodeId); else modifiers.remove(nodeId);
        progress.node(nodeId).toggled(enabled||progress.activeModifiers().values().stream().anyMatch(ids->ids.contains(nodeId)));
        MasteryRuntime.sync(player); return "";
    }
    private static boolean related(NodeDefinition node,String spell,Set<String> visited) {
        if(!visited.add(node.id())) return false;
        if(node.spell().equals(spell)||grants(node,"mastery:unlock_spell","spell",spell)||grants(node,"mastery:spell_modifier","spell",spell))return true;
        if(!node.spell().isBlank()||node.effects().stream().map(SpellService::resolve).filter(Objects::nonNull).anyMatch(e->text(e,"type","").equals("mastery:spell_modifier")&&e.has("spell")))return false;
        return node.dependencyLeaves().stream().map(dep -> MasteryRuntime.definitions().nodes().get(dep.node())).filter(Objects::nonNull)
                .anyMatch(parent -> related(parent,spell,visited));
    }
    /** The same deterministic slot budget is used for native spell bonuses and combat trigger attribution. */
    public static Set<String> activeModifiers(ServerPlayer player,String spell) {
        var definition=MasteryRuntime.definitions().spells().get(spell);
        if(definition==null||!owned(player,spell))return Set.of();
        var result=new LinkedHashSet<String>();int limit=modifierLimit(player,definition);
        for(String id:new TreeSet<>(MasteryRuntime.progress(player).modifiers(spell))) {
            var node=MasteryRuntime.definitions().nodes().get(id);
            if(node==null||!node.spellModifier()||!EffectService.active(player,node)||!related(node,spell,new HashSet<>()))continue;
            if(result.size()>=limit)break;
            result.add(id);
        }
        return Set.copyOf(result);
    }
    public static SpellModifiers modifiers(ServerPlayer player,String spell) {
        SpellModifiers result=new SpellModifiers(); var definition=MasteryRuntime.definitions().spells().get(spell);
        if(definition==null||!owned(player,spell)) return result;
        var passiveHandler=SpellModifierRegistry.get("mastery:native_spell");
        if(passiveHandler!=null)for(var node:MasteryRuntime.definitions().nodes().values().stream().sorted(Comparator.comparing(NodeDefinition::id)).toList()) {
            if(node.spellModifier()||!EffectService.active(player,node))continue;
            if(node.treeModifier()&&!com.cappleapple.mastery.mechanics.TreeModifiers.matches(node,com.cappleapple.mastery.mechanics.DamageContexts.capture(SpellRegistry.getSpell(spell).getDamageSource(player))))continue;
            for(var effect:modifierEffects(node,spell))passiveHandler.apply(player,spell,EffectService.effectiveRank(player,node),effect,result);
        }
        for(String id:new TreeSet<>(activeModifiers(player,spell))) {
            var node=MasteryRuntime.definitions().nodes().get(id);
            var handler=SpellModifierRegistry.get(node.modifier());if(handler==null)continue;
            var effects=modifierEffects(node,spell);
            if(effects.isEmpty())handler.apply(player,spell,EffectService.effectiveRank(player,node),new JsonObject(),result);
            else for(var effect:effects)handler.apply(player,spell,EffectService.effectiveRank(player,node),effect,result);
        }
        return result;
    }
    public static String press(ServerPlayer player,int slot) {
        if(slot<0||slot>=capacity(player)||!player.isAlive()||player.isSpectator()) return "Cannot use this spell slot";
        reconcile(player); String context=context(player);
        if(context.isBlank()) return "No matching spell context";
        if(slot>=BindingSlots.limit(context))return "Invalid spell slot";
        String id=BindingSlots.get(MasteryRuntime.progress(player).loadouts(),context,slot);
        var definition=MasteryRuntime.definitions().spells().get(id);
        if(definition==null||!owned(player,id)) return "No unlocked spell prepared in this slot";
        AbstractSpell spell=SpellRegistry.getSpell(id);
        if(spell==SpellRegistry.none()||!spell.isEnabled()) return "Native spell is unavailable";
        var magic=MagicData.getPlayerMagicData(player);
        if(magic.isCasting()) {
            boolean same=magic.getCastingSpellId().equals(id);
            Utils.serverSideCancelCast(player,magic.getCastType()!=CastType.LONG);
            STARTED.remove(player.getUUID());
            if(same) { MasteryRuntime.sync(player); return ""; }
        }
        ChargeService.prepare(player,id,slot);
        INITIATING.set(new Intent(player.getUUID(),id));
        boolean started;
        try {
            started=spell.attemptInitiateCast(ItemStack.EMPTY,spell.getLevelFor(baseLevel(player,id),player),player.level(),player,
                    CastSource.SPELLBOOK,true,Curios.SPELLBOOK_SLOT);
        } finally { INITIATING.remove(); }
        if(started) STARTED.put(player.getUUID(),new Started(id,slot));else ChargeService.clear(player);
        MasteryRuntime.sync(player);
        return ""; // Native cast checks already provide their own feedback.
    }
    /** Native quick casts are press/toggle actions; release never executes a second cast or bypasses cast time. */
    public static void release(ServerPlayer player,int slot) {ChargeService.release(player,slot);}
    public static boolean initiating(ServerPlayer player,String spell) {
        Intent intent=INITIATING.get(); return intent!=null&&intent.player().equals(player.getUUID())&&intent.spell().equals(spell);
    }
    public static void tick(ServerPlayer player) {
        ChargeService.tick(player);
        String current=context(player); String previous=CONTEXTS.put(player.getUUID(),current);
        if(!current.equals(previous)) MasteryRuntime.sync(player);
        if(player.tickCount%20==0) {
            int capacity=capacity(player); Integer previousCapacity=CAPACITIES.put(player.getUUID(),capacity);
            if(reconcile(player)||previousCapacity==null||previousCapacity!=capacity) MasteryRuntime.sync(player);
        }
        if(player.tickCount%10==0) {
            var charges=com.cappleapple.mastery.integration.TempoSpellStats.charges(player);
            if(!charges.equals(CHARGE_STATS.put(player.getUUID(),charges)))MasteryRuntime.sync(player);
        }
        var magic=MagicData.getPlayerMagicData(player);
        boolean busy=magic.isCasting()||magic.getPlayerCooldowns().hasCooldownsActive(); Boolean before=BUSY.put(player.getUUID(),busy);
        if((busy&&player.tickCount%5==0)||before==null||busy!=before) MasteryRuntime.sync(player);
        var started=STARTED.get(player.getUUID());
        if(started!=null&&(!magic.isCasting()||!magic.getCastingSpellId().equals(started.spell()))) STARTED.remove(player.getUUID());
    }
    /** Administrative/loadout changes cancel only a cast initiated by Mastery. Native combat interruption remains Iron-owned. */
    public static void interrupt(ServerPlayer player,String reason) {
        if(reason.equals("damage")||reason.equals("context changed")) return;
        ChargeService.clear(player);
        Started started=STARTED.remove(player.getUUID()); var magic=MagicData.getPlayerMagicData(player);
        if(started!=null&&magic.isCasting()&&magic.getCastingSpellId().equals(started.spell())) Utils.serverSideCancelCast(player);
        MasteryRuntime.sync(player);
    }
    public static void forget(ServerPlayer player) {
        interrupt(player,"logout"); CONTEXTS.remove(player.getUUID()); CAPACITIES.remove(player.getUUID()); BUSY.remove(player.getUUID()); CHARGE_STATS.remove(player.getUUID());
    }
    public static boolean reconcile(ServerPlayer player) {
        var progress=MasteryRuntime.progress(player); var definitions=MasteryRuntime.definitions(); int count=0,budget=capacity(player); boolean changed=false;
        for(String mode:List.of("hotbar","quick_cast")) {
            count=0;Map<String,Integer> perTree=new HashMap<>();
            for(String context:new TreeSet<>(BindingSlots.modeLoadouts(progress.loadouts(),mode).keySet())) {
                var slots=progress.loadout(context); Set<String> unique=new HashSet<>();
                if(slots.size()>BindingSlots.limit(context)){slots.subList(BindingSlots.limit(context),slots.size()).clear();changed=true;}
                for(int index=0;index<slots.size();index++) {
                    String id=slots.get(index);if(id.isBlank())continue;
                    var spell=definitions.spells().get(id);
                    int cap=spell==null?-1:definitions.trees().get(spell.tree()).capAt(MasteryRuntime.worldTier(player)).activeCapacity();
                    boolean valid=spell!=null&&unlocked(player,id)&&unique.add(id)&&count<budget&&index<budget
                            &&(cap<0||perTree.getOrDefault(spell.tree(),0)<cap);
                    if(valid){count++;perTree.merge(spell.tree(),1,Integer::sum);}else{slots.set(index,"");changed=true;}
                }
            }
        }
        for(var entry:progress.activeModifiers().entrySet()) {
            var spell=definitions.spells().get(entry.getKey()); int used=0;
            for(String nodeId:new TreeSet<>(entry.getValue())) {
                var node=definitions.nodes().get(nodeId);
                if(spell==null||node==null||!node.spellModifier()||EffectService.eligibleRank(player,node)==0||!related(node,entry.getKey(),new HashSet<>())
                        ||used>=modifierLimit(player,spell)) { entry.getValue().remove(nodeId); changed=true; }
                else used++;
            }
        }
        return changed;
    }
    public static JsonObject snapshot(ServerPlayer player) {
        JsonObject result=new JsonObject(); result.addProperty("context",context(player)); result.addProperty("capacity",capacity(player));
        long time=player.level().getGameTime(); result.addProperty("game_time",time);
        JsonObject limits=new JsonObject();MasteryRuntime.definitions().spells().forEach((id,spell)->limits.addProperty(id,modifierLimit(player,spell)));result.add("modifier_limits",limits);
        JsonArray available=new JsonArray(); MasteryRuntime.definitions().spells().keySet().stream().filter(id -> owned(player,id)).sorted().forEach(available::add);
        var levels=new JsonObject();for(var value:available){String id=value.getAsString();levels.addProperty(id,effectiveLevel(player,id));}result.add("spell_levels",levels);
        var stats=new JsonObject();var equipped=new JsonObject();var eligible=new JsonArray();
        for(var node:MasteryRuntime.definitions().nodes().values())if(node.spellModifier()&&MasteryRuntime.progress(player).rank(node.id())==0)
            for(var value:available) {
                String id=value.getAsString();
                if(related(node,id,new HashSet<>())&&MasteryRuntime.progress(player).modifiers(id).size()<limits.get(id).getAsInt()) {eligible.add(node.id());break;}
            }
        for(var value:available) {
            String id=value.getAsString();var spell=SpellRegistry.getSpell(id);int level=levels.get(id).getAsInt();var modifiers=modifiers(player,id);
            var stat=new JsonObject();
            stat.addProperty("mana",SpellModifiers.scale(spell.getManaCost(level),modifiers.manaMultiplier()));
            stat.addProperty("cast_ticks",spell.getEffectiveCastTime(level,player));
            stat.addProperty("cooldown_ticks",com.cappleapple.mastery.integration.TempoSpellStats.cooldown(spell,SpellModifiers.scale(io.redspace.ironsspellbooks.capabilities.magic.MagicManager.getEffectiveSpellCooldown(spell,player,io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK),modifiers.cooldownMultiplier())));
            stats.add(id,stat);var nodes=new JsonArray();activeModifiers(player,id).stream().sorted().forEach(nodes::add);equipped.add(id,nodes);
        }
        result.add("spell_charges",com.cappleapple.mastery.integration.TempoSpellStats.charges(player));
        result.add("spell_stats",stats);result.add("equipped_modifiers",equipped);result.add("available_modifier_nodes",eligible);
        result.add("available",available); JsonObject cooldowns=new JsonObject(); var magic=MagicData.getPlayerMagicData(player);
        magic.getPlayerCooldowns().getSpellCooldowns().forEach((id,cooldown) -> cooldowns.addProperty(id,time+cooldown.getCooldownRemaining()));
        result.add("cooldowns",cooldowns); JsonObject casting=new JsonObject();
        if(magic.isCasting()) {
            casting.addProperty("spell",magic.getCastingSpellId()); casting.addProperty("ticks",magic.getCastDuration()-magic.getCastDurationRemaining());
            casting.addProperty("max",magic.getCastDuration()); var started=STARTED.get(player.getUUID());
            if(started!=null) casting.addProperty("slot",started.slot());
        }
        result.add("casting",casting); return result;
    }
    public static List<String> validateDefinitions(DefinitionSet definitions) { return SpellValidation.validate(definitions); }
    private static double number(JsonObject json,String key,double fallback) { return json.has(key)?json.get(key).getAsDouble():fallback; }
    private static String text(JsonObject json,String key,String fallback) { return json.has(key)?json.get(key).getAsString():fallback; }
}
