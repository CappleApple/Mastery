# Datapack reference

Place definitions under `data/<namespace>/mastery/<kind>/<path>.json`. The namespace and path form the ID. Higher-priority packs replace matching resources. Editor overrides use the parallel `data/<namespace>/masteryedits/<kind>/<path>.json` path and take precedence over base `mastery` definitions. Supported directories are `classes`, `trees`, `nodes`, `synergies`, `spells`, `contexts`, `xp_sources`, `requirements`, `effects`, `settings`, `elements`, `mob_types`, `weapon_types`, `triggers`, `keywords`, and the legacy organizational `groups` schema.

The [demo generator](../scripts/generate_demo.py) produces the 21 base trees, the promoted Flame Blade tree, and nine bindings to existing Iron's Spells. Run `python scripts/generate_mechanics_examples.py` afterward to add the 29 mechanics example nodes without overwriting existing definitions. `.\gradlew.bat demoDatapack` creates `build/distributions/mastery-demo-1.3.0.zip`, an editable standalone copy for a world's `datapacks` folder. Minecraft 1.21.1 uses datapack format 48.

Operators can export the accepted merged definitions with `/mastery export`. The resulting ZIP is saved in the calling client's `config/exports/` folder. Original namespaces and resource kinds are preserved; editor overrides become ordinary `mastery` resources, including disabled tombstones. See [exporting a datapack](editor.md#exporting-a-datapack) for naming and scope.

## Independent trees

`data/mastery/mastery/trees/two_handed.json`:

```json
{
  "name": "Two-Handed",
  "icon": "minecraft:iron_sword",
  "section": "west",
  "xp_base": 25,
  "xp_growth": 10,
  "point_every": 1,
  "points_per_award": 1,
  "tier_caps": [
    {"tier": 0, "max_level": 20, "max_rank": 3, "max_depth": 4},
    {"tier": 1, "max_level": 40, "max_rank": 5, "max_depth": 4}
  ]
}
```

A tree's maximum level is the first level whose cumulative point awards cover the maximum demand for its currency across all nodes and ranks, including nodes owned by other trees. AND costs add their demands; OR costs use the largest demand for that currency. Depth scaling is included. The budget is at least `sum(cost * max_rank)` across the tree's own nodes, including hidden and book-gated branches, so item- or XP-funded nodes retain their proficiency progression. The cap also reaches the highest explicit `level` gate on the tree's own nodes. See [purchase costs](costs.md#automatic-proficiency-caps). Legacy root `max_level` values are ignored and omitted from editor snapshots. Recalculation occurs on load, editor save, and Mastery reload; world-tier caps can temporarily limit progression below this maximum.

For nine spendable points, `point_every: 1` and `points_per_award: 2` gives a maximum of level 5; `point_every: 5` and `points_per_award: 1` gives level 45. Milestones and cumulative formulas contribute to the same calculation. Trees with no rank costs, or no level-based point awards, have a maximum of zero unless an explicit node level gate requires a higher cap. An insufficient finite schedule rejects the reload. Formula-driven cap searches support up to 100,000 levels; periodic/milestone schedules use the integer level range.

Every bundled tree gives one point per newly earned level. This is also the loader default. Points belong to their tree. Ordinary trees appear after the first point grant and stay visible after spending; they have no category parents. A promoted tree appears at its purchased root node and becomes usable when that root is eligible.

| Field | Behavior |
| --- | --- |
| `section` | Initial placement and outward growth: `north` (up), `northeast`, `east` (right), `southeast`, `south` (down), `southwest`, `west` (left), or `northwest`; default `south` |
| `xp_base`, `xp_growth` | Cost from level L to L+1 is `xp_base + xp_growth * L` |
| `xp_attribute` | Optional registered player attribute used as this tree's earned-XP multiplier; bundled trees have built-in defaults. See [experience modifiers](experience.md). |
| `point_every` | Levels between periodic awards; default `1`, zero disables periodic awards |
| `points_per_award` | Points per periodic award; default `1` |
| `point_milestones` | Optional level array awarding one point, or level-to-count object |
| `point_formula` | Optional cumulative formula, additive with periodic and milestone awards |
| `tier_caps` | Highest applicable tier entry; omitted limits or `-1` mean unlimited |

