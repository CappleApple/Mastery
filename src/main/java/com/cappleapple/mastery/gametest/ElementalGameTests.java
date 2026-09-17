package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.elemental.ElementalDamage;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("mastery") @PrefixGameTestTemplate(false)
public final class ElementalGameTests {
    private static LivingEntity target(GameTestHelper h) {
        var mob=h.spawn(EntityType.COW,new BlockPos(2,2,2));mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);mob.setHealth(200);return mob;
    }
    private static void attribute(LivingEntity entity,String name,double value) {entity.getAttribute(BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse("mastery:"+name)).orElseThrow()).setBaseValue(value);}
    private static ItemStack aspect(GameTestHelper h,net.minecraft.world.item.Item item,int level) {
        var stack=new ItemStack(item);stack.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(ResourceLocation.parse("minecraft:fire_aspect")).orElseThrow(),level);return stack;
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void fireAspectIsBonusWithoutIgnitionAndRespectsRejectedHits(GameTestHelper h) {
        var player=MasteryTestPlayers.create(h);player.setItemSlot(EquipmentSlot.MAINHAND,aspect(h,Items.DIAMOND_SWORD,2));var victim=target(h);
        h.assertTrue(victim.hurt(h.getLevel().damageSources().playerAttack(player),10),"Physical hit was rejected");
        h.assertTrue(Math.abs(victim.getHealth()-188)<.01,"Fire Aspect II must add two fire damage to ten physical, actual "+victim.getHealth());
        h.assertTrue(!victim.isOnFire(),"Fire Aspect must not ignite");
        float after=victim.getHealth();victim.hurt(h.getLevel().damageSources().playerAttack(player),10);
        h.assertTrue(victim.getHealth()==after,"Rejected same-tick attack gained elemental damage");
        victim.invulnerableTime=0;victim.setInvulnerable(true);victim.hurt(h.getLevel().damageSources().playerAttack(player),10);
        h.assertTrue(victim.getHealth()==after,"Invulnerable target took elemental damage");
        var enchant=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).get(ResourceLocation.parse("minecraft:fire_aspect"));
        h.assertTrue(enchant.getMaxLevel()==10&&enchant.isSupportedItem(new ItemStack(Items.BOW))&&enchant.isSupportedItem(new ItemStack(Items.CROSSBOW)),"Fire Aspect must reach X and support bows and crossbows");
        var aspects=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getTag(net.minecraft.tags.TagKey.create(Registries.ENCHANTMENT,ResourceLocation.parse("mastery:exclusive_set/aspects"))).orElseThrow();
        h.assertTrue(aspects.size()==9,"All nine school Aspects must share the conflict group");
        for(var first:aspects)for(var second:aspects)if(!first.equals(second))
            h.assertTrue(!net.minecraft.world.item.enchantment.Enchantment.areCompatible(first,second),"Different Aspects must conflict");
        victim.discard();player.discard();h.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void conversionReplacesDamageAndUsesSchoolResistance(GameTestHelper h) {
        var player=MasteryTestPlayers.create(h);attribute(player,"fire_conversion",.5);var victim=target(h);
        victim.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.FIRE_MAGIC_RESIST).setBaseValue(2);
        victim.hurt(h.getLevel().damageSources().playerAttack(player),10);
        h.assertTrue(victim.getHealth()>190&&victim.getHealth()<195,"Conversion must replace half and use fire resistance, actual "+victim.getHealth());
        victim.discard();victim=target(h);attribute(player,"fire_conversion",1);attribute(player,"ice_conversion",1);
        victim.hurt(h.getLevel().damageSources().playerAttack(player),10);
        h.assertTrue(Math.abs(victim.getHealth()-190)<.01,"Over-allocation must normalize and not double damage, actual "+victim.getHealth());
        victim.discard();player.discard();h.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void arrowsSnapshotAspectsAndSkillsAtLaunch(GameTestHelper h) {
        var player=MasteryTestPlayers.create(h);var bow=aspect(h,Items.BOW,2);player.setItemSlot(EquipmentSlot.MAINHAND,bow);attribute(player,"elemental_damage",.5);
        var arrow=new Arrow(h.getLevel(),player,new ItemStack(Items.ARROW),bow);h.getLevel().addFreshEntity(arrow);
        player.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);attribute(player,"elemental_damage",0);
        var victim=target(h);victim.hurt(h.getLevel().damageSources().arrow(arrow,player),10);
        h.assertTrue(Math.abs(victim.getHealth()-187)<.01,"Arrow must retain its launch weapon and power, actual "+victim.getHealth());
        victim.discard();arrow.discard();player.discard();h.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void elementalPowerBoostsNativeSchoolDamageOnce(GameTestHelper h) {
        var player=MasteryTestPlayers.create(h);attribute(player,"elemental_damage",.5);attribute(player,"fire_damage",.2);attribute(player,"fire_weapon_damage",1);
        var victim=target(h);var holder=h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SchoolRegistry.FIRE.get().getDamageType());
        victim.hurt(new DamageSource(holder,player),10);
        h.assertTrue(Math.abs(victim.getHealth()-182)<.01,"Native school damage must scale once without weapon infusion, actual "+victim.getHealth());
        victim.discard();player.discard();h.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void holyMatchupAttunementAndInverseHealing(GameTestHelper h) {
        var owner=MasteryTestPlayers.createCombat(h);var player=MasteryTestPlayers.createCombat(h);player.setHealth(20);
        attribute(owner,"holy_attunement",.2);
        ElementalDamage.hurt(player,owner,"mastery:holy",10);
        h.assertTrue(Math.abs(player.getHealth()-13)<.01,"Attunement must deepen the player damage penalty to -30%, actual "+player.getHealth());
        player.setHealth(5);ElementalDamage.heal(player,owner,"mastery:holy",10);
        h.assertTrue(Math.abs(player.getHealth()-18)<.01,"Player holy healing must gain inverse +30% bonus, actual "+player.getHealth());
        attribute(owner,"holy_mitigation",.5);player.setHealth(20);
        ElementalDamage.hurt(player,owner,"mastery:holy",10);
        h.assertTrue(Math.abs(player.getHealth()-11.5)<.01,"Mitigation must halve the -30% damage penalty");
        owner.discard();player.discard();h.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void overlappingGroupsStackAndWeaponDefaultsAreTyped(GameTestHelper h) {
        var before=com.cappleapple.mastery.MasteryRuntime.definitions();
        var json=before.toJson();
        var groups=json.getAsJsonObject("mob_types");
        groups.add("mastery:test_animals",com.google.gson.JsonParser.parseString("{\"entities\":[\"minecraft:cow\",\"minecraft:pig\"]}"));
        groups.add("mastery:test_cattle",com.google.gson.JsonParser.parseString("{\"entities\":[\"minecraft:cow\"]}"));
        var modifiers=new com.google.gson.JsonObject();modifiers.addProperty("mastery:test_animals",.25);modifiers.addProperty("mastery:test_cattle",-.10);
        json.getAsJsonObject("elements").getAsJsonObject("mastery:slashing").add("damage_modifiers",modifiers);
        var owner=MasteryTestPlayers.create(h);var victim=target(h);
        try {
            com.cappleapple.mastery.MasteryRuntime.install(com.cappleapple.mastery.data.DefinitionSet.fromJson(json));
            attribute(owner,"slashing_attunement",1);attribute(owner,"slashing_potency",1);attribute(owner,"slashing_mitigation",.5);
            owner.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
            victim.hurt(h.getLevel().damageSources().playerAttack(owner),10);
            h.assertTrue(Math.abs(victim.getHealth()-181)<.01,"Sword must become slashing and stack both group modifiers: +100%-10%, actual "+victim.getHealth());
        } finally {com.cappleapple.mastery.MasteryRuntime.install(before);owner.discard();victim.discard();}
        h.succeed();
    }

    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void changingConversionCannotBypassWholeAttackHurtCooldown(GameTestHelper h) {
        var owner=MasteryTestPlayers.create(h);var victim=target(h);
        try {
            attribute(owner,"fire_conversion",.5);
            h.assertTrue(victim.hurt(h.getLevel().damageSources().playerAttack(owner),10),"Initial conversion hit failed");
            h.assertTrue(Math.abs(victim.getHealth()-190)<.01,"Initial conversion did not retain total damage");
            attribute(owner,"fire_conversion",0);
            h.assertTrue(!victim.hurt(h.getLevel().damageSources().playerAttack(owner),10),"Removing conversion bypassed hurt cooldown");
            h.assertTrue(Math.abs(victim.getHealth()-190)<.01,"Repeated raw hit dealt extra damage");
            h.assertTrue(victim.hurt(h.getLevel().damageSources().playerAttack(owner),20),"Higher raw damage should apply only its difference");
            h.assertTrue(Math.abs(victim.getHealth()-180)<.01,"Higher hit applied more than its ten-damage difference");
        } finally {owner.discard();victim.discard();}
        h.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void rejectedShieldHitCannotLeakElementalDamage(GameTestHelper h) {
        var owner=MasteryTestPlayers.create(h);
        var victim=new net.minecraft.world.entity.animal.Cow(EntityType.COW,h.getLevel()) {
            @Override public boolean isDamageSourceBlocked(DamageSource source){return true;}
        };
        victim.setPos(h.absolutePos(new BlockPos(2,2,2)).getCenter());h.getLevel().addFreshEntity(victim);
        try {
            owner.setItemSlot(EquipmentSlot.MAINHAND,aspect(h,Items.DIAMOND_SWORD,10));
            float health=victim.getHealth();
            h.assertTrue(!victim.hurt(h.getLevel().damageSources().playerAttack(owner),10),"Fixture did not reject its completely shielded primary hit");
            h.assertTrue(victim.getHealth()==health,"Blocked primary hit leaked an elemental bonus");
        } finally {owner.discard();victim.discard();}
        h.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void immunityToFirstConvertedSchoolKeepsOtherPortions(GameTestHelper h) {
        var owner=MasteryTestPlayers.create(h);
        var victim=new net.minecraft.world.entity.animal.Cow(EntityType.COW,h.getLevel()) {
            @Override public boolean isInvulnerableTo(DamageSource source){return source.is(SchoolRegistry.FIRE.get().getDamageType())||super.isInvulnerableTo(source);}
        };
        victim.setPos(h.absolutePos(new BlockPos(2,2,2)).getCenter());h.getLevel().addFreshEntity(victim);
        try {
            attribute(owner,"fire_conversion",.5);attribute(owner,"ice_conversion",.5);
            float health=victim.getHealth();
            h.assertTrue(victim.hurt(h.getLevel().damageSources().playerAttack(owner),10),"An immune fire portion discarded the independent ice portion");
            h.assertTrue(Math.abs(victim.getHealth()-(health-5))<.01,"Expected only the five ice damage to pass immunity");
        } finally {owner.discard();victim.discard();}
        h.succeed();
    }    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void elementalPortionsShareOneApothicCriticalRoll(GameTestHelper h) {
        var owner=MasteryTestPlayers.create(h);var victim=target(h);
        try {
            owner.setItemSlot(EquipmentSlot.MAINHAND,aspect(h,Items.DIAMOND_SWORD,2));
            owner.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_CHANCE).setBaseValue(1);
            owner.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_DAMAGE).setBaseValue(2);
            victim.hurt(h.getLevel().damageSources().playerAttack(owner),10);
            h.assertTrue(Math.abs(victim.getHealth()-176)<.01,"A guaranteed double critical must deal 20 physical and 4 fire once; actual "+victim.getHealth());
        } finally {owner.discard();victim.discard();}
        h.succeed();
    }}
