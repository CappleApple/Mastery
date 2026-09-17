# Public API

Integration entry points live under `com.cappleapple.mastery.api`. Java binary compatibility across future versions is not yet guaranteed.

Access player state on the server thread. Register providers and handlers during common setup, using `enqueueWork`, before datapack loading. Common handlers must not import client classes.

## Proficiency and class grants

```java
MasteryAPI.grantPoints(player, "mastery:two_handed", 3);
MasteryAPI.grantPoints(player, "mastery:fire", 1);
int level = MasteryAPI.level(player, "mastery:fire");
int rank = MasteryAPI.rank(player, "mastery:fireball");
MasteryAPI.grantXp(player, "mastery:fire", 10);
```

`player` is a `ServerPlayer`. Positive point grants discover that independent tree. Grant methods return `ProgressionService.Change` with `success()` and `message()`; invalid IDs, nonfinite XP, and negative grants fail. Each balance belongs to one tree.

`ProficiencyChangedEvent` is posted on the NeoForge game bus for XP/level mutations and exposes the player, tree ID, previous level, and current level.

## Usage and requirements

`MasteryAPI.emitUsage(player, event, JsonObject)` feeds authoritative server events into datapack XP rules. Native school damage already enters through `LivingDamageEvent.Post`; integrations must not emit that same hit again or it would award XP twice.

For custom usage events, choose an event name and context fields matching the integration's datapack rules. `RequirementRegistry.register(id, BiPredicate<RequirementContext, JsonObject>)` adds a predicate. It must return false for unmet conditions without mutating progression. Duplicate registrations fail.

`mastery:book_unlocked` tests a persistent consumable-book token. Nodes may instead use `book_token` to hide and gate an entire dependency branch.

## World progression

`MasteryAPI.registerWorldTierProvider(WorldTierProvider)` registers a server-thread `getTier(ServerLevel)` callback. Return a nonnegative tier or `-1` to defer. The first nonnegative result wins; otherwise `defaultWorldTier` applies.

## Effects and equipment

`effects.EffectRegistry.register(id, Handler)` supplies `apply`, optional `remove`, and optional `onUsage`. Effects derive from effective purchased ranks. Removal must undo only the handler's own changes. Rebuilds occur on progression changes, login, respawn, dimension changes, reload, and changed effective conditions.

`SpellEquipmentRegistry.registerCapacity(id, SpellCapacitySource)` adds assignable capacity to native equipment slots and configured/progression bonuses. Do not count native spellbook capacity again; Mastery already reads it.

`SpellEquipmentRegistry.registerContext(priority, CombatContextProvider)` adds a combat usage classification candidate. It does not select spell assignments; those follow hotbar positions or the shared quick-cast mode. Return a defined context ID or `Optional.empty()`. Higher priorities win, with ID ordering for ties. The bundled two-handed context is reserved for Better Combat's resolved classification.

## Existing-spell modifiers

`api.spell.SpellModifierRegistry.register(id, SpellModifier)` registers a modifier for an existing Iron's spell. The callback receives the server player, native spell ID, effective node rank, effect parameters, and a `SpellModifiers` accumulator.

Use `addLevels`, `multiplyMana`, `multiplyCooldown`, and `multiplyCastTime` to adjust native calculations. Do not retain the accumulator or mutate a shared spell definition. Duplicate IDs fail registration. See [spell modifiers](spells.md) for the bundled data contract.

This API does not register or execute new spells. Spell identity and casting remain in Iron's registry and implementation.
