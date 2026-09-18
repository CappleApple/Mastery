package com.cappleapple.mastery.mechanics;

import com.cappleapple.mastery.Mastery;
import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.elemental.ElementalDamage;
import com.google.gson.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;
import static com.cappleapple.mastery.mechanics.MechanicsRules.*;

/** Server-thread combat scripting. Queued events observe completed damage/death and never reenter combat. */
public final class MechanicsRuntime {
    private static final String PROC = "mastery_proc_effect";
    private static final int MAX_EVENTS = 2048, MAX_ACTIONS = 8192, MAX_DEPTH = 16;
    private static final Map<UUID, Map<String, KeywordState>> KEYWORDS = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<UUID, Integer> DEATHS = new HashMap<>();
    private static int depth, actions;
    private static boolean initialized;
    private MechanicsRuntime() {}

    private static final class KeywordState {
        final LivingEntity target;
        ServerPlayer owner;
        int stacks, rank;
        long expires, nextTick, nextDecay;
        DamageContext source=DamageContext.EMPTY;
        KeywordState(ServerPlayer owner, LivingEntity target, int rank, long nextTick) {
            this.owner = owner; this.target = target; this.rank = rank; this.nextTick = nextTick;
        }
    }

    private static final class Pending {
        final ServerPlayer owner;
        final LivingEntity target;
        final String event;
        final float damage;
        final DamageContext damageContext;
        final Set<String> spellModifiers;
        double selfHealth, targetHealth;
        final double selfMaximum, targetMaximum;
        Pending(ServerPlayer owner, LivingEntity target, String event, float damage, DamageContext damageContext) {
            this.owner = owner; this.target = target; this.event = event; this.damage = damage; this.damageContext=damageContext;
            spellModifiers=owner.getUUID().equals(damageContext.caster())
                    ?com.cappleapple.mastery.spells.SpellService.activeModifiers(owner,damageContext.spell()):Set.of();
            selfHealth = owner.getHealth(); selfMaximum = owner.getMaxHealth();
            targetHealth = target == null ? 0 : target.getHealth(); targetMaximum = target == null ? 0 : target.getMaxHealth();
        }
    }

    private record Context(ServerPlayer owner, LivingEntity target, int rank, int stacks, float damage,
            double selfHealth, double selfMaximum, double targetHealth, double targetMaximum, DamageContext damageContext,String keyword) {
        Context(ServerPlayer owner,LivingEntity target,int rank,int stacks,float damage,double selfHealth,double selfMaximum,double targetHealth,double targetMaximum,DamageContext source) {
            this(owner,target,rank,stacks,damage,selfHealth,selfMaximum,targetHealth,targetMaximum,source,"");
        }
        Context keyword(String id,DamageContext source){return new Context(owner,target,rank,stacks,damage,selfHealth,selfMaximum,targetHealth,targetMaximum,source,id);}
        static Context current(ServerPlayer owner, LivingEntity target, int rank, int stacks) {
            return new Context(owner, target, rank, stacks, 0, owner.getHealth(), owner.getMaxHealth(),
                    target == null ? 0 : target.getHealth(), target == null ? 0 : target.getMaxHealth(),DamageContext.EMPTY);
        }
        Context withTarget(LivingEntity selected) { return new Context(owner, selected, rank, stacks, damage,
                selfHealth, selfMaximum, selected == null ? 0 : selected.getHealth(), selected == null ? 0 : selected.getMaxHealth(),damageContext,keyword); }
    }

    public static void initialize() {
        if (!initialized) { initialized = true; NeoForge.EVENT_BUS.register(MechanicsRuntime.class); NeoForge.EVENT_BUS.register(NativeHealingIntegration.class); }
    }

    /** Reload and server shutdown invalidate temporary keyword stacks and proc cooldowns. */
    public static void clear() { KEYWORDS.clear(); COOLDOWNS.clear(); PENDING.clear(); DEATHS.clear(); NativeHealingIntegration.clear(); actions = 0; }

    public static boolean processing() { return depth > 0; }

    public static void withProcEntity(Entity entity, Runnable tick) {
        boolean secondary = entity.getPersistentData().getBoolean(PROC);
        if (secondary) depth++;
        try { tick.run(); } finally { if (secondary) depth--; }
    }

