package com.cappleapple.mastery.elemental;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.mixin.LivingDamageStateAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.*;

/** Splits accepted weapon attacks into native damage types, retaining each type's mitigation rules. */
public final class ElementalDamage {
    private static final String SNAPSHOT="mastery_elements";
    private static final ThreadLocal<Deque<HitBatch>> HIT_BATCHES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final class HitBatch {
        final LivingEntity target;
        float applied;
        double criticalMultiplier=1;
        HitBatch(LivingEntity target) { this.target = target; }
    }
    public static boolean partitioning(LivingEntity target) {
        var batches = HIT_BATCHES.get(); return !batches.isEmpty() && batches.peek().target == target;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void applied(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (partitioning(event.getEntity())) HIT_BATCHES.get().peek().applied += event.getNewDamage();
    }
    public static void recordCritical(LivingIncomingDamageEvent event,float before) {
        if(before>0 && partitioning(event.getEntity()) && !isWeaponBonus(event.getSource())) {
            double multiplier=event.getAmount()/before;
            if(Double.isFinite(multiplier))HIT_BATCHES.get().peek().criticalMultiplier=Math.clamp(multiplier,0,1000000);
        }
    }
    private ElementalDamage() {}
    public static double attribute(LivingEntity owner,String id) {
        if(owner==null||id.isEmpty())return 0;
        var holder=BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(id));
        return holder.isPresent()&&owner.getAttribute(holder.get())!=null?owner.getAttributeValue(holder.get()):0;
    }
    private static double masteryPower(LivingEntity owner,DamageTypes.Type type) {
        return ElementalRules.multiplier(type.elemental()?attribute(owner,"mastery:elemental_damage"):0,attribute(owner,type.attribute("power")));
    }
    private static double weaponPower(LivingEntity owner,DamageTypes.Type type) {
        var school=type.school();
        double nativePower=school==null?1:school.getPowerFor(owner)*(owner.getAttribute(AttributeRegistry.SPELL_POWER)!=null?owner.getAttributeValue(AttributeRegistry.SPELL_POWER):1);
        return Math.max(0,nativePower)*masteryPower(owner,type);
    }
    private static CompoundTag snapshot(LivingEntity owner,ItemStack weapon) {
        if(owner instanceof ServerPlayer player)EffectService.refresh(player);
        var result=new CompoundTag();double conversions=0;
        for(var type:DamageTypes.all()) {
            double bonus=attribute(owner,type.attribute("weapon"));
            String enchantment=type.field("enchantment","");
            if(!enchantment.isEmpty()) {
                var holder=owner.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(ResourceLocation.parse(enchantment));
                if(holder.isPresent())bonus+=weapon.getEnchantmentLevel(holder.get())*(type.data().has("damage_per_level")?type.data().get("damage_per_level").getAsDouble():0.1);
            }
            double conversion=Math.max(0,attribute(owner,type.attribute("conversion")));conversions+=conversion;
            var part=new CompoundTag();part.putDouble("bonus",Math.max(0,bonus));part.putDouble("conversion",conversion);
            part.putDouble("power",weaponPower(owner,type));part.putDouble("attunement",attribute(owner,type.attribute("attunement")));part.putDouble("potency",attribute(owner,type.attribute("potency")));part.putDouble("mitigation",attribute(owner,type.attribute("mitigation")));result.put(type.id(),part);
        }
        String defaultType=DamageTypes.weapon(weapon);
        if(!defaultType.isEmpty()&&result.contains(defaultType)) {
            var part=result.getCompound(defaultType);part.putDouble("conversion",part.getDouble("conversion")+ElementalRules.physical(conversions));
        }
        return result;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void launched(EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide&&event.getEntity() instanceof AbstractArrow arrow&&arrow.getOwner() instanceof LivingEntity owner&&!arrow.getPersistentData().contains(SNAPSHOT)) {
            ItemStack weapon=arrow.getWeaponItem();
            arrow.getPersistentData().put(SNAPSHOT,snapshot(owner,weapon==null?owner.getMainHandItem():weapon));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void scale(LivingIncomingDamageEvent event) {
        if(event.getEntity().level().isClientSide||event.getSource() instanceof BonusSource)return;
        var owner=event.getSource().getEntity() instanceof LivingEntity living?living:null;
        var type=DamageTypes.find(event.getSource());
        if(type==null) {
            // Addon schools without a custom definition still participate in global elemental power.
            for(var school:SchoolRegistry.REGISTRY)if(event.getSource().is(school.getDamageType())) {
                event.setAmount((float)(event.getAmount()*Math.max(0,1+attribute(owner,"mastery:elemental_damage"))));break;
            }
            return;
        }
        double multiplier=(event.getSource() instanceof ProcSource && type.school()!=null?io.redspace.ironsspellbooks.damage.DamageSources.getResist(event.getEntity(),type.school()):1)*masteryPower(owner,type)*DamageTypes.matchup(type,owner,event.getEntity(),false,attribute(owner,type.attribute("attunement")),attribute(owner,type.attribute("potency")),attribute(owner,type.attribute("mitigation")));
        event.setAmount((float)Math.min(Float.MAX_VALUE,event.getAmount()*multiplier));
    }
    public static boolean aroundHurt(LivingEntity target,DamageSource source,float amount,Operation<Boolean> original) {
        if(target.level().isClientSide||amount<=0||!Float.isFinite(amount)||source instanceof BonusSource||source instanceof ProcSource||DamageTypes.find(source)!=null
                ||!(source.getEntity() instanceof LivingEntity owner)||!weaponSource(source))return original.call(source,amount);
        if(target.isInvulnerableTo(source))return original.call(source,amount);
        CompoundTag parts=source.getDirectEntity() instanceof AbstractArrow arrow&&arrow.getPersistentData().contains(SNAPSHOT)?arrow.getPersistentData().getCompound(SNAPSHOT):snapshot(owner,owner.getMainHandItem());
        double total=0;for(String key:parts.getAllKeys())if(DamageTypes.find(key)!=null)total+=parts.getCompound(key).getDouble("conversion");
        List<Portion> damage=new ArrayList<>();
        for(String key:parts.getAllKeys().stream().sorted().toList()) {
            var part=parts.getCompound(key);var type=DamageTypes.find(key);if(type==null||!target.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).containsKey(type.damageType().location()))continue;
            double converted=ElementalRules.conversion(part.getDouble("conversion"),total);
            double fraction=part.getDouble("bonus")+converted;
            double power=(type.school()!=null?io.redspace.ironsspellbooks.damage.DamageSources.getResist(target,type.school()):1)*part.getDouble("power")*DamageTypes.matchup(type,owner,target,false,part.getDouble("attunement"),part.getDouble("potency"),part.getDouble("mitigation"));
            if(fraction>0)damage.add(new Portion(type,(float)Math.min(Float.MAX_VALUE,amount*fraction*power),converted));
        }
        if(damage.isEmpty())return original.call(source,amount);
        damage.sort(Comparator.comparingDouble(Portion::converted).reversed());
        float physical=(float)(amount*ElementalRules.physical(total));
        DamageSource primarySource=source;float primaryAmount=physical;
        if(physical<=0) {
            // An immune first school must not discard other converted portions of the same accepted weapon attack.
            while(!damage.isEmpty()) {
                var first=damage.removeFirst();
                var candidate=new BonusSource(holder(target,first.type()),source.getDirectEntity(),owner,false,source);
                if(first.amount()<=0 || target.isInvulnerableTo(candidate)
                        || candidate.is(DamageTypeTags.IS_FIRE)&&target.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE))continue;
                primarySource=candidate;primaryAmount=first.amount();break;
            }
            if(primaryAmount<=0)return false;
        }
        var state=(LivingDamageStateAccessor)target;
        float previousHurt=state.mastery$getLastHurt();int previousInvulnerability=target.invulnerableTime;
        boolean cooldown=previousInvulnerability>10&&!source.is(DamageTypeTags.BYPASSES_COOLDOWN);
        if(cooldown&&amount<=previousHurt)return false;
        double acceptedFraction=cooldown?(amount-previousHurt)/amount:1;
        if(cooldown)state.mastery$setLastHurt(previousHurt*primaryAmount/amount);
        var batch=new HitBatch(target);HIT_BATCHES.get().push(batch);
        boolean accepted=false;int resultingInvulnerability=previousInvulnerability;
        try {
            accepted=original.call(primarySource,primaryAmount);
            resultingInvulnerability=target.invulnerableTime;
            if(!accepted||!target.isAlive())return accepted;
            for(var part:damage) {
                if(!target.isAlive())break;
                target.hurt(new BonusSource(holder(target,part.type()),source.getDirectEntity(),owner,true,source),(float)Math.min(Float.MAX_VALUE,part.amount()*acceptedFraction*batch.criticalMultiplier));
            }
            return true;
        } finally {
            // Remember the complete raw attack, so changing conversion or weapons cannot defeat vanilla hurt immunity.
            state.mastery$setLastHurt(accepted?amount:previousHurt);
            target.invulnerableTime=accepted?resultingInvulnerability:previousInvulnerability;
            HIT_BATCHES.get().pop();if(HIT_BATCHES.get().isEmpty())HIT_BATCHES.remove();
            com.cappleapple.mastery.mechanics.MechanicsRuntime.weaponHitFinished(target,source,batch.applied);
        }
    }
    private static boolean weaponSource(DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)||source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK)
                ||source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK_NO_AGGRO)||source.is(net.minecraft.world.damagesource.DamageTypes.ARROW)||source.is(net.minecraft.world.damagesource.DamageTypes.TRIDENT);
    }
    private record Portion(DamageTypes.Type type,float amount,double converted) {}
    private static Holder<DamageType> holder(LivingEntity target,DamageTypes.Type type) {return target.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type.damageType());}
    public static boolean isWeaponBonus(DamageSource source) {return source instanceof BonusSource bonus&&bonus.supplemental;}
    /** Proc/DOT damage uses a scoped cooldown bypass so the initiating attack cannot swallow it. */
    public static boolean hurt(LivingEntity target,LivingEntity owner,String typeId,float amount) {
        var type=DamageTypes.find(typeId);if(type==null||amount<=0||!Float.isFinite(amount)||!target.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).containsKey(type.damageType().location()))return false;
        var state=(LivingDamageStateAccessor)target;float previousHurt=state.mastery$getLastHurt();int previousInvulnerability=target.invulnerableTime;
        try {return target.hurt(new ProcSource(holder(target,type),owner),amount);}
        finally {state.mastery$setLastHurt(previousHurt);target.invulnerableTime=previousInvulnerability;}
    }
    public static double healingMultiplier(LivingEntity target,LivingEntity owner,String typeId) {
        var type=DamageTypes.find(typeId);return type==null?1:masteryPower(owner,type)*DamageTypes.matchup(type,owner,target,true,attribute(owner,type.attribute("attunement")),attribute(owner,type.attribute("potency")),attribute(owner,type.attribute("mitigation")));
    }
    public static void heal(LivingEntity target,LivingEntity owner,String typeId,float amount) {
        if(amount>0&&Float.isFinite(amount))target.heal((float)Math.min(Float.MAX_VALUE,amount*healingMultiplier(target,owner,typeId)));
    }
    public static final class ProcSource extends DamageSource {
        ProcSource(Holder<DamageType> type,LivingEntity owner) {super(type,owner);}
        @Override public boolean is(TagKey<DamageType> tag) {return tag.equals(DamageTypeTags.BYPASSES_COOLDOWN)||super.is(tag);}
    }
    public static final class BonusSource extends DamageSource {
        private final boolean supplemental;private final DamageSource original;
        BonusSource(Holder<DamageType> type,Entity direct,LivingEntity owner,boolean supplemental,DamageSource original) {super(type,direct,owner);this.supplemental=supplemental;this.original=original;}
        @Override public boolean is(TagKey<DamageType> tag) {
            if(supplemental&&(tag.equals(DamageTypeTags.BYPASSES_COOLDOWN)||tag.equals(dev.shadowsoffire.apothic_attributes.api.ALObjects.Tags.CANNOT_CRITICALLY_STRIKE)))return true;
            if(tag.equals(DamageTypeTags.IS_PROJECTILE)||tag.equals(DamageTypeTags.BYPASSES_SHIELD))return original.is(tag);
            return super.is(tag);
        }
    }
}
