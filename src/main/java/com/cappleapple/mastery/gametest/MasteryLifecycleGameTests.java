package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.*;
import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.cappleapple.mastery.progression.*;
import com.cappleapple.mastery.storage.*;
import com.google.gson.*;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class MasteryLifecycleGameTests {
    private MasteryLifecycleGameTests(){}
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void attachmentRoundTripAndDeathCopy(GameTestHelper helper) {
        var original=MasteryTestPlayers.create(helper);
        var state=MasteryRuntime.progress(original);
        ProgressionService.grantPoints(MasteryRuntime.definitions(),state,"mastery:two_handed",3);
        ProgressionService.purchase(MasteryRuntime.definitions(),state,"mastery:two_handed/foundation",-1,j->true);
        state.loadout("mastery:two_handed").set(0,"mastery:two_handed/foundation");
        var tag=MasteryAttachments.CODEC.encodeStart(NbtOps.INSTANCE,state).getOrThrow();
        var restored=MasteryAttachments.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow();
        helper.assertTrue(restored.toJson().equals(state.toJson()),"Attachment NBT roundtrip changed progression");
        var clone=MasteryTestPlayers.create(helper);
        clone.restoreFrom(original,false);
        var copied=MasteryRuntime.progress(clone);
        helper.assertTrue(copied.rank("mastery:two_handed/foundation")==1 && copied.tree("mastery:two_handed").points()==2,"Death lost purchases/points");
        helper.assertTrue(copied!=state,"Death copy aliased the old mutable attachment");
        copied.tree("mastery:two_handed").points(0);
        helper.assertTrue(copied.tree("mastery:two_handed").discovered(),"Spending all points hid tree");
        helper.assertTrue(state.tree("mastery:two_handed").points()==2,"New player mutated old player");
        original.discard();clone.discard();helper.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void packetCodecsAndIndependentPlayers(GameTestHelper helper) {
        var alice=MasteryTestPlayers.create(helper);var bob=MasteryTestPlayers.create(helper);
        MasteryRuntime.grantPoints(alice,"mastery:fire",3);
        helper.assertTrue(MasteryRuntime.progress(bob).trees().isEmpty(),"Player progress leaked across players");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),helper.getLevel().registryAccess());
        var packet=new MasteryNetwork.Snapshot("progress",7,0,1,MasteryRuntime.progress(alice).toJson().toString());
        MasteryNetwork.Snapshot.CODEC.encode(buffer,packet);
        helper.assertTrue(MasteryNetwork.Snapshot.CODEC.decode(buffer).equals(packet),"Snapshot packet roundtrip failed");
        buffer.clear();
        var action=new MasteryNetwork.Action("equip","irons_spellbooks:fireball","mastery:spells",0);
        MasteryNetwork.Action.CODEC.encode(buffer,action);
        helper.assertTrue(MasteryNetwork.Action.CODEC.decode(buffer).equals(action),"Action packet roundtrip failed");
        buffer.release();alice.discard();bob.discard();helper.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void derivedAttributesRebuildWithoutStacking(GameTestHelper helper) {
        var player=MasteryTestPlayers.create(helper);
        var weapon=net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().map(net.minecraft.world.item.ItemStack::new).filter(stack->{var a=net.bettercombat.logic.WeaponRegistry.getAttributes(stack);return a!=null&&a.isTwoHanded();}).findFirst().orElseThrow();
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,weapon);
        double original=player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ProgressionService.setRank(MasteryRuntime.definitions(),MasteryRuntime.progress(player),"mastery:two_handed/foundation",2,-1);
        EffectService.rebuild(player);EffectService.rebuild(player);
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE)==original+2,"Reload stacked/lost transient attribute effects");
        ProgressionService.resetTree(MasteryRuntime.definitions(),MasteryRuntime.progress(player),"mastery:two_handed");
        EffectService.rebuild(player);
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE)==original,"Reset left orphan attribute effect");
        EffectService.clear(player);player.discard();helper.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void loadedDatapackAndWorldIdentity(GameTestHelper helper) {
        helper.assertTrue(MasteryRuntime.definitions().trees().size()==21,"Demo tree resources failed to load");
        helper.assertTrue(MasteryRuntime.definitions().spells().size()==9,"Existing spell bindings failed to load");
        helper.assertTrue(MasteryRuntime.lastReloadErrors.isEmpty(),"Demo graph rejected");
        String first=WorldIdentity.get(helper.getLevel().getServer());
        helper.assertTrue(!first.isBlank()&&first.equals(WorldIdentity.get(helper.getLevel().getServer())),"World identity not stable");
        helper.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void adminCommandsAcceptNamespacedIds(GameTestHelper helper) {
        var player=MasteryTestPlayers.create(helper);
        try {
            var source=player.createCommandSourceStack().withPermission(4);
            var dispatcher=helper.getLevel().getServer().getCommands().getDispatcher();
            int result=dispatcher.execute("mastery points add @s mastery:two_handed 2",source);
            helper.assertTrue(result==1 && MasteryRuntime.progress(player).tree("mastery:two_handed").points()==2,"Namespaced points command failed");
            result=dispatcher.execute("mastery node unlock @s mastery:two_handed/foundation",source);
            helper.assertTrue(result==1 && MasteryRuntime.progress(player).rank("mastery:two_handed/foundation")==1,"Namespaced node command failed");
            helper.succeed();
        } catch(Exception error) { helper.fail("Command failed: "+error.getMessage()); }
        finally { player.discard(); }
    }
}
