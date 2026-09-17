package com.cappleapple.mastery.mechanics;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/** An immediate native spell activation with private cast data; a player's channel is never replaced. */
public final class ProcSpellCaster {
    private static final ThreadLocal<Boolean> CASTING = ThreadLocal.withInitial(() -> false);
    private ProcSpellCaster() {}
    public static boolean casting() { return CASTING.get(); }

    public static boolean cast(ServerPlayer owner, LivingEntity target, String id, int requestedLevel) {
        var spell = SpellRegistry.getSpell(id);
        if (spell == SpellRegistry.none() || !spell.isEnabled()) return false;
        int level = Math.clamp(spell.getLevelFor(Math.clamp(requestedLevel, 1, 100), owner), 1, 100);
        float yaw = owner.getYRot(), pitch = owner.getXRot(), head = owner.getYHeadRot();
        var data = new MagicData(true);
        data.getSyncedData();
        data.initiateCast(spell, level, 0, CastSource.COMMAND, "");
        boolean previous = CASTING.get();
        CASTING.set(true);
        try {
            if (target != null && target != owner) {
                Vec3 delta = target.getEyePosition().subtract(owner.getEyePosition());
                double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                owner.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
                owner.setYHeadRot(owner.getYRot());
                owner.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, horizontal)));
            }
            if (!spell.checkPreCastConditions(owner.level(), level, owner, data)) return false;
            if (NeoForge.EVENT_BUS.post(new SpellPreCastEvent(owner, id, level, spell.getSchoolType(), CastSource.COMMAND)).isCanceled()) return false;
            spell.onServerPreCast(owner.level(), level, owner, data);
            var cast = new SpellOnCastEvent(owner, id, level, 0, spell.getSchoolType(), CastSource.COMMAND);
            NeoForge.EVENT_BUS.post(cast);
            spell.onCast(owner.level(), Math.clamp(cast.getSpellLevel(), 1, 100), owner, CastSource.COMMAND, data);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(owner,
                    new io.redspace.ironsspellbooks.network.casting.OnClientCastPacket(id, cast.getSpellLevel(), CastSource.COMMAND, data.getAdditionalCastData()));
            return true;
        } finally {
            owner.setYRot(yaw);
            owner.setXRot(pitch);
            owner.setYHeadRot(head);
            data.resetAdditionalCastData();
            if (previous) CASTING.set(true); else CASTING.remove();
        }
    }
}
