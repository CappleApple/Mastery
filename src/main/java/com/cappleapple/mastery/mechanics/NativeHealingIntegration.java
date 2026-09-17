package com.cappleapple.mastery.mechanics;

import com.cappleapple.mastery.elemental.ElementalDamage;
import io.redspace.ironsspellbooks.api.events.SpellHealEvent;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Bridges Iron's read-only announcement to the immediately following mutable native heal event. */
public final class NativeHealingIntegration {
    private record Announced(LivingEntity target, LivingEntity caster, String school, float amount, long tick) {}
    private static final Map<UUID, Announced> PENDING = new HashMap<>();
    private NativeHealingIntegration() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void announced(SpellHealEvent event) {
        var target = event.getTargetEntity();
        if (target.level().isClientSide || !Float.isFinite(event.getHealAmount()) || event.getHealAmount() <= 0 || PENDING.size() >= 2048) return;
        PENDING.put(target.getUUID(), new Announced(target, event.getEntity(), event.getSchoolType().getId().toString(), event.getHealAmount(), target.level().getGameTime()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void heal(LivingHealEvent event) {
        var pending = PENDING.remove(event.getEntity().getUUID());
        if (pending == null || pending.target != event.getEntity() || pending.tick != event.getEntity().level().getGameTime()
                || Float.floatToIntBits(pending.amount) != Float.floatToIntBits(event.getAmount())) return;
        double multiplier = ElementalDamage.healingMultiplier(pending.target, pending.caster, pending.school);
        event.setAmount((float)Math.clamp(event.getAmount() * multiplier, 0, 1000000));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { clear(); }
    public static void clear() { PENDING.clear(); }
}
