package com.cappleapple.mastery.integration;

import com.cappleapple.mastery.Mastery;
import com.cappleapple.mastery.crafting.CraftingService;
import com.cappleapple.mastery.crafting.PlacedComfortData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import java.lang.reflect.*;
import java.util.*;

/** Optional public-provider adapter. No Needs Not Necessities classes enter Mastery's class linkage. */
public final class NeedsNotNecessitiesIntegration {
    private static final String ROOT="com.cappleapple.needsnotnecessities.";
    private static boolean initialized;
    private static boolean failed;
    private static Constructor<?> contributionConstructor;
    private static Constructor<?> mealConstructor;
    private static Constructor<?> modifierConstructor;
    private static Method score,duration,complexity,traits,modifiers,quality;
    private static Method modifierId,modifierTarget,modifierAmount,modifierOperation;
    private NeedsNotNecessitiesIntegration() {}
    /** Invoke on the common setup work queue, after all mods have registered their public APIs. */
    public static synchronized void initialize() {
        if(initialized||!ModList.get().isLoaded("needs_not_necessities"))return;
        initialized=true;
        try {
            Class<?> registry=Class.forName(ROOT+"api.provider.SurvivalProviderRegistry");
            Class<?> comfortProvider=Class.forName(ROOT+"api.provider.ComfortProvider");
            Class<?> contribution=Class.forName(ROOT+"api.provider.ComfortProvider$Contribution");
            contributionConstructor=contribution.getConstructor(ResourceLocation.class,String.class,double.class);
            Class<?> mealAnalyzer=Class.forName(ROOT+"api.provider.MealAnalyzer");
            Class<?> meal=Class.forName(ROOT+"survival.meal.MealAnalysis");
            Class<?> modifier=Class.forName(ROOT+"modifier.SurvivalModifier");
            Class<?> operation=Class.forName(ROOT+"modifier.ModifierOperation");
            mealConstructor=meal.getConstructor(double.class,double.class,int.class,Map.class,List.class,double.class);
            modifierConstructor=modifier.getConstructor(ResourceLocation.class,ResourceLocation.class,double.class,operation);
            score=meal.getMethod("score");duration=meal.getMethod("durationBiologicalHours");complexity=meal.getMethod("recipeComplexity");
            traits=meal.getMethod("traits");modifiers=meal.getMethod("modifiers");quality=meal.getMethod("qualityValue");
            modifierId=modifier.getMethod("id");modifierTarget=modifier.getMethod("target");modifierAmount=modifier.getMethod("amount");modifierOperation=modifier.getMethod("operation");
            Object comfortProxy=Proxy.newProxyInstance(comfortProvider.getClassLoader(),new Class<?>[]{comfortProvider},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
                if(failed)return List.of();
                try {return comfort((ServerPlayer)args[0]);}catch(ReflectiveOperationException|RuntimeException ex){disable(ex);return List.of();}
            });
            Object mealProxy=Proxy.newProxyInstance(mealAnalyzer.getClassLoader(),new Class<?>[]{mealAnalyzer},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
                if(failed)return args[3];
                try {return meal((ItemStack)args[1],args[3]);}catch(ReflectiveOperationException|RuntimeException ex){disable(ex);return args[3];}
            });
            registry.getMethod("registerComfortProvider",comfortProvider).invoke(null,comfortProxy);
            registry.getMethod("registerMealAnalyzer",mealAnalyzer).invoke(null,mealProxy);
            Mastery.LOGGER.info("Registered optional Needs Not Necessities comfort and crafted-meal providers");
        } catch(ReflectiveOperationException|LinkageError|RuntimeException ex) {disable(ex);}
    }
    private static Object objectMethod(Object proxy,Method method,Object[] args) {
        return switch(method.getName()) {
            case "equals" -> proxy==args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "Mastery optional Needs Not Necessities provider";
            default -> null;
        };
    }
    private static List<Object> comfort(ServerPlayer player)throws ReflectiveOperationException {
        List<Object> result=new ArrayList<>();
        for(var bonus:PlacedComfortData.get(player.serverLevel()).nearby(player)) {
            ResourceLocation id=ResourceLocation.fromNamespaceAndPath("mastery","placed/"+bonus.id().replace(':','/')+"/"+bonus.position().asLong());
            result.add(contributionConstructor.newInstance(id,bonus.type(),bonus.amount()));
        }
        return List.copyOf(result);
    }
    private static Object meal(ItemStack stack,Object current)throws ReflectiveOperationException {
        double strength=CraftingService.mealBonus(stack,CraftingService.MEAL_STRENGTH);
        double time=CraftingService.mealBonus(stack,CraftingService.MEAL_DURATION);
        if(strength==0&&time==0)return current;
        List<Object> enhanced=new ArrayList<>();
        for(Object modifier:(List<?>)modifiers.invoke(current)) {
            double amount=((Number)modifierAmount.invoke(modifier)).doubleValue();
            // Only strengthen positive buffs. Penalties retain the exact original modifier.
            enhanced.add(amount>0?modifierConstructor.newInstance(modifierId.invoke(modifier),modifierTarget.invoke(modifier),amount*(1+strength),modifierOperation.invoke(modifier)):modifier);
        }
        return mealConstructor.newInstance(score.invoke(current),((Number)duration.invoke(current)).doubleValue()*(1+time),complexity.invoke(current),traits.invoke(current),List.copyOf(enhanced),quality.invoke(current));
    }
    private static void disable(Throwable error) {
        if(!failed)Mastery.LOGGER.warn("Needs Not Necessities integration is unavailable for this installed API; other Mastery features remain active",error);
        failed=true;
    }
}
