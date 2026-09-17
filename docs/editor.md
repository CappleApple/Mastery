# In-game editor

Operators can enable editing for themselves:

```mcfunction
/mastery edit_mode true
```

Open Mastery with **M**. The same map displays every tree fully expanded, including undiscovered and book-gated branches. Positions use the default sections, orientation, and deterministic order. Personal node placement and purchase history are not used for this view. Pan, zoom, **Fit map**, and the **Sectors** toggle remain available; node dragging is disabled while editing.

Right-click a tree or node to open its menu. Available actions include editing its definition, adding a child, adding a tree, and deleting the selected definition. Definitions open as visual forms. Search for a setting, open nested groups, choose IDs from searchable lists, and edit numbers, text, booleans, and colors through typed controls. Arrays support adding, removing, and moving entries upward. **Reset** removes an override so it inherits or uses its default. **Add field** supports extension fields and named milestone levels; value-type controls can create nested objects and lists.

The **Definitions** browser exposes trees, nodes, spell bindings, global settings, XP sources, reusable requirements/effects, combat contexts, and legacy groups. New spell bindings choose an existing Iron registry spell; they do not register spells. Global settings use the fixed ID `mastery:defaults`.

The **JSON editor** uses colored keys, strings, numbers, literals, and brackets, with native text selection, clipboard shortcuts, and a **Format JSON** button. Switching between JSON and visual forms preserves the current draft. **Save to world** or **Ctrl+S** validates and submits the entire definition; nested **Done** buttons only return to the enclosing form. Rejected saves retain the draft. See the actual schemas in [datapacks](datapacks.md).

For node definitions, **Require Skill Book** updates the JSON `book_token` field. Enabling an empty gate uses the node's resource ID as its token. You can edit that field to share a custom token with another branch; turning the toggle off clears the field's value. **Copy book command** copies the `/give` command for a matching generic `mastery:skill_book`. The JSON view also includes the book controls; they follow manual JSON changes and disable while JSON is malformed.

Tree forms include **Theme** (external border and connector colors/gradient) and **Unlock** (fill direction and sounds). The JSON view also offers compact theme and sound controls above the text. **Defaults** edits global inherited settings; **Edit spell upgrades** opens a node's native spell binding. Charge and modifier-slot groups can be overridden globally, by tree, by spell, and by node. Missing values inherit, and literal `default` is retained.

Tree maximum levels are calculated from all node rank costs and the tree's point schedule. Edit `point_every` and `points_per_award` to change the award rate.

Dependency lists provide **Add AND** and **Add OR** controls; groups can contain further groups or node/rank entries.

Repeated purchases use `max_rank`; child prerequisites use the dependency's `rank` field. See the [rank-threshold example](datapacks.md#repeated-purchases-and-rank-thresholds). Editor mode shows those children even before their rank or book requirements are met.

Edits save to `<world>/datapacks/mastery-editor/data/<namespace>/masteryedits/<kind>/<path>.json`. For example, editing `mastery:fire/foundation` writes `data/mastery/masteryedits/nodes/fire/foundation.json` inside that datapack. These overrides take precedence over bundled `mastery` definitions.

Saves and `/mastery reload` refresh only Mastery data. Recipes, tags, loot tables, and other mods are not reloaded. The editor datapack is selected for subsequent world loads. Deleting a resource writes a `disabled: true` override; deletion is rejected while other definitions still reference it.

The server checks operator permissions and edit-mode membership for every request. It validates the full resulting graph, including dependencies and native spell IDs, before writing. Stale edits are rejected if the definitions changed after opening the form. Failed reloads restore the previous files.

Disable editing with:

```mcfunction
/mastery edit_mode false
```

The command has no player argument and affects only its caller. Editor mode also ends on logout or loss of operator permissions. Leaving it restores the player's normal map and saved layout. Changes to definitions apply to the world, while the editor view is private to the operator.

## Exporting a datapack

