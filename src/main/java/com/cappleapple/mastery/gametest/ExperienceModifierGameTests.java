package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.progression.*;
import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class ExperienceModifierGameTests {
    private ExperienceModifierGameTests() {}
    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void earnedApiXpScalesOnceWhileAdministrativeXpAndPointsStayExact(GameTestHelper helper) {
        var original = MasteryRuntime.definitions(); var player = MasteryTestPlayers.create(helper);
        try {
            var snapshot = fixture(original);
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot));
            var state = MasteryRuntime.progress(player);
            helper.assertTrue(ProgressionService.setRank(MasteryRuntime.definitions(), state, "mastery:xp_test", 2, -1).success(), "Could not grant XP bonus node");
            attribute(player, "mastery:experience_gain", 2);
            attribute(player, "mastery:fire_experience_gain", 1.5);
            helper.assertTrue(MasteryRuntime.grantXp(player, "mastery:fire", 10).success(), "Earned grant failed");
            close(helper, state.tree("mastery:fire").lifetimeXp(), 51, "Earned XP did not compose attributes and rank-scaled effects once");
            MasteryRuntime.grantXpExact(player, "mastery:fire", 10);
            close(helper, state.tree("mastery:fire").lifetimeXp(), 61, "Administrative XP was modified");
            MasteryRuntime.grantPoints(player, "mastery:fire", 7);
            helper.assertTrue(state.tree("mastery:fire").points() == 7, "Direct skill points were multiplied");
            MasteryRuntime.grantXp(player, "mastery:ice", 10);
            close(helper, state.tree("mastery:ice").lifetimeXp(), 24, "Fire tree bonus leaked into Ice");
            state.node("mastery:xp_test").toggled(false);
            close(helper, ExperienceModifiers.apply(player, "mastery:fire", 10), 30, "Disabled skill still increased earned XP");
            helper.succeed();
        } finally { MasteryRuntime.install(original); MasteryRuntime.logout(player); player.discard(); }
    }
    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void arbitraryTreesUseRegisteredAttributeAndOnceSourcesDoNotMultiplyPoints(GameTestHelper helper) {
        var original = MasteryRuntime.definitions(); var player = MasteryTestPlayers.create(helper);
        try {
            var snapshot = fixture(original);
            snapshot.getAsJsonObject("trees").add("test:custom", json("{\"xp_base\":1000000,\"xp_attribute\":\"mastery:ice_experience_gain\"}"));
            snapshot.getAsJsonObject("nodes").add("test:custom_node", json("{\"tree\":\"test:custom\"}"));
            snapshot.getAsJsonObject("xp_sources").add("test:zero_once_xp", json("{\"tree\":\"test:custom\",\"event\":\"test:zero_earned\",\"amount\":10,\"points\":3,\"once\":true}"));
            snapshot.getAsJsonObject("xp_sources").add("test:once_xp", json("{\"tree\":\"test:custom\",\"event\":\"test:earned\",\"amount\":10,\"points\":2,\"once\":true}"));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot)); var state = MasteryRuntime.progress(player);
            ProgressionService.setRank(MasteryRuntime.definitions(), state, "mastery:xp_test", 2, -1);
            attribute(player, "mastery:experience_gain", 2);
            attribute(player, "mastery:ice_experience_gain", 3);
            MasteryRuntime.usage(player, "test:earned", new JsonObject());
            close(helper, state.tree("test:custom").lifetimeXp(), 72, "Custom tree did not use its xp_attribute and global skill effect");
            helper.assertTrue(state.tree("test:custom").points() == 2, "Once-source direct points were multiplied");
            MasteryRuntime.usage(player, "test:earned", new JsonObject());
            close(helper, state.tree("test:custom").lifetimeXp(), 72, "Once source ran twice");
            helper.assertTrue(state.tree("test:custom").points() == 2 && state.usageGrants().contains("test:once_xp"), "Once reward was not recorded");
            attribute(player, "mastery:experience_gain", 0);
            MasteryRuntime.usage(player, "test:zero_earned", new JsonObject());
            MasteryRuntime.usage(player, "test:zero_earned", new JsonObject());
            close(helper, state.tree("test:custom").lifetimeXp(), 72, "Zero XP multiplier allowed earned XP");
            helper.assertTrue(state.tree("test:custom").points() == 5 && state.usageGrants().contains("test:zero_once_xp"), "Zero XP altered the one-time direct point reward");
            helper.succeed();
        } finally { MasteryRuntime.install(original); MasteryRuntime.logout(player); player.discard(); }
    }
    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty")
    public static void ordinaryAttributeEffectsChangeExperienceWithoutDoubleCounting(GameTestHelper helper) {
        var original = MasteryRuntime.definitions(); var player = MasteryTestPlayers.create(helper);
        try {
            var snapshot = fixture(original);
            snapshot.getAsJsonObject("nodes").add("mastery:xp_attribute_test", json("{\"tree\":\"mastery:fire\",\"max_rank\":2,\"cost\":0,\"effects\":[{\"type\":\"mastery:attribute\",\"attribute\":\"mastery:experience_gain\",\"amount\":0.25}]}"));
            MasteryRuntime.install(DefinitionSet.fromJson(snapshot)); var state = MasteryRuntime.progress(player);
            ProgressionService.setRank(MasteryRuntime.definitions(), state, "mastery:xp_attribute_test", 2, -1);
            EffectService.rebuild(player);
            close(helper, ExperienceModifiers.apply(player, "mastery:fire", 10), 15, "Attribute effect was missing or applied twice");
            state.node("mastery:xp_attribute_test").toggled(false); EffectService.rebuild(player);
            close(helper, ExperienceModifiers.apply(player, "mastery:fire", 10), 10, "Disabled attribute effect remained active");
            helper.succeed();
        } finally { MasteryRuntime.install(original); MasteryRuntime.logout(player); player.discard(); }
    }
    private static JsonObject fixture(DefinitionSet original) {
        var snapshot = original.toJson();
        snapshot.getAsJsonObject("trees").getAsJsonObject("mastery:fire").addProperty("xp_base", 1000000);
        snapshot.getAsJsonObject("effects").add("mastery:xp_test_bonus", json("{\"type\":\"mastery:experience_gain\",\"tree\":\"mastery:fire\",\"amount\":0.25}"));
        snapshot.getAsJsonObject("nodes").add("mastery:xp_test", json("{\"tree\":\"mastery:fire\",\"cost\":0,\"max_rank\":3,\"effects\":[{\"type\":\"mastery:experience_gain\",\"amount\":0.1},{\"ref\":\"mastery:xp_test_bonus\"}]}"));
        return snapshot;
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void attribute(ServerPlayer player, String id, double value) {
        var holder = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(id)).orElseThrow();
        var instance = player.getAttribute(holder);
        if (instance == null) throw new IllegalStateException("XP attribute not attached: " + id);
        instance.setBaseValue(value);
    }
    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 0.00001, message + ": expected " + expected + ", got " + actual);
    }
}
