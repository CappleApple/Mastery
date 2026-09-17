package com.cappleapple.mastery.items;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.effects.EffectService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** Pack-authored custom data identifies a persistent player unlock, not a spell inscription. */
public final class SkillBookItem extends Item {
    public static final String TOKEN_KEY = "mastery_unlock";
    public SkillBookItem(Properties properties) { super(properties); }

    public static String token(ItemStack stack) {
        String token = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(TOKEN_KEY);
        return ResourceLocation.tryParse(token) != null && token.contains(":") ? token : "";
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String token = token(stack);
        if (token.isEmpty() || player.isSpectator()) return InteractionResultHolder.fail(stack);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!MasteryRuntime.progress(serverPlayer).bookUnlocks().add(token)) {
                player.displayClientMessage(Component.translatable("message.mastery.book_known"), true);
                return InteractionResultHolder.fail(stack);
            }
            stack.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(this));
            EffectService.rebuild(serverPlayer);
            MasteryRuntime.sync(serverPlayer);
            player.displayClientMessage(Component.translatable("message.mastery.book_learned"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