    public static boolean secondary(DamageSource source) {
        return processing() || source.getDirectEntity() != null && source.getDirectEntity().getPersistentData().getBoolean(PROC)
                || source.getEntity() != null && source.getEntity().getPersistentData().getBoolean(PROC);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || event.getEntity().level().isClientSide || secondary(event.getSource()) || ElementalDamage.isWeaponBonus(event.getSource()) || ElementalDamage.partitioning(event.getEntity())) return;
        var target = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer owner && owner != target)
            queue(new Pending(owner, target, "hit", event.getNewDamage(),DamageContexts.capture(event.getSource())));
        if (target instanceof ServerPlayer owner)
            queue(new Pending(owner, event.getSource().getEntity() instanceof LivingEntity attacker ? attacker : null, "hurt", event.getNewDamage(),DamageContexts.capture(event.getSource())));
    }

    /** One successful-hit event for all damage portions, including complete absorption of the primary portion. */
    public static void weaponHitFinished(LivingEntity target, DamageSource source, float applied, DamageContext damageContext) {
        if (applied <= 0 || secondary(source)) return;
        if (source.getEntity() instanceof ServerPlayer owner && owner != target) queue(new Pending(owner, target, "hit", applied,damageContext));
        if (target instanceof ServerPlayer owner) queue(new Pending(owner, source.getEntity() instanceof LivingEntity attacker ? attacker : null, "hurt", applied,damageContext));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void died(LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide) return;
        var victim = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer owner && owner != victim) queue(new Pending(owner, victim, "kill", 0,DamageContexts.capture(event.getSource())));
        if (victim instanceof ServerPlayer owner)
            queue(new Pending(owner, event.getSource().getEntity() instanceof LivingEntity attacker ? attacker : null, "death", 0,DamageContexts.capture(event.getSource())));
    }

    private static void queue(Pending event) { if (PENDING.size() < MAX_EVENTS) PENDING.add(event); }

    /** Called after all portions of a weapon hit, so thresholds include the complete elemental result. */
    public static void refreshPendingHealth(LivingEntity damaged) {
        boolean hit = false, hurt = false;
        for (int index = PENDING.size() - 1; index >= 0; index--) {
            var event = PENDING.get(index);
            if (!hit && event.event.equals("hit") && event.target == damaged) { event.targetHealth = damaged.getHealth(); hit = true; }
            if (!hurt && event.event.equals("hurt") && event.owner == damaged) { event.selfHealth = damaged.getHealth(); hurt = true; }
            if (hit && hurt) break;
        }
    }

    @SubscribeEvent
    public static void created(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && (processing() || ProcSpellCaster.casting()) && !(event.getEntity() instanceof Player)) event.getEntity().getPersistentData().putBoolean(PROC, true);
        // Descendant projectiles retain the proc origin even when spawned on a later tick.
        if (event.getEntity() instanceof Projectile projectile && projectile.getOwner() != null && projectile.getOwner().getPersistentData().getBoolean(PROC))
            projectile.getPersistentData().putBoolean(PROC, true);
    }

    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        KEYWORDS.remove(event.getEntity().getUUID());
        DEATHS.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }
    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) forget(player); }
    @SubscribeEvent public static void respawned(PlayerEvent.PlayerRespawnEvent event) { if (event.getEntity() instanceof ServerPlayer player) forget(player); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { clear(); }

    public static void forget(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID()); KEYWORDS.remove(player.getUUID()); DEATHS.remove(player.getUUID());
        KEYWORDS.values().forEach(map -> map.values().removeIf(state -> state.owner.getUUID().equals(player.getUUID())));
        KEYWORDS.values().removeIf(Map::isEmpty);
        PENDING.removeIf(event -> event.owner.getUUID().equals(player.getUUID()));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { tick(); }

    /** Also used by integration GameTests to finish the current synchronous damage batch. */
    public static void tick() {
        actions = 0;
        var events = new ArrayList<>(PENDING); PENDING.clear();
        events.sort(Comparator.comparingInt(p -> p.event.equals("kill") || p.event.equals("death") ? 1 : 0));
        for (var pending : events) {
            if (pending.owner.isRemoved() || pending.owner.isSpectator()) continue;
            if (pending.event.equals("death") && pending.owner.isAlive()) continue;
            if (pending.event.equals("kill") && (pending.target == null || pending.target.isAlive())) continue;
            if (pending.event.equals("kill") || pending.event.equals("death")) {
                UUID victim = pending.event.equals("death") ? pending.owner.getUUID() : pending.target.getUUID();
                int flag = pending.event.equals("death") ? 2 : 1;
                int handled = DEATHS.getOrDefault(victim, 0);
                if ((handled & flag) != 0) continue;
                DEATHS.put(victim, handled | flag);
            }
            runGuarded(() -> fire(pending));
        }
        var states = new ArrayList<Map.Entry<String, KeywordState>>();
        KEYWORDS.values().forEach(map -> map.forEach((id, state) -> states.add(Map.entry(id, state))));
        for (var entry : states) {
            String id = entry.getKey(); var state = entry.getValue();
            var current = KEYWORDS.get(state.target.getUUID());
            if (current == null || current.get(id) != state) continue;
            long now = state.target.level().getGameTime();
            var definition = MasteryRuntime.definitions().keywords().get(id);
            if (definition == null || !state.target.isAlive() || state.target.isRemoved()
                    || state.owner.isRemoved() || state.owner.level() != state.target.level()) { current.remove(id); continue; }
            if(state.expires<=now){removeKeyword(state.target,id,0);continue;}
            if(number(definition,"decay_stacks",0)>0&&now>=state.nextDecay) {
                state.nextDecay=now+(int)number(definition,"decay_interval",20);
                removeKeyword(state.target,id,(int)number(definition,"decay_stacks",1));
                if(current.get(id)!=state)continue;
            }
            if (now >= state.nextTick) {
                state.nextTick = now + (int)number(definition, "tick_interval", 20);
                runGuarded(() -> execute(definition, "tick_actions", Context.current(state.owner, state.target, state.rank, state.stacks).keyword(id,state.source)));
            }
        }
        KEYWORDS.values().removeIf(Map::isEmpty);
    }

    private static void fire(Pending event) {
        Map<String, Integer> granted = new TreeMap<>();
        for (var node : MasteryRuntime.definitions().nodes().values()) {
            if(node.spellModifier()&&!event.spellModifiers.contains(node.id()))continue;
            if(!TreeModifiers.matches(node,event.damageContext))continue;
            int rank = EffectService.effectiveRank(event.owner, node);
            if (rank <= 0) continue;
            for (var raw : node.effects()) {
                var effect = resolve(raw, 0);
                if (effect != null && text(effect, "type", "").equals("mastery:trigger")) granted.merge(text(effect, "trigger", ""), rank, Math::max);
            }
        }
        for (var entry : granted.entrySet()) {
            var definition = MasteryRuntime.definitions().triggers().get(entry.getKey());
            if (definition == null || !text(definition, "event", "").equals(event.event)) continue;
            var context = new Context(event.owner, event.target, entry.getValue(), 1, event.damage,
                    event.selfHealth, event.selfMaximum, event.targetHealth, event.targetMaximum,event.damageContext);
            if (!conditions(definition, context)) continue;
            long now = event.owner.level().getGameTime();
            var cooldowns = COOLDOWNS.computeIfAbsent(event.owner.getUUID(), ignored -> new HashMap<>());
            if (cooldowns.getOrDefault(entry.getKey(), 0L) > now) continue;
            if (event.owner.getRandom().nextDouble() >= chance(number(definition, "chance", 1), procBonus(event.owner))) continue;
            cooldowns.put(entry.getKey(), now + (int)number(definition, "cooldown", 0));
            execute(definition, "actions", context);
        }
    }

    private static double procBonus(ServerPlayer owner) {
        var holder = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath("mastery", "proc_chance"));
        return holder.isPresent() && owner.getAttribute(holder.get()) != null ? owner.getAttributeValue(holder.get()) : 0;
    }

    private static JsonObject resolve(JsonObject value, int count) {
        if (value == null || count > 16) return null;
        return value.has("ref") ? resolve(MasteryRuntime.definitions().effects().get(value.get("ref").getAsString()), count + 1) : value;
    }

    private static boolean conditions(JsonObject value, Context context) {
        if (!value.has("conditions")) return true;
        for (var raw : value.getAsJsonArray("conditions")) {
            var condition = raw.getAsJsonObject();
            if(text(condition,"type","").equals("damage")) {
                if(!context.damageContext.matches(condition))return false;
                continue;
            }
            boolean self = text(condition, "target", "target").equals("self");
            var selected = self ? context.owner : context.target;
            if (selected == null) return false;
            if (text(condition, "type", "").equals("health")) {
                if (!health(self ? context.selfHealth : context.targetHealth, self ? context.selfMaximum : context.targetMaximum, condition)) return false;
            } else {
                int count = stacks(selected, text(condition, "keyword", ""));
                if (count < number(condition, "min", 1) || count > number(condition, "max", 1024)) return false;
            }
        }
        return true;
    }

    /** Current live stack count; expired entries are invisible even before the next tick. */
    public static int stacks(LivingEntity entity, String id) {
        var state = KEYWORDS.getOrDefault(entity.getUUID(), Map.of()).get(id);
        return state == null || state.expires <= entity.level().getGameTime() ? 0 : state.stacks;
    }

    public static void applyKeyword(ServerPlayer owner, LivingEntity target, String id, int amount, int duration) {
        runGuarded(() -> applyKeyword(Context.current(owner, target, 1, 1), id, amount, duration));
    }

    private static void applyKeyword(Context context, String id, int amount, int duration) {
        var definition = MasteryRuntime.definitions().keywords().get(id);
        if (definition == null || context.target == null || !context.target.isAlive() || context.owner.level() != context.target.level() || amount <= 0) return;
        if (!KEYWORDS.containsKey(context.target.getUUID()) && KEYWORDS.size() >= 4096) return;
        double adjusted=KeywordModifiers.adjust(context.owner,id,"stacks",amount,context.damageContext);
        amount=(int)Math.floor(adjusted)+(context.owner.getRandom().nextDouble()<adjusted-Math.floor(adjusted)?1:0);
        if(amount<=0)return;
        var existing=KEYWORDS.getOrDefault(context.target.getUUID(),Map.of()).get(id);
        if(existing!=null&&existing.expires<=context.target.level().getGameTime())removeKeyword(context.target,id,0);
        var map = KEYWORDS.computeIfAbsent(context.target.getUUID(), ignored -> new HashMap<>());
        if (!map.containsKey(id) && map.size() >= 64) return;
        long now = context.target.level().getGameTime();
        var state = map.computeIfAbsent(id, ignored -> new KeywordState(context.owner, context.target, context.rank, now + (int)number(definition, "tick_interval", 20)));
        if (state.expires <= now) { state.stacks = 0; state.nextTick = now + (int)number(definition, "tick_interval", 20); }
        int previous = state.stacks;
        state.stacks = MechanicsRules.stacks(previous, amount, (int)number(definition, "max_stacks", 1));
        state.owner = context.owner; state.rank = context.rank;state.source=context.damageContext;
        int baseDuration=duration>0?duration:(int)number(definition,"duration",100);
        state.expires=baseDuration==0?Long.MAX_VALUE:now+Math.clamp((int)Math.ceil(KeywordModifiers.adjust(context.owner,id,"duration",baseDuration,context.damageContext)),1,72000);
        state.nextDecay=now+Math.max(1,(int)number(definition,"decay_delay",0));
        int threshold = (int)number(definition, "threshold", 0);
        if (crossed(previous, state.stacks, threshold)) {
            int triggeringStacks = state.stacks;
            // Consume before executing actions so cross-keyword interactions are deterministic and recursion-safe.
            if (bool(definition, "consume_stacks", true)) {
                removeKeyword(context.target,id,threshold);
            }
            runGuarded(() -> execute(definition, "threshold_actions", Context.current(context.owner, context.target, context.rank, triggeringStacks).keyword(id,context.damageContext)));
        }
    }

    private static void removeKeyword(LivingEntity target, String id, int count) {
        var values = KEYWORDS.get(target.getUUID());
        if (values == null) return;
        var state = values.get(id);
        if (state == null) return;
        int lost=count==0?state.stacks:Math.min(count,state.stacks);if(lost<=0)return;
        state.stacks-=lost;boolean empty=state.stacks==0;if(empty)values.remove(id);
        var definition=MasteryRuntime.definitions().keywords().get(id);
        if(definition!=null) {
            var context=Context.current(state.owner,target,state.rank,lost).keyword(id,state.source);
            runGuarded(()->execute(definition,"stacks_lost_actions",context));
            if(empty)runGuarded(()->execute(definition,"all_stacks_lost_actions",context));
        }
    }

    private static void execute(JsonObject definition, String field, Context context) {
        if (!definition.has(field)) return;
        for (var raw : definition.getAsJsonArray(field)) {
            if (actions >= MAX_ACTIONS) return;
            var action = raw.getAsJsonObject();
            if (context.owner.getRandom().nextDouble() >= number(action, "chance", 1)) continue;
            var selected = targets(action, context);
            if (selected.isEmpty() && text(action, "type", "").equals("spell") && text(action, "target", "target").equals("aim")) {
                if (conditions(action, context)) { actions++; ProcSpellCaster.cast(context.owner, null, text(action, "spell", ""), (int)number(action, "level", 1)); }
                continue;
            }
            for (var target : selected) {
                if (++actions > MAX_ACTIONS) return;
                var selectedContext = context.withTarget(target);
                if (conditions(action, selectedContext)) action(action, selectedContext);
            }
        }
    }

    private static String damageType(JsonObject action, String fallback) {
        String element = text(action, "element", ""), school = text(action, "school", "");
        return !element.isBlank() ? element : !school.isBlank() ? school : fallback;
    }

    private static void action(JsonObject action, Context context) {
        var target = context.target;
        double scale = (bool(action, "per_rank", false) ? context.rank : 1) * (bool(action, "per_stack", false) ? context.stacks : 1);
        double baseAmount=number(action,"amount",0)+context.damage*number(action,"damage_fraction",0);
        if(!context.keyword.isBlank()&&Set.of("damage","lightning").contains(text(action,"type","")))
            baseAmount=KeywordModifiers.adjust(context.owner,context.keyword,"damage",baseAmount,context.damageContext);
        double amount=Math.min(1000000,baseAmount*scale);
        switch (text(action, "type", "")) {
            case "keyword" -> applyKeyword(context, text(action, "keyword", ""), (int)Math.min(1024, number(action, "stacks", 1) * scale), (int)number(action, "duration", 0));
            case "remove_keyword" -> removeKeyword(target, text(action, "keyword", ""), (int)number(action, "stacks", 0));
            case "damage" -> { if (target.isAlive()) ElementalDamage.hurt(target, context.owner, damageType(action, "irons_spellbooks:evocation"), (float)amount); }
            case "heal" -> { if (target.isAlive()) ElementalDamage.heal(target, context.owner, damageType(action, "irons_spellbooks:holy"), (float)amount); }
            case "effect" -> {
                if (!target.isAlive()) break;
                BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(text(action, "effect", "")))
                        .ifPresent(effect -> target.addEffect(new MobEffectInstance(effect, (int)number(action, "duration", 100), (int)number(action, "amplifier", 0)), context.owner));
            }
            case "spell" -> ProcSpellCaster.cast(context.owner, target, text(action, "spell", ""), (int)number(action, "level", 1));
            case "lightning" -> {
                var bolt = EntityType.LIGHTNING_BOLT.create(target.level());
                if (bolt != null) {
                    bolt.moveTo(target.position()); bolt.setVisualOnly(true); bolt.setCause(context.owner);
                    target.level().addFreshEntity(bolt);
                }
                if (target.isAlive()) ElementalDamage.hurt(target, context.owner, damageType(action, "irons_spellbooks:lightning"), (float)amount);
            }
            case "particles" -> {
                var particle = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(text(action, "particle", "")));
                if (particle instanceof SimpleParticleType simple && target.level() instanceof ServerLevel level) {
                    double spread = number(action, "spread", 0.5);
                    level.sendParticles(simple, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                            (int)number(action, "count", 12), spread, spread, spread, number(action, "speed", 0));
                }
            }
        }
    }

    private static List<LivingEntity> targets(JsonObject action, Context context) {
        String selector = text(action, "target", "target");
        if (selector.equals("self")) return List.of(context.owner);
        if (selector.equals("target")) return context.target != null && context.owner.level() == context.target.level() ? List.of(context.target) : List.of();
        double radius = number(action, "radius", 8);
        if (selector.equals("aim")) {
            Vec3 start = context.owner.getEyePosition(), end = start.add(context.owner.getLookAngle().scale(radius));
            end = context.owner.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context.owner)).getLocation();
            var hit = ProjectileUtil.getEntityHitResult(context.owner.level(), context.owner, start, end,
                    context.owner.getBoundingBox().expandTowards(end.subtract(start)).inflate(1), candidate -> candidate instanceof LivingEntity living && eligible(living, context.owner, action));
            return hit != null && hit.getEntity() instanceof LivingEntity living ? List.of(living) : List.of();
        }
        var center = text(action, "center", "target").equals("self") || context.target == null ? context.owner : context.target;
        return center.level().getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius),
                        target -> eligible(target, context.owner, action) && target.distanceToSqr(center) <= radius * radius)
                .stream().sorted(Comparator.comparingDouble((LivingEntity target) -> target.distanceToSqr(center)).thenComparing(Entity::getUUID))
                .limit((int)number(action, "limit", 16)).toList();
    }

    private static boolean eligible(LivingEntity target, ServerPlayer owner, JsonObject action) {
        return target != owner && target.isAlive() && !target.isSpectator()
                && (!(target instanceof Player) || bool(action, "include_players", false))
                && (bool(action, "include_allies", false) || !target.isAlliedTo(owner))
                && (!(target instanceof Player player) || owner.canHarmPlayer(player));
    }

    private static void runGuarded(Runnable operation) {
        if (depth >= MAX_DEPTH || actions >= MAX_ACTIONS) return;
        depth++;
        try { operation.run(); }
        catch (RuntimeException failure) { Mastery.LOGGER.error("Mastery combat action failed; remaining action chain was stopped", failure); }
        finally { depth--; }
    }
}
