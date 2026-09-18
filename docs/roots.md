# Promoted skill roots

A node can start an independent specialization without losing its existing prerequisites, rank history, rewards, or purchase costs. Set `root_tree` on the node. The root is purchased using its existing owning tree or custom cost expression; purchasing it activates a separate XP bar and point balance for its branch.

The bundled **Flame Blade** root keeps the saved node ID `mastery:spellblade_practice`. It requires Two-Handed level 3, Fire level 3, and both foundation skills. Its generated tree is `mastery:flame_blade`. Outside its own skills, only two-handed melee Fire damage earns XP. Its own unlocked skills earn XP from their damage regardless of damage type. The root grants +10% Two-Handed Weapon Fire Damage. Flaming Strike raises its native base level from 1 to 5 with rank. Flaming Strike, Fire Infusion, Fire Conversion, Flame Edge, and Tempered Flame belong to its branch.

## In-game editing

1. Enable operator edit mode with `/mastery edit_mode true` and open a node's visual editor.
2. Open **Root tree**, enable it, and enter a distinct tree ID. Configure its XP curve, point schedule, and optional XP attribute.
3. Save the node. The generated currency becomes available in tree selectors for nodes, costs, XP sources, and starting class points.
4. Create an XP source for the generated tree in the definition browser. Existing sources for the old tree do not automatically award the new currency.

The node remains the visible root icon; there is no duplicate tree icon. Its outline shows the new tree's XP. Hover or open its details to see the separate proficiency level and points alongside the node rank, original requirements, and purchase price.

## Data format

`data/example/mastery/nodes/flame_blade_root.json`:

```json
{
  "tree": "mastery:fire",
  "name": "Flame Blade",
  "icon": "minecraft:blaze_powder",
  "dependencies": ["mastery:fire/foundation"],
  "cost": 1,
  "root_tree": {
    "id": "example:flame_blade",
    "xp_base": 25,
    "xp_growth": 10,
    "point_every": 1,
    "points_per_award": 1,
    "xp_attribute": "mastery:flame_blade_experience_gain"
  }
}
```

`root_tree: true` or an empty object uses defaults and creates `<node_id>_tree`, such as `example:flame_blade_root_tree`. `false`, `null`, omission, or an object with `"enabled": false` disables promotion. An enabled object accepts the ordinary [tree fields](datapacks.md), including point milestones, point formulas, tier caps, inherited settings, and placement section. Name, description, and icon default to the node's values. The default XP curve is 100 plus 20 per current level; maximum proficiency level is derived from purchase costs rather than a manually fixed cap.

Generated IDs must be valid resource locations and must not collide with another node or tree. A generated tree does not need a separate file under `trees/`.

`data/example/mastery/nodes/flame_edge.json`:

```json
{
  "tree": "mastery:fire",
  "name": "Flame Edge",
  "dependencies": ["example:flame_blade_root"],
  "max_rank": 3,
  "cost": 1,
  "effects": [
    {
      "type": "mastery:attribute",
      "attribute": "mastery:fire_weapon_damage",
      "amount": 0.1
    }
  ]
}
```

A descendant authored in the same original tree automatically moves to the promoted currency. Its dependency expression stays unchanged. This continues through the branch until another promoted root starts a new specialization. The first descendant has depth zero within the new tree for depth-based cost modifiers and tier depth caps; the promoted node itself retains its depth in its previous owning tree.

For an explicit owner, set the descendant's `tree` to the generated ID. Use this when a node depends on multiple promoted branches; an ambiguous inferred owner is rejected with a diagnostic. Dependencies on an unrelated tree remain cross-tree requirements and do not transfer ownership on their own.

`data/example/mastery/xp_sources/flame_blade_damage.json`:

```json
{
  "tree": "example:flame_blade",
  "event": "damage",
  "amount": 1,
  "scale": "damage",
  "condition": {
    "school": "irons_spellbooks:fire",
    "context": "mastery:two_handed",
    "melee": true,
    "projectile": false
  }
}
```

XP sources use the normal usage events and conditions. Global and per-tree XP modifiers also apply to a promoted tree. See [Experience modifiers](experience.md) for attributes and the data-defined modifier effect.

## Gates, costs, and saved progress

- The generated tree cannot earn XP or spend its stored points until the root and its prerequisites are eligible. A blocked one-time XP source remains unclaimed. Descendant effects also remain inactive while the gate is blocked.
- Classes or administrative grants can seed points before a root is unlocked. Those points remain stored and cannot fund purchases in another tree until the gate passes.
- A root's initial cost must have a payment route that does not require its own locked point currency. Existing costs keep using the previous tree. Custom XP, item, and nested AND/OR costs are supported.
- Resetting an original prerequisite suspends the promoted branch. It does not erase its existing ranks, XP history, or stored points. Restoring the prerequisite makes the branch available again, subject to current caps and requirements.
- Automatic promotion preserves node IDs. Exported snapshots retain authored owners and `root_tree` objects so reloading does not create duplicate generated tree files. Removing promotion moves automatically assigned descendants back to their authored tree; explicitly assigned descendants and XP sources must also be updated to reference an existing tree.
- Keep generated tree IDs stable after release. Changing an ID creates a different proficiency balance; it does not migrate the old tree's XP or points.

Each visible root displays its available points above the icon. Fire Infusion (`mastery:fire/weapon_aspect`) and Fire Conversion (`mastery:fire/conversion`) use Flame Blade points and retain their existing node IDs.

Tree modifiers inherit into promoted sub-trees by default. Fire modifiers reach Flame Blade while retaining their Fire damage filter. See [tree modifiers](mechanics.md#tree-modifiers).
