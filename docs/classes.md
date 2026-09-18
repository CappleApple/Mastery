# Player classes

Classes provide a one-time starting package and persistent attribute modifiers. Definitions live at `data/<namespace>/mastery/classes/<id>.json`. The default pack includes `mastery:warrior`, `mastery:ranger`, and `mastery:mage`.

With the server setting `classSelectorEnabled = true` (the default), players without a selected class choose one when they join. This also applies to existing players joining a world after the feature is installed. Choosing a class preserves their inventory and existing progression.

## Selection and server policy

The selector places a player preview between starting skills on the left and a portrait inventory on the right. An icon strip selects classes; class attributes appear below the player. Hover armor, items, or skill nodes for their tooltips. Selection is validated by the server. Players enter spectator mode while choosing and remain at their saved position; spectator camera targeting and movement cannot carry them away from that position.

A successful selection restores the previous game mode, including creative, adventure, or an existing spectator mode. Disconnecting during selection preserves the original mode and location. Rejoining resumes selection rather than treating the temporary spectator mode as the original mode.

Set `classSelectorEnabled = false` in the server config to disable mandatory selection. Pending players return to their previous game mode on their next server tick. An empty class definition set also releases pending players. This setting does not remove chosen classes, starter rewards, or class attributes. See [server configuration](configuration.md#server-policy) for config locations.

A full inventory never blocks class selection. Items that fit enter the main inventory; overflow remains saved in player progression and is delivered automatically when main-inventory slots become available. The player receives a message when items are waiting. Delivery retries once per second and on login. Queued items survive death, disconnects, server restarts, and progression resets. Items whose components cannot currently be decoded remain queued so reinstalling their required content can recover them.

## Definition

This example grants Fire Points, an unlocked Fire foundation, supplies, and additional maximum health:

```json
{
  "name": "Fire Adept",
  "description": "Begin with fire practice, supplies, and extra health.",
  "icon": "minecraft:blaze_powder",
  "starting_points": {
    "mastery:fire": 2
  },
  "starting_skills": {
    "mastery:fire/foundation": 1
  },
  "starting_inventory": [
    {"id": "minecraft:bread", "count": 8},
    {"id": "minecraft:wooden_sword", "count": 1}
  ],
  "attributes": [
    {
      "attribute": "minecraft:generic.max_health",
      "amount": 4,
      "operation": "add_value"
    }
  ]
}
```

Save it as `data/example/mastery/classes/fire_adept.json` to define `example:fire_adept`, then run `/reload`. The in-game editor's Classes library can create and edit the same definition.

| Field | Default | Behavior |
| --- | --- | --- |
| `name` | Definition ID | Display name. |
| `description` | Empty | Explanation shown during selection. |
| `icon` | `minecraft:book` | Item icon, native spell icon, or loaded texture resource, using the skill icon formats. |
| `starting_points` | `{}` | Map of tree IDs to nonnegative integer point grants. Each tree keeps its own currency. |
| `starting_skills` | `{}` | Map of node IDs to granted ranks, from 1 through each node's `max_rank`. Existing higher ranks are preserved. |
| `starting_inventory` | `[]` | Native item-stack definitions, with optional item components. |
| `attributes` | `[]` | Attribute modifiers active while this class definition is present. |

Starting point and skill maps support up to 1,024 entries each. Inventory and attribute arrays support up to 128 entries each. Point grants cannot overflow the integer point balance.

## Starting skills

Starting skills bypass purchase prices. Include the required prerequisite ranks in `starting_skills`; AND/OR prerequisites use their normal logic. Mutually exclusive starting skills are rejected, as are conflicts with skills a player already owns when selecting the class.

The grant discovers each affected tree, enables the granted skill, unlocks its skill-book gate if present, and raises the tree to the skill's required proficiency level when necessary. These starting levels do not award milestone points; use `starting_points` to specify the starting balance. World-tier limits and external requirements still restrict the skill's effective behavior normally.

A node can grant multiple passives or spell unlocks as usual. Class definitions grant the node's rank, so changes to that node's effects use the normal skill runtime. Unlocked active spells still need to be assigned to casting slots, and spell modifier nodes still need to be equipped within the normal modifier capacity.

## Starting item components

Each inventory entry uses Minecraft 1.21.1's native item-stack codec:

```json
{
  "id": "minecraft:diamond_sword",
  "count": 1,
  "components": {
    "minecraft:custom_name": "\"Starter Blade\"",
    "minecraft:enchantments": {
      "levels": {
        "minecraft:sharpness": 2
      }
    }
  }
}
```

`count` defaults to 1 and must be between 1 and 99. Stacks are split according to the item's actual maximum stack size. The native component format supports names, enchantments, potion contents, and components registered by other installed mods. Component IDs and values must be valid for the running server; malformed values reject the definition reload.

The first starter armor item for each armor slot equips automatically. Displaced gear enters the main inventory, or the saved overflow queue when full. Additional armor for the same slot stays in inventory. Offhand contents remain equipped. Items merge only when their item and components match. The preview uses the same placement rules as the server.

## Attribute modifiers

Every attribute entry requires `attribute` and a finite `amount`. `operation` defaults to `add_value`; the other supported operations are `add_multiplied_base` and `add_multiplied_total`. Attribute IDs must exist in the running registry. An attribute absent from the player's attribute container has no effect.

Class modifiers are separate from skill and equipment modifiers. They are rebuilt on login, respawn, and definition reload, without granting starting rewards again. Editing a class's attributes changes its current players' class modifiers after reload. Removing a class definition removes its modifiers while retaining the selected class ID and one-time reward receipt; restoring the definition restores its modifiers.

## Persistence and resets

Class choice, the reward receipt, overflow items, and any pending selector recovery state are saved in the player attachment. Starting points, skill ranks, and inventory are granted only on the initial successful choice. Relogging, dying, reloading definitions, and editing the class do not regrant them.

The normal Mastery progression reset commands preserve class choice, its attribute modifiers, the one-time reward receipt, and queued items. Resetting a tree or all progression can remove the originally granted points and skills. It does not reopen class selection or duplicate inventory. There is currently no class-change command.
