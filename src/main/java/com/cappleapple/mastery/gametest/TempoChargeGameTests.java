package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.progression.ProgressionService;
import com.cappleapple.mastery.spells.SpellService;
import com.google.gson.*;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class TempoChargeGameTests {
    private static final String FIRE="irons_spellbooks:fireball",MOD="mastery:charge_test";
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void extraChargesRejectInvalidDefinitions(GameTestHelper h) {
        for(String value:List.of("-1","1.5","10001","\"two\"")) {
            var json=MasteryRuntime.definitions().toJson();json.getAsJsonObject("effects").add("mastery:invalid_charges",JsonParser.parseString("{\"type\":\"mastery:spell_modifier\",\"extra_charges\":"+value+"}"));
            h.assertTrue(SpellService.validateDefinitions(DefinitionSet.fromJson(json)).stream().anyMatch(e->e.contains("extra_charges")),"Invalid charge value accepted: "+value);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void extraChargesFollowRanksAssignmentAndTheTempoEvent(GameTestHelper h)throws Exception {
        var original=MasteryRuntime.definitions();var player=MasteryTestPlayers.createCombat(h);
        try {
            var json=original.toJson();
            var node=JsonParser.parseString("{\"tree\":\"mastery:fire\",\"name\":\"Extra Charges\",\"type\":\"modifier\",\"spell\":\"irons_spellbooks:fireball\",\"dependencies\":[\"mastery:fireball\"],\"max_rank\":3,\"effects\":[{\"type\":\"mastery:spell_modifier\",\"extra_charges\":2}]}");
            json.getAsJsonObject("nodes").add(MOD,node);json.getAsJsonObject("nodes").add(MOD+"_second",node.deepCopy());
            MasteryRuntime.install(DefinitionSet.fromJson(json));
            rank(player,"mastery:fireball",1);rank(player,"mastery:lightning_bolt",1);rank(player,MOD,1);
            check(h,player,FIRE,0); // Purchased modifiers require assignment.
            h.assertTrue(SpellService.setModifier(player,FIRE,MOD,true).isEmpty(),"Assign extra charges");check(h,player,FIRE,2);
            rank(player,MOD,3);check(h,player,FIRE,6);check(h,player,"irons_spellbooks:lightning_bolt",0);
            h.assertTrue(SpellService.effectiveLevel(player,FIRE)==1,"Extra charges must not change spell level");
            rank(player,MOD+"_second",1);SpellService.setModifier(player,FIRE,MOD+"_second",true);check(h,player,FIRE,8);
            SpellService.setModifier(player,FIRE,MOD,false);check(h,player,FIRE,2);
            SpellService.setModifier(player,FIRE,MOD,true);check(h,player,FIRE,8);
            SpellService.toggleNode(player,MasteryRuntime.definitions().nodes().get("mastery:fireball"),false);check(h,player,FIRE,0);
            h.succeed();
        } finally {MasteryRuntime.logout(player);player.discard();MasteryRuntime.install(original);}
    }
    @GameTest(batch="tempo_held",templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=240)
    public static void heldCastConsumesWholeBaseChargesAndCapsWhenOnlyOneRemains(GameTestHelper h)throws Exception {
        if(!net.neoforged.fml.ModList.get().isLoaded("temponottime")){h.succeed();return;}
        var original=MasteryRuntime.definitions();var player=MasteryTestPlayers.createCombat(h);
        var json=original.toJson();json.getAsJsonObject("nodes").add(MOD,JsonParser.parseString("{\"tree\":\"mastery:fire\",\"type\":\"modifier\",\"spell\":\"irons_spellbooks:fireball\",\"dependencies\":[\"mastery:fireball\"],\"max_rank\":1,\"effects\":[{\"type\":\"mastery:spell_modifier\",\"extra_charges\":6}]}"));
        MasteryRuntime.install(DefinitionSet.fromJson(json));rank(player,"mastery:fireball",2);rank(player,"mastery:fireball/empowered",1);rank(player,MOD,1);
        SpellService.setModifier(player,FIRE,"mastery:fireball/empowered",true);SpellService.setModifier(player,FIRE,MOD,true);
        var item=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK);
        io.redspace.ironsspellbooks.api.spells.ISpellContainer.set(item,io.redspace.ironsspellbooks.api.spells.ISpellContainer.create(4,true,false));player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,item);
        var spell=SpellRegistry.getSpell(FIRE);var magic=io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);magic.setMana(500);h.getLevel().addNewPlayer(player);
        var type=Class.forName("com.cappleapple.temponottime.casting.CooldownManager");Object manager=type.getField("INSTANCE").get(null);
        var dataMethod=type.getMethod("data",net.minecraft.world.entity.player.Player.class);Object data=dataMethod.invoke(manager,player);
        var forSpell=data.getClass().getMethod("forSpell",String.class);var clear=type.getMethod("clear",ServerPlayer.class);
        SpellService.assign(player,SpellService.context(player),0,FIRE);SpellService.press(player,0);
        h.runAfterDelay(magic.getCastDuration()+4,()->{
            try {
                var rows=(List<?>)forSpell.invoke(data,FIRE);int units=com.cappleapple.mastery.integration.TempoHeldCharges.units(player,spell,2,4);
                h.assertTrue(units>1&&rows.size()==units,"Held cast should consume "+units+" base charges, got "+rows.size());
                double total=0;
                for(Object row:rows){h.assertTrue((int)row.getClass().getMethod("spellLevel").invoke(row)==4,"Held native level lost");total+=(double)row.getClass().getMethod("castingDraw").invoke(row);}
                double draw=(double)type.getMethod("castingDraw",net.minecraft.world.entity.player.Player.class,io.redspace.ironsspellbooks.api.spells.AbstractSpell.class,int.class).invoke(manager,player,spell,4);
                h.assertTrue(Math.abs(total-draw)<1e-6,"Splitting charges duplicated reserve debt");
                clear.invoke(manager,player);com.cappleapple.mastery.spells.ChargeService.clear(player);
                SpellService.setModifier(player,FIRE,MOD,false);
                int maximum=(int)type.getMethod("maxCharges",net.minecraft.world.entity.player.Player.class,io.redspace.ironsspellbooks.api.spells.AbstractSpell.class,int.class).invoke(manager,player,spell,2);
                var add=data.getClass().getMethod("add",String.class,int.class,double.class,double.class,boolean.class,boolean.class,boolean.class);
                for(int i=1;i<maximum;i++)add.invoke(data,FIRE,2,0.0,10000.0,false,false,false);
                SpellService.press(player,0);
                h.runAfterDelay(magic.getCastDuration()+4,()->{
                    try {
                        var limited=(List<?>)forSpell.invoke(data,FIRE);
                        h.assertTrue(limited.size()==maximum,"Unaffordable hold overspent charges");
                        h.assertTrue((int)limited.getLast().getClass().getMethod("spellLevel").invoke(limited.getLast())==2,"Single remaining charge must retain modified base level");h.succeed();
                    }catch(Exception e){throw new RuntimeException(e);}finally{MasteryRuntime.logout(player);player.discard();MasteryRuntime.install(original);}
                });
            }catch(Exception|AssertionError e){MasteryRuntime.logout(player);player.discard();MasteryRuntime.install(original);throw new RuntimeException(e);}
        });
    }
    private static void rank(ServerPlayer player,String id,int rank){var r=ProgressionService.setRank(MasteryRuntime.definitions(),MasteryRuntime.progress(player),id,rank,-1);if(!r.success())throw new IllegalStateException(r.message());}
    private static void check(GameTestHelper h,ServerPlayer player,String id,int bonus)throws Exception {
        h.assertTrue(SpellService.modifiers(player,id).extraCharges()==bonus,"Wrong rank-scaled bonus for "+id);
        var snapshot=SpellService.snapshot(player).getAsJsonObject("spell_charges");
        if(!net.neoforged.fml.ModList.get().isLoaded("temponottime")){h.assertTrue(snapshot.isEmpty(),"No charge stats without Tempo");return;}
        var manager=Class.forName("com.cappleapple.temponottime.casting.CooldownManager");
        int maximum=(int)manager.getMethod("maxCharges",net.minecraft.world.entity.player.Player.class,io.redspace.ironsspellbooks.api.spells.AbstractSpell.class,int.class)
                .invoke(manager.getField("INSTANCE").get(null),player,SpellRegistry.getSpell(id),SpellService.effectiveLevel(player,id));
        h.assertTrue(snapshot.get(id).getAsInt()==maximum,"Stats use current Tempo maximum for unslotted spells");
        var type=Class.forName("com.cappleapple.temponottime.api.event.ChargeCalculationEvent");
        var event=type.getConstructor(net.minecraft.world.entity.player.Player.class,io.redspace.ironsspellbooks.api.spells.AbstractSpell.class,int.class,int.class).newInstance(player,SpellRegistry.getSpell(id),1,4);
        // Existing event adjustments must be preserved rather than resetting to originalCharges.
        type.getMethod("setCharges",int.class).invoke(event,7);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post((net.neoforged.bus.api.Event)event);
        h.assertTrue((int)type.getMethod("getCharges").invoke(event)==7+bonus,"Public Tempo event did not preserve and add charges");
    }
}
