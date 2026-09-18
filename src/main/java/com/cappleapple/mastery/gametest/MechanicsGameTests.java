package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.mechanics.*;
import com.cappleapple.mastery.progression.ProgressionService;
import com.google.gson.*;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.entity.spells.firebolt.FireboltProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class MechanicsGameTests {
    private MechanicsGameTests() {}
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static void install(net.minecraft.server.level.ServerPlayer player, Map<String, JsonObject> triggers, Map<String, JsonObject> keywords) {
        var value = MasteryRuntime.definitions().toJson();
        value.getAsJsonObject("trees").add("mastery:mechanics_test", json("{\"name\":\"Mechanics test\"}"));
        var effects = new JsonArray();
        triggers.forEach((id, trigger) -> {
            value.getAsJsonObject("triggers").add(id, trigger);
            var effect = new JsonObject(); effect.addProperty("type", "mastery:trigger"); effect.addProperty("trigger", id); effects.add(effect);
        });
        keywords.forEach((id, keyword) -> value.getAsJsonObject("keywords").add(id, keyword));
        var node = json("{\"tree\":\"mastery:mechanics_test\",\"name\":\"Mechanics test\"}"); node.add("effects", effects);
        value.getAsJsonObject("nodes").add("mastery:mechanics_test", node);
        MasteryRuntime.install(DefinitionSet.fromJson(value));
        ProgressionService.setRank(MasteryRuntime.definitions(), MasteryRuntime.progress(player), "mastery:mechanics_test", 1, -1);
    }
    private static JsonObject marker() { return json("{\"max_stacks\":100,\"duration\":1000}"); }
    private static JsonObject markTrigger(String event, String keyword) {
        return json("{\"event\":\"" + event + "\",\"actions\":[{\"type\":\"keyword\",\"target\":\"self\",\"keyword\":\"" + keyword + "\"}]}");
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void damageFiltersUseAcceptedPortionsAndPreserveAttackOrigin(GameTestHelper helper) {
        var before=MasteryRuntime.definitions();var owner=MasteryTestPlayers.createCombat(helper);
        var target=helper.spawn(EntityType.COW,new BlockPos(1,2,1));target.setNoAi(true);
        try {
            var fire=markTrigger("hit","mastery:fire_marker");
            fire.add("conditions",JsonParser.parseString("[{\"type\":\"damage\",\"elements\":[\"mastery:fire\"],\"categories\":[\"melee\"]}]"));
            var slash=markTrigger("hit","mastery:slash_marker");slash.add("conditions",JsonParser.parseString("[{\"type\":\"damage\",\"elements\":[\"mastery:slashing\"]}]"));
            var ranged=markTrigger("hit","mastery:ranged_marker");ranged.add("conditions",JsonParser.parseString("[{\"type\":\"damage\",\"categories\":[\"ranged\"]}]"));
            var magic=markTrigger("hurt","mastery:magic_marker");magic.add("conditions",JsonParser.parseString("[{\"type\":\"damage\",\"categories\":[\"magic\"]}]"));
            install(owner,Map.of("mastery:fire_trigger",fire,"mastery:slash_trigger",slash,"mastery:ranged_trigger",ranged,"mastery:magic_trigger",magic),
                    Map.of("mastery:fire_marker",marker(),"mastery:slash_marker",marker(),"mastery:ranged_marker",marker(),"mastery:magic_marker",marker()));
            owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            owner.getAttribute(BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse("mastery:fire_weapon_damage")).orElseThrow()).setBaseValue(.2);
            target.hurt(owner.damageSources().playerAttack(owner),2);
            // Queued matching uses impact facts even after equipment changes.
            owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.item.ItemStack.EMPTY);MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(owner,"mastery:fire_marker")==1,"Mixed melee fire hit did not proc exactly once");
            helper.assertTrue(MechanicsRuntime.stacks(owner,"mastery:slash_marker")==1,"Default sword slashing type was not captured");
            helper.assertTrue(MechanicsRuntime.stacks(owner,"mastery:ranged_marker")==0,"Melee incorrectly matched ranged");
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> immunity=event->{if(event.getEntity()==target&&event.getSource().is(io.redspace.ironsspellbooks.api.registry.SchoolRegistry.FIRE.get().getDamageType()))event.setCanceled(true);};
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(immunity);
            try{target.invulnerableTime=0;target.hurt(owner.damageSources().playerAttack(owner),2);}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(immunity);}
            MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(owner,"mastery:fire_marker")==1,"Immune fire portion incorrectly matched");
            owner.hurt(owner.damageSources().magic(),2);MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(owner,"mastery:magic_marker")==1,"Source-free magic hurt could not match damage conditions");
            var arrow=new net.minecraft.world.entity.projectile.Arrow(helper.getLevel(),owner,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
            var context=DamageContexts.capture(owner.damageSources().arrow(arrow,owner));
            helper.assertTrue(context.categories().contains("ranged")&&context.damageTags().contains("minecraft:is_projectile"),"Projectile delivery and native tag not captured");
            target.invulnerableTime=0;target.hurt(owner.damageSources().arrow(arrow,owner),1);MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(owner,"mastery:ranged_marker")==1,"Ranged attack did not fire its filtered hit trigger");
            helper.succeed();
        }finally{owner.discard();target.discard();MasteryRuntime.install(before);}
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void successfulHitThresholdChanceCooldownAndRecursion(GameTestHelper helper) {
        var before = MasteryRuntime.definitions(); var owner = MasteryTestPlayers.createCombat(helper);
        var target = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1)); target.setNoAi(true);
        try {
            install(owner, Map.of("mastery:test_hit", json("""
                    {"event":"hit","chance":0,"cooldown":20,
                     "conditions":[{"type":"health","unit":"fraction","max":0.75}],
                     "actions":[{"type":"keyword","target":"self","keyword":"mastery:test_marker"},
                                {"type":"damage","amount":1,"school":"irons_spellbooks:fire"}]}
                    """)), Map.of("mastery:test_marker", marker()));
            var chance = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse("mastery:proc_chance")).orElseThrow();
            helper.assertTrue(target.hurt(owner.damageSources().playerAttack(owner), 5), "Fixture damage was rejected");
            MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(owner, "mastery:test_marker") == 0, "Zero chance unexpectedly fired");
            owner.getAttribute(chance).setBaseValue(1);
            target.invulnerableTime = 0;
            target.hurt(owner.damageSources().playerAttack(owner), 1);
            float health = target.getHealth(); MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(owner, "mastery:test_marker") == 1, "Proc chance or post-hit health threshold failed");
            helper.assertTrue(target.getHealth() < health, "Immediate triggered school damage was swallowed by hurt cooldown");
            MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(owner, "mastery:test_marker") == 1, "Triggered damage recursively triggered another hit");
            target.invulnerableTime = 0; target.hurt(owner.damageSources().playerAttack(owner), 1); MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(owner, "mastery:test_marker") == 1, "Trigger ignored its cooldown");
            target.setInvulnerable(true); target.invulnerableTime = 0;
            target.hurt(owner.damageSources().playerAttack(owner), 20); MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(owner, "mastery:test_marker") == 1, "Rejected damage produced an on-hit proc");
            helper.succeed();
        } finally { owner.discard(); target.discard(); MasteryRuntime.install(before); }
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void killAndBeingHitUseCorrectOwnerAndHealth(GameTestHelper helper) {
        var before = MasteryRuntime.definitions(); var owner = MasteryTestPlayers.createCombat(helper);
        var target = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1)); target.setNoAi(true);
        try {
            var hurt = markTrigger("hurt", "mastery:test_hurt");
            var kill=markTrigger("kill","mastery:test_kill");kill.add("conditions",JsonParser.parseString("[{\"type\":\"damage\",\"categories\":[\"melee\"],\"damage_types\":[\"minecraft:player_attack\"]}]"));
            hurt.add("conditions", JsonParser.parseString("[{\"type\":\"health\",\"target\":\"self\",\"unit\":\"points\",\"max\":15}]"));
            install(owner, Map.of("mastery:test_hurt", hurt, "mastery:test_kill", kill),
                    Map.of("mastery:test_hurt", marker(), "mastery:test_kill", marker()));
            owner.hurt(owner.damageSources().mobAttack(target), 5); MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(owner, "mastery:test_hurt") == 1, "Being-hit threshold did not use the player's post-hit health");
            target.hurt(owner.damageSources().playerAttack(owner), 100); MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(owner, "mastery:test_kill") == 1, "Confirmed kill did not grant its killer's proc");
            helper.succeed();
        } finally { owner.discard(); target.discard(); MasteryRuntime.install(before); }
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void playerDeathCanApplyKeywordToKiller(GameTestHelper helper) {
        var before = MasteryRuntime.definitions(); var owner = MasteryTestPlayers.createCombat(helper);
        var target = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1)); target.setNoAi(true);
        try {
            install(owner, Map.of("mastery:test_death", json("{\"event\":\"death\",\"conditions\":[{\"type\":\"damage\",\"categories\":[\"melee\"]}],\"actions\":[{\"type\":\"keyword\",\"keyword\":\"mastery:test_marker\"}]}")),
                    Map.of("mastery:test_marker", marker()));
            owner.hurt(owner.damageSources().mobAttack(target), 1000); MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(target, "mastery:test_marker") == 1, "Death proc was discarded because its owner died");
            helper.succeed();
        } finally { owner.discard(); target.discard(); MasteryRuntime.install(before); }
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void stackingThresholdConsumesAndNonconsumingThresholdRearms(GameTestHelper helper) {
        var before = MasteryRuntime.definitions(); var owner = MasteryTestPlayers.createCombat(helper);
        var target = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1)); target.setNoAi(true);
        try {
            install(owner, Map.of(), Map.of("mastery:test_marker", marker(), "mastery:test_latch", json("""
                    {"max_stacks":10,"threshold":3,"consume_stacks":false,
                     "threshold_actions":[{"type":"keyword","keyword":"mastery:test_marker"}]}
                    """), "mastery:test_consume", json("""
                    {"max_stacks":10,"threshold":3,"consume_stacks":true,
                     "threshold_actions":[{"type":"keyword","keyword":"mastery:test_marker"}]}
                    """)));
            MechanicsRuntime.applyKeyword(owner, target, "mastery:test_latch", 3, 100);
            MechanicsRuntime.applyKeyword(owner, target, "mastery:test_latch", 1, 100);
            helper.assertTrue(MechanicsRuntime.stacks(target, "mastery:test_marker") == 1, "Nonconsuming threshold fired repeatedly above threshold");
            MechanicsRuntime.applyKeyword(owner, target, "mastery:test_consume", 4, 100);
            helper.assertTrue(MechanicsRuntime.stacks(target, "mastery:test_consume") == 1, "Threshold did not consume exactly its threshold count");
            helper.assertTrue(MechanicsRuntime.stacks(target, "mastery:test_marker") == 2, "Consuming threshold action did not run");
            MechanicsRuntime.forget(owner);
            helper.assertTrue(MechanicsRuntime.stacks(target, "mastery:test_marker") == 0, "Logout left owned keyword state behind");
            helper.succeed();
        } finally { owner.discard(); target.discard(); MasteryRuntime.install(before); }
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void procSpellAimsAtTargetAndPreservesActiveCast(GameTestHelper helper) {
        var owner = MasteryTestPlayers.create(helper); var target = helper.spawn(EntityType.COW, new BlockPos(4, 2, 1)); target.setNoAi(true);
        owner.setPos(helper.absolutePos(new BlockPos(1, 2, 1)).getCenter());
        var magic = MagicData.getPlayerMagicData(owner); magic.getSyncedData();
        magic.initiateCast(SpellRegistry.getSpell("irons_spellbooks:fireball"), 3, 100, CastSource.SPELLBOOK, ""); magic.setMana(50);
        owner.setYRot(35); owner.setXRot(15);
        try {
            helper.assertTrue(ProcSpellCaster.cast(owner, target, "irons_spellbooks:firebolt", 1), "Native proc cast failed");
            helper.assertTrue(magic.isCasting() && magic.getCastingSpellId().equals("irons_spellbooks:fireball") && magic.getCastDurationRemaining() == 100,
                    "Proc spell replaced the active native cast");
            helper.assertTrue(magic.getMana() == 50 && owner.getYRot() == 35 && owner.getXRot() == 15, "Proc consumed mana or changed player aim");
            var bolts = helper.getLevel().getEntitiesOfClass(FireboltProjectile.class, owner.getBoundingBox().inflate(16), bolt -> bolt.getOwner() == owner);
            helper.assertTrue(bolts.size() == 1, "Proc did not spawn exactly one native firebolt");
            helper.assertTrue(bolts.getFirst().getDeltaMovement().x > 0, "Native firebolt did not aim at selected target");
            bolts.forEach(net.minecraft.world.entity.Entity::discard); helper.succeed();
        } finally { magic.resetCastingState(); owner.discard(); target.discard(); }
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void holySpellHealingUsesInverseMatchupWithoutChangingOrdinaryHealing(GameTestHelper helper) {
        var before = MasteryRuntime.definitions(); var owner = MasteryTestPlayers.createCombat(helper);
        try {
            var snapshot = before.toJson();
            snapshot.getAsJsonObject("mob_types").add("mastery:test_players", json("{\"entities\":[\"minecraft:player\"]}"));
            snapshot.getAsJsonObject("elements").getAsJsonObject("mastery:holy").add("damage_modifiers", json("{\"mastery:test_players\":-0.25}"));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));
            owner.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); owner.setHealth(1);
            float amount = 5;
            var healing = new io.redspace.ironsspellbooks.entity.spells.HealingAoe(owner.level());
            healing.setOwner(owner); healing.setDamage(amount); healing.applyEffect(owner); healing.discard();
            helper.assertTrue(Math.abs(owner.getHealth() - (1 + amount * 1.25)) < .01, "Holy native healing did not invert the target damage matchup; health=" + owner.getHealth() + ", base=" + amount + ", multiplier=" + com.cappleapple.mastery.elemental.ElementalDamage.healingMultiplier(owner, owner, "irons_spellbooks:holy"));
            owner.setHealth(1); owner.heal(4);
            helper.assertTrue(owner.getHealth() == 5, "Ordinary healing inherited stale school scaling");
            helper.succeed();
        } finally { owner.discard(); MasteryRuntime.install(before); }
    }

    @GameTest(batch="mechanics_keyword_ticks", templateNamespace="minecraft", template="bastion/mobs/empty", timeoutTicks=140)
    public static void dotTicksAndExpiresWithOwnerAttribution(GameTestHelper helper) {
        var owner = MasteryTestPlayers.create(helper); var target = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1)); target.setNoAi(true);
        MechanicsRuntime.applyKeyword(owner, target, "mastery:scorch", 2, 45);
        float initial = target.getHealth();
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(target.getHealth() < initial, "Scorch did not deal ticking damage");
            helper.assertTrue(target.getLastHurtByMob() == owner, "DOT lost its owning player");
        });
        helper.runAfterDelay(48, () -> {
            helper.assertTrue(MechanicsRuntime.stacks(target, "mastery:scorch") == 0, "Expired keyword remained active");
            owner.discard(); target.discard(); helper.succeed();
        });
    }
    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void procDamageKillsStillRunOneKillTrigger(GameTestHelper helper) {
        var before=MasteryRuntime.definitions();var owner=MasteryTestPlayers.createCombat(helper);
        var victim=helper.spawn(EntityType.COW,new BlockPos(1,2,1));victim.setNoAi(true);
        try {
            install(owner,Map.of("mastery:test_hit",json("{\"event\":\"hit\",\"actions\":[{\"type\":\"damage\",\"amount\":100}]}"),
                    "mastery:test_kill",markTrigger("kill","mastery:test_marker")),Map.of("mastery:test_marker",marker()));
            victim.hurt(owner.damageSources().playerAttack(owner),1);
            MechanicsRuntime.tick();MechanicsRuntime.tick();MechanicsRuntime.tick();
            helper.assertTrue(!victim.isAlive(),"Proc damage did not kill its target");
            helper.assertTrue(MechanicsRuntime.stacks(owner,"mastery:test_marker")==1,"Proc kill was missing or recursively duplicated its on-kill trigger");
            helper.succeed();
        } finally {owner.discard();victim.discard();MasteryRuntime.install(before);}
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void elementalRemainderAfterAbsorptionProducesOneAggregatedHit(GameTestHelper helper) {
        var before=MasteryRuntime.definitions();var owner=MasteryTestPlayers.createCombat(helper);
        var victim=helper.spawn(EntityType.COW,new BlockPos(1,2,1));victim.setNoAi(true);
        try {
            install(owner,Map.of("mastery:test_hit",json("""
                    {"event":"hit","conditions":[{"type":"health","unit":"points","max":18}],
                     "actions":[{"type":"keyword","target":"self","keyword":"mastery:test_marker"},
                                {"type":"damage","school":"irons_spellbooks:fire","damage_fraction":1}]}
                    """)),Map.of("mastery:test_marker",marker()));
            owner.getAttribute(BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse("mastery:fire_weapon_damage")).orElseThrow()).setBaseValue(.2);
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20); victim.setHealth(20);
            victim.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(10); victim.setAbsorptionAmount(10);
            helper.assertTrue(victim.getAbsorptionAmount()==10,"Fixture failed to grant absorption");
            victim.hurt(owner.damageSources().playerAttack(owner),10);MechanicsRuntime.tick();
            helper.assertTrue(MechanicsRuntime.stacks(owner,"mastery:test_marker")==1,"Elemental-only health damage after absorption lost or duplicated its hit proc");
            helper.assertTrue(Math.abs(victim.getHealth()-16)<.01,"Expected two elemental health damage plus two from the aggregate damage_fraction action; actual "+victim.getHealth());
            helper.succeed();
        } finally {owner.discard();victim.discard();MasteryRuntime.install(before);}
    }}
