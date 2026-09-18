package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.mechanics.*;
import com.cappleapple.mastery.progression.ProgressionService;
import com.cappleapple.mastery.spells.SpellService;
import com.google.gson.*;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class ModifierTriggerGameTests {
    private static final String FIRE="irons_spellbooks:fireball", LIGHTNING="irons_spellbooks:lightning_bolt";
    private ModifierTriggerGameTests() {}
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void bundledModifiersRequireSlotsAndTheirOwnNativeSpellDamage(GameTestHelper h) {
        var original=MasteryRuntime.definitions();var owner=MasteryTestPlayers.createCombat(h);
        var target=h.spawn(EntityType.COW,new BlockPos(1,2,1));target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);
        try {
            var snapshot=original.toJson();
            snapshot.getAsJsonObject("settings").getAsJsonObject("mastery:defaults").add("modifier_slots",json("{\"base\":1,\"per_level\":0,\"max\":1}"));
            snapshot.getAsJsonObject("triggers").getAsJsonObject("mastery:scorch_on_hit").addProperty("chance",1);
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));
            for(String id:java.util.List.of("fireball","lightning_bolt","fire/scorch","lightning/thunder","fireball/efficiency"))rank(owner,id);
            var fire=SpellRegistry.FIREBALL_SPELL.get();
            hit(target,fire.getDamageSource(owner),1);MechanicsRuntime.tick();
            h.assertTrue(MechanicsRuntime.stacks(target,"mastery:scorch")==0,"Purchased but unassigned modifier acted as a passive");
            h.assertTrue(!SpellService.setModifier(owner,LIGHTNING,"mastery:fire/scorch",true).isEmpty(),"Scorch attached to the wrong spell");
            h.assertTrue(SpellService.setModifier(owner,FIRE,"mastery:fire/scorch",true).isEmpty(),"Scorch assignment failed");
            h.assertTrue(!SpellService.setModifier(owner,FIRE,"mastery:fireball/efficiency",true).isEmpty(),"Trigger modifier did not consume a slot");
            h.assertTrue(SpellService.setModifier(owner,LIGHTNING,"mastery:lightning/thunder",true).isEmpty(),"Thunder assignment failed");
            hit(target,owner.damageSources().playerAttack(owner),1);
            hit(target,SpellRegistry.BLAZE_STORM_SPELL.get().getDamageSource(owner),1);
            elementalDamageHit(owner,target);MechanicsRuntime.tick();
            h.assertTrue(MechanicsRuntime.stacks(target,"mastery:scorch")==0&&MechanicsRuntime.stacks(owner,"mastery:thunder")==0,"Modifiers leaked into melee, elemental weapon damage, or another Fire spell");
            var projectile=new io.redspace.ironsspellbooks.entity.spells.fireball.MagicFireball(h.getLevel(),owner);
            hit(target,fire.getDamageSource(projectile,owner),1);MechanicsRuntime.tick();projectile.discard();
            h.assertTrue(MechanicsRuntime.stacks(target,"mastery:scorch")==1,"Delayed projectile source lost the Fireball modifier");
            h.assertTrue(MechanicsRuntime.stacks(owner,"mastery:thunder")==0,"Fireball stacked Thunder");
            hit(target,SpellRegistry.LIGHTNING_BOLT_SPELL.get().getDamageSource(owner),1);MechanicsRuntime.tick();
            h.assertTrue(MechanicsRuntime.stacks(owner,"mastery:thunder")==1,"Lightning Bolt did not stack Thunder");
            h.assertTrue(MechanicsRuntime.stacks(target,"mastery:scorch")==1,"Lightning Bolt applied Scorch");
            SpellService.setModifier(owner,FIRE,"mastery:fire/scorch",false);
            hit(target,fire.getDamageSource(owner),1);
            SpellService.setModifier(owner,FIRE,"mastery:fire/scorch",true);MechanicsRuntime.tick();
            h.assertTrue(MechanicsRuntime.stacks(target,"mastery:scorch")==1,"Assignment after impact retroactively added a proc");
            target.setInvulnerable(true);hit(target,fire.getDamageSource(owner),1);MechanicsRuntime.tick();target.setInvulnerable(false);
            h.assertTrue(MechanicsRuntime.stacks(target,"mastery:scorch")==1,"Rejected damage triggered Scorch");
            MasteryRuntime.progress(owner).node("mastery:fireball").toggled(false);
            hit(target,fire.getDamageSource(owner),1);MechanicsRuntime.tick();
            h.assertTrue(MechanicsRuntime.stacks(target,"mastery:scorch")==1,"Disabled parent spell still applied its modifier");
            h.succeed();
        }finally{MasteryRuntime.install(original);MasteryRuntime.logout(owner);owner.discard();target.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void modifierTypeScopesDataDefinedKillTriggersAndNeverUsesTheVictimsAssignments(GameTestHelper h) {
        var original=MasteryRuntime.definitions();var owner=MasteryTestPlayers.createCombat(h);var enemy=MasteryTestPlayers.createCombat(h);
        var target=h.spawn(EntityType.COW,new BlockPos(1,2,1));target.setNoAi(true);
        try {
            var snapshot=original.toJson();
            snapshot.getAsJsonObject("keywords").add("test:marker",json("{\"max_stacks\":100,\"duration\":100}"));
            snapshot.getAsJsonObject("triggers").add("test:kill",json("{\"event\":\"kill\",\"actions\":[{\"type\":\"keyword\",\"target\":\"self\",\"keyword\":\"test:marker\"}]}"));
            snapshot.getAsJsonObject("triggers").add("test:hurt",json("{\"event\":\"hurt\",\"actions\":[{\"type\":\"keyword\",\"target\":\"self\",\"keyword\":\"test:marker\"}]}"));
            snapshot.getAsJsonObject("nodes").add("mastery:test_modifier",json("{\"tree\":\"mastery:fire\",\"type\":\"modifier\",\"spell\":\"irons_spellbooks:fireball\",\"effects\":[{\"type\":\"mastery:trigger\",\"trigger\":\"test:kill\"},{\"type\":\"mastery:trigger\",\"trigger\":\"test:hurt\"}]}"));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));rank(owner,"fireball");rank(owner,"test_modifier");
            h.assertTrue(SpellService.setModifier(owner,FIRE,"mastery:test_modifier",true).isEmpty(),"Modifier type did not imply assignment behavior without a handler field");
            hit(owner,SpellRegistry.FIREBALL_SPELL.get().getDamageSource(enemy),1);MechanicsRuntime.tick();
            h.assertTrue(MechanicsRuntime.stacks(owner,"test:marker")==0,"Enemy spell used the victim's modifier assignment");
            hit(target,SpellRegistry.FIREBALL_SPELL.get().getDamageSource(owner),100);MechanicsRuntime.tick();
            h.assertTrue(!target.isAlive()&&MechanicsRuntime.stacks(owner,"test:marker")==1,"Assigned spell kill did not run its data-defined trigger exactly once");
            MechanicsRuntime.tick();h.assertTrue(MechanicsRuntime.stacks(owner,"test:marker")==1,"Kill trigger duplicated");h.succeed();
        }finally{MasteryRuntime.install(original);MasteryRuntime.logout(owner);MasteryRuntime.logout(enemy);owner.discard();enemy.discard();target.discard();}
    }
    private static void elementalDamageHit(ServerPlayer owner,LivingEntity target) {
        com.cappleapple.mastery.elemental.ElementalDamage.hurt(target,owner,"mastery:fire",1);
    }
    private static void hit(LivingEntity target,DamageSource source,float amount){target.invulnerableTime=0;target.hurt(source,amount);}
    private static void rank(ServerPlayer player,String id) {
        var result=ProgressionService.setRank(MasteryRuntime.definitions(),MasteryRuntime.progress(player),"mastery:"+id,1,-1);
        if(!result.success())throw new IllegalStateException(result.message());
    }
    private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
}
