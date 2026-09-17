# Damage types, mob groups, and weapon defaults

Mastery splits weapon damage into typed portions before applying each portion's defenses. Iron's nine schools use their registered damage types. Slashing, piercing, and blunt use Mastery's registered physical damage types. Native Iron's spell damage participates in the same mob matchups.

## Bonus damage and conversion

An Aspect adds damage. Fire Aspect II adds 20% of the incoming weapon hit as fire, so a 10-damage hit has 10 base damage plus 2 fire damage before defenses and power modifiers. It does not ignite the target. All nine school Aspects support levels I-X, melee weapons, bows, crossbows, and tridents. Only one Aspect can be applied to a weapon by default. All nine enchantments share `#mastery:exclusive_set/aspects`; datapacks can override `data/mastery/tags/enchantment/exclusive_set/aspects.json` to change these conflicts. Existing or command-forced combinations are not stripped.

Conversion replaces damage. With 30% fire conversion, a 10-damage hit has 7 base damage and 3 fire damage. An Aspect on that same weapon still adds its bonus from the original 10 damage. If requested conversion totals more than 100%, the shares are normalized: 100% fire plus 100% ice becomes 50% of each. Physical damage never becomes negative.

A default weapon type fills the unconverted remainder. For example, a sword with 30% fire conversion deals 70% slashing and 30% fire. The bundled defaults classify swords and axes as slashing; bows, crossbows, tridents, and pickaxes as piercing; and maces and shovels as blunt. Untagged items remain ordinary physical damage unless a matching rule is added.

Arrows snapshot their firing weapon's bonuses, conversions, and power at launch. Changing held equipment after firing does not change those values. Melee attacks use the current weapon and active skills. The base amount is the incoming attack damage, including the game's attack calculation; it is not the item's tooltip number. Each typed portion has its own damage-type defenses.

## Attributes and skills

Every bundled damage type has these attributes. Replace `fire` with `ice`, `lightning`, `holy`, `ender`, `blood`, `evocation`, `nature`, `eldritch`, `slashing`, `piercing`, or `blunt`.

| Attribute | Meaning of `0.2` |
| --- | --- |
| `mastery:fire_weapon_damage` | Add 20% of the base weapon hit as fire |
| `mastery:fire_conversion` | Replace 20% of the base weapon hit with fire |
| `mastery:fire_damage` | Increase fire damage and typed fire healing by 20% |
| `mastery:fire_attunement` | Amplify favorable and unfavorable fire matchups by 20% |
| `mastery:fire_potency` | Amplify only favorable fire matchups by 20% |
| `mastery:fire_mitigation` | Reduce the magnitude of unfavorable fire matchups by 20% |

`mastery:elemental_damage` increases every type marked elemental. It affects weapon elemental portions, native school damage, and typed healing. Physical slashing/piercing/blunt are not elemental by default. `mastery:proc_chance` adds to trigger and crafting proc chances; `0.1` adds ten percentage points, with the final chance clamped to 0-100%.

Use ordinary attribute effects in a node's `effects` list:

```json
{
  "type": "mastery:attribute",
  "attribute": "mastery:fire_conversion",
  "amount": 0.2,
  "operation": "add_value"
}
```

Amounts multiply by the purchased node rank. A five-rank node with this effect reaches 100% fire conversion. Multiple effects can share the same node. Disabled or ineligible nodes contribute nothing. Weapon-tree attribute effects inherit that tree's combat context; set the effect's `context` to an empty string when the bonus should apply regardless of held weapon.

Iron's existing school power and spell-power attributes also scale weapon school portions. Mastery's global and per-type damage attributes multiply those results. Native spell damage already includes Iron's own power calculation, so Mastery applies only its additional multiplier.

## Mob groups

Path: `data/<namespace>/mastery/mob_types/<id>.json`.

```json
{
  "name": "Undead",
  "entities": ["irons_spellbooks:necromancer"],
  "entity_tags": ["minecraft:undead"]
}
```

An entity belongs to a group if any listed ID or tag matches. Repeated matches inside one group count once. An entity may belong to any number of different groups; every matching group's damage modifier contributes. Missing entity IDs or empty tags match nothing, allowing definitions that mention optional mob mods.

