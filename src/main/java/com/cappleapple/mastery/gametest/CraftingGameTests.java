package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.crafting.*;
import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.progression.ProgressionService;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class CraftingGameTests {
    private CraftingGameTests() {}
    private static CraftingService.ActiveEffect effect(String id,String json) {
        return new CraftingService.ActiveEffect(id,1,JsonParser.parseString(json).getAsJsonObject());
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void craftedComponentsPersistAndCannotApplyTwice(GameTestHelper helper) {
        var player=MasteryTestPlayers.create(helper);
        try {
            var sword=new ItemStack(Items.DIAMOND_SWORD);
            var effects=List.of(effect("mastery:test/0","{\"type\":\"mastery:crafting_attribute\",\"attribute\":\"minecraft:generic.attack_damage\",\"amount\":2}"));
            int original=sword.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().size();
            CraftingService.apply(player,sword,effects);
            helper.assertTrue(sword.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().size()==original+1,"Crafting replaced default weapon modifiers");
            var saved=sword.save(player.registryAccess());
            var restored=ItemStack.parseOptional(player.registryAccess(),(net.minecraft.nbt.CompoundTag)saved);
            CraftingService.apply(player,restored,effects);
            helper.assertTrue(restored.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().size()==original+1,"Persisted craft was applied twice");
            helper.assertTrue(restored.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream().anyMatch(e->e.modifier().amount()==2&&e.slot().test(EquipmentSlot.MAINHAND)),"Crafted damage modifier missing");
            var failure=new ItemStack(Items.DIAMOND_SWORD);
            CraftingService.apply(player,failure,List.of(effect("mastery:test/0","{\"type\":\"mastery:crafting_attribute\",\"attribute\":\"minecraft:generic.attack_damage\",\"amount\":2,\"chance\":0}")));
            CraftingService.apply(player,failure,effects);
            helper.assertTrue(failure.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().size()==original,"A failed proc could be retried on the same output");
            helper.succeed();
        } finally {MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void foodAndPotionsUseRealPersistentComponents(GameTestHelper helper) {
        var player=MasteryTestPlayers.create(helper);
        try {
            var food=new ItemStack(Items.BREAD);
            var before=food.get(DataComponents.FOOD);
            CraftingService.apply(player,food,List.of(effect("mastery:test/0","{\"type\":\"mastery:crafting_food\",\"nutrition_bonus\":1,\"saturation_bonus\":0.5,\"meal_strength_bonus\":0.25}")));
            helper.assertTrue(food.get(DataComponents.FOOD).nutrition()==before.nutrition()*2,"Food nutrition did not change");
            helper.assertTrue(Math.abs(food.get(DataComponents.FOOD).saturation()-before.saturation()*1.5)<0.0001,"Food saturation did not change");
            helper.assertTrue(CraftingService.mealBonus(food,CraftingService.MEAL_STRENGTH)==0.25,"Optional meal boost was not persisted");
            var potion=PotionContents.createItemStack(Items.POTION,Potions.SWIFTNESS);
            int original=potion.get(DataComponents.POTION_CONTENTS).getAllEffects().iterator().next().getDuration();
            CraftingService.apply(player,potion,List.of(effect("mastery:test/0","{\"type\":\"mastery:crafting_potion\",\"amplifier_bonus\":1,\"duration_bonus\":0.5,\"added_effect\":\"minecraft:regeneration\",\"added_duration\":100}")));
            var values=potion.get(DataComponents.POTION_CONTENTS).customEffects();
            helper.assertTrue(values.size()==2,"Potion retained duplicate base effects");
            helper.assertTrue(values.stream().anyMatch(e->e.is(MobEffects.MOVEMENT_SPEED)&&e.getAmplifier()==1&&e.getDuration()==original*1.5),"Potion strength or duration incorrect");
            helper.assertTrue(values.stream().anyMatch(e->e.is(MobEffects.REGENERATION)&&e.getDuration()==100),"Added potion ability missing");
            var brewing=player.level().potionBrewing();var redstone=new ItemStack(Items.REDSTONE);
            helper.assertTrue(brewing.hasMix(potion,redstone),"Crafted potion lost its brewing recipe identity");
            var rebrewed=brewing.mix(redstone,potion);
            helper.assertTrue(rebrewed.get(DataComponents.POTION_CONTENTS).potion().filter(p->p.is(Potions.LONG_SWIFTNESS)).isPresent(),"Rebrewing did not produce the extended vanilla potion");
            helper.succeed();
        } finally {MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void shiftClickAndNormalCraftKeepTheBonus(GameTestHelper helper) {
        var player=MasteryTestPlayers.create(helper);var original=MasteryRuntime.definitions();
        try {
            var snapshot=original.toJson();
            snapshot.getAsJsonObject("nodes").add("mastery:crafting_test",JsonParser.parseString("{\"tree\":\"mastery:fire\",\"max_rank\":1,\"effects\":[{\"type\":\"mastery:crafting_attribute\",\"item\":\"minecraft:oak_planks\",\"attribute\":\"minecraft:generic.luck\",\"amount\":2}]}"));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));
            ProgressionService.setRank(MasteryRuntime.definitions(),MasteryRuntime.progress(player),"mastery:crafting_test",1,-1);
            var menu=player.inventoryMenu;
            menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_PLANKS),"Crafting fixture has no planks output");
            menu.clicked(0,0,ClickType.QUICK_MOVE,player);
            helper.assertTrue(player.getInventory().items.stream().filter(s->s.is(Items.OAK_PLANKS)).anyMatch(s->CraftingService.data(s).getBoolean(CraftingService.MARKER)),"Shift-click copied output before its skill modifier");
            menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
            menu.clicked(0,0,ClickType.PICKUP,player);
            helper.assertTrue(menu.getCarried().is(Items.OAK_PLANKS)&&CraftingService.data(menu.getCarried()).getBoolean(CraftingService.MARKER),"Normal click lost skill modifier");
            helper.succeed();
        } finally {MasteryRuntime.install(original);MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void placedComfortSurvivesSaveAndClearsOnReplacement(GameTestHelper helper) {
        var player=MasteryTestPlayers.create(helper);
        try {
            BlockPos pos=helper.absolutePos(new BlockPos(1,1,1));
            helper.getLevel().setBlockAndUpdate(pos,Blocks.OAK_PLANKS.defaultBlockState());player.setPos(pos.getCenter());
            var data=PlacedComfortData.get(helper.getLevel());
            data.put(pos,List.of(new PlacedComfortData.Bonus(pos,"minecraft:oak_planks","mastery:test/0","furniture",2,8)));
            var restored=PlacedComfortData.load(data.save(new net.minecraft.nbt.CompoundTag(),player.registryAccess()),player.registryAccess());
            helper.assertTrue(restored.nearby(player).stream().anyMatch(b->b.position().equals(pos)&&b.amount()==2),"Placed comfort did not persist");
            helper.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(pos,Blocks.OAK_PLANKS.defaultBlockState());
            helper.assertTrue(data.nearby(player).stream().noneMatch(b->b.position().equals(pos)),"Replacing a block recovered a stale comfort bonus");
            helper.succeed();
        } finally {MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void optionalNeedsProvidersScaleMealsAndProvideComfortWhenInstalled(GameTestHelper helper) throws ReflectiveOperationException {
        if(!net.neoforged.fml.ModList.get().isLoaded("needs_not_necessities")){helper.succeed();return;}
        var player=MasteryTestPlayers.create(helper);
        try {
            com.cappleapple.mastery.integration.NeedsNotNecessitiesIntegration.initialize();
            String prefix="com.cappleapple.needsnotnecessities.";
            var registry=Class.forName(prefix+"api.provider.SurvivalProviderRegistry");
            var mealProvider=Class.forName(prefix+"api.provider.MealAnalyzer");
            var comfortProvider=Class.forName(prefix+"api.provider.ComfortProvider");
            var analyzer=((java.util.List<?>)registry.getMethod("mealAnalyzers").invoke(null)).stream().filter(p->p.toString().contains("Mastery optional")).findFirst().orElseThrow();
            var comfort=((java.util.List<?>)registry.getMethod("comfortProviders").invoke(null)).stream().filter(p->p.toString().contains("Mastery optional")).findFirst().orElseThrow();
            var meal=Class.forName(prefix+"survival.meal.MealAnalysis");
            var modifier=Class.forName(prefix+"modifier.SurvivalModifier");
            var operation=Class.forName(prefix+"modifier.ModifierOperation");
            Object op=java.util.Arrays.stream(operation.getEnumConstants()).filter(o->o.toString().equals("MULTIPLY_TOTAL")).findFirst().orElseThrow();
            var id=net.minecraft.resources.ResourceLocation.parse("mastery:test_buff");
            var target=net.minecraft.resources.ResourceLocation.parse("minecraft:generic.max_health");
            var constructor=modifier.getConstructor(net.minecraft.resources.ResourceLocation.class,net.minecraft.resources.ResourceLocation.class,double.class,operation);
            var base=meal.getConstructor(double.class,double.class,int.class,java.util.Map.class,java.util.List.class,double.class).newInstance(5d,10d,1,java.util.Map.of(),java.util.List.of(constructor.newInstance(id,target,.1,op),constructor.newInstance(net.minecraft.resources.ResourceLocation.parse("mastery:test_penalty"),target,-.1,op)),0d);
            var food=new ItemStack(Items.BREAD);
            CraftingService.update(food,tag->{tag.putDouble(CraftingService.MEAL_STRENGTH,.5);tag.putDouble(CraftingService.MEAL_DURATION,.25);});
            var improved=mealProvider.getMethod("modify",net.minecraft.server.level.ServerPlayer.class,ItemStack.class,double.class,meal).invoke(analyzer,player,food,5d,base);
            helper.assertTrue(Math.abs(((Number)meal.getMethod("durationBiologicalHours").invoke(improved)).doubleValue()-12.5)<1e-8,"Optional meal duration provider failed");
            var modifiers=(java.util.List<?>)meal.getMethod("modifiers").invoke(improved);
            helper.assertTrue(Math.abs(((Number)modifier.getMethod("amount").invoke(modifiers.get(0))).doubleValue()-.15)<1e-8,"Optional meal buff strength provider failed");
            helper.assertTrue(((Number)modifier.getMethod("amount").invoke(modifiers.get(1))).doubleValue()==-.1,"Optional meal provider amplified a penalty");
            var pos=helper.absolutePos(new BlockPos(1,1,1));helper.getLevel().setBlockAndUpdate(pos,Blocks.OAK_PLANKS.defaultBlockState());player.setPos(pos.getCenter());
            var storage=PlacedComfortData.get(helper.getLevel());storage.put(pos,List.of(new PlacedComfortData.Bonus(pos,"minecraft:oak_planks","mastery:optional_test/0","test_comfort",3,8)));
            var contributions=(java.util.List<?>)comfortProvider.getMethod("provide",net.minecraft.server.level.ServerPlayer.class).invoke(comfort,player);
            boolean found=false;
            for(Object contribution:contributions)if(contribution.getClass().getMethod("type").invoke(contribution).equals("test_comfort")&&((Number)contribution.getClass().getMethod("comfort").invoke(contribution)).doubleValue()==3)found=true;
            helper.assertTrue(found,"Optional placed comfort provider returned no placed-block contribution");storage.remove(pos);
            helper.succeed();
        } finally {MasteryRuntime.logout(player);player.discard();}
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void failedExtractionCannotRerollRegeneratedCraftingPreviews(GameTestHelper helper) {
        var player=MasteryTestPlayers.create(helper);var original=MasteryRuntime.definitions();
        try {
            var snapshot=original.toJson();
            snapshot.getAsJsonObject("nodes").add("mastery:preview_test",JsonParser.parseString("{\"tree\":\"mastery:fire\",\"effects\":[{\"type\":\"mastery:crafting_attribute\",\"item\":\"minecraft:oak_planks\",\"chance\":0.5,\"attribute\":\"minecraft:generic.luck\",\"amount\":2}]}"));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));ProgressionService.setRank(MasteryRuntime.definitions(),MasteryRuntime.progress(player),"mastery:preview_test",1,-1);
            for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
            var menu=player.inventoryMenu;menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
            long sequence=player.getPersistentData().getLong("mastery_craft_sequence");
            menu.clicked(0,0,ClickType.QUICK_MOVE,player);var first=menu.getSlot(0).getItem().copy();
            helper.assertTrue(menu.getSlot(1).getItem().getCount()==1,"Failed extraction consumed the input");
            for(int attempt=0;attempt<12;attempt++) {
                menu.getSlot(1).set(ItemStack.EMPTY);menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
                menu.clicked(0,0,ClickType.QUICK_MOVE,player);
                helper.assertTrue(ItemStack.isSameItemSameComponents(first,menu.getSlot(0).getItem()),"Rebuilding an uncollected preview rerolled its chance");
            }
            helper.assertTrue(player.getPersistentData().getLong("mastery_craft_sequence")==sequence,"Failed moves advanced the production sequence");
            menu.clicked(0,0,ClickType.PICKUP,player);
            helper.assertTrue(menu.getCarried().is(Items.OAK_PLANKS)&&menu.getSlot(1).getItem().isEmpty(),"Successful collection failed to consume the recipe");
            helper.assertTrue(player.getPersistentData().getLong("mastery_craft_sequence")==sequence+1,"Successful extraction did not commit exactly one production roll");
            helper.assertTrue(!CraftingService.data(menu.getCarried()).contains("mastery_craft_attempt"),"Per-attempt metadata prevents otherwise equal outputs from stacking");
            helper.succeed();
        } finally {MasteryRuntime.install(original);MasteryRuntime.logout(player);player.discard();}
    }
}