Formulas support `level`, `+ - * / %`, parentheses, `floor`, `ceil`, `round`, `abs`, `min`, `max`, and `pow`. For example, `floor(level / 5)` adds one point every fifth level. Cumulative results must start at zero and never decrease. Administrative level reductions do not let players earn previously awarded level points again.

Tier entries also accept `modifier_slots` and `active_capacity`. XP beyond a current level cap is discarded. See [configuration](configuration.md) for the world-tier provider and fallback. Organizational groups remain parseable for tooling but are not shown in the player map.

## Classes and promoted roots

Classes live at `data/<namespace>/mastery/classes/<path>.json`. They define a name, icon, starting tree points, pre-unlocked node ranks, item stacks, and persistent attribute modifiers. The enabled-by-default server selector temporarily places an unclassified player in spectator mode. See [player classes](classes.md) for the schema, saved reward behavior, and config toggle.

A node's optional `root_tree` object creates a separate proficiency and point currency while retaining the node's ID, prerequisites, and initial owning-tree costs. Same-tree descendants automatically join the new branch; an explicit generated tree ID resolves branches with multiple promoted parents. The generated root's XP curve, point rules, tier caps, and `xp_attribute` use the same fields as an ordinary tree. Define XP sources against its generated ID. See [promoted skill roots](roots.md) for authoring, nesting, cost gates, and the bundled Flame Blade example.

## Inherited settings and tree presentation

The global file is `data/mastery/mastery/settings/defaults.json`. Settings merge from built-ins to global defaults, tree, spell binding, then node. Missing values and `"default"` inherit, including individual nested fields. Editor overrides use `data/mastery/masteryedits/settings/defaults.json`.

```json
{
  "appearance": {"shape": "circle", "show_name": false},
  "connections": {"parent_line_style": "dashed", "child_line_style": "solid"},
  "theme": {"inner_color": "#D3AD5B", "outer_color": "#604522", "gradient": 1},
  "unlock": {
    "fill_direction": "vertical",
    "progress_sound": "minecraft:entity.experience_orb.pickup",
    "complete_sound": "minecraft:block.beacon.power_select",
    "hold_delay_ms": 100
  },
  "modifier_slots": {"base": 2, "per_level": 1, "max": 64},
  "charge": {"ticks_per_level": 20, "instant_base_ticks": 10, "max_levels": 16, "burst_interval_ticks": 3}
}
```

`connections.parent_line_style` controls incoming synergy and cross-tree prerequisite lines (default `dashed`). `connections.child_line_style` controls outgoing connections within a branch (default `solid`), including children of a promoted root such as Flame Blade. Each accepts `dashed`, `solid`, or `default` to inherit. Prerequisite edges use the destination node's parent style; branch edges use the source node's child style. Styles do not change after purchase. Both fields are available in the visual editor at global, tree, spell, and node scope.

Colors are six-digit RGB hex strings. `gradient` ranges from 0 (sharp midpoint) to 1 (full-width blend). Themes affect outer outlines and connector bands; internal borders retain passive/active/modifier styling. Unpurchased outlines are gray; disabled purchased nodes use dimmed theme colors. Middle-click a purchased node or use its details panel to toggle it without disabling downstream nodes.

`fill_direction` accepts `vertical`, `horizontal`, or `default`. Sound fields accept registered sound IDs, `none`, or `default`. The hold starts silently for `hold_delay_ms` milliseconds (default 100, range 0-60000), rounded up to the next client tick. It then shakes and fills for one second with eight rising-pitch progress sounds. Releasing or dragging during the initial delay produces no unlock feedback. The completion sound plays after the server confirms a rank increase. Tree fields can explicitly retain `default` in the editor.

`appearance.shape` accepts `circle`, `square`, `diamond`, `hexagon`, `pentagon`, or `triangle`. Roots default to pentagons, passives to circles, actives to rounded squares, and modifiers to triangles. The modifier type defaults to spell assignment and consumes modifier slots. Set `modifier: "mastery:tree"` for a tree modifier that activates without a spell slot. Other skill types default to circles; explicit global, tree, spell, or node shapes override those defaults. `appearance.show_name` defaults to `false`; enabling it prints the name below the icon. Each field accepts `"default"` to inherit. `appearance.scale` scales the node and icon; `appearance.root_scale` additionally scales roots, including promoted roots. The bundled data defaults are `1` and `1.3`, making roots 30% larger. Both accept 0.25-4. Hit areas and spacing follow the rendered size. The visual editor exposes these fields.

