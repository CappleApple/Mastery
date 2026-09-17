package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.api.CombatContextProvider;
import com.cappleapple.mastery.api.SpellEquipmentRegistry;
import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.data.NodeDefinition;
import com.cappleapple.mastery.integration.IronSpellsIntegration;
import com.cappleapple.mastery.progression.ProgressionService;
import com.cappleapple.mastery.spells.*;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.events.ModifySpellLevelEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.compat.Curios;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class MasterySpellGameTests {
    private static final String FIREBALL="irons_spellbooks:fireball";
    private MasterySpellGameTests() {}
    private static FakePlayer player(GameTestHelper helper) {
        var player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"MasterySpellTest"));
        player.setPos(helper.absolutePos(net.minecraft.core.BlockPos.ZERO).getCenter());
        MagicData.getPlayerMagicData(player).getSyncedData();
        return player;
    }
    private static void rank(FakePlayer player,String node,int rank) {
        var change=ProgressionService.setRank(MasteryRuntime.definitions(),MasteryRuntime.progress(player),node,rank,-1);
        if(!change.success()) throw new IllegalStateException(change.message());
    }
    private static ItemStack capacityItem(int slots) {
        var stack=new ItemStack(Items.BOOK);
        ISpellContainer.set(stack,ISpellContainer.create(slots,true,false));
        return stack;
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void weaponBonusesFollowBetterCombatAndNativeApothicArrows(GameTestHelper helper) {
        var player=player(helper);
        try {
            var attack=net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
            var damage=dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.ARROW_DAMAGE;
            var velocity=dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.ARROW_VELOCITY;
            double baseline=player.getAttributeValue(attack);
            rank(player,"mastery:two_handed/foundation",2);rank(player,"mastery:bow/foundation",2);rank(player,"mastery:crossbow/foundation",2);
            com.cappleapple.mastery.effects.EffectService.rebuild(player);
            helper.assertTrue(player.getAttributeValue(attack)==baseline,"Two-handed bonus applied to empty hands");
            var weapon=BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(stack->{var a=WeaponRegistry.getAttributes(stack);return a!=null&&a.isTwoHanded();}).findFirst().orElseThrow();
            player.setItemSlot(EquipmentSlot.MAINHAND,weapon);com.cappleapple.mastery.effects.EffectService.refreshWeaponContext(player);
            helper.assertTrue(player.getAttributeValue(attack)==baseline+2,"Two-handed bonus missing with Better Combat weapon");
            player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));com.cappleapple.mastery.effects.EffectService.refreshWeaponContext(player);
            helper.assertTrue(player.getAttributeValue(attack)==baseline,"Two-handed bonus remained after weapon switch");
            helper.assertTrue(Math.abs(player.getAttributeValue(damage)-1.1)<1e-8,"Bow ranks did not use Apothic arrow damage");
            helper.assertTrue(player.getAttributeValue(velocity)==1,"Crossbow velocity leaked into bow context");
            var arrow=net.minecraft.world.entity.EntityType.ARROW.create(helper.getLevel());arrow.setOwner(player);arrow.setPos(player.position());arrow.setBaseDamage(2);arrow.setDeltaMovement(0,0,2);helper.getLevel().addFreshEntity(arrow);
            helper.assertTrue(Math.abs(arrow.getBaseDamage()-2.2)<1e-8,"Apothic did not scale the native arrow damage");
            player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.CROSSBOW));com.cappleapple.mastery.effects.EffectService.refreshWeaponContext(player);
            helper.assertTrue(player.getAttributeValue(damage)==1,"Bow damage leaked into crossbow context");
            var bolt=net.minecraft.world.entity.EntityType.ARROW.create(helper.getLevel());bolt.setOwner(player);bolt.setPos(player.position());bolt.setBaseDamage(2);bolt.setDeltaMovement(0,0,2);helper.getLevel().addFreshEntity(bolt);
            helper.assertTrue(Math.abs(bolt.getDeltaMovement().length()-2.2)<1e-8,"Apothic did not scale native arrow velocity");
            arrow.discard();bolt.discard();helper.succeed();
        } finally {MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void groupedParentsKeepPurchasedEffectsActiveThroughEitherRoute(GameTestHelper helper) {
        var original=MasteryRuntime.definitions();var player=player(helper);
        try {
            var snapshot=original.toJson();var node=snapshot.getAsJsonObject("nodes").getAsJsonObject("mastery:fire/extra_slot");
            node.add("dependencies",com.google.gson.JsonParser.parseString("[{\"or\":[{\"node\":\"mastery:fire/foundation\",\"rank\":2},{\"node\":\"mastery:fireball\",\"rank\":1}]}]"));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));rank(player,"mastery:fire/extra_slot",1);rank(player,"mastery:fireball",1);
            helper.assertTrue(com.cappleapple.mastery.effects.EffectService.active(player,MasteryRuntime.definitions().nodes().get("mastery:fire/extra_slot")),"Second OR route did not activate purchased effect");
            rank(player,"mastery:fireball",0);rank(player,"mastery:fire/foundation",2);
            helper.assertTrue(com.cappleapple.mastery.effects.EffectService.active(player,MasteryRuntime.definitions().nodes().get("mastery:fire/extra_slot")),"First OR route did not activate purchased effect");
            rank(player,"mastery:fire/foundation",1);
            helper.assertTrue(!com.cappleapple.mastery.effects.EffectService.active(player,MasteryRuntime.definitions().nodes().get("mastery:fire/extra_slot")),"Unsatisfied OR group remained active");helper.succeed();
        } finally {MasteryRuntime.install(original);MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(batch="mastery_burst",templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=120)
    public static void instantFireboltUsesConfiguredChargeTimeAndNativeBurst(GameTestHelper helper) {
        var original=MasteryRuntime.definitions();var snapshot=original.toJson();String id="irons_spellbooks:firebolt";
        var binding=com.google.gson.JsonParser.parseString("{\"spell\":\"irons_spellbooks:firebolt\",\"tree\":\"mastery:fire\"}").getAsJsonObject();
        var node=com.google.gson.JsonParser.parseString("{\"tree\":\"mastery:fire\",\"spell\":\"irons_spellbooks:firebolt\",\"type\":\"active\",\"max_rank\":3}").getAsJsonObject();
        snapshot.getAsJsonObject("spells").add(id,binding);snapshot.getAsJsonObject("nodes").add("mastery:burst_test",node);
        MasteryRuntime.install(DefinitionSet.fromJson(snapshot));
        var player=player(helper);player.setNoGravity(true);player.setPos(player.getX(),player.getY()+6,player.getZ());player.setXRot(-30);
        player.setItemSlot(EquipmentSlot.OFFHAND,capacityItem(4));rank(player,"mastery:burst_test",2);
        var magic=MagicData.getPlayerMagicData(player);magic.setMana(100);helper.getLevel().addNewPlayer(player);
        helper.assertTrue(SpellService.assign(player,SpellService.context(player),0,id).isEmpty(),"Burst fixture assignment failed");
        // Native magic projectiles discard outside entity-ticking chunks; count their actual spawn events.
        var spawned=new ArrayList<io.redspace.ironsspellbooks.entity.spells.firebolt.FireboltProjectile>();
        var spawnTicks=new ArrayList<Integer>();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> capture=event->{
            if(event.getEntity() instanceof io.redspace.ironsspellbooks.entity.spells.firebolt.FireboltProjectile bolt&&bolt.getOwner()==player) {
                spawned.add(bolt);spawnTicks.add(player.tickCount);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,false,net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class,capture);
        SpellService.press(player,0);helper.assertTrue(magic.getCastDuration()==50,"Instant base duration or linear charge ranks ignored");
        // FakePlayer is in the level's native cast loop, but not the server connection list used by Mastery's tick loop.
        for(int tick=1;tick<=60;tick++)helper.runAfterDelay(tick,()->SpellService.tick(player));
        helper.runAfterDelay(61,()->{
            try {

                helper.assertTrue(spawned.size()==3,"Expected native Firebolt plus two burst shots; got "+spawned.size()+", spawn ticks="+spawnTicks+", mana="+magic.getMana());
                helper.assertTrue(spawnTicks.getFirst()>=49,"Instant spell spawned before its configured charge duration: "+spawnTicks);
                helper.assertTrue(magic.getMana()>=100-SpellRegistry.getSpell(id).getManaCost(1)&&magic.getMana()<100,"Burst charged native mana more than once");
                helper.assertTrue(magic.getPlayerCooldowns().isOnCooldown(SpellRegistry.getSpell(id)),"Burst lost native cooldown");
                helper.succeed();
            } finally {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(capture);spawned.forEach(net.minecraft.world.entity.Entity::discard);MasteryRuntime.install(original);MasteryRuntime.logout(player);player.discard();}
        });
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void modifierCapacityInheritsTreeAndNodeAndGrowsWithSpellRank(GameTestHelper helper) {
        var original=MasteryRuntime.definitions();var player=player(helper);
        try {
            var snapshot=original.toJson();
            snapshot.getAsJsonObject("trees").getAsJsonObject("mastery:fire").add("modifier_slots",com.google.gson.JsonParser.parseString("{\"base\":1,\"per_level\":2}"));
            snapshot.getAsJsonObject("nodes").getAsJsonObject("mastery:fireball").add("modifier_slots",com.google.gson.JsonParser.parseString("{\"per_level\":3}"));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));rank(player,"mastery:fireball",1);
            helper.assertTrue(SpellService.modifierLimit(player,MasteryRuntime.definitions().spells().get(FIREBALL))==1,"Tree base modifier capacity ignored");
            rank(player,"mastery:fireball",3);
            helper.assertTrue(SpellService.modifierLimit(player,MasteryRuntime.definitions().spells().get(FIREBALL))==7,"Node growth override ignored");
            helper.succeed();
        } finally {MasteryRuntime.install(original);MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void hotbarSelectionAndModeCapacityAreServerAuthoritative(GameTestHelper helper) {
        int oldCapacity=com.cappleapple.mastery.config.MasteryConfig.ACTIVE_CAPACITY.get();
        com.cappleapple.mastery.config.MasteryConfig.ACTIVE_CAPACITY.set(0);
        var player=player(helper);
        try {
            player.setItemSlot(EquipmentSlot.OFFHAND,capacityItem(1));rank(player,"mastery:fireball",1);
            int capacity=SpellService.capacity(player);helper.assertTrue(capacity==1,"Fixture needs capacity 1");
            var progress=MasteryRuntime.progress(player);
            helper.assertTrue(SpellService.assign(player,BindingSlots.hotbar(0),0,FIREBALL).isEmpty(),"First binding rejected");
            helper.assertTrue(!SpellService.assign(player,BindingSlots.hotbar(1),0,FIREBALL).isEmpty(),"Second hotbar exceeded capacity");
            helper.assertTrue(BindingSlots.visible(progress.loadouts(),"hotbar",BindingSlots.hotbar(1),1).isEmpty(),"Phantom empty slot shown");
            player.getInventory().selected=1;
            helper.assertTrue(SpellService.context(player).equals(BindingSlots.hotbar(1)),"Hotbar selection did not change active set");
            helper.assertTrue(SpellService.bindingMode(player,"quick_cast").isEmpty(),"Quick mode rejected");
            helper.assertTrue(SpellService.assign(player,BindingSlots.QUICK,0,FIREBALL).isEmpty(),"Inactive layout consumed quick capacity");
            helper.assertTrue(!SpellService.assign(player,BindingSlots.QUICK,1,FIREBALL).isEmpty(),"Nonexistent slot accepted");
            SpellService.bindingMode(player,"hotbar");player.getInventory().selected=0;
            helper.assertTrue(progress.loadout(BindingSlots.hotbar(0)).get(0).equals(FIREBALL),"Mode switch erased hotbar bindings");
            helper.succeed();
        } finally {com.cappleapple.mastery.config.MasteryConfig.ACTIVE_CAPACITY.set(oldCapacity);MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(batch="mastery_charge",templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=140)
    public static void chargedFireballUsesNativeProjectileWithSyncedSizeAndRadius(GameTestHelper helper) {
        var player=player(helper);player.setItemSlot(EquipmentSlot.MAINHAND,capacityItem(4));
        player.setPos(player.getX(),player.getY()+6,player.getZ());player.setNoGravity(true);player.setXRot(-30);
        var magic=MagicData.getPlayerMagicData(player);magic.setMana(100);rank(player,"mastery:fireball",2);
        var spell=SpellRegistry.getSpell(FIREBALL);int base=spell.getEffectiveCastTime(1,player);
        helper.getLevel().addNewPlayer(player);
        helper.assertTrue(SpellService.assign(player,SpellService.context(player),0,FIREBALL).isEmpty(),"Charge fixture assignment failed");
        SpellService.press(player,0);
        helper.assertTrue(magic.getCastDuration()==base+40,"Two charge ranks did not add two equal windows");
        helper.assertTrue(magic.getCastingSpellLevel()==spell.getLevelFor(1,player),"Charge ranks raised the base spell level");
        helper.runAfterDelay(magic.getCastDuration()+3,()->{
            try {
                var fireballs=helper.getLevel().getEntitiesOfClass(io.redspace.ironsspellbooks.entity.spells.fireball.MagicFireball.class,player.getBoundingBox().inflate(30),e->e.getOwner()==player);
                helper.assertTrue(!fireballs.isEmpty(),"Native charged fireball did not spawn");
                var fireball=fireballs.getFirst();
                helper.assertTrue(Math.abs(((NativeProjectileScale)fireball).mastery$getScale()-2)<.01,"Charge scale was not synchronized on native entity");
                helper.assertTrue(fireball.getExplosionRadius()>=6,"Charged explosion radius did not grow");
                helper.assertTrue(magic.getMana()>=100-spell.getManaCost(1)&&magic.getMana()<100,"Native mana was charged more than once");
                helper.assertTrue(magic.getPlayerCooldowns().isOnCooldown(spell),"Native cooldown missing");
                helper.succeed();
            } finally {MasteryRuntime.logout(player);player.discard();}
        });
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void disablingSpellKeepsModifiersAndAssignmentsAndReenablesCleanly(GameTestHelper helper) {
        var player=player(helper);
        try {
            player.setItemSlot(EquipmentSlot.MAINHAND,capacityItem(4));
            rank(player,"mastery:fireball",1);rank(player,"mastery:fireball/efficiency",2);
            var defs=MasteryRuntime.definitions();var progress=MasteryRuntime.progress(player);
            var child=defs.nodes().get("mastery:fireball/efficiency");var parent=defs.nodes().get("mastery:fireball");
            helper.assertTrue(SpellService.toggleNode(player,child,true).isEmpty(),"Child activation failed");
            String context=SpellService.context(player);
            helper.assertTrue(SpellService.assign(player,context,0,FIREBALL).isEmpty(),"Fixture assignment failed");
            helper.assertTrue(SpellService.toggleNode(player,parent,false).isEmpty(),"Parent disable failed");
            ProgressionService.reconcile(defs,progress);SpellService.reconcile(player);
            helper.assertTrue(!SpellService.owned(player,FIREBALL),"Disabled spell remained castable");
            helper.assertTrue(progress.loadout(context).get(0).equals(FIREBALL),"Disabling erased spell assignment");
            helper.assertTrue(com.cappleapple.mastery.effects.EffectService.effectiveRank(player,child)==2,"Parent switch disabled child");
            helper.assertTrue(!SpellService.press(player,0).isEmpty(),"Disabled spell cast was admitted");
            helper.assertTrue(SpellService.toggleNode(player,parent,true).isEmpty(),"Parent reenable failed");
            helper.assertTrue(Math.abs(SpellService.modifiers(player,FIREBALL).manaMultiplier()-.81)<1e-8,"Child modifier lost after parent toggle");
            helper.assertTrue(SpellService.toggleNode(player,child,false).isEmpty(),"Child disable failed");
            helper.assertTrue(SpellService.modifiers(player,FIREBALL).manaMultiplier()==1,"Disabled modifier still applied");
            helper.assertTrue(SpellService.toggleNode(player,child,true).isEmpty(),"Disabled child could not reenable");
            helper.succeed();
        } finally {MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void capacityUsesMaximumAndPreservesInscription(GameTestHelper helper) {
        var player=player(helper);
        try {
            var book=capacityItem(4); player.setItemSlot(EquipmentSlot.MAINHAND,book);
            int empty=SpellService.capacity(player);
            var mutable=ISpellContainer.get(book).mutableCopy();
            mutable.addSpellAtIndex(SpellRegistry.getSpell(FIREBALL),1,0,true);
            ISpellContainer.set(book,mutable.toImmutable());
            var original=book.copy();
            helper.assertTrue(SpellService.capacity(player)==empty,"Native inscription reduced Mastery capacity");
            helper.assertTrue(IronSpellsIntegration.equipmentCapacity(player)==4,"Capacity did not use native maximum slots");
            rank(player,"mastery:fireball",1);
            String context=SpellService.context(player);
            helper.assertTrue(SpellService.assign(player,context,0,FIREBALL).isEmpty(),"Owned spell could not be assigned");
            SpellService.reconcile(player); SpellService.snapshot(player);
            helper.assertTrue(ItemStack.isSameItemSameComponents(book,original),"Mastery rewrote native spell-container data");
            helper.assertTrue(ISpellContainer.get(book).getActiveSpellCount()==1,"Mastery changed native inscription count");
            player.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            helper.assertTrue(SpellService.capacity(player)==empty-4,"Removing the native item retained its capacity");
            rank(player,"mastery:fireball",0);
            helper.assertTrue(!SpellService.owned(player,FIREBALL),"Revoked node still granted native spell ownership");
            SpellService.reconcile(player);
            helper.assertTrue(MasteryRuntime.progress(player).loadout(context).getFirst().isEmpty(),"Ownership reconciliation did not clear slot");
            helper.succeed();
        } finally { MasteryRuntime.logout(player); player.discard(); }
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void preparedSpellUsesNativeCastManaAndCooldownWithoutInscription(GameTestHelper helper) {
        var player=player(helper);
        try {
            helper.assertTrue(!player.isCreative(),"Mana test player must be survival");
            var spell=SpellRegistry.getSpell(FIREBALL); var magic=MagicData.getPlayerMagicData(player);
            player.setItemSlot(EquipmentSlot.MAINHAND,capacityItem(4));
            magic.setMana(100); rank(player,"mastery:fireball",1);
            String context=SpellService.context(player);
            helper.assertTrue(SpellService.assign(player,context,0,FIREBALL).isEmpty(),"Assignment required native inscription");
            helper.assertTrue(ISpellContainer.get(player.getMainHandItem()).isEmpty(),"Fixture must contain no inscribed spells");
            helper.assertTrue(!spell.attemptInitiateCast(ItemStack.EMPTY,1,player.level(),player,CastSource.SPELLBOOK,true,Curios.SPELLBOOK_SLOT),"Native book shortcut bypassed Mastery slot validation");
            helper.assertTrue(SpellService.press(player,0).isEmpty(),"Mastery did not initiate the existing native spell");
            helper.assertTrue(magic.isCasting()&&magic.getCastingSpellId().equals(FIREBALL),"Native MagicData did not own casting state");
            int duration=magic.getCastDuration(); SpellService.release(player,0);
            helper.assertTrue(magic.isCasting()&&magic.getCastDuration()==duration,"Release bypassed native cast duration");
            float manaBefore=magic.getMana();
            // Invoke Iron's completion entrypoint directly: FakePlayer is not in the live level player tick list.
            spell.castSpell(player.level(),magic.getCastingSpellLevel(),player,CastSource.SPELLBOOK,true);
            spell.onServerCastComplete(player.level(),1,player,magic,false);
            helper.assertTrue(magic.getMana()==manaBefore-spell.getManaCost(1),"Iron did not debit its native mana");
            helper.assertTrue(magic.getPlayerCooldowns().isOnCooldown(spell),"Iron did not create native cooldown");
            SpellService.press(player,0);
            helper.assertTrue(!magic.isCasting(),"Mastery bypassed native cooldown");
            helper.assertTrue(ISpellContainer.get(player.getMainHandItem()).isEmpty(),"Casting inscribed a spell into native item");
            helper.assertTrue(SpellService.snapshot(player).getAsJsonObject("cooldowns").has(FIREBALL),"Native cooldown missing from read-only snapshot");
            helper.succeed();
        } finally { MasteryRuntime.logout(player); player.discard(); }
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void modifiersAdjustNativeAdmissionDurationLevelsAndCosts(GameTestHelper helper) {
        var player=player(helper);
        try {
            var spell=SpellRegistry.getSpell(FIREBALL); var magic=MagicData.getPlayerMagicData(player);
            rank(player,"mastery:fireball",1);
            int originalTime=spell.getEffectiveCastTime(1,player);
            int originalLevel=spell.getLevelFor(1,player);
            rank(player,"mastery:fireball/efficiency",3);
            rank(player,"mastery:fireball/empowered",1);
            helper.assertTrue(SpellService.setModifier(player,FIREBALL,"mastery:fireball/efficiency",true).isEmpty(),"Efficiency modifier rejected");
            helper.assertTrue(SpellService.setModifier(player,FIREBALL,"mastery:fireball/empowered",true).isEmpty(),"Level modifier rejected");
            helper.assertTrue(spell.getLevelFor(1,player)==originalLevel+1,"Public native level event was not adjusted");
            helper.assertTrue(spell.getEffectiveCastTime(1,player)==SpellModifiers.scale(originalTime,Math.pow(.9,3)),"Native cast duration mixin not applied");
            int reduced=SpellModifiers.scale(spell.getManaCost(1),Math.pow(.9,3));
            magic.setMana(reduced-1);
            helper.assertTrue(!spell.canBeCastedBy(1,CastSource.SPELLBOOK,magic,player).isSuccess(),"Mana admission accepted insufficient adjusted mana");
            magic.setMana(reduced);
            helper.assertTrue(spell.canBeCastedBy(1,CastSource.SPELLBOOK,magic,player).isSuccess(),"Native mana admission ignored enabled discount");
            spell.castSpell(player.level(),1,player,CastSource.SPELLBOOK,true);
            helper.assertTrue(magic.getMana()==0,"Native cast debited a different amount than admission checked");
            int expected=SpellModifiers.scale(io.redspace.ironsspellbooks.capabilities.magic.MagicManager.getEffectiveSpellCooldown(spell,player,CastSource.SPELLBOOK),Math.pow(.9,3));
            helper.assertTrue(magic.getPlayerCooldowns().getSpellCooldowns().get(FIREBALL).getCooldownRemaining()==expected,"Native cooldown did not apply enabled multiplier");
            helper.succeed();
        } finally { MasteryRuntime.logout(player); player.discard(); }
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=120)
    public static void nativeServerTickCompletesPreparedSpell(GameTestHelper helper) {
        var player=player(helper);
        player.setItemSlot(EquipmentSlot.MAINHAND,capacityItem(4));
        var magic=MagicData.getPlayerMagicData(player); magic.setMana(100);
        rank(player,"mastery:fireball",1);
        helper.getLevel().addNewPlayer(player);
        String context=SpellService.context(player);
        helper.assertTrue(SpellService.assign(player,context,0,FIREBALL).isEmpty(),"Tick fixture assignment failed");
        helper.assertTrue(SpellService.press(player,0).isEmpty(),"Tick fixture cast failed");
        helper.runAfterDelay(magic.getCastDuration()+4,() -> {
            try {
                helper.assertTrue(!magic.isCasting(),"Native server tick did not complete the cast");
                helper.assertTrue(magic.getMana()<100,"Native server tick did not spend mana");
                helper.assertTrue(magic.getPlayerCooldowns().isOnCooldown(SpellRegistry.getSpell(FIREBALL)),"Native server tick did not create cooldown");
                helper.assertTrue(ISpellContainer.get(player.getMainHandItem()).isEmpty(),"Native timed casting changed item inscriptions");
                helper.succeed();
            } finally { MasteryRuntime.logout(player); player.discard(); }
        });
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void registeredProviderCannotSpoofBetterCombatTwoHanded(GameTestHelper helper) {
        var player=player(helper);
        CombatContextProvider spoof=ignored -> Optional.of("mastery:two_handed");
        SpellEquipmentRegistry.registerContext(Integer.MAX_VALUE,spoof);
        try {
            helper.assertTrue(!ContextResolver.resolve(player,MasteryRuntime.definitions(),id->true).equals("mastery:two_handed"),"An API provider classified empty hands as two-handed");
            var genuine=BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(stack -> {
                var attributes=WeaponRegistry.getAttributes(stack);
                return attributes!=null&&attributes.isTwoHanded();
            }).findFirst();
            helper.assertTrue(genuine.isPresent(),"Required Better Combat fixture has no resolved two-handed weapon");
            player.setItemSlot(EquipmentSlot.MAINHAND,genuine.orElseThrow());
            helper.assertTrue(ContextResolver.resolve(player,MasteryRuntime.definitions(),id->true).equals("mastery:two_handed"),"Reserved context rejected Better Combat's actual classification");
            helper.succeed();
        } finally {
            var providers=SpellEquipmentRegistry.CONTEXTS.get(Integer.MAX_VALUE);
            providers.remove(spoof);
            if(providers.isEmpty()) SpellEquipmentRegistry.CONTEXTS.remove(Integer.MAX_VALUE);
            MasteryRuntime.logout(player); player.discard();
        }
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void nativeLevelsRemainUntouchedWithoutBonusAndAddWithoutOverflow(GameTestHelper helper) {
        var player=player(helper);
        try {
            var spell=SpellRegistry.getSpell(FIREBALL);
            for(int original:new int[]{0,300,Integer.MAX_VALUE}) {
                var event=new ModifySpellLevelEvent(spell,player,1,original);
                IronSpellsIntegration.level(event);
                helper.assertTrue(event.getLevel()==original,"Mastery changed an unowned native spell level without a bonus");
            }
            rank(player,"mastery:fireball",1);
            rank(player,"mastery:fireball/empowered",3);
            helper.assertTrue(SpellService.setModifier(player,FIREBALL,"mastery:fireball/empowered",true).isEmpty(),"Native level modifier fixture rejected");
            var large=new ModifySpellLevelEvent(spell,player,1,300);
            IronSpellsIntegration.level(large);
            helper.assertTrue(large.getLevel()==303,"Mastery truncated native addon levels above255");
            var overflow=new ModifySpellLevelEvent(spell,player,1,Integer.MAX_VALUE-1);
            IronSpellsIntegration.level(overflow);
            helper.assertTrue(overflow.getLevel()==Integer.MAX_VALUE,"Adding Mastery levels overflowed a native integer level");
            var negative=new ModifySpellLevelEvent(spell,player,1,Integer.MIN_VALUE);
            IronSpellsIntegration.level(negative);
            helper.assertTrue(negative.getLevel()==1,"Applying an actual Mastery bonus produced a nonpositive level");
            helper.succeed();
        } finally { MasteryRuntime.logout(player); player.discard(); }
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void explicitModifierTargetDoesNotLeakToPrerequisiteSpell(GameTestHelper helper) {
        var player=player(helper); var original=MasteryRuntime.definitions();
        try {
            String other="irons_spellbooks:magic_missile";
            var node=original.nodes().get("mastery:fireball/efficiency");
            // A valid cross-tree dependency unlocks the modifier, while its explicit target is Magic Missile.
            var targeted=new NodeDefinition(node.id(),original.spells().get(other).tree(),node.name(),node.description(),node.icon(),
                    node.type(),node.dependencies(),node.maxRank(),node.cost(),node.level(),node.worldTier(),node.requirements(),node.effects(),
                    node.visibility(),node.exclusions(),node.toggleable(),other,node.modifier(),node.bookToken());
            var nodes=new HashMap<>(original.nodes()); nodes.put(targeted.id(),targeted);
            MasteryRuntime.install(new DefinitionSet(original.groups(),original.trees(),nodes,original.spells(),original.contexts(),
                    original.xpSources(),original.requirements(),original.effects()));
            rank(player,"mastery:fireball",1); rank(player,"mastery:magic_missile",1); rank(player,targeted.id(),1);
            helper.assertTrue(!SpellService.setModifier(player,FIREBALL,targeted.id(),true).isEmpty(),"Explicit target was ignored in favor of prerequisite ancestry");
            MasteryRuntime.progress(player).modifiers(FIREBALL).add(targeted.id());
            helper.assertTrue(SpellService.modifiers(player,FIREBALL).manaMultiplier()==1,"Stale mismatched modifier changed the prerequisite spell");
            SpellService.reconcile(player);
            helper.assertTrue(!MasteryRuntime.progress(player).modifiers(FIREBALL).contains(targeted.id()),"Reconciliation retained a modifier on the wrong spell");
            helper.assertTrue(SpellService.setModifier(player,other,targeted.id(),true).isEmpty(),"Cross-tree prerequisite blocked the explicit intended spell target");
            helper.assertTrue(Math.abs(SpellService.modifiers(player,other).manaMultiplier()-.9)<1e-8,"Explicit target lost its native modifier");
            helper.succeed();
        } finally {
            MasteryRuntime.install(original);
            MasteryRuntime.logout(player); player.discard();
        }
    }
}
