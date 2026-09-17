package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.progression.ProgressionService;
import com.cappleapple.mastery.spells.SpellService;
import com.google.gson.JsonParser;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class MultiSpellNodeGameTests {
    private static final String FIRE="irons_spellbooks:fireball",ICE="irons_spellbooks:icicle";
    private MultiSpellNodeGameTests() {}
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void combinedNodeGrantsTwoSpellsPassiveEffectsAndRankUpgrades(GameTestHelper helper) {
        var original=MasteryRuntime.definitions();var player=MasteryTestPlayers.create(helper);
        try {
            var snapshot=original.toJson();
            snapshot.getAsJsonObject("nodes").add("mastery:combined_test",JsonParser.parseString("""
                {"tree":"mastery:fire","max_rank":3,"effects":[
                  {"type":"mastery:unlock_spell","spell":"irons_spellbooks:fireball","level":2,"levels_per_rank":1},
                  {"type":"mastery:unlock_spell","spell":"irons_spellbooks:icicle","level":1},
                  {"type":"mastery:attribute","attribute":"minecraft:generic.luck","amount":1},
                  {"type":"mastery:spell_modifier","spell":"irons_spellbooks:fireball","spell_level":1,"mana_multiplier":0.8},
                  {"type":"mastery:spell_modifier","spell":"irons_spellbooks:fireball","spell_level":1},
                  {"type":"mastery:spell_modifier","mana_multiplier":0.9}
                ]}
                """));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));
            helper.assertTrue(SpellService.validateDefinitions(MasteryRuntime.definitions()).isEmpty(),"Combined node failed native spell validation");
            ProgressionService.setRank(MasteryRuntime.definitions(),MasteryRuntime.progress(player),"mastery:combined_test",2,-1);
            EffectService.rebuild(player);
            helper.assertTrue(SpellService.owned(player,FIRE)&&SpellService.owned(player,ICE),"One node did not unlock both spells");
            helper.assertTrue(SpellService.baseLevel(player,FIRE)==3,"Rank-based native level grant was not applied");
            helper.assertTrue(SpellService.grantingRank(player,FIRE)==2&&SpellService.grantingRank(player,ICE)==2,"Array spell grants lost charge rank");
            helper.assertTrue(player.getAttributeValue(Attributes.LUCK)==2,"Combined node lost passive attributes");
            helper.assertTrue(SpellService.modifiers(player,FIRE).levels()==4,"Repeated modifier effects overwrote each other");
            helper.assertTrue(SpellService.modifiers(player,ICE).levels()==0,"Spell-specific levels leaked into another grant");
            helper.assertTrue(Math.abs(SpellService.modifiers(player,FIRE).manaMultiplier()-.5184)<1e-8,"Ranked and wildcard multipliers did not compose");
            helper.assertTrue(Math.abs(SpellService.modifiers(player,ICE).manaMultiplier()-.81)<1e-8,"Wildcard passive did not reach second spell");
            var book=new ItemStack(Items.BOOK);ISpellContainer.set(book,ISpellContainer.create(4,true,false));player.setItemSlot(EquipmentSlot.OFFHAND,book);
            helper.assertTrue(SpellService.assign(player,SpellService.context(player),0,FIRE).isEmpty(),"First grant could not be assigned");
            helper.assertTrue(SpellService.assign(player,SpellService.context(player),1,ICE).isEmpty(),"Second grant could not be assigned");
            var node=MasteryRuntime.definitions().nodes().get("mastery:combined_test");SpellService.toggleNode(player,node,false);EffectService.rebuild(player);
            helper.assertTrue(!SpellService.owned(player,FIRE)&&!SpellService.owned(player,ICE),"Disabled combined node still owns active spells");
            helper.assertTrue(SpellService.unlocked(player,FIRE)&&SpellService.unlocked(player,ICE),"Disabling combined node erased purchased unlocks");
            helper.assertTrue(player.getAttributeValue(Attributes.LUCK)==0,"Disabled combined node retained passive attribute");
            helper.succeed();
        } finally {MasteryRuntime.install(original);MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void explicitMultiTargetModifierDoesNotNeedSingularSpell(GameTestHelper helper) {
        var original=MasteryRuntime.definitions();var player=MasteryTestPlayers.create(helper);
        try {
            var snapshot=original.toJson();
            snapshot.getAsJsonObject("nodes").add("mastery:dual_grant_test",JsonParser.parseString("""
                {"tree":"mastery:fire","max_rank":1,"effects":[
                  {"type":"mastery:unlock_spell","spell":"irons_spellbooks:fireball"},
                  {"type":"mastery:unlock_spell","spell":"irons_spellbooks:icicle"}]}
                """));
            snapshot.getAsJsonObject("nodes").add("mastery:dual_modifier_test",JsonParser.parseString("""
                {"tree":"mastery:fire","modifier":"mastery:native_spell","max_rank":1,"effects":[
                  {"type":"mastery:spell_modifier","spell":"irons_spellbooks:fireball","spell_level":1},
                  {"type":"mastery:spell_modifier","spell":"irons_spellbooks:icicle","spell_level":2}]}
                """));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));
            helper.assertTrue(SpellService.validateDefinitions(MasteryRuntime.definitions()).isEmpty(),"Explicit multi-target modifier was rejected");
            ProgressionService.setRank(MasteryRuntime.definitions(),MasteryRuntime.progress(player),"mastery:dual_grant_test",1,-1);
            ProgressionService.setRank(MasteryRuntime.definitions(),MasteryRuntime.progress(player),"mastery:dual_modifier_test",1,-1);
            String error=SpellService.toggleNode(player,MasteryRuntime.definitions().nodes().get("mastery:dual_modifier_test"),true);
            helper.assertTrue(error.isEmpty(),"Multi-target modifier failed to equip: "+error);
            ProgressionService.reconcile(MasteryRuntime.definitions(),MasteryRuntime.progress(player));
            helper.assertTrue(SpellService.modifiers(player,FIRE).levels()==1&&SpellService.modifiers(player,ICE).levels()==2,"Explicit spell filters cross-applied modifier payloads");
            SpellService.setModifier(player,FIRE,"mastery:dual_modifier_test",false);
            helper.assertTrue(SpellService.modifiers(player,FIRE).levels()==0&&SpellService.modifiers(player,ICE).levels()==2,"Unequipping one target disabled another target");
            helper.succeed();
        } finally {MasteryRuntime.install(original);MasteryRuntime.logout(player);player.discard();}
    }
}
