package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.progression.*;
import com.google.gson.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class PromotedTreeGameTests {
    private PromotedTreeGameTests() {}
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void promotedTreeGatesUsageRewardsAndEffectsUntilItsRetainedPrerequisitesPass(GameTestHelper helper) {
        var original=MasteryRuntime.definitions();var player=MasteryTestPlayers.create(helper);
        try {
            var snapshot=original.toJson();var nodes=snapshot.getAsJsonObject("nodes");
            nodes.add("mastery:promoted_test_parent",json("{\"tree\":\"mastery:fire\"}"));
            nodes.add("mastery:promoted_test_root",json("{\"tree\":\"mastery:fire\",\"dependencies\":[\"mastery:promoted_test_parent\"],\"root_tree\":{\"id\":\"mastery:promoted_test_tree\",\"xp_base\":10,\"xp_growth\":0}}"));
            nodes.add("mastery:promoted_test_child",json("{\"tree\":\"mastery:fire\",\"dependencies\":[\"mastery:promoted_test_root\"],\"max_rank\":6,\"effects\":[{\"type\":\"mastery:attribute\",\"attribute\":\"minecraft:generic.luck\",\"amount\":1}]}"));
            snapshot.getAsJsonObject("xp_sources").add("mastery:promoted_test_once",json("{\"tree\":\"mastery:promoted_test_tree\",\"event\":\"test:promotion\",\"amount\":10,\"points\":2,\"once\":true}"));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));var d=MasteryRuntime.definitions();var state=MasteryRuntime.progress(player);
            MasteryRuntime.usage(player,"test:promotion",new JsonObject());
            helper.assertTrue(!state.usageGrants().contains("mastery:promoted_test_once"),"Locked root consumed one-time XP source");
            helper.assertTrue(!state.trees().containsKey("mastery:promoted_test_tree"),"Locked root earned or discovered proficiency");
            ProgressionService.grantPoints(d,state,"mastery:fire",2);
            helper.assertTrue(ProgressionService.purchase(d,state,"mastery:promoted_test_parent",-1,r->true).success(),"Prerequisite purchase failed");
            helper.assertTrue(ProgressionService.purchase(d,state,"mastery:promoted_test_root",-1,r->true).success(),"Root did not spend original tree points");
            MasteryRuntime.usage(player,"test:promotion",new JsonObject());
            helper.assertTrue(state.tree("mastery:promoted_test_tree").points()==3,"Independent level point plus one-time direct points were not awarded");
            helper.assertTrue(ProgressionService.purchase(d,state,"mastery:promoted_test_child",-1,r->true).success(),"Child did not spend generated tree points");
            EffectService.rebuild(player);helper.assertTrue(player.getAttributeValue(Attributes.LUCK)==1,"Active promoted branch did not apply child attribute");
            ProgressionService.setRank(d,state,"mastery:promoted_test_parent",0,-1);EffectService.rebuild(player);
            helper.assertTrue(player.getAttributeValue(Attributes.LUCK)==0,"Reset prerequisite left promoted branch effects active");
            helper.assertTrue(!MasteryRuntime.grantXp(player,"mastery:promoted_test_tree",1).success(),"Reset prerequisite left promoted XP active");
            helper.assertTrue(state.tree("mastery:promoted_test_tree").points()==2,"Disabling a branch destroyed stored currency");
            helper.succeed();
        }finally{MasteryRuntime.install(original);MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void flameBladeRequiresTwoHandedMeleeFireButCreditsItsOwnSkills(GameTestHelper helper) {
        var original=MasteryRuntime.definitions();var player=MasteryTestPlayers.create(helper);
        try {
            var state=MasteryRuntime.progress(player);
            for(String tree:java.util.List.of("mastery:fire","mastery:two_handed")) {
                ProgressionService.setLevel(original,state,tree,3,-1);
                ProgressionService.setRank(original,state,tree+"/foundation",1,-1);
            }
            ProgressionService.setRank(original,state,"mastery:spellblade_practice",1,-1);
            var event=json("{\"damage\":4,\"projectile\":false,\"melee\":true,\"school\":\"irons_spellbooks:fire\",\"context\":\"mastery:two_handed\"}");
            MasteryRuntime.usage(player,"damage",event);
            helper.assertTrue(state.tree("mastery:flame_blade").lifetimeXp()==4,"Two-handed melee fire should earn XP");
            for(String key:java.util.List.of("school","context","melee","projectile")) {
                var wrong=event.deepCopy();
                switch(key) {
                    case "school" -> wrong.addProperty(key,"irons_spellbooks:ice");
                    case "context" -> wrong.addProperty(key,"mastery:one_handed");
                    case "melee" -> wrong.addProperty(key,false);
                    case "projectile" -> wrong.addProperty(key,true);
                }
                MasteryRuntime.usage(player,"damage",wrong);
            }
            helper.assertTrue(state.tree("mastery:flame_blade").lifetimeXp()==4,"Unrelated damage qualified");
            ProgressionService.setRank(original,state,"mastery:flame_blade/flaming_strike",1,-1);
            helper.assertTrue(com.cappleapple.mastery.spells.SpellService.unlocked(player,"irons_spellbooks:flaming_strike"),"Bundled Flaming Strike was not unlocked");
            ProgressionService.setRank(original,state,"mastery:flame_blade/flaming_strike",3,-1);
            helper.assertTrue(com.cappleapple.mastery.spells.SpellService.baseLevel(player,"irons_spellbooks:flaming_strike")==3,"Flaming Strike ranks did not upgrade its native base level");
            event.addProperty("skill_spell","irons_spellbooks:flaming_strike");
            MasteryRuntime.usage(player,"damage",event);
            helper.assertTrue(state.tree("mastery:flame_blade").lifetimeXp()==8,"Skill and matching source counted damage twice");
            event.addProperty("school","irons_spellbooks:ice");event.addProperty("context","mastery:one_handed");event.addProperty("melee",false);
            MasteryRuntime.usage(player,"damage",event);
            helper.assertTrue(state.tree("mastery:flame_blade").lifetimeXp()==12,"Owned skill with a different damage type did not credit its tree");
            state.node("mastery:flame_blade/flaming_strike").toggled(false);
            MasteryRuntime.usage(player,"damage",event);
            helper.assertTrue(state.tree("mastery:flame_blade").lifetimeXp()==12,"Inactive skill earned automatic skill XP");
            var snapshot=original.toJson();snapshot.getAsJsonObject("settings").getAsJsonObject("mastery:defaults").addProperty("skill_damage_xp",0);
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));state.node("mastery:flame_blade/flaming_strike").toggled(true);
            MasteryRuntime.usage(player,"damage",event);
            helper.assertTrue(state.tree("mastery:flame_blade").lifetimeXp()==12,"Data setting did not disable skill XP");
            var custom=original.toJson();var source=custom.getAsJsonObject("xp_sources").getAsJsonObject("mastery:flame_blade_damage");source.addProperty("amount",2);source.addProperty("points",1);
            MasteryRuntime.install(DefinitionSet.fromJson(custom));int points=state.tree("mastery:flame_blade").points();
            event.addProperty("school","irons_spellbooks:fire");event.addProperty("context","mastery:two_handed");event.addProperty("melee",true);
            MasteryRuntime.usage(player,"damage",event);
            helper.assertTrue(state.tree("mastery:flame_blade").lifetimeXp()==20&&state.tree("mastery:flame_blade").points()==points+1,"Automatic skill XP replaced an authored XP rate or direct point reward");
            helper.succeed();
        } finally {MasteryRuntime.install(original);MasteryRuntime.logout(player);player.discard();}
    }
    private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
}
