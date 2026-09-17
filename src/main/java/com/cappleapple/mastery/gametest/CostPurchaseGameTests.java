package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.costs.*;
import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.progression.ProgressionService;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class CostPurchaseGameTests {
    private CostPurchaseGameTests() {}
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void nestedCostsAreAtomicAndExperienceUsesActualLevels(GameTestHelper helper) {
        var original=MasteryRuntime.definitions();var player=MasteryTestPlayers.create(helper);
        try {
            var snapshot=original.toJson();
            snapshot.getAsJsonObject("nodes").add("mastery:cost_test",JsonParser.parseString("""
                {"tree":"mastery:fire","max_rank":2,"costs":{"and":[
                  {"or":[{"type":"points","tree":"mastery:fire","amount":2},{"type":"points","tree":"mastery:ice","amount":3}]},
                  {"type":"points","tree":"mastery:fire","amount":2},
                  {"type":"experience","amount":100},
                  {"type":"item","item":"minecraft:diamond","amount":2}
                ]}}
                """));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));var progress=MasteryRuntime.progress(player);
            progress.tree("mastery:fire").points(2);progress.tree("mastery:ice").points(3);
            player.setExperienceLevels(16);player.setExperiencePoints(7);player.totalExperience=0;
            player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,1));
            var failed=CostService.purchase(MasteryRuntime.definitions(),progress,"mastery:cost_test",-1,e->true,player);
            helper.assertTrue(!failed.success(),"Insufficient item count allowed purchase");
            helper.assertTrue(progress.rank("mastery:cost_test")==0&&progress.tree("mastery:fire").points()==2&&progress.tree("mastery:ice").points()==3,"Failed nested cost partially debited points or rank");
            helper.assertTrue(ExperiencePoints.total(player.experienceLevel,player.experienceProgress)==359&&player.getInventory().getItem(0).getCount()==1,"Failed nested cost debited XP or items");
            player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,2));
            var result=CostService.purchase(MasteryRuntime.definitions(),progress,"mastery:cost_test",-1,e->true,player);
            helper.assertTrue(result.success(),"Affordable nested cost failed: "+result.message());
            helper.assertTrue(progress.rank("mastery:cost_test")==1&&progress.tree("mastery:fire").points()==0&&progress.tree("mastery:ice").points()==0,"OR planner failed to backtrack around later AND costs");
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(),"Item cost was not removed");
            helper.assertTrue(ExperiencePoints.total(player.experienceLevel,player.experienceProgress)==259&&player.totalExperience==259,"Raw XP debit trusted stale totalExperience or removed wrong amount");
            helper.succeed();
        } finally {MasteryRuntime.install(original);MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void depthInflationAndPrerequisitesAreCheckedBeforePayment(GameTestHelper helper) {
        var original=MasteryRuntime.definitions();var player=MasteryTestPlayers.create(helper);
        try {
            var snapshot=original.toJson();
            snapshot.getAsJsonObject("nodes").add("mastery:cost_parent",JsonParser.parseString("{\"tree\":\"mastery:fire\",\"cost\":0}"));
            snapshot.getAsJsonObject("nodes").add("mastery:cost_child",JsonParser.parseString("""
                {"tree":"mastery:fire","dependencies":[{"node":"mastery:cost_parent"}],"cost_depth_percent":0.1,"costs":{"type":"experience","amount":10}}
                """));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));var progress=MasteryRuntime.progress(player);
            player.setExperienceLevels(2);player.setExperiencePoints(4);player.totalExperience=999;
            helper.assertTrue(ExperiencePoints.total(player.experienceLevel,player.experienceProgress)==20,"XP fixture incorrect");
            var blocked=CostService.purchase(MasteryRuntime.definitions(),progress,"mastery:cost_child",-1,e->true,player);
            helper.assertTrue(!blocked.success()&&ExperiencePoints.total(player.experienceLevel,player.experienceProgress)==20,"Unmet prerequisite consumed purchase resources");
            ProgressionService.setRank(MasteryRuntime.definitions(),progress,"mastery:cost_parent",1,-1);
            var result=CostService.purchase(MasteryRuntime.definitions(),progress,"mastery:cost_child",-1,e->true,player);
            helper.assertTrue(result.success(),"Depth cost purchase failed: "+result.message());
            helper.assertTrue(ExperiencePoints.total(player.experienceLevel,player.experienceProgress)==9,"Depth one 10% price did not round to eleven XP");
            helper.succeed();
        } finally {MasteryRuntime.install(original);MasteryRuntime.logout(player);player.discard();}
    }
}
