package com.cappleapple.mastery.classes;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.config.MasteryConfig;
import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.progression.PlayerProgress;
import com.cappleapple.mastery.spells.SpellService;
import com.cappleapple.mastery.storage.MasteryAttachments;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Server-thread class selection transaction and its narrowly scoped spectator waiting state. */
public final class ClassService {
    private ClassService() {}
    public static boolean required(ServerPlayer player) {
        return MasteryConfig.CLASS_SELECTOR_ENABLED.get() && !MasteryRuntime.definitions().classes().isEmpty()
                && MasteryRuntime.progress(player).selectedClass().isBlank();
    }
    public static boolean pending(ServerPlayer player) { return MasteryRuntime.progress(player).classSelection() != null; }
    public static void login(ServerPlayer player) { applyAttributes(player); tick(player); deliverPendingItems(player); }
    public static void reload(ServerPlayer player) { applyAttributes(player); tick(player); }
    public static void logout(ServerPlayer player) { clearAttributes(player); }
    public static void tick(ServerPlayer player) {
        var state = MasteryRuntime.progress(player);
        if (!required(player)) {
            if (state.classSelection() != null) restore(player, state);
            if (player.tickCount % 20 == 0) deliverPendingItems(player);
            return;
        }
        if (!player.isAlive()) return;
        if (state.classSelection() == null) {
            state.classSelection(new ClassSelectionState(player.gameMode.getGameModeForPlayer().getName(),
                    player.serverLevel().dimension().location().toString(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
            player.closeContainer();
            SpellService.interrupt(player, "class selection");
            MasteryRuntime.sync(player);
        }
        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) player.setGameMode(GameType.SPECTATOR);
        anchor(player, state.classSelection());
    }
    private static void anchor(ServerPlayer player, ClassSelectionState anchor) {
        player.stopRiding(); player.setCamera(player); player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(anchor.dimension()));
        var destination = player.server.getLevel(dimension);
        // A removed custom dimension cannot trap a restored player. Continue at the server's current safe location.
        if (destination == null) {
            var state = MasteryRuntime.progress(player);
            anchor = new ClassSelectionState(anchor.gameMode(), player.serverLevel().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            state.classSelection(anchor); destination = player.serverLevel();
        }
        if (destination != player.serverLevel() || player.distanceToSqr(anchor.x(), anchor.y(), anchor.z()) > 0.0001) {
            if (destination == player.serverLevel()) player.moveTo(anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), anchor.pitch());
            player.teleportTo(destination, anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), anchor.pitch());
        }
    }
    private static void restore(ServerPlayer player, PlayerProgress state) {
        ClassSelectionState anchor = state.classSelection();
        anchor(player, anchor);
        GameType original = GameType.byName(anchor.gameMode(), GameType.SURVIVAL);
        if (player.gameMode.getGameModeForPlayer() != original) player.setGameMode(original);
        // Honor another mod's game-mode event veto and retry instead of discarding the only recovery record.
        if (player.gameMode.getGameModeForPlayer() == original) { state.classSelection(null); MasteryRuntime.sync(player); }
    }
    public static JsonObject snapshot(ServerPlayer player) {
        var result = new JsonObject(); var state = MasteryRuntime.progress(player);
        result.addProperty("required", required(player));
        result.addProperty("selected", state.selectedClass());
        result.addProperty("pending_items", state.pendingClassItems().size());
        result.addProperty("can_select", required(player) && pending(player) && player.isAlive() && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR);
        result.addProperty("prior_game_mode", state.classSelection() == null ? "" : state.classSelection().gameMode());
        return result;
    }
    /** Empty means success. Every failure occurs before points, ranks, items, or class identity are committed. */
    public static String select(ServerPlayer player, String id) {
        if (!required(player) || !pending(player)) return "Class selection is not pending";
        if (!player.isAlive() || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) return "Wait for class selection to become ready";
        var raw = MasteryRuntime.definitions().classes().get(id);
        if (raw == null) return "Unknown class: " + id;
        var original = MasteryRuntime.progress(player);
        if (original.classRewardsGranted()) return "Starting class rewards have already been granted";
        PlayerProgress next;
        InventoryPlan plan;
        try {
            var definition = ClassDefinition.parse(id, raw);
            next = ClassRewards.prepare(MasteryRuntime.definitions(), original, definition);
            plan = prepareInventory(player, definition.startingInventory(), true);
            next.pendingClassItems().addAll(plan.remaining());
        } catch (IllegalArgumentException | ArithmeticException ex) { return "Cannot select class: " + ex.getMessage(); }
        // Inventory and attachment are saved in the same player data snapshot. Overflow stays in that attachment.
        for (int slot = 0; slot < plan.items().size(); slot++) player.getInventory().items.set(slot, plan.items().get(slot));
        plan.armor().forEach(player::setItemSlot);
        player.setData(MasteryAttachments.PROGRESS, next);
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
        applyAttributes(player); EffectService.rebuild(player); SpellService.reconcile(player);
        restore(player, next); MasteryRuntime.sync(player);
        if (!next.pendingClassItems().isEmpty()) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Your remaining class starting items will be delivered automatically when you free inventory space."));
        return "";
    }
    private record InventoryPlan(List<ItemStack> items, List<JsonObject> remaining, java.util.Map<net.minecraft.world.entity.EquipmentSlot,ItemStack> armor) {}
    private static InventoryPlan prepareInventory(ServerPlayer player, List<JsonObject> rawItems, boolean strict) {
        var decoded=new ArrayList<ItemStack>();var remaining=new ArrayList<JsonObject>();
        var ops=RegistryOps.create(JsonOps.INSTANCE,player.registryAccess());
        for(var raw:rawItems) {
            var parsed=ItemStack.CODEC.parse(ops,raw);
            if(parsed.error().isPresent()||parsed.result().map(ItemStack::isEmpty).orElse(true)) {
                if(strict)throw new IllegalArgumentException("Invalid starting item: "+parsed.error().map(error->error.message()).orElse("empty stack"));
                remaining.add(raw.deepCopy());continue;
            }
            decoded.add(parsed.getOrThrow());
        }
        var plan=ClassEquipment.prepare(player,decoded,strict);
        for(var stack:plan.overflow())remaining.add(ItemStack.CODEC.encodeStart(ops,stack).getOrThrow().getAsJsonObject());
        return new InventoryPlan(plan.inventory(),remaining,strict?plan.armor():java.util.Map.of());
    }
    /** Full inventories do not block the selector: saved overflow is delivered as main-inventory space opens. */
    public static void deliverPendingItems(ServerPlayer player) {
        var state = MasteryRuntime.progress(player);
        if (state.pendingClassItems().isEmpty() || pending(player) || !player.isAlive()) return;
        var plan = prepareInventory(player, state.pendingClassItems(), false);
        if (plan.remaining().equals(state.pendingClassItems())) return;
        for (int slot = 0; slot < plan.items().size(); slot++) player.getInventory().items.set(slot, plan.items().get(slot));
        state.pendingClassItems().clear(); state.pendingClassItems().addAll(plan.remaining());
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); MasteryRuntime.sync(player);
    }
    public static void clearAttributes(ServerPlayer player) {
        BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
            var instance = player.getAttribute(holder);
            if (instance != null) for (var modifier : List.copyOf(instance.getModifiers()))
                if (modifier.id().getNamespace().equals("mastery") && modifier.id().getPath().startsWith("class/")) instance.removeModifier(modifier.id());
        });
    }
    public static void applyAttributes(ServerPlayer player) {
        clearAttributes(player);
        String id = MasteryRuntime.progress(player).selectedClass();
        var json = MasteryRuntime.definitions().classes().get(id);
        if (json == null) {
            if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
            return;
        }
        var definition = ClassDefinition.parse(id, json); int index = 0;
        for (var grant : definition.attributes()) {
            var holder = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(grant.attribute()));
            if (holder.isEmpty()) continue;
            var attribute = player.getAttribute(holder.get());
            if (attribute == null) continue;
            var modifierId = ResourceLocation.fromNamespaceAndPath("mastery", "class/" + id.replace(':', '/') + "/" + index++);
            var operation = switch (grant.operation()) {
                case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                default -> AttributeModifier.Operation.ADD_VALUE;
            };
            attribute.addTransientModifier(new AttributeModifier(modifierId, grant.amount(), operation));
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }
    public static void validateRuntime(DefinitionSet definitions, List<String> errors) {
        var server = ServerLifecycleHooks.getCurrentServer();
        definitions.classes().forEach((id, json) -> {
            try {
                var definition = ClassDefinition.parse(id, json);
                for (var grant : definition.attributes()) if (!BuiltInRegistries.ATTRIBUTE.containsKey(ResourceLocation.parse(grant.attribute())))
                    errors.add("classes/" + id + ": unknown attribute " + grant.attribute());
                for (var item : definition.startingInventory()) {
                    var itemId = ResourceLocation.parse(item.get("id").getAsString());
                    if (!BuiltInRegistries.ITEM.containsKey(itemId) || itemId.equals(ResourceLocation.withDefaultNamespace("air")))
                        errors.add("classes/" + id + ": unknown or empty starting item " + itemId);
                    else if (server != null) ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), item)
                            .error().ifPresent(error -> errors.add("classes/" + id + ": invalid starting item components: " + error.message()));
                }
            } catch (RuntimeException ex) { errors.add("classes/" + id + ": " + ex.getMessage()); }
        });
    }
}
