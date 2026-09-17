package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.integration.SchoolDamage;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class SchoolDamageGameTests {
    private SchoolDamageGameTests() {}
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void nativeSchoolDamageAwardsOnlyMatchingSchool(GameTestHelper helper) {
        var level=helper.getLevel();
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"MasteryDamage")) {
            @Override public boolean isCreative(){return false;}
            @Override public boolean isSpectator(){return false;}
        };
        var school=SchoolRegistry.FIRE.get();
        var holder=level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(school.getDamageType());
        var source=new DamageSource(holder,player);
        var context=new JsonObject();
        SchoolDamage.addContext(source,context);
        helper.assertTrue(context.get("school").getAsString().equals("irons_spellbooks:fire"),"Native damage type did not resolve its registered school");
        var ordinary=new JsonObject();
        SchoolDamage.addContext(level.damageSources().onFire(),ordinary);
        helper.assertTrue(!ordinary.has("school"),"Vanilla fire was incorrectly treated as an Iron's school");
        var target=helper.spawn(EntityType.ZOMBIE,new BlockPos(1,2,1));
        helper.assertTrue(target.hurt(source,10),"Fixture did not take native school damage");
        var state=MasteryRuntime.progress(player);
        helper.assertTrue(state.tree("mastery:fire").lifetimeXp()>0,"Dealt school damage did not award XP");
        helper.assertTrue(state.trees().entrySet().stream().filter(e->!e.getKey().equals("mastery:fire")).allMatch(e->e.getValue().lifetimeXp()==0),"School damage leaked XP into another tree");
        double before=state.tree("mastery:fire").lifetimeXp();
        target.invulnerableTime=0;target.setInvulnerable(true);
        target.hurt(source,10);
        helper.assertTrue(state.tree("mastery:fire").lifetimeXp()==before,"Rejected damage awarded XP");
        target.discard();player.discard();helper.succeed();
    }
}
