package com.cappleapple.mastery.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.util.UUID;

/** Avoids a network login handshake while testing real server player attachments and items. */
final class MasteryTestPlayers {
    private MasteryTestPlayers() {}
    /** A real ServerPlayer damage/death implementation with an inert test network connection. */
    static net.minecraft.server.level.ServerPlayer createCombat(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "MasteryCombat");
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other){return true;}
        };
        player.connection = new FakePlayer(helper.getLevel(), profile).connection;
        player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        try {
            var grace = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(player, 0);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Unable to disable test spawn grace", error); }
        player.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_CHANCE).setBaseValue(0);
        return player;
    }
    static FakePlayer create(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"MasteryTest")) {
            @Override protected int getPermissionLevel(){return server.getPlayerList().isOp(getGameProfile())?4:0;}
            @Override public boolean isCreative(){return false;}
            @Override public boolean isSpectator(){return false;}
        };
        player.getAttribute(dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes.CRIT_CHANCE).setBaseValue(0);
        return player;
    }
}
