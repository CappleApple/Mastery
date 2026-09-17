package com.cappleapple.mastery.crafting;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.effects.EffectRegistry;
import com.cappleapple.mastery.effects.EffectService;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import java.util.*;
import java.util.function.Consumer;
import static com.cappleapple.mastery.crafting.CraftingRules.*;

/** Server-authoritative output modification. Persistent attempt markers prevent extraction rerolls. */
public final class CraftingService {
    public static final String MARKER="mastery_crafted";
    public static final String BREWED="mastery_brewed";
    public static final String BASE_POTION="mastery_base_potion";
    private static final String SEQUENCE="mastery_craft_sequence",SALT="mastery_craft_salt";
    private record Attempt(UUID owner,long sequence) {}
    private static final Map<ItemStack,Attempt> PENDING=new WeakHashMap<>();
    public record Extraction(ServerPlayer player,Slot slot,ItemStack source,int count,long sequence) {}
    public static final String MEAL_STRENGTH="mastery_meal_strength";
    public static final String MEAL_DURATION="mastery_meal_duration";
    public record ActiveEffect(String id, int rank, JsonObject json) {}
    private CraftingService() {}
    public static void initialize() {
        for(String type:TYPES) EffectRegistry.register(type,(p,n,r,e)->{});
    }
    public static void validateRuntime(com.cappleapple.mastery.data.DefinitionSet definitions,List<String> errors) {
        definitions.effects().forEach((id,effect)->validateRuntimeEffect(effect,"effects/"+id,errors));
        definitions.nodes().forEach((id,node)->node.effects().forEach(effect->validateRuntimeEffect(effect,"nodes/"+id,errors)));
    }
    private static void validateRuntimeEffect(JsonObject effect,String path,List<String> errors) {
        try {
            if(!TYPES.contains(text(effect,"type","")))return;
            for(String key:List.of("item","block","attribute","added_effect"))if(effect.has(key)) {
                ResourceLocation id=ResourceLocation.parse(text(effect,key,""));
                boolean exists=switch(key){case "item"->BuiltInRegistries.ITEM.containsKey(id);case "block"->BuiltInRegistries.BLOCK.containsKey(id);case "attribute"->BuiltInRegistries.ATTRIBUTE.containsKey(id);default->BuiltInRegistries.MOB_EFFECT.containsKey(id);};
                if(!exists)errors.add(path+": unknown "+key+" "+id);
            }
        }catch(RuntimeException ex){errors.add(path+": malformed crafting effect: "+ex.getMessage());}
    }
    public static List<ActiveEffect> activeEffects(ServerPlayer player) {
        List<ActiveEffect> result=new ArrayList<>();
        MasteryRuntime.definitions().nodes().values().stream().sorted(Comparator.comparing(n->n.id())).forEach(node->{
            int rank=EffectService.effectiveRank(player,node);
            if(rank<=0)return;
            for(int i=0;i<node.effects().size();i++) {
                JsonObject effect=resolve(node.effects().get(i),0);
                if(effect!=null&&TYPES.contains(text(effect,"type",""))) result.add(new ActiveEffect(node.id()+"/"+i,rank,effect));
            }
        });
        return result;
    }
    private static JsonObject resolve(JsonObject effect,int depth) {
        if(effect==null||depth>16)return null;
        return effect.has("ref")?resolve(MasteryRuntime.definitions().effects().get(effect.get("ref").getAsString()),depth+1):effect;
    }
    public static boolean matches(ItemStack stack,JsonObject effect) {
        if(effect.has("item")&&!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(text(effect,"item","")))return false;
        return !effect.has("item_tag")||stack.is(TagKey.create(Registries.ITEM,ResourceLocation.parse(text(effect,"item_tag",""))));
    }
    public static boolean roll(ServerPlayer player,JsonObject effect) {
        var holder=BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath("mastery","proc_chance"));
        double bonus=holder.filter(h->player.getAttribute(h)!=null).map(player::getAttributeValue).orElse(0.0);
        return player.getRandom().nextDouble()<chance(number(effect,"chance",1),bonus);
    }
    /** Prepare before the result is copied; commit after transfer to prevent free preview rerolls. */
    public static Extraction prepareOutput(AbstractContainerMenu menu,int slotId,Player player) {
        if(!(player instanceof ServerPlayer server)||slotId<0||slotId>=menu.slots.size())return null;
        Slot slot=menu.getSlot(slotId);
        if(!slot.hasItem()||!slot.mayPickup(player))return null;
        boolean crafting=slot instanceof ResultSlot||slot instanceof FurnaceResultSlot
                ||menu instanceof StonecutterMenu&&slotId==1||menu instanceof SmithingMenu&&slotId==3;
        boolean brewed=menu instanceof BrewingStandMenu&&slotId<3&&data(slot.getItem()).getBoolean(BREWED);
        if(!crafting&&!brewed)return null;
        var stack=slot.getItem();applyPrepared(server,stack,activeEffects(server));var attempt=PENDING.get(stack);
        long sequence=server.getPersistentData().getLong(SEQUENCE);
        return attempt!=null&&attempt.sequence()==sequence&&attempt.owner().equals(server.getUUID())
                ?new Extraction(server,slot,stack,stack.getCount(),sequence):null;
    }
    public static void completeOutput(Extraction extraction) {
        if(extraction!=null&&(extraction.source().getCount()<extraction.count()||extraction.slot().getItem()!=extraction.source())) {
            advance(extraction.player(),extraction.sequence());PENDING.remove(extraction.source());
        }
    }
    private static void advance(ServerPlayer player,long expected) {
        var persistent=player.getPersistentData();
        if(persistent.getLong(SEQUENCE)==expected)persistent.putLong(SEQUENCE,expected+1);
    }
    /** For an integration that has already committed the production operation. */
    public static void apply(ServerPlayer player,ItemStack stack) {apply(player,stack,activeEffects(player));}
    public static void apply(ServerPlayer player,ItemStack stack,List<ActiveEffect> effects) {
        long sequence=player.getPersistentData().getLong(SEQUENCE);
        if(applyPrepared(player,stack,effects)){advance(player,sequence);PENDING.remove(stack);}
    }
    private static boolean applyPrepared(ServerPlayer player,ItemStack stack,List<ActiveEffect> effects) {
        if(stack.isEmpty()||data(stack).getBoolean(MARKER))return false;
        List<ActiveEffect> applicable=effects.stream().filter(e->!text(e.json(),"type","").equals("mastery:placed_comfort")&&matches(stack,e.json())&&supported(stack,e.json())).toList();
        if(applicable.isEmpty())return false;
        var persistent=player.getPersistentData();
        if(!persistent.contains(SALT))persistent.putLong(SALT,player.getRandom().nextLong());
        long sequence=persistent.getLong(SEQUENCE),seed=persistent.getLong(SALT)^(sequence*0x9E3779B97F4A7C15L)^BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
        update(stack,tag->tag.putBoolean(MARKER,true));PENDING.put(stack,new Attempt(player.getUUID(),sequence));
        var holder=BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath("mastery","proc_chance"));
        double bonus=holder.filter(h->player.getAttribute(h)!=null).map(player::getAttributeValue).orElse(0.0);
        for(ActiveEffect effect:applicable) {
            var random=new java.util.SplittableRandom(seed^((long)effect.id().hashCode()*0xD1B54A32D192ED03L));
            if(random.nextDouble()<chance(number(effect.json(),"chance",1),bonus))applyEffect(stack,effect);
        }
        return true;
    }
    private static boolean supported(ItemStack stack,JsonObject effect) {
        return switch(text(effect,"type","")) {
            case "mastery:crafting_food" -> stack.has(DataComponents.FOOD);
            case "mastery:crafting_potion" -> stack.has(DataComponents.POTION_CONTENTS);
            case "mastery:crafting_attribute" -> true;
            default -> false;
        };
    }
    private static void applyEffect(ItemStack stack,ActiveEffect active) {
        JsonObject effect=active.json();int rank=active.rank();
        switch(text(effect,"type","")) {
            case "mastery:crafting_attribute" -> {
                var attribute=BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(text(effect,"attribute","minecraft:generic.attack_damage")));
                if(attribute.isEmpty())return;
                var operation=switch(text(effect,"operation","add_value")) {
                    case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                    case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                    default -> AttributeModifier.Operation.ADD_VALUE;
                };
                var slot=EquipmentSlotGroup.valueOf(text(effect,"slot","mainhand").toUpperCase(Locale.ROOT));
                var id=ResourceLocation.fromNamespaceAndPath("mastery","crafted/"+active.id().replace(':','/'));
                var modifiers=stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS,modifiers.withModifierAdded(attribute.get(),new AttributeModifier(id,number(effect,"amount",0)*rank,operation),slot));
            }
            case "mastery:crafting_food" -> {
                FoodProperties food=stack.get(DataComponents.FOOD);
                if(food==null)return;
                double strength=number(effect,"buff_strength_bonus",0)*rank;
                double duration=number(effect,"buff_duration_bonus",0)*rank;
                var effects=food.effects().stream().map(e->new FoodProperties.PossibleEffect(()->scaled(e.effect(),strength,duration,0),e.probability())).toList();
                stack.set(DataComponents.FOOD,new FoodProperties((int)Math.clamp(Math.round(food.nutrition()*(1+number(effect,"nutrition_bonus",0)*rank)),0,1000000),
                        (float)Math.clamp(food.saturation()*(1+number(effect,"saturation_bonus",0)*rank),0,1000000),food.canAlwaysEat(),food.eatSeconds(),food.usingConvertsTo(),effects));
                update(stack,tag->{
                    tag.putDouble(MEAL_STRENGTH,Math.clamp(tag.getDouble(MEAL_STRENGTH)+number(effect,"meal_strength_bonus",0)*rank,0,100));
                    tag.putDouble(MEAL_DURATION,Math.clamp(tag.getDouble(MEAL_DURATION)+number(effect,"meal_duration_bonus",0)*rank,0,100));
                });
            }
            case "mastery:crafting_potion" -> {
                PotionContents potion=stack.get(DataComponents.POTION_CONTENTS);
                if(potion==null)return;
                List<MobEffectInstance> effects=new ArrayList<>();
                potion.getAllEffects().forEach(e->effects.add(scaled(e,0,number(effect,"duration_bonus",0)*rank,(int)number(effect,"amplifier_bonus",0)*rank)));
                if(effect.has("added_effect"))BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(text(effect,"added_effect",""))).ifPresent(holder->effects.add(new MobEffectInstance(holder,(int)number(effect,"added_duration",200),(int)number(effect,"added_amplifier",0))));
                potion.potion().flatMap(net.minecraft.core.Holder::unwrapKey).ifPresent(key->update(stack,tag->tag.putString(BASE_POTION,key.location().toString())));
                if(!stack.has(DataComponents.ITEM_NAME)&&!stack.has(DataComponents.CUSTOM_NAME))stack.set(DataComponents.ITEM_NAME,stack.getHoverName());
                // Flatten base effects once, preventing vanilla base effects from being applied a second time.
                stack.set(DataComponents.POTION_CONTENTS,new PotionContents(Optional.empty(),potion.customColor(),List.copyOf(effects)));
            }
        }
    }
    private static MobEffectInstance scaled(MobEffectInstance effect,double strength,double duration,int amplifier) {
        return new MobEffectInstance(effect.getEffect(),duration(effect.getDuration(),duration),Math.clamp(strength(effect.getAmplifier(),strength)+amplifier,0,254),effect.isAmbient(),effect.isVisible(),effect.showIcon());
    }
    public static CompoundTag data(ItemStack stack) {return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    public static void update(ItemStack stack,Consumer<CompoundTag> action) {CustomData.update(DataComponents.CUSTOM_DATA,stack,action);}
    /** Recover only the base recipe input when a finished custom potion is brewed again. */
    public static ItemStack brewingBase(ItemStack stack) {
        if(stack.getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY).potion().isPresent())return stack;
        ResourceLocation id=ResourceLocation.tryParse(data(stack).getString(BASE_POTION));
        if(id==null)return stack;
        var holder=BuiltInRegistries.POTION.getHolder(id);
        if(holder.isEmpty())return stack;
        ItemStack base=stack.copy();base.set(DataComponents.POTION_CONTENTS,new PotionContents(holder.get()));return base;
    }
    public static double mealBonus(ItemStack stack,String key) {double value=data(stack).getDouble(key);return Double.isFinite(value)?Math.clamp(value,0,100):0;}
}
