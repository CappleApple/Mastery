package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.classes.*;
import com.cappleapple.mastery.config.MasteryConfig;
import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.progression.PlayerProgress;
import com.cappleapple.mastery.storage.MasteryAttachments;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class ClassGameTests {
    private ClassGameTests() {}
    private static DefinitionSet definitions(DefinitionSet original) {
        var snapshot = original.toJson();
        snapshot.getAsJsonObject("nodes").add("mastery:class_test_skill", JsonParser.parseString("""
                {"tree":"mastery:fire","max_rank":3,"effects":[{"type":"mastery:attribute","attribute":"minecraft:generic.attack_damage","amount":1}]}
                """));
        snapshot.getAsJsonObject("classes").add("mastery:class_test", JsonParser.parseString("""
                {"name":"Test Class", "starting_points":{"mastery:fire":3}, "starting_skills":{"mastery:class_test_skill":2},
                 "starting_inventory":[{"id":"minecraft:diamond_sword","count":1,"components":{"minecraft:enchantments":{"levels":{"minecraft:sharpness":2}}}},{"id":"minecraft:bread","count":5}],
                 "attributes":[{"attribute":"minecraft:generic.attack_damage","amount":2}]}
                """));
        snapshot.getAsJsonObject("classes").getAsJsonObject("mastery:class_test").getAsJsonArray("starting_inventory")
                .get(0).getAsJsonObject().getAsJsonObject("components").addProperty("minecraft:custom_name", "\"Starter Blade\"");
        return DefinitionSet.fromJson(snapshot);
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void starterArmorEquipsAndDisplacedGearSurvivesOverflow(GameTestHelper helper) {
        var original=MasteryRuntime.definitions();var player=MasteryTestPlayers.createUnselected(helper);boolean enabled=MasteryConfig.CLASS_SELECTOR_ENABLED.get();
        try {
            MasteryConfig.CLASS_SELECTOR_ENABLED.set(true);var data=original.toJson();
            data.getAsJsonObject("classes").add("mastery:armor_test",JsonParser.parseString("""
                {"starting_inventory":[{"id":"minecraft:diamond_helmet","count":1},{"id":"minecraft:iron_chestplate","count":1},{"id":"minecraft:iron_leggings","count":1},{"id":"minecraft:iron_boots","count":1},{"id":"minecraft:golden_helmet","count":1}]}
                """));
            MasteryRuntime.install(DefinitionSet.fromJson(data));
            var oldHelmet=new ItemStack(Items.LEATHER_HELMET);oldHelmet.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Keep me"));
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,oldHelmet.copy());
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));
            for(int slot=0;slot<36;slot++)player.getInventory().items.set(slot,new ItemStack(Items.STONE,64));
            var preview=ClassEquipment.prepare(player,java.util.List.of(new ItemStack(Items.DIAMOND_HELMET),new ItemStack(Items.IRON_CHESTPLATE),new ItemStack(Items.IRON_LEGGINGS),new ItemStack(Items.IRON_BOOTS),new ItemStack(Items.GOLDEN_HELMET)),true);
            helper.assertTrue(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.LEATHER_HELMET),"Preview mutated worn gear");
            ClassService.login(player);helper.assertTrue(ClassService.select(player,"mastery:armor_test").isBlank(),"Armored class selection failed");
            for(var slot:ClassEquipment.ARMOR)helper.assertTrue(ItemStack.matches(player.getItemBySlot(slot),preview.armor().get(slot)),"Actual equipment differs from preview: "+slot);
            helper.assertTrue(player.getOffhandItem().is(Items.SHIELD),"Starter armor displaced unrelated offhand gear");
            helper.assertTrue(MasteryRuntime.progress(player).pendingClassItems().size()==2,"Displaced helmet and duplicate-slot armor were not preserved");
            player.getInventory().items.set(0,ItemStack.EMPTY);ClassService.deliverPendingItems(player);
            helper.assertTrue(player.getInventory().getItem(0).getHoverName().getString().equals("Keep me"),"Displaced gear lost its components");
            player.getInventory().items.set(1,ItemStack.EMPTY);ClassService.deliverPendingItems(player);
            helper.assertTrue(player.getInventory().getItem(1).is(Items.GOLDEN_HELMET),"Extra same-slot armor was lost");
            helper.assertTrue(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),"Overflow delivery replaced chosen class armor");helper.succeed();
        }finally{MasteryConfig.CLASS_SELECTOR_ENABLED.set(enabled);MasteryRuntime.install(original);MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void classChoiceGrantsOnceAndRestoresAdventureMode(GameTestHelper helper) {
        var original = MasteryRuntime.definitions(); var player = MasteryTestPlayers.createUnselected(helper);
        boolean enabled = MasteryConfig.CLASS_SELECTOR_ENABLED.get();
        try {
            MasteryConfig.CLASS_SELECTOR_ENABLED.set(true); MasteryRuntime.install(definitions(original));
            player.setGameMode(GameType.ADVENTURE); player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
            double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            ClassService.login(player);
            helper.assertTrue(player.isSpectator() && ClassService.pending(player), "Unselected join did not enter spectator selection");
            String result = ClassService.select(player, "mastery:class_test");
            helper.assertTrue(result.isBlank(), "Class selection failed: " + result);
            helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE && !ClassService.pending(player), "Selection did not restore adventure mode");
            var state = MasteryRuntime.progress(player);
            helper.assertTrue(state.selectedClass().equals("mastery:class_test") && state.tree("mastery:fire").points() == 3 && state.rank("mastery:class_test_skill") == 2, "Starter progression was not applied");
            helper.assertTrue(player.getInventory().getItem(0).getCount() == 7 && player.getInventory().countItem(Items.BREAD) == 5, "Starting inventory replaced existing items or lost grants");
            var sword = player.getInventory().items.stream().filter(stack -> stack.is(Items.DIAMOND_SWORD)).findFirst().orElseThrow();
            helper.assertTrue(sword.getHoverName().getString().equals("Starter Blade") && sword.isEnchanted(), "Native starting item components were discarded");
            helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == damage + 4, "Class and purchased skill attributes did not stack");
            ClassService.applyAttributes(player); ClassService.login(player);
            helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == damage + 4 && player.getInventory().countItem(Items.BREAD) == 5, "Relog duplicated rewards or class attributes");
            helper.assertTrue(!ClassService.select(player, "mastery:class_test").isBlank(), "Repeated selection granted starter rewards");
            var clone = MasteryTestPlayers.createCombat(helper);
            clone.restoreFrom(player, false); ClassService.login(clone);
            helper.assertTrue(MasteryRuntime.progress(clone).selectedClass().equals("mastery:class_test") && !ClassService.pending(clone), "Death lost selected class");
            ClassService.logout(clone); clone.discard();
            state.clear(); ClassService.login(player);
            helper.assertTrue(state.selectedClass().equals("mastery:class_test") && !ClassService.pending(player) && player.getInventory().countItem(Items.BREAD) == 5, "Progression reset silently reset class receipt");
            helper.succeed();
        } finally { MasteryConfig.CLASS_SELECTOR_ENABLED.set(enabled); MasteryRuntime.install(original); MasteryRuntime.logout(player); player.discard(); }
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void pendingSelectionSurvivesSaveAndDisablingRestoresOriginalMode(GameTestHelper helper) {
        var original = MasteryRuntime.definitions(); var player = MasteryTestPlayers.createUnselected(helper);
        boolean enabled = MasteryConfig.CLASS_SELECTOR_ENABLED.get();
        try {
            MasteryConfig.CLASS_SELECTOR_ENABLED.set(true); MasteryRuntime.install(definitions(original));
            player.setGameMode(GameType.CREATIVE); player.moveTo(1, 70, 1, 0, 0); ClassService.login(player);
            var saved = MasteryRuntime.progress(player).toJson(); player.setData(MasteryAttachments.PROGRESS, PlayerProgress.fromJson(saved));
            player.moveTo(9, 80, 9, 0, 0); player.setGameMode(GameType.SURVIVAL); ClassService.login(player);
            helper.assertTrue(player.isSpectator() && player.distanceToSqr(1, 70, 1) < 0.01, "Reconnect did not enforce spectator anchor");
            helper.assertTrue(MasteryRuntime.progress(player).classSelection().gameMode().equals("creative"), "Reconnect overwrote pre-selection game mode");
            MasteryConfig.CLASS_SELECTOR_ENABLED.set(false); ClassService.tick(player);
            helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE && !ClassService.pending(player), "Disabling selector stranded player in spectator");
            MasteryConfig.CLASS_SELECTOR_ENABLED.set(true); player.setGameMode(GameType.SPECTATOR); ClassService.login(player);
            helper.assertTrue(ClassService.select(player, "mastery:class_test").isBlank() && player.isSpectator(), "Legitimate spectator mode was not preserved");
            helper.succeed();
        } finally { MasteryConfig.CLASS_SELECTOR_ENABLED.set(enabled); MasteryRuntime.install(original); MasteryRuntime.logout(player); player.discard(); }
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void fullInventoryQueuesRewardsAndInvalidComponentsFailValidation(GameTestHelper helper) {
        var original = MasteryRuntime.definitions(); var player = MasteryTestPlayers.createUnselected(helper);
        boolean enabled = MasteryConfig.CLASS_SELECTOR_ENABLED.get();
        try {
            MasteryConfig.CLASS_SELECTOR_ENABLED.set(true); MasteryRuntime.install(definitions(original));
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) player.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
            ClassService.login(player);
            String result = ClassService.select(player, "mastery:class_test");
            helper.assertTrue(result.isBlank() && !player.isSpectator(), "Full inventory trapped class selection: " + result);
            helper.assertTrue(player.getInventory().items.stream().allMatch(stack -> stack.is(Items.STONE) && stack.getCount() == 64), "Class selection replaced existing inventory stacks");
            helper.assertTrue(MasteryRuntime.progress(player).pendingClassItems().size() == 2, "Overflow starter items were not saved for later delivery");
            var clone = MasteryTestPlayers.createCombat(helper); clone.restoreFrom(player, false);
            helper.assertTrue(MasteryRuntime.progress(clone).pendingClassItems().size() == 2, "Death copy lost queued starter items");
            ClassService.logout(clone); clone.discard();
            player.setData(MasteryAttachments.PROGRESS, MasteryRuntime.progress(player).copy());
            player.getInventory().items.set(0, ItemStack.EMPTY); ClassService.deliverPendingItems(player);
            helper.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND_SWORD) && MasteryRuntime.progress(player).pendingClassItems().size() == 1, "Opening one slot failed to deliver exactly one queued starter stack");
            player.getInventory().items.set(1, ItemStack.EMPTY); ClassService.deliverPendingItems(player); ClassService.deliverPendingItems(player);
            helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 5 && MasteryRuntime.progress(player).pendingClassItems().isEmpty(), "Queued starter items were lost or duplicated");
            var invalid = MasteryRuntime.definitions().toJson();
            invalid.getAsJsonObject("classes").getAsJsonObject("mastery:class_test").getAsJsonArray("starting_inventory").get(0).getAsJsonObject()
                    .getAsJsonObject("components").addProperty("minecraft:damage", "invalid");
            var errors = new java.util.ArrayList<String>(); ClassService.validateRuntime(DefinitionSet.fromJson(invalid), errors);
            helper.assertTrue(!errors.isEmpty(), "Invalid native item components passed runtime validation");
            helper.succeed();
        } finally { MasteryConfig.CLASS_SELECTOR_ENABLED.set(enabled); MasteryRuntime.install(original); MasteryRuntime.logout(player); player.discard(); }
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void emptyClassReloadReleasesSelectionAndRemovedClassAttributesAreCleaned(GameTestHelper helper) {
        var original = MasteryRuntime.definitions(); var player = MasteryTestPlayers.createUnselected(helper);
        boolean enabled = MasteryConfig.CLASS_SELECTOR_ENABLED.get();
        try {
            MasteryConfig.CLASS_SELECTOR_ENABLED.set(true); var populated = definitions(original); MasteryRuntime.install(populated);
            player.setGameMode(GameType.SURVIVAL); ClassService.login(player);
            var empty = populated.toJson(); empty.getAsJsonObject("classes").entrySet().clear();
            MasteryRuntime.install(DefinitionSet.fromJson(empty)); ClassService.reload(player);
            helper.assertTrue(!ClassService.pending(player) && !player.isSpectator(), "Empty class definitions trapped an unselected player");
            MasteryRuntime.install(populated); ClassService.login(player); ClassService.select(player, "mastery:class_test");
            double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            MasteryRuntime.install(DefinitionSet.fromJson(empty)); ClassService.reload(player);
            helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == damage - 2, "Removed class definition left stale class attribute modifiers");
            helper.assertTrue(MasteryRuntime.progress(player).selectedClass().equals("mastery:class_test"), "Removed class definition erased grant receipt");
            MasteryRuntime.install(populated); ClassService.reload(player);
            helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == damage && player.getInventory().countItem(Items.BREAD) == 5, "Restoring class definition reapplied starting inventory");
            helper.succeed();
        } finally { MasteryConfig.CLASS_SELECTOR_ENABLED.set(enabled); MasteryRuntime.install(original); MasteryRuntime.logout(player); player.discard(); }
    }
}
