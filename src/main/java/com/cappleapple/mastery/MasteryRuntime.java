package com.cappleapple.mastery;

import com.cappleapple.mastery.spells.SpellService;
import com.cappleapple.mastery.api.MasteryAPI;
import com.cappleapple.mastery.api.ProficiencyChangedEvent;
import com.cappleapple.mastery.config.MasteryConfig;
import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.integration.OptionalIntegrations;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.cappleapple.mastery.progression.*;
import com.cappleapple.mastery.requirements.*;
import com.cappleapple.mastery.storage.MasteryAttachments;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.util.*;

/** Server-thread application service. Definitions are immutable snapshots, progress is player-owned. */
public final class MasteryRuntime {
    private static volatile DefinitionSet definitions=DefinitionSet.EMPTY;
    private static long definitionRevision;
    public static volatile List<String> lastReloadErrors=List.of();
    private static Map<String,List<XpSourceDefinition>> xpIndex=Map.of();
    private static final Set<UUID> DIRTY=new HashSet<>();
    private static final Map<UUID,String> LAST_ERRORS=new HashMap<>();
    private static final Map<UUID,Long> REQUEST_TICK=new HashMap<>();
    private static final Map<UUID,Integer> REQUEST_COUNT=new HashMap<>();
    private static final Map<UUID,Integer> LAST_TIER=new HashMap<>();
    private static final Set<UUID> USAGE_GUARD=new HashSet<>();
    private MasteryRuntime() {}
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(()->{SpellService.initialize();OptionalIntegrations.initialize();com.cappleapple.mastery.integration.NeedsNotNecessitiesIntegration.initialize();});
    }
    public static DefinitionSet definitions(){return definitions;}
    public static long definitionRevision(){return definitionRevision;}
    public static PlayerProgress progress(ServerPlayer player){return player.getData(MasteryAttachments.PROGRESS);}
    public static int worldTier(ServerPlayer player){return MasteryAPI.worldTier(player.serverLevel());}
    public static void install(DefinitionSet next) {
        com.cappleapple.mastery.mechanics.MechanicsRuntime.clear();
        definitions=next; definitionRevision++;
        Map<String,List<XpSourceDefinition>> index=new HashMap<>();
        for(var source:next.xpSources().values())index.computeIfAbsent(source.event(),s->new ArrayList<>()).add(source);
        xpIndex=index;
        var server=ServerLifecycleHooks.getCurrentServer();
        if(server!=null)server.execute(()->{
            for(var player:server.getPlayerList().getPlayers()) {
                SpellService.interrupt(player,"reload");
                ProgressionService.reconcile(next,progress(player));
                SpellService.reconcile(player); EffectService.rebuild(player);
                sendDefinitions(player); sync(player);
            }
        });
    }
    public static void sendDefinitions(ServerPlayer player) {
        var snapshot=definitions.toJson();
        snapshot.addProperty("definition_revision",definitionRevision);
        MasteryNetwork.send(player,"definitions",snapshot.toString());
    }
    public static void sync(ServerPlayer player) { DIRTY.add(player.getUUID()); }
    public static void flush(ServerPlayer player) {
        if(!DIRTY.remove(player.getUUID()))return;
        ProgressionService.refreshUnlocks(definitions,progress(player),worldTier(player),j->RequirementRegistry.test(new RequirementContext(player,new JsonObject()),j));
        var json=progress(player).toJson();
        json.addProperty("edit_mode",com.cappleapple.mastery.editor.EditorService.enabled(player));
        json.addProperty("definition_revision",definitionRevision);
        var ability=SpellService.snapshot(player);
        ability.entrySet().forEach(e->json.add(e.getKey(),e.getValue()));
        json.addProperty("last_error",LAST_ERRORS.getOrDefault(player.getUUID(),""));
        json.addProperty("world_tier",worldTier(player)); json.addProperty("world_id",com.cappleapple.mastery.storage.WorldIdentity.get(player.server));
        MasteryNetwork.send(player,"progress",json.toString());
    }
    public static void tick(ServerPlayer player) {
        EffectService.refreshWeaponContext(player);
        SpellService.tick(player);
        if(player.tickCount%20==0) {
            com.cappleapple.mastery.editor.EditorService.enabled(player);
            EffectService.refresh(player);
            int tier=worldTier(player); Integer previous=LAST_TIER.put(player.getUUID(),tier);
            if(previous==null || previous!=tier){EffectService.rebuild(player);SpellService.reconcile(player);sync(player);}
        }
        if(player.tickCount%5==0)flush(player);
    }
    public static void login(ServerPlayer player) {
        ProgressionService.reconcile(definitions,progress(player));
        SpellService.reconcile(player); EffectService.rebuild(player);
        sendDefinitions(player);sync(player);flush(player);
    }
    public static void logout(ServerPlayer player) {
        SpellService.forget(player);EffectService.clear(player);
        com.cappleapple.mastery.editor.EditorService.forget(player);
        LAST_ERRORS.remove(player.getUUID());DIRTY.remove(player.getUUID());REQUEST_TICK.remove(player.getUUID());REQUEST_COUNT.remove(player.getUUID());LAST_TIER.remove(player.getUUID());
    }
    public static ProgressionService.Change grantXp(ServerPlayer player,String tree,double amount) {
        if(!definitions.trees().containsKey(tree))return ProgressionService.Change.failure("Unknown tree: "+tree);
        int before=progress(player).tree(tree).level();
        var result=ProgressionService.addXp(definitions,progress(player),tree,amount,worldTier(player));
        if(result.success()) {
            NeoForge.EVENT_BUS.post(new ProficiencyChangedEvent(player,tree,before,progress(player).tree(tree).level()));
            sync(player);
        }
        return result;
    }
    public static ProgressionService.Change grantPoints(ServerPlayer player,String tree,int amount) {
        var result=ProgressionService.grantPoints(definitions,progress(player),tree,amount);
        if(result.success())sync(player);
        return result;
    }
    public static void usage(ServerPlayer player,String event,JsonObject data) {
        if(!player.isAlive() || player.isSpectator() || (!MasteryConfig.CREATIVE_XP.get()&&player.isCreative()))return;
        if(!USAGE_GUARD.add(player.getUUID()))return;
        try {
            JsonObject context=data.deepCopy();
            if(!context.has("context"))context.addProperty("context",SpellService.combatContext(player));
            var state=progress(player);
            for(var source:xpIndex.getOrDefault(event,List.of())) {
                if(source.once()&&state.usageGrants().contains(source.id()))continue;
                if(!RequirementRegistry.test(new RequirementContext(player,context),source.condition()))continue;
                double scale=source.scale().equals("none")?1:RequirementRegistry.number(context,source.scale(),0);
                double amount=source.amount()*scale;
                if(!Double.isFinite(amount)||amount<0)continue;
                var result=grantXp(player,source.tree(),amount);
                if(result.success()) {
                    if(source.points()>0)grantPoints(player,source.tree(),source.points());
                    if(source.once())state.usageGrants().add(source.id());
                }
            }
            EffectService.onUsage(player,event,context);
        } finally { USAGE_GUARD.remove(player.getUUID()); }
    }
    public static void handleAction(ServerPlayer player,String action,String id,String value,int number) {
        long tick=player.serverLevel().getGameTime();
        if(REQUEST_TICK.getOrDefault(player.getUUID(),-1L)!=tick){REQUEST_TICK.put(player.getUUID(),tick);REQUEST_COUNT.put(player.getUUID(),0);}
        int count=REQUEST_COUNT.merge(player.getUUID(),1,Integer::sum);
        if(count>16||!player.isAlive()||player.isSpectator())return;
        String error="";
        switch(action) {
            case "editor_get", "editor_save", "editor_delete" -> error=com.cappleapple.mastery.editor.EditorService.handle(player,action,id,value);
            case "purchase" -> {
                var result=com.cappleapple.mastery.costs.CostService.purchase(definitions,progress(player),id,worldTier(player),
                        json->RequirementRegistry.test(new RequirementContext(player,new JsonObject()),json),player);
                if(!result.success())error=result.message();else {
                    EffectService.rebuild(player);
                    if(progress(player).rank(id)==1)SpellService.toggleNode(player,definitions.nodes().get(id),true);
                }
            }
            case "toggle" -> {
                var node=definitions.nodes().get(id);
                if(node==null||progress(player).rank(id)==0)error="Purchase this node before toggling it";
                else {
                    error=SpellService.toggleNode(player,node,number!=0);
                    if(error.isBlank()) { EffectService.rebuild(player);SpellService.reconcile(player); }
                }
            }
            case "binding_mode" -> error=SpellService.bindingMode(player,id);
            case "equip" -> error=SpellService.assign(player,value,number,id);
            case "modifier" -> error=SpellService.setModifier(player,id,value,number!=0);
            case "press" -> error=SpellService.press(player,number);
            case "release" -> SpellService.release(player,number);
            case "refresh" -> {}
            default -> {return;}
        }
        LAST_ERRORS.put(player.getUUID(),error);
        if(!error.isBlank()) {
            player.displayClientMessage(Component.literal(error),true);
            if(action.startsWith("editor_"))com.cappleapple.mastery.editor.EditorService.status(player,false,error);
        }
        sync(player);
    }
}
