package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.mechanics.*;
import com.cappleapple.mastery.progression.ProgressionService;
import com.google.gson.*;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class TreeKeywordGameTests {
    private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void treeModifiersInheritButKeepDamageScopeAndKeywordBonuses(GameTestHelper h) {
        io.redspace.ironsspellbooks.api.config.SpellConfigManager.onDatapackSync(new net.neoforged.neoforge.event.OnDatapackSyncEvent(h.getLevel().getServer().getPlayerList(),null));
        var original=MasteryRuntime.definitions();var owner=MasteryTestPlayers.createCombat(h);
        var target=h.spawn(EntityType.COW,new BlockPos(1,2,1));target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);
        long start=h.getLevel().getGameTime();
        try {
            var snapshot=original.toJson();
            snapshot.getAsJsonObject("keywords").add("test:ember",json("{\"max_stacks\":100,\"duration\":30,\"tick_interval\":5,\"tick_actions\":[{\"type\":\"damage\",\"school\":\"irons_spellbooks:evocation\",\"amount\":2}]}"));
            snapshot.getAsJsonObject("triggers").add("test:ember",json("{\"event\":\"hit\",\"actions\":[{\"type\":\"keyword\",\"keyword\":\"test:ember\"}]}"));
            snapshot.getAsJsonObject("nodes").add("test:tree_modifier",json("{\"tree\":\"mastery:fire\",\"type\":\"modifier\",\"modifier\":\"mastery:tree\",\"effects\":[{\"type\":\"mastery:trigger\",\"trigger\":\"test:ember\"},{\"type\":\"mastery:keyword_modifier\",\"keyword\":\"test:ember\",\"stacks\":1,\"stacks_percent\":1,\"damage\":1,\"damage_percent\":1,\"duration\":10,\"duration_percent\":0.5}]}"));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));
            ProgressionService.setRank(MasteryRuntime.definitions(),MasteryRuntime.progress(owner),"test:tree_modifier",1,-1);
            var node=MasteryRuntime.definitions().nodes().get("test:tree_modifier");
            var fire=DamageContexts.capture(SpellRegistry.FIREBALL_SPELL.get().getDamageSource(owner));
            var flame=SpellRegistry.getSpell("irons_spellbooks:flaming_strike").getDamageSource(owner);
            h.assertTrue(TreeModifiers.matches(node,DamageContexts.capture(flame)),"Fire modifier did not inherit into Flame Blade: "+DamageContexts.capture(flame));
            h.assertTrue(!TreeModifiers.matches(node,DamageContexts.capture(SpellRegistry.LIGHTNING_BOLT_SPELL.get().getDamageSource(owner))),"Fire modifier matched lightning");
            target.hurt(owner.damageSources().playerAttack(owner),1);MechanicsRuntime.tick();
            h.assertTrue(MechanicsRuntime.stacks(target,"test:ember")==0,"Fire modifier applied to physical melee");
            com.cappleapple.mastery.elemental.ElementalDamage.hurt(target,owner,"mastery:fire",1);MechanicsRuntime.tick();
            h.assertTrue(MechanicsRuntime.stacks(target,"test:ember")==4,"Fire damage outside a native spell lost its tree modifier");MechanicsRuntime.clear();
            target.invulnerableTime=0;target.hurt(flame,1);MechanicsRuntime.tick();
            h.assertTrue(MechanicsRuntime.stacks(target,"test:ember")==4,"Tree modifier needed a spell slot or stack bonuses were not applied");
            h.assertTrue(KeywordModifiers.adjust(owner,"test:ember","damage",2,fire)==6,"Flat and percentage keyword damage did not compose");
            float health=target.getHealth();target.invulnerableTime=0;
            ((net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData()).setGameTime(start+5);MechanicsRuntime.tick();
            h.assertTrue(Math.abs(target.getHealth()-(health-6))<0.01,"Stored keyword origin lost tree damage bonus");
            ((net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData()).setGameTime(start+59);h.assertTrue(MechanicsRuntime.stacks(target,"test:ember")==4,"Duration bonus expired early");
            ((net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData()).setGameTime(start+60);MechanicsRuntime.tick();h.assertTrue(MechanicsRuntime.stacks(target,"test:ember")==0,"Modified duration did not expire");
            snapshot.getAsJsonObject("nodes").getAsJsonObject("test:tree_modifier").addProperty("inherit_subtrees",false);
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));node=MasteryRuntime.definitions().nodes().get("test:tree_modifier");
            h.assertTrue(!TreeModifiers.matches(node,DamageContexts.capture(flame))&&TreeModifiers.matches(node,fire),"Subtree opt-out changed direct-tree scope");
            h.succeed();
        }finally{((net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData()).setGameTime(start);MasteryRuntime.install(original);MasteryRuntime.logout(owner);owner.discard();target.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void keywordDecayResetsDelayAndFiresLossEventsForDecayExpiryAndConsumption(GameTestHelper h) {
        var original=MasteryRuntime.definitions();var owner=MasteryTestPlayers.createCombat(h);
        var target=h.spawn(EntityType.COW,new BlockPos(1,2,1));target.setNoAi(true);long start=h.getLevel().getGameTime();
        try {
            var snapshot=original.toJson();var keywords=snapshot.getAsJsonObject("keywords");
            keywords.add("test:lost",json("{\"max_stacks\":100,\"duration\":0}"));keywords.add("test:empty",json("{\"max_stacks\":100,\"duration\":0}"));
            var keyword=json("{\"max_stacks\":100,\"duration\":0,\"decay_delay\":10,\"decay_interval\":5,\"decay_stacks\":2,\"stacks_lost_actions\":[{\"type\":\"keyword\",\"target\":\"self\",\"keyword\":\"test:lost\",\"per_stack\":true}],\"all_stacks_lost_actions\":[{\"type\":\"keyword\",\"target\":\"self\",\"keyword\":\"test:empty\"}]}");
            keywords.add("test:decay",keyword);var expires=keyword.deepCopy();expires.addProperty("duration",3);expires.addProperty("decay_stacks",0);keywords.add("test:expires",expires);
            var consume=expires.deepCopy();consume.addProperty("threshold",3);consume.addProperty("consume_stacks",true);keywords.add("test:consume",consume);
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));
            MechanicsRuntime.applyKeyword(owner,target,"test:decay",4,0);
            ((net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData()).setGameTime(start+9);MechanicsRuntime.tick();h.assertTrue(MechanicsRuntime.stacks(target,"test:decay")==4,"Decay started before delay");
            MechanicsRuntime.applyKeyword(owner,target,"test:decay",1,0);
            ((net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData()).setGameTime(start+18);MechanicsRuntime.tick();h.assertTrue(MechanicsRuntime.stacks(target,"test:decay")==5,"Reapplication did not restart delay");
            ((net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData()).setGameTime(start+19);MechanicsRuntime.tick();h.assertTrue(MechanicsRuntime.stacks(target,"test:decay")==3&&MechanicsRuntime.stacks(owner,"test:lost")==2,"Initial decay lost incorrect stacks");
            ((net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData()).setGameTime(start+24);MechanicsRuntime.tick();h.assertTrue(MechanicsRuntime.stacks(target,"test:decay")==1,"Decay interval was ignored");
            ((net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData()).setGameTime(start+29);MechanicsRuntime.tick();h.assertTrue(MechanicsRuntime.stacks(owner,"test:lost")==5&&MechanicsRuntime.stacks(owner,"test:empty")==1,"Final loss callbacks did not count actual lost stacks");
            MechanicsRuntime.applyKeyword(owner,target,"test:expires",2,0);((net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData()).setGameTime(start+32);MechanicsRuntime.tick();
            h.assertTrue(MechanicsRuntime.stacks(owner,"test:lost")==7&&MechanicsRuntime.stacks(owner,"test:empty")==2,"Expiry did not fire loss callbacks");
            MechanicsRuntime.applyKeyword(owner,target,"test:consume",3,0);MechanicsRuntime.tick();
            h.assertTrue(MechanicsRuntime.stacks(target,"test:consume")==0&&MechanicsRuntime.stacks(owner,"test:lost")==10&&MechanicsRuntime.stacks(owner,"test:empty")==3,"Threshold consumption did not fire loss callbacks");
            MechanicsRuntime.tick();h.assertTrue(MechanicsRuntime.stacks(owner,"test:empty")==3,"Final loss event fired twice");h.succeed();
        }finally{((net.minecraft.world.level.storage.ServerLevelData)h.getLevel().getLevelData()).setGameTime(start);MasteryRuntime.install(original);MasteryRuntime.logout(owner);owner.discard();target.discard();}
    }
}