Run `/mastery export` as an operator to download the last accepted Mastery definitions to your client. Edit mode is not required. The client saves `config/exports/mastery-HH-mm-dd-MM-yyyy.zip` using its local time; repeated exports in the same minute receive `-2`, `-3`, and later suffixes. Chat shows the saved path.

The ZIP contains `pack.mcmeta` and the merged definitions from loaded packs and accepted editor overrides. Files use `data/<original-namespace>/mastery/<kind>/<path>.json`; `masteryedits` is flattened into that base format. Disabled-definition overrides remain in the export so removed bundled content stays disabled.

Exports include Mastery definitions, including those provided by other namespaces. They do not copy unrelated datapack resources, client assets, player progression, or personal layouts. The server sends the archive directly to the requesting client without saving a server-side copy or reloading data. Unsaved edits and rejected reloads are not exported.

## Visual combat scripts

The **Definitions** browser also includes `elements`, `mob_types`, `weapon_types`, `triggers`, and `keywords`. Trigger and keyword definitions open a connected card view. The **Settings** button opens the same definition as a typed form; **JSON editor** remains available. All three views share one draft and use the same server validation.

For a combat trigger, the columns read **WHEN → IF → THEN**:

1. Choose **hit**, **kill**, **hurt**, or **death** in the event card. `hurt` and `death` belong to the skilled player; `hit` and `kill` belong to the target they attacked.
2. Set **Chance** and **Cooldown**. Chance `0.25` means 25%; cooldown `20` is one second. The player's `mastery:proc_chance` adds to the chance, clamped to `[0, 1]`.
3. Choose **+ Condition**, then **health** or **keyword**. Health can inspect the player (`self`) or event target (`target`), in points or a fraction of maximum health. `max: 0.3` with `unit: fraction` means at most 30%. Bounds include their endpoints; all conditions must pass.
4. Choose **+ Action**, select its type, and fill in its typed fields. Click an existing action card to edit it. Use `^` to move an action earlier and `-` to remove it. Actions run in list order.
5. Save the trigger, then add a `mastery:trigger` effect to an active skill and choose its `trigger` ID. Unreferenced trigger definitions are inactive.

Hit and hurt health conditions use health after the triggering hit. Within an action, conditions inspect the selected recipient. An action can have its own chance and condition list.

| Action | Required or useful fields |
| --- | --- |
| `keyword` | `keyword`, `stacks`, optional `duration` override |
| `remove_keyword` | `keyword`, `stacks`; `0` removes all stacks |
| `damage` | `amount`, optional `element` or native `school`, `damage_fraction`, `per_stack`, `per_rank` |
| `effect` | `effect`, `duration`, `amplifier` (`0` is level I) |
| `spell` | Native Iron `spell` ID and `level` |
| `heal` | `amount`, optional `per_stack` and `per_rank` |
| `lightning` | Controlled `amount` of damage with lightning presentation |
| `particles` | Simple `particle` ID, `count`, `spread`, `speed` |

Targets are `self`, `target`, `nearby`, or `aim`. For `hurt` and `death`, the event target is the attacker; for `hit` and `kill`, it is the victim. Nearby actions use `center: target` by default or `center: self`, plus `radius` and `limit`. Players and allies are excluded from area selection unless enabled; PvP permissions still apply. The particle picker includes simple particle types that do not need additional parameters. Parameterized particles such as dust are unsupported.

### Keywords and interactions

A keyword is stack state attached to an entity. Its card view switches between **Periodic actions** and **Threshold actions**. Settings expose `name`, `max_stacks`, `duration`, `tick_interval`, `threshold`, and `consume_stacks`.

Periodic actions run every `tick_interval` ticks while the keyword lasts. A damage action with `per_stack: true` creates a stacking DOT. Threshold actions run when application reaches `threshold`; `consume_stacks: true` removes the threshold amount. Without consumption, a threshold fires once on crossing from below and rearms when stacks drop below it. Reapplication refreshes the keyword's lifetime. Durations are ticks, at 20 ticks per second.