The bundled `mastery:undead` includes Minecraft's undead tag and Iron's necromancer. `mastery:players` contains `minecraft:player`.

## Type definitions

Path: `data/<namespace>/mastery/elements/<id>.json`. The directory name remains `elements` for both elemental and physical types.

```json
{
  "school": "irons_spellbooks:holy",
  "enchantment": "mastery:holy_aspect",
  "damage_per_level": 0.1,
  "weapon_attribute": "mastery:holy_weapon_damage",
  "conversion_attribute": "mastery:holy_conversion",
  "power_attribute": "mastery:holy_damage",
  "attunement_attribute": "mastery:holy_attunement",
  "potency_attribute": "mastery:holy_potency",
  "mitigation_attribute": "mastery:holy_mitigation",
  "damage_modifiers": {
    "mastery:undead": 0.25,
    "mastery:players": -0.25
  }
}
```

`school` names an existing Iron's school. For another native damage type, use `damage_type` instead, for example `mastery:slashing`. If both are present, `damage_type` selects the damage source and `school` supplies native school power. `elemental` defaults to true for a school and false otherwise; it controls the global elemental-power multiplier.

Attribute fields refer to already registered attributes. Omitting them uses `mastery:<definition-path>_weapon_damage`, `_conversion`, `_damage`, `_attunement`, `_potency`, and `_mitigation`; missing fallback attributes contribute zero. Additional definitions and native damage types can be supplied by datapacks. New attribute registry entries require a mod, not a Mastery-only reload.

`enchantment` is optional. Its level is multiplied by `damage_per_level` (default `0.1`) and added to the weapon bonus fraction. The native enchantment files live at `data/<namespace>/enchantment/<id>.json`. Their level caps, costs, item tags, and availability are ordinary Minecraft datapack data. `/mastery reload` reloads Mastery definitions only; native enchantment and damage-type changes require the normal datapack lifecycle.

## Matchup arithmetic

Each matching mob group contributes a signed delta. For damage:

```text
delta = configured modifier × (1 + Attunement)
positive delta *= (1 + Potency)
negative delta *= max(0, 1 - Mitigation)
matchup multiplier = max(0, 1 + sum(all matching deltas))
```

Attunement preserves the tradeoff. With +20% Holy Attunement, the bundled +25% against undead becomes +30%, while -25% against players becomes -30%. Adding 50% Holy Mitigation reduces the latter penalty to -15%. Potency leaves penalties unchanged.

Apply each modifier separately before summing them. An entity matching both a +25% group and a -10% group receives both contributions; one membership does not override the other. Multipliers cannot turn damage into healing or healing into damage.

Typed healing negates each configured damage delta before applying the same Attunement/Potency/Mitigation rules. Thus the bundled -25% holy damage to players becomes +25% holy healing. Iron's native healing that emits `SpellHealEvent` and Mastery's typed `heal` actions use this path. Ordinary food regeneration and unrelated `LivingEntity.heal` calls retain their normal behavior. Native blood lifesteal that does not emit that school-healing event is not relabeled as holy healing.

## Default weapon classification

Path: `data/<namespace>/mastery/weapon_types/<id>.json`.

```json
{
  "items": [],
  "item_tags": ["minecraft:swords", "minecraft:axes"],
  "element": "mastery:slashing",
  "priority": 0
}
```

Each rule matches any listed item or tag. The highest `priority` wins; equal priorities sort by definition ID. Only one rule supplies the base type. Skill conversions take their shares first, then the winning rule supplies the remainder. Use a higher-priority item-specific rule to override a broad tag rule.

## In-game authoring

Enable `/mastery edit_mode true`, open **Definitions**, and choose **Elements**, **Mob types**, or **Weapon types**. The visual forms expose the corresponding fields and resource selectors. A skill's **Effects** list can add any of the attributes above, reference combat triggers, modify crafted items, and grant spells together.

Saves validate the complete definition set before replacing the active snapshot. `/mastery export` exports the accepted Mastery definitions, including these mappings, under their ordinary datapack paths. See [combat scripting](mechanics.md), [the editor](editor.md), and [spell rewards](spells.md).
