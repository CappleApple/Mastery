package com.cappleapple.mastery.integration;

import com.cappleapple.mastery.api.SpellEquipmentRegistry;
import net.bettercombat.logic.WeaponRegistry;
import java.util.Optional;

/** Better Combat's resolved weapon attributes are the sole two-handed classifier. */
final class BetterCombatIntegration {
    static void register() {
        SpellEquipmentRegistry.registerContext(500,player -> {
            var attributes=WeaponRegistry.getAttributes(player.getMainHandItem());
            return attributes!=null&&attributes.isTwoHanded()?Optional.of("mastery:two_handed"):Optional.empty();
        });
        SpellEquipmentRegistry.registerContext(50,player -> {
            var attributes=WeaponRegistry.getAttributes(player.getMainHandItem());
            if(attributes==null||attributes.isTwoHanded()) return Optional.empty();
            var offhand=WeaponRegistry.getAttributes(player.getOffhandItem());
            return Optional.of(offhand!=null?"mastery:dual_wield":"mastery:one_handed");
        });
    }
}