For scorch, apply a keyword to the victim on hit, deal fire damage periodically, then use nearby damage plus explosion particles at the threshold. For thunder, apply the keyword to `self` on hit, then use nearby lightning centered on `self` at the threshold. These explosions are action sequences and do not damage terrain.

The bundled `mastery:scorch` and `mastery:thunder` keywords and their `mastery:scorch_on_hit` and `mastery:thunder_on_hit` triggers provide starting examples. They only activate after a skill references the trigger. Create interactions by adding a keyword condition to an action, followed by `remove_keyword` when consumption is desired.

Proc spells run one native activation without spending mana, native cooldown, or an unlock; the trigger controls chance and cooldown. Native preconditions still apply. Continuous spells receive one activation instead of a held channel.

Damage from script actions or proc projectiles does not recursively start hit/hurt chains. Actual kills and deaths from those actions can still run their distinct kill/death triggers once. Temporary keyword state and cooldowns are cleared when Mastery definitions reload or the server stops. Each action list accepts at most 64 entries, and each condition list at most 32. Nearby radius and target count are bounded to 64; stack counts to 1,024; keyword duration, intervals, and trigger cooldowns to 72,000 ticks. The server rejects invalid references and unsupported native spells or particles.

### Elemental skills and damage groups

Edit `elements` to map existing school/damage-type IDs to enchantments and registered attributes. `damage_per_level: 0.1` adds 10% weapon damage per enchantment level. Aspects add damage; `mastery:<type>_conversion` moves a fraction out of physical damage. `mastery:<type>_weapon_damage` adds a damage fraction, `mastery:<type>_damage` boosts that type, and `mastery:elemental_damage` boosts all elements and native Iron schools.

Use `mastery:attribute` effects to grant these bonuses. With `add_value`, `amount: 0.2` grants a 20% fraction per active rank. An `elements` mapping cannot register a new attribute or enchantment on reload; those IDs must already exist.

`mob_types` defines named entity groups from entity IDs and entity tags. An element's `damage_modifiers` maps group IDs to signed damage fractions: `0.25` means +25%, and `-0.25` means -25%. Open the map and **Add field** to choose a mob-group definition; its value is a number. `attunement_attribute` identifies the registered attribute that amplifies both the bonuses and penalties: +20% attunement changes +25% to +30% and -25% to -30%. Healing reverses the group modifier before applying attunement. `potency_attribute` increases positive modifiers only; `mitigation_attribute` reduces negative modifiers only. Multiple matched groups add their adjusted deltas, while multiple selectors within one group count once. `weapon_types` associates item IDs/tags with an element and a priority for the weapon's unconverted damage. See [damage types and elemental damage](elemental.md) for formulas and installed defaults.

### Crafted items and placement

Crafting abilities are node effects, edited through the same typed effect forms. Choose a crafting effect type, fill its fields, and set optional item ID/tag filters and chance. Reset an unused filter instead of leaving an empty string.

| Effect | Purpose and fields |
| --- | --- |
| `mastery:crafting_attribute` | Persist an attribute modifier on the output: `attribute`, `amount`, `operation`, `slot`. Amount scales with active rank. |
| `mastery:crafting_food` | Percentage increases to `nutrition_bonus`, `saturation_bonus`, `buff_strength_bonus`, and `buff_duration_bonus`. Optional meal bonuses use `meal_strength_bonus` and `meal_duration_bonus`. |
| `mastery:crafting_potion` | `amplifier_bonus` adds strength tiers; `duration_bonus` adds a duration fraction. Optional `added_effect`, `added_duration`, and `added_amplifier` append an effect. |
| `mastery:placed_comfort` | Extra comfort from blocks placed by the skilled player: `amount`, `radius`, `comfort_type`, optional `block`/`block_tag`. |

Percentage bonuses are fractions per active rank: `0.25` means +25%. Potion amplifiers are integer tiers. Crafting modifiers stay on the made item when another player receives it. Chance attempts are marked on the output to prevent retrying extraction for a better roll. Player-owned crafting, furnace extraction, stonecutting, smithing, and brewing extraction have integration paths; automated production without an identifiable player has no producer skills to apply.

