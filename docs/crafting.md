# Crafting modifiers and placed comfort

Purchased, active skills can change items a player produces. The resulting item stores its modifiers in Minecraft data components, so the bonuses survive dropping, trading, saving and restarting. Removing or disabling the crafter's skill does not remove bonuses from completed items.

Create these effects in a node's `effects` array, or save reusable definitions at `data/<namespace>/mastery/effects/<id>.json` and reference them with `{"ref":"<namespace>:<id>"}`. The in-game effect editor exposes the same fields. See [the editor guide](editor.md) for editing, saving and export.

## Shared rules

| Field | Default | Behavior |
| --- | --- | --- |
| `type` | Required | One of the four effect types below. |
| `chance` | `1` | Probability from `0` to `1`. `0.25` means 25%. The player's `mastery:proc_chance` attribute adds to it; the result is clamped to 0–1. |
| `item` | No filter | Exact output item ID. |
| `item_tag` | No filter | Output item tag ID, without `#`. |

When both filters are present, both must match. Remove unused filter fields rather than entering empty strings. Amounts scale with the skill's effective rank; chance does not scale with rank. Skill toggles, prerequisites, equipped spell modifiers and progression limits determine whether a skill is active.

Each matching effect rolls separately. Effects run in node ID order and then array order. One roll affects the entire output stack or crafting batch. Percentage changes to a component apply to its current value, so separate successful effects compound.

Supported player production paths are the inventory crafting grid, crafting table, furnace/smoker/blast furnace output, stonecutter, smithing table and collection of bottles actually changed by a brewing operation. Normal clicks, hotbar swaps, throwing output and shift-clicking use the same component mutation before the output is copied or merged. Brewing uses the skills of the player collecting the finished bottle. An automatic crafter or hopper has no player skill context.

Once an eligible output receives an attempt, its `minecraft:custom_data` contains `mastery_crafted: true`, including failed rolls. Taking the same output again cannot reroll it. Existing modified items copied by an upgrading recipe retain this marker and are not enhanced repeatedly. A new brewing transformation produces a new potion and can receive a new attempt when collected. Preview items are modified when extraction is attempted, not while browsing recipes. A failed extraction does not advance the player's production roll: rebuilding the same uncollected output keeps the same outcome. The roll advances only after an output is collected. Equal completed outputs can still stack because attempt counters are stored on the player, not on each item.

Other mods can use `CraftingService.apply(ServerPlayer, ItemStack)` after committing production and before distributing its result. Calling this method commits a production roll, so it must not be used for a cancelable preview. Mastery also listens to NeoForge's `ItemCraftedEvent`; a custom machine must fire it before copying or transferring the output for component changes to reach the received item. A machine that has no player actor needs its own integration.

## Attributes on crafted items

`mastery:crafting_attribute` adds an equipment attribute modifier while retaining the item's existing attribute modifiers.

```json
{
  "type": "mastery:crafting_attribute",
  "item": "minecraft:diamond_sword",
  "chance": 0.25,
  "attribute": "minecraft:generic.attack_damage",
  "amount": 2,
  "operation": "add_value",
  "slot": "mainhand"
}
```

At rank 1 this gives the completed sword a 25% chance to gain 2 attack damage while held in the main hand. At rank 2 the added amount is 4. Modifier IDs derive from the source node and effect index; repeated application cannot add duplicate copies of the same modifier.

| Field | Default | Values |
| --- | --- | --- |
| `attribute` | Required | Registered attribute ID. Unknown IDs reject the reload. |
| `amount` | `0` | Finite number from −1,000,000 to 1,000,000; multiplied by effective rank. |
| `operation` | `add_value` | `add_value`, `add_multiplied_base`, `add_multiplied_total`. |
| `slot` | `mainhand` | `any`, `mainhand`, `offhand`, `hand`, `feet`, `legs`, `chest`, `head`, `armor`, `body`. |

Multiplicative operations follow Minecraft's attribute rules. They modify the equipped entity's attribute calculation; they are not a separate multiplier applied exclusively to the item's printed damage number. Use an explicit `item` or `item_tag` filter when only weapons should receive the effect.

## Food nutrition, saturation and buffs

`mastery:crafting_food` requires a `minecraft:food` component on the output.

```json
{
  "type": "mastery:crafting_food",
  "item": "minecraft:bread",
  "chance": 0.5,
  "nutrition_bonus": 0.2,
  "saturation_bonus": 0.25,
  "buff_strength_bonus": 0.5,
  "buff_duration_bonus": 0.25,
  "meal_strength_bonus": 0.15,
  "meal_duration_bonus": 0.1
}
```

Every bonus defaults to `0` and accepts `0`–`100`. Values are fractions: `0.25` adds 25% per effective rank. A successful roll applies every supplied field. A food without vanilla status effects can still receive nutrition, saturation and optional Needs Not Necessities meal bonuses.

| Field | Changes |
| --- | --- |
| `nutrition_bonus` | Food points; rounded to the nearest integer. |
| `saturation_bonus` | The food component's saturation value. |
| `buff_strength_bonus` | Existing food status effect levels. |
| `buff_duration_bonus` | Existing food status effect duration. |
| `meal_strength_bonus` | Positive Needs Not Necessities meal modifier amounts, when that mod is installed. |
| `meal_duration_bonus` | Needs Not Necessities meal duration, when that mod is installed. |

