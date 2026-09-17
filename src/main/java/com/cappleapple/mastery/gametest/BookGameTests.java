package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.items.MasteryItems;
import com.cappleapple.mastery.items.SkillBookItem;
import com.cappleapple.mastery.progression.ProgressionService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class BookGameTests {
    private BookGameTests() {}
    private static ItemStack book(String token) {
        ItemStack stack = new ItemStack(MasteryItems.SKILL_BOOK.get(), 2);
        CompoundTag tag = new CompoundTag();
        tag.putString(SkillBookItem.TOKEN_KEY, token);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void skillBookConsumesOnceAndKnowledgeSurvivesDeath(GameTestHelper helper) {
        var player = MasteryTestPlayers.create(helper);
        var clone = MasteryTestPlayers.create(helper);
        try {
            player.setGameMode(GameType.SURVIVAL);
            ItemStack stack = book("mastery:fire_secrets");
            player.setItemSlot(EquipmentSlot.MAINHAND, stack);
            var state = MasteryRuntime.progress(player);
            var definitions = MasteryRuntime.definitions();
            helper.assertTrue(!ProgressionService.bookUnlocked(definitions, state, "mastery:fire/secret_capacity"),
                    "Hidden descendant was already book-unlocked");
            stack.use(player.level(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(stack.getCount() == 1, "Learning a skill book did not consume exactly one item");
            helper.assertTrue(state.bookUnlocks().contains("mastery:fire_secrets"), "Book token was not stored on the player");
            helper.assertTrue(ProgressionService.bookUnlocked(definitions, state, "mastery:fire/secret_capacity"),
                    "Book did not reveal descendant gate");
            helper.assertTrue(state.rank("mastery:fire/secret_studies") == 0 && state.tree("mastery:fire").points() == 0,
                    "A skill book granted ranks or points");
            stack.use(player.level(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(stack.getCount() == 1, "A duplicate learned book was consumed");
            clone.restoreFrom(player, false);
            helper.assertTrue(MasteryRuntime.progress(clone).bookUnlocks().contains("mastery:fire_secrets"),
                    "Death copy lost learned book knowledge");
            MasteryRuntime.progress(clone).bookUnlocks().clear();
            helper.assertTrue(state.bookUnlocks().contains("mastery:fire_secrets"), "Death copy aliased original book set");
            helper.succeed();
        } finally {
            MasteryRuntime.logout(player); MasteryRuntime.logout(clone);
            player.discard(); clone.discard();
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void malformedSkillBooksAreNotConsumed(GameTestHelper helper) {
        var player = MasteryTestPlayers.create(helper);
        try {
            player.setGameMode(GameType.SURVIVAL);
            ItemStack stack = book("not a resource ID");
            player.setItemSlot(EquipmentSlot.MAINHAND, stack);
            stack.use(player.level(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(stack.getCount() == 2, "Malformed skill book consumed an item");
            helper.assertTrue(MasteryRuntime.progress(player).bookUnlocks().isEmpty(), "Malformed token entered player data");
            helper.succeed();
        } finally { MasteryRuntime.logout(player); player.discard(); }
    }
}