Needs Not Necessities is optional. When installed, Mastery can supply placed-block comfort and alter its meal buffs. Its absence does not disable the ordinary attribute, food, or potion crafting effects. See [crafting and comfort](crafting.md) for persistence and production-path details.

## In-game reference

With Patchouli installed, the **Operator Workshop** category appears in the Mastery Field Guide while the server has enabled your edit mode. It covers the editor map, prerequisites, inherited settings, elemental damage, script events, health checks, action targeting, stacking keywords, scorch/thunder examples, crafting, placement, validation, and export. Turn edit mode off to hide the category. No external wiki is needed for these workflows.


### Several spell rewards on one skill

A node's **Effects** list can contain several `mastery:unlock_spell` entries alongside attributes, triggers, or production bonuses. Each grant specifies `spell`, `level`, and `levels_per_rank`. After the first rank, the reward level is `level + levels_per_rank * (rank - 1)`; the native binding's base level remains a floor, and the result is capped at 255. The spell must already have a Mastery binding. Node details list all grants, and **Spell bindings** offers a picker when there is more than one.

Add `mastery:spell_modifier` effects to an enabled ordinary skill to change its owned spells automatically. Set `spell` to limit one effect to a particular spell; **Reset** removes that filter so it applies to all owned spells. Multiple matching entries compose. `spell_level` adds per active rank, while mana, cooldown, and cast-time multipliers compound per rank. Existing slot-equipped modifier nodes still work. See [spells](spells.md) for full examples.


## Icon resources and XP outlines

Open a tree or node's **Icon** field and choose **Choose...**. The icon browser previews PNG resources under `textures/` from every loaded namespace, including resource packs. Its type button cycles through **Textures**, **Items**, and native **Spells**; search matches resource IDs and paths. Select an entry, then **Apply** and save the definition.

An icon accepts a registered item ID, a loaded PNG resource ID such as `mastery:textures/gui/sprites/graph/rune.png`, or an installed native spell ID. Loaded PNG IDs outside `textures/` can also be entered directly in the Icon field. A raw texture uses the complete image scaled into the icon square. Both the skill map and matching Skill Book covers use the same resolution rules. Resource files must be installed on each client; exporting Mastery definitions does not copy image assets. Reloading resource packs refreshes the available resources.

In player mode, each specialization root shows current-level proficiency XP as a clockwise outline beginning at the top. The outline follows its configured circle, square, diamond, or hexagon, and is full at the current level cap. Its ratio is current XP divided by XP needed for the next level. Editor mode retains the normal presentation border while authoring.

## Nested purchase costs

Trees, nodes, and global defaults expose **Costs** and **Cost depth percent**. Reset **Costs** to inherit a parent cost rule or fall back to the node's legacy point cost. A cost override replaces the inherited rule as a whole. Inside **Costs**, **Legacy point cost** explicitly restores the node's numeric point cost even when its parent defines custom costs.

A leaf can spend specialization `points`, raw vanilla `experience` points, or inventory `item` quantities. Points default to the node's own tree; choose another **Tree** to spend another specialization's currency. An item cost uses an **Item** or **Item tag** selector. Reset the unused selector.

Use **Use AND** to require all children or **Use OR** to choose an affordable alternative. Open the resulting group, then use **Add entry**, **Add AND**, or **Add OR** to nest additional costs. For example, an AND group can require points from two trees and an OR group containing either vanilla XP or a diamond. List order determines the preferred alternative among affordable complete plans. The server checks a whole payment plan before deducting anything.

**Cost depth percent** adds a fraction per node depth and rounds each leaf cost up: `0.1` means +10% per depth. Nodes without dependencies have depth zero; each dependency link adds one along the longest path. Visual placement does not affect the price. A leaf's **Depth percent** overrides the inherited value for that cost alone. The node's displayed cost and purchase eligibility use the resolved rule. See [purchase costs](costs.md) for the complete schema and payment/refund behavior.