`effect_context` scopes attribute bonuses to a combat context. The bundled weapon trees use `mastery:one_handed`, `mastery:two_handed`, `mastery:dual_wield`, `mastery:bow`, or `mastery:crossbow`. Empty means any weapon; `"default"` inherits. Individual attribute effects can override this with `context`. Two-handed matching always requires Better Combat's resolved weapon data.

See [spell settings](spells.md#inherited-settings) for charge scaling and modifier-slot growth fields.

## Existing spell bindings

A binding references an existing Iron spell registry entry. Its resource ID and explicit `spell` field must match. For example, `data/irons_spellbooks/mastery/spells/fireball.json`:

```json
{
  "spell": "irons_spellbooks:fireball",
  "tree": "mastery:fire",
  "name": "Fireball",
  "icon": "minecraft:blaze_powder",
  "contexts": ["mastery:spells", "mastery:staff", "mastery:unarmed"],
  "level": 1,
  "modifier_slots": 2
}
```

`level` defaults to 1 and must fit the native spell's base level range. `modifier_slots` inherits global/tree growth defaults; a number overrides its base. `charge` adjusts hold time and existing native spell behavior. Iron owns spell implementation, mana, and cooldowns. Mastery does not accept `behavior`, `cooldown` or `parameters` fields in bindings. Reload rejects an ID absent from Iron's spell registry.

The bundled school-to-spell bindings are:

| School ID | Spell ID |
| --- | --- |
| `irons_spellbooks:fire` | `irons_spellbooks:fireball` |
| `irons_spellbooks:ice` | `irons_spellbooks:icicle` |
| `irons_spellbooks:lightning` | `irons_spellbooks:lightning_bolt` |
| `irons_spellbooks:holy` | `irons_spellbooks:guiding_bolt` |
| `irons_spellbooks:ender` | `irons_spellbooks:magic_missile` |
| `irons_spellbooks:blood` | `irons_spellbooks:blood_slash` |
| `irons_spellbooks:evocation` | `irons_spellbooks:fang_strike` |
| `irons_spellbooks:nature` | `irons_spellbooks:poison_arrow` |
| `irons_spellbooks:eldritch` | `irons_spellbooks:eldritch_blast` |

See [spell preparation](spells.md) for native equipment capacity and casting behavior. Prepared slots are stored on the player; changing them never inscribes or removes spells from an item.

## Nodes, modifiers and synergies

`data/mastery/mastery/nodes/fireball/efficiency.json`:

```json
{
  "tree": "mastery:fire",
  "name": "Fireball Efficiency",
  "type": "modifier",
  "spell": "irons_spellbooks:fireball",
  "modifier": "mastery:native_spell",
  "dependencies": ["mastery:fireball"],
  "cost": 1,
  "max_rank": 3,
  "toggleable": true,
  "effects": [{
    "type": "mastery:spell_modifier",
    "mana_multiplier": 0.9,
    "cooldown_multiplier": 0.9,
    "cast_time_multiplier": 0.9
  }]
}
```

An unlock node uses `spell` without `modifier`. It authorizes preparing the existing spell without requiring native item inscription. Modifier nodes must be purchased and enabled within the binding's modifier limit. `mastery:native_spell` reads `mastery:spell_modifier` effects: `spell_level` adds casting levels per effective rank; each multiplier is raised to that rank. Casting-level bonuses compose with Iron's native level adjustments.

Node types are `passive`, `active`, `keystone`, `capstone`, `modifier`, `synergy`, and `utility`. By default, each rank costs `cost` points from its owning tree. An inherited `costs` definition can replace that price with nested skill-point, Minecraft XP, and item costs; see [purchase costs](costs.md). Dependencies can be node ID strings or objects such as `{"node":"mastery:fireball","rank":1}`. Cross-tree dependencies respect the source tree's caps. `exclusions` are symmetric even if only one node lists the other. `level`, `world_tier` and `requirements` gate purchases.

`visibility` defaults to `available`; other values are `always`, `discovered`, `invested`, and `hidden`. An available node first appears after its prerequisites, level, world-tier, book, and external requirements are met, regardless of the current point balance. Its first reveal is remembered, so a later unmet requirement can disable its effects without hiding its revealed history. Visibility never reveals an undiscovered tree. Synergies use `mastery/synergies/` or `type: synergy` within `mastery/nodes/` and must reference at least two trees. Each player can move branches and trees; datapacks define placement sections and dependencies, not fixed X/Y coordinates. A dragged root grows outward from the fixed logical map center in its new compass sector. Panning does not change orientation. The **Sectors** button controls boundary-line visibility.

## Grouped prerequisites

The `dependencies` list uses AND between entries. Each entry can be a node/rank condition, an `and` group, or an `or` group. For example, Fire Practice AND (Fireball rank 2 OR Icicle rank 1):

```json
{
  "dependencies": [
    {"node": "mastery:fire/foundation", "rank": 1},
    {"or": [
      {"node": "mastery:fireball", "rank": 2},
      {"node": "mastery:icicle", "rank": 1}
    ]}
  ]
}
```

Groups can nest up to 32 levels. Empty groups, missing nodes, impossible ranks, and dependency cycles reject the reload. Each referenced node contributes a graph connection. Book gates on an unused OR route do not block another valid route. The visual editor provides **Add AND** and **Add OR** buttons in dependency lists.

## Repeated purchases and rank thresholds

Each purchase adds one rank up to `max_rank`, which defaults to 1 and accepts any positive 32-bit integer. Every rank pays its resolved `costs` expression, falling back to the node's `cost` points when no custom expression applies; active world-tier rank caps can impose a lower limit.

For example, a pack can replace `data/mastery/mastery/nodes/two_handed/foundation.json` with a ten-rank upgrade:

```json
{
  "tree": "mastery:two_handed",
  "name": "Two-Handed Practice",
  "max_rank": 10,
  "cost": 1,
  "effects": [{
    "type": "mastery:attribute",
    "attribute": "minecraft:generic.attack_damage",
    "amount": 1,
    "operation": "add_value"
  }]
}
```

A new pack-authored child at `data/mastery/mastery/nodes/two_handed/rank_five_bonus.json` can require five purchases of that parent:

```json
{
  "tree": "mastery:two_handed",
  "name": "Rank Five Bonus",
  "dependencies": [{"node": "mastery:two_handed/foundation", "rank": 5}],
  "cost": 1,
  "effects": [{
    "type": "mastery:attribute",
    "attribute": "minecraft:generic.max_health",
    "amount": 2,
    "operation": "add_value"
  }]
}
```

Both examples use the default `available` visibility. The child appears when the parent reaches an effective rank of five, including when the fifth purchase spends the player's last point. Purchasing the child still requires its own point cost. Editor mode shows all nodes regardless of these reveal gates.

## Consumable skill books

A node's `book_token` hides that node and its dependency descendants until the player learns the token. This gate is separate from `visibility`; the default `available` visibility also waits for the node's other prerequisites. Books do not grant ranks or skill points.

The bundled `mastery:fire/secret_studies` node uses `book_token: mastery:fire_secrets`. Its child, `mastery:fire/secret_capacity`, inherits the same gate. Give a matching consumable book with:

```mcfunction
/give @s mastery:skill_book[minecraft:custom_data={mastery_unlock:"mastery:fire_secrets"}]
```

Using the book learns its token and consumes one item in survival. A duplicate learned token or malformed token does not consume the book. Tokens persist in player data through death and reload. Resetting one tree preserves learned tokens; resetting all Mastery data clears them. The owning tree still needs its first point before it appears. Books are not equipment and do not supply spell capacity.

A Skill Book's cover displays the matching node's `icon`, chosen from its `mastery_unlock` token and the node's `book_token`. A node with no explicit book token can also match by node ID. Shared tokens prefer a matching ancestor before its descendants, then sort by node ID. Icon selection is independent of purchases and discovery. Unknown tokens or nonexistent item/texture IDs keep the plain book cover.

Both item IDs and `namespace:textures/...png` icons are supported. The book uses Minecraft's flat generated-item shape with a one-pixel base depth and a flat icon stamp on each cover. Resource packs can replace `assets/mastery/textures/item/skill_book.png` and the referenced icon resources.

The node editor's **Require Skill Book** toggle writes `book_token` and can copy the matching item command. See [the editor](editor.md).

Requirements can also check knowledge explicitly with `{"type":"mastery:book_unlocked","token":"mastery:fire_secrets"}`. Custom token IDs are allowed; pack authors can supply books through loot, recipes, commands or other reward systems.

## Requirements and effects

Inline objects and `{"ref":"namespace:path"}` references are accepted in node `requirements` and `effects`. References resolve to `data/<namespace>/mastery/requirements/<path>.json` or `data/<namespace>/mastery/effects/<path>.json`. Compose requirements with `and`, `or` and `not`.

| Requirement | Fields |
| --- | --- |
| `mastery:tree_level` | `tree`, `level` |
| `mastery:node_rank` | `node`, `rank` |
| `mastery:world_tier` | `tier` |
| `mastery:advancement` | `id` |
| `mastery:book_unlocked` | `token` |
| `mastery:condition` | Usage condition fields below |

| Effect | Fields and behavior |
| --- | --- |
| `mastery:attribute` | Existing `attribute` ID, `amount`, and `operation`: `add_value`, `add_multiplied_base`, or `add_multiplied_total` |
| `mastery:experience_gain` | `amount`, optional `tree`; rank-scaled earned-XP bonus for every tree or one tree. See [experience modifiers](experience.md). |
| `mastery:bonus` | `key`, `amount`, optional `spell` filter; consumed keys include `active_capacity`, `spell_slots`, `modifier_slots` |
| `mastery:unlock_spell` | Existing bound `spell` ID, `level` (default 1), `levels_per_rank` (default 0); multiple entries can grant different spells |
| `mastery:unlock_context` | `context` ID; relevant when that context requires unlocking |
| `mastery:spell_modifier` | Optional `spell` filter, `spell_level`, `mana_multiplier`, `cooldown_multiplier`, `cast_time_multiplier`; see [spell modifiers](spells.md) |
| `mastery:on_usage` | `event`, optional `condition`, then `ignite_ticks` or an existing `effect` with `duration`/`amplifier` |

Attribute and numeric bonuses scale with effective rank. Transient attributes rebuild from saved purchases and are removed when a node stops contributing. `mastery:fire/extra_slot` is a bundled example adding one prepared-spell capacity beyond native book/equipment capacity.

## Contexts

Contexts under `data/<namespace>/mastery/contexts/` define a `name`, integer `priority`, and `condition`. The highest-priority eligible match wins. Conditions support `item`, `item_tag`, `offhand`, `offhand_tag`, `blocking`, `empty`, `dual_wield`, and `and`/`or`/`not`. `requires_unlock: true` requires a matching `mastery:unlock_context` effect.

`mastery:two_handed` requires `condition: {"provider_only":true}`. Better Combat's resolved two-handed weapon attribute is its sole classifier. No axe or weapon tag substitutes for it. The bundled staff context uses the native `irons_spellbooks:staff` item tag. `mastery:spells` is the empty-condition fallback with priority -100. Contexts classify combat usage XP. Spell assignments use hotbar-position sets or the shared quick-cast set, independently of weapon contexts. The binding `contexts` list remains readable for legacy data.

## Usage XP

`data/mastery/mastery/xp_sources/fire_damage.json`:

```json
{
  "tree": "mastery:fire",
  "event": "damage",
  "amount": 1,
  "scale": "damage",
  "condition": {"school": "irons_spellbooks:fire"}
}
```

School XP follows the actual damage source. Mastery matches its damage type against each registered Iron school's damage type and emits that school's ID as `school`. Native spell damage also exposes `spell` when its damage source identifies the originating spell. The nine bundled school XP sources use this classification. Unlocked skills also credit their own tree through `skill_damage_xp`, even when their damage school differs. Casting without dealing damage, holding an item, or ordinary fire damage alone does not award school XP.

Other adapters emit `damage`, `kill`, `block`, `block_break`, `craft`, `smelt`, `brew`, `consume`, `travel`, and `advancement`. Integrations can emit events through `MasteryAPI.emitUsage`.

Earned XP composes the global `mastery:experience_gain` attribute, the selected tree attribute, and active `mastery:experience_gain` effects. These modifiers do not multiply direct skill-point grants or administrative XP commands. See [experience modifiers](experience.md) for defaults and the calculation. Locked promoted trees reject XP without consuming one-time source rewards.

`scale` defaults to `none`. Another value selects a numeric event field, such as `damage`, `distance`, or `amount`, which multiplies the source's `amount`; a missing field contributes zero. Optional `points` grants points directly. `once: true` permits one accepted grant per player until reset.

Conditions match exact event fields, including `school`, `spell`, `item`, `entity`, `block`, `damage_type`, `dimension`, and `biome`. Arrays match any listed value. Tag predicates are `item_tag`, `entity_tag`, `block_tag`, `damage_tag`, and `biome_tag`. Numeric bounds include `min_damage`, `max_damage`, `min_distance`, `max_distance`, `min_amount`, and `max_amount`; boolean fields include `projectile`, `critical`, and `blocking`. Conditions also compose with `and`, `or`, and `not`.

Projectile events preserve their launch weapon/context. Bundled physical weapon XP requires an empty school field so magic damage does not become melee or bow XP merely because of held equipment. Creative usage XP is disabled by default. These rules do not prevent farming repeated block placements or crafting; choose eligible content and rates accordingly.

## Disabling definitions and reload failures

`/mastery reload` and editor saves refresh only Mastery definitions, effects and player snapshots. They read the current resource manager plus live files under `<world>/datapacks/mastery-editor/data/<namespace>/masteryedits/`; they do not reload recipes, loot tables, tags or other mods' data. Standard server startup and Minecraft datapack reloads also recognize `masteryedits` overrides.

A higher-priority replacement resource containing `{"disabled":true}` removes that definition from the candidate graph. The normal required fields are unnecessary for a disabled file. Dependent resources must also be disabled or updated: references to removed nodes, trees and spells still fail validation. Keep an override in the original kind directory (`nodes` or `synergies`) even if its node's visual type changes. Editor overrides use `masteryedits` in place of the base `mastery` path.

Reload validates references, ranks, cycles, synergy trees, formula rules, native spell IDs and base levels, registered effects/modifiers, book-token syntax, context schemas, class reward references and item components, promoted root ownership, and registered XP attributes. A rejected candidate leaves the previous valid graph active. Accepted reloads reconcile missing nodes, modifiers, assignments and effects, interrupt Mastery-initiated casts, and synchronize definitions to online players. Routine player-state updates do not resend the graph.

## Damage, combat scripts, and crafting

- [Damage types](elemental.md): additive and converted damage, Aspects, registered attributes, overlapping mob types, weapon defaults, Attunement/Potency/Mitigation, and inverse healing matchups.
- [Combat scripts](mechanics.md): triggers, health conditions, action targeting, proc chance, keyword stacks, DOT, and interactions.
- [Crafting](crafting.md): persisted item attributes, food/potion improvements, placed-block comfort, and optional Needs Not Necessities providers.
- [Purchase costs](costs.md): nested AND/OR currencies, raw Minecraft XP, items/tags, and depth scaling.
- [Player classes](classes.md): join selection, starting packages, and class attributes.
- [Experience modifiers](experience.md): global and per-tree earned-XP multipliers.
- [Promoted roots](roots.md): independent currencies rooted in existing nodes.

All Mastery definition kinds participate in the same validated reload, world editor, synchronization, and export. Native Minecraft enchantment and damage-type registry JSON uses its own datapack paths and needs the normal datapack lifecycle.

`damage_filter`, `inherit_subtrees`, and `skill_damage_xp` also inherit through global, tree, spell, and node settings. Tree modifiers use the first two; automatic XP for an unlocked skill uses `skill_damage_xp` (default 1 XP per damage; zero disables). See [tree modifiers](mechanics.md#tree-modifiers) and [skill XP](experience.md#damage-from-a-trees-skills). Attribute effects may set `display_name` to override their bonus label without changing their numeric calculation.
