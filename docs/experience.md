# Proficiency XP modifiers

Mastery proficiency XP fills a tree's level bar and awards that tree's skill points. These modifiers affect earned proficiency XP. They do not change Minecraft experience, purchase costs, or direct skill-point grants.

## Attributes

`mastery:experience_gain` affects every tree. Each bundled tree also has a separate attribute named `mastery:<tree>_experience_gain`:

| Tree IDs | Corresponding attribute suffix |
| --- | --- |
| `mastery:alchemy`, `mastery:engineering`, `mastery:mining`, `mastery:smithing`, `mastery:survival` | `<tree>_experience_gain` |
| `mastery:bow`, `mastery:crossbow`, `mastery:dual_wield`, `mastery:one_handed`, `mastery:shields`, `mastery:two_handed`, `mastery:mobility` | `<tree>_experience_gain` |
| `mastery:blood`, `mastery:eldritch`, `mastery:ender`, `mastery:evocation`, `mastery:fire`, `mastery:holy`, `mastery:ice`, `mastery:lightning`, `mastery:nature` | `<tree>_experience_gain` |
| `mastery:flame_blade` | `flame_blade_experience_gain` |

For example, Fire uses `mastery:fire_experience_gain`. Every XP attribute starts at **1**, accepts values from **0 to 100**, and is synchronized to clients. A value of `1.2` means 120% of the usual XP; `0` disables earned XP for its scope.

Skills use the existing attribute effect. Adding `0.2` with `add_value` adds 20 percentage points to the multiplier:

```json
{
  "type": "mastery:attribute",
  "attribute": "mastery:fire_experience_gain",
  "amount": 0.2,
  "operation": "add_value"
}
```

The amount scales with active skill rank. Classes can grant these same attributes through their attribute modifiers. Ordinary attribute operations apply: two additive `0.2` bonuses produce a multiplier of `1.4`; multiplicative attribute operations follow Minecraft's normal attribute calculation.

## Custom trees

A tree can choose any already registered attribute using `xp_attribute` in `data/<namespace>/mastery/trees/<id>.json`:

```json
{
  "name": "Pyromancy",
  "xp_base": 25,
  "xp_growth": 10,
  "point_every": 1,
  "xp_attribute": "mastery:fire_experience_gain"
}
```

This example makes a custom tree share Fire's XP attribute. Omitting `xp_attribute` uses the matching bundled attribute for a bundled tree ID. Other trees have a neutral per-tree multiplier of `1`. Referencing an unregistered attribute rejects the reload.

Attribute registration happens when the game starts. Datapacks cannot add new Minecraft attribute registry entries during `/reload`. Use the following effect to define bonuses for any tree, including one added by a datapack or promoted from a node.

## Data-defined XP effects

Add `mastery:experience_gain` to a node's `effects` array. `tree` is optional; omitting it or setting it to an empty string affects every tree.

```json
{
  "type": "mastery:experience_gain",
  "tree": "mastery:flame_blade",
  "amount": 0.25
}
```

The example adds 25% Flame Blade XP per active rank. Amounts are fractions, may be negative, and must be finite numbers between `-100` and `100`. A nonempty `tree` must identify a loaded tree. Named effects under `data/<namespace>/mastery/effects/<id>.json` may contain the same object and be referenced normally.

All matching effects add together after rank scaling. Disabled skills, inactive modifiers, and skills whose prerequisites or external requirements are unmet contribute nothing. Attribute effects use their normal activation and equipment-context rules.

The final award is:

```text
earned XP = source XP
          * overall XP attribute
          * tree XP attribute
          * clamp(1 + sum(matching effect amount * active rank), 0, 101)
```

For example, 10 source XP with an overall attribute of `1.2`, a tree attribute of `1.5`, and a matching `0.25` effect becomes **22.5 XP**. Fractional XP is retained. Runtime factors are bounded, negative totals become zero, and a single earned award is capped at `1,000,000,000,000` XP. Existing tree and world-tier level caps still apply.

## Award behavior and API

| Award path | XP modifiers applied? |
| --- | --- |
| Data-defined `xp_sources` from gameplay usage | Yes, once after the source amount and event scale |
| `MasteryAPI.grantXp(player, tree, amount)` | Yes, once |
| `/mastery xp add <player> <tree> <amount>` | No; the administrative amount is exact |
| `MasteryRuntime.grantXpExact(player, tree, amount)` | No; internal administrative path |
| Direct points from commands, classes, API, or an XP source's `points` field | No |
| Minecraft XP or Minecraft XP purchase costs | No |

Call `MasteryAPI.grantXp` with the unmodified amount. Do not pre-apply `ExperienceModifiers` or the player will receive modifiers twice. Calls must run on the server thread. Nonfinite or negative award amounts are rejected before progression changes.

A source with `once: true` still awards its direct points and records its one-time completion when XP is reduced to zero. If the progression transaction fails, including a promoted tree whose root is still locked, the source does not spend its one-time reward. Raising multipliers later does not replay a successfully consumed source.

See [datapack definitions](datapacks.md) for XP sources and [purchase costs](costs.md) for Minecraft XP payments.


## Damage from a tree's skills

An unlocked, enabled native skill grants its own tree XP for damage it deals, regardless of damage type, held weapon, or that tree's ordinary XP-source filters. The server uses native spell attribution, including delayed projectiles. Holding a spell or buying a passive does not make unrelated damage count as skill damage.

`skill_damage_xp` is the XP awarded per point of skill damage. It defaults to `1` in `data/mastery/mastery/settings/defaults.json`; trees and nodes may override it. Set it to `0` to disable automatic skill credit and use only authored XP sources. The editor exposes **Skill damage xp** alongside tree and node settings. Ordinary overall and per-tree XP bonuses apply afterward.

Ordinary damage XP sources run first and preserve their authored rates, direct point rewards, and once-only behavior. Automatic skill XP supplies credit only when those sources did not already award positive XP to the skill’s tree. Other trees can still earn their normal matching XP. Other event types, such as kill rewards, remain independent. Failed prerequisites and locked promoted roots prevent automatic skill XP.

The bundled Flame Blade source requires Fire school damage, `context: "mastery:two_handed"`, `melee: true`, and `projectile: false`. Its Flaming Strike skill also grants Flame Blade XP through automatic skill credit. A non-Fire spell from another tree does not satisfy either route.