Vanilla status effect levels are integers. Strength uses `floor((amplifier + 1) × (1 + bonus)) − 1`. A 25% boost cannot turn level I into a fractional level, so level I stays level I; a 100% boost makes it level II. Effect probabilities, ambient/particle/icon settings, eating speed and consumed-item replacements remain intact. Infinite durations stay infinite. Finite durations are capped at 1,728,000 ticks; nutrition and saturation are capped at 1,000,000.

Food component boosts affect all status effects already on the food, including negative effects. Needs Not Necessities meal strength deliberately increases only positive modifier amounts. Existing negative meal penalties keep their original amounts. The optional meal strength and duration totals stored on one item are capped at `100` each.

## Potions

`mastery:crafting_potion` requires a `minecraft:potion_contents` component.

```json
{
  "type": "mastery:crafting_potion",
  "chance": 0.2,
  "amplifier_bonus": 1,
  "duration_bonus": 0.25,
  "added_effect": "minecraft:regeneration",
  "added_duration": 200,
  "added_amplifier": 0
}
```

| Field | Default | Behavior |
| --- | --- | --- |
| `amplifier_bonus` | `0` | Integer levels added to existing effects per effective rank, from 0 to 254. |
| `duration_bonus` | `0` | Fractional duration boost per effective rank, from 0 to 100. |
| `added_effect` | None | Registered mob effect ID to add. Remove the field to omit an added effect. |
| `added_duration` | `200` | Added effect duration in ticks, from 1 to 1,728,000. Does not scale with rank. |
| `added_amplifier` | `0` | Added effect amplifier, from 0 to 254; `0` means level I. Does not scale with rank. |

The example has a 20% chance to increase all existing potion effects by one level, extend them by 25%, and add regeneration I for 10 seconds. An instant effect can gain strength, but extending its duration does not make it a damage-over-time effect.

A modified potion stores its final effects as custom effects and preserves its original display name. The base effects are flattened exactly once, preventing an instant healing or harming effect from firing both its original and upgraded versions. Drinking, splash and lingering potion code then reads the same component.

The original potion ID is retained for later brewing. Brewing an enhanced potion again creates the normal output of that recipe, replacing its previous enhancements; the collecting player's current skills can improve that new output. Custom brewing recipes receive an opportunity to match the enhanced bottle first. Bottles that were present in a stand but did not change during a brew do not acquire a new crafting opportunity.

## Comfort from placed blocks

`mastery:placed_comfort` records a placement bonus from the placing player's active skill. It does not add an attribute to the held block item.

```json
{
  "type": "mastery:placed_comfort",
  "block": "minecraft:oak_planks",
  "chance": 1,
  "amount": 2,
  "radius": 8,
  "comfort_type": "mastery_woodwork"
}
```

| Field | Default | Behavior |
| --- | --- | --- |
| `block` | No filter | Exact placed block ID. |
| `block_tag` | No filter | Placed block tag ID, without `#`. |
| `amount` | `0` | Additional comfort per effective rank, from 0 to 1,000,000. |
| `radius` | `8` | Distance from the block center, from 1 to 64 blocks. |
| `comfort_type` | `mastery_crafted` | Needs Not Necessities comfort group used for diminishing returns. Must be nonblank. |

Shared item filters, if used here, match the placed block's item. Every supplied filter must match. A multiblock placement such as a bed creates one placement contribution. The bonus is saved at the original block position and retains the placer skill value even if that player later loses the skill. Any nearby player can benefit.

Destroying or replacing the block type removes the saved bonus. Ordinary changes to the same block's state preserve it. Piston movement does not copy the bonus to the destination. Drops do not inherit comfort data; replacing a dropped block evaluates the new placer's current skill again. Data is stored per dimension in `data/mastery_placed_comfort.dat`. Scans inspect an index of nearby saved placements and do not force chunks to load.

## Optional Needs Not Necessities integration

Needs Not Necessities (`needs_not_necessities`) is optional. Mastery has no compile or runtime dependency on it. Without it, crafted weapon, food and potion components continue working, while meal bonuses and placed comfort are stored but have no survival-system effect.

When present, Mastery registers the public `ComfortProvider` and `MealAnalyzer` interfaces through reflection during common setup. No private fields or replacement survival logic are used. The installed Needs Not Necessities version must expose `SurvivalProviderRegistry`, `ComfortProvider.Contribution`, `MealAnalysis` and `SurvivalModifier` with the documented current constructor/accessor contracts. If that API is unavailable, Mastery logs the integration failure and continues running its other features.

Needs Not Necessities' compatibility module must be enabled for it to invoke providers. Its comfort rules still apply diminishing returns by `comfort_type`. Its meal selection, score, replacement policy and biological time system remain authoritative. Mastery's percentage meal boosts are evaluated from the consumed item's saved data, so the consumer does not need the crafting skill. Held-food consumption preserves that data; mods that convert placed food back into a fresh item without its components need a separate adapter to retain crafting bonuses.

## Validation and integration limits

Reload validation rejects invalid probabilities, numeric strings, nonfinite/out-of-range numeric fields, fractional potion amplifiers, unsupported attribute operations/slots and malformed resource IDs. Exact item, block, attribute and mob effect IDs must exist in the installed registries. Tag filters use the current datapack tags when the action occurs.

Automated coverage exercises persistent item components, failed rolls and rebuilt uncollected previews that cannot be retried, normal and shift-click crafting, original potion effects without duplication, subsequent brewing recipes, saved comfort and cleanup after block replacement. The optional integration GameTest invokes the installed public providers when Needs Not Necessities is present; an absent-mod run checks that the rest of Mastery starts without it. These checks are separate from observing tooltips, crafting controls and comfort panels in a real client.
