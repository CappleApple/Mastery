# Mastery

Mastery adds usage-based proficiency, attribute upgrades, and a draggable skill map for **Iron's Spells 'n Spellbooks**, **Better Combat**, and **Apothic Attributes** on Minecraft 1.21.1.

Mastery modifies existing spells and assigns them to hotbar or shared quick-cast slots. Iron's owns the spells, projectiles, damage types, mana, casting, and cooldowns. Mastery adds typed weapon damage, data-defined combat triggers and keywords, crafting bonuses, and three physical damage types while reusing Iron's native school damage and spells.

## Progression and map

- Each specialization has its own XP, level, and points. The bundled rules award **one point every level**. Each tree's maximum level is calculated from all rank costs and its point-award schedule.
- School XP comes from damage actually dealt with an Iron's registered school damage type. Ordinary vanilla fire damage does not count as Fire school damage.
- Trees appear when you first receive a point. Spending the last point keeps them visible.
- Nodes can be purchased repeatedly up to their rank limit. Children support required ranks and nested AND/OR parent groups; the default view reveals them when their prerequisites are first met.
- Press **M** to open the single map. Drag the background to pan; scroll to zoom; right-click a node to expand or collapse it. Left-click selects its details; hold to buy a rank. Middle-click toggles an unlocked node. Press **F** to center the selected node.
- Drag roots or individual children. Descendants follow their parents, with closer parents exerting more influence on shared children. Child positions save as relative offsets.
- Trees start in their configured compass sector. Dragging a root sets outward growth from the fixed map center in eight directions, including diagonals. Panning and zooming do not change that direction. The **Sectors** button toggles the boundary lines.
- Nodes default to circles showing only their icons. Shapes and optional names inherit global, tree, and node settings.
- Zoom out to 0.01% while nodes retain a configurable minimum screen size and stay separated. Root tooltips show level, points, and XP; skill-node tooltips show rank and stat bonuses; proficiency level-ups play a sound and show a HUD toast.
- Root XP fills around the selected circle, square, diamond, or hexagon outline. Skill icons can use item, native spell, or resource-pack images.
- Purchase costs support nested AND/OR choices across several trees' points, raw Minecraft XP, and items, with optional depth scaling.
- Tree themes customize outer outlines and connections. Purchased nodes can be disabled independently through their details panel.
- Layouts save locally per client, world, and player, including the camera and expanded branches.

Weapon-tree attribute bonuses apply only while their configured combat context is active. Bow damage and crossbow velocity bonuses use Apothic Attributes.

Two-handed weapon classification comes exclusively from Better Combat's resolved weapon attributes. There is no axe or item-tag fallback for two-handed XP.

## Spells and equipment

Purchase a node for an existing Iron's spell, then open **Spells** to assign it. Choose a separate spell set for each hotbar position or one shared quick-cast set. Defaults are **Q**, **E**, **F**, and **C**, remappable in Minecraft Controls; **[** and **]** change pages when capacity exceeds four. Unassigned keys keep their ordinary behavior. Iron's standard HUD shows the active set.

Equipped native spellbooks and eligible items provide assignable capacity and their normal stats. Mastery upgrades can add capacity. **Assignments are stored in Mastery's player data, never inscribed into item slots.** Existing book contents are preserved. Native spellbook and sword bindings cannot bypass Mastery's progression; scrolls remain native consumables.

The bundled spell upgrades modify native spell level, mana cost, cooldown, or cast time. Repeated spell ranks can extend charging: Fireball grows with hold time, and Firebolt has a native burst preset. Spell levels can unlock more modifier slots. Attribute nodes use existing Minecraft and Iron's attributes. See [spell slots and upgrades](docs/spells.md).

## Damage, triggers, and crafting

- School Aspects I-X add 10% weapon damage per enchantment level. Fire Aspect now adds fire damage without igniting; all Aspects support bows and crossbows. Only one Aspect can be applied per weapon by default.
- Skills can add elemental damage or convert a share of base damage. Global and per-type power affect weapons and native spells.
- Slashing, piercing, and blunt defaults, overlapping mob groups, and damage/healing matchups are data-defined. Attunement strengthens both sides of a matchup; Potency and Mitigation adjust the upside and downside separately.
- On-hit, on-kill, being-hit, and death triggers support proc chances, health thresholds, keywords, DOT, stack thresholds, nearby or aimed targets, and native spell activations.
- Active crafting skills can improve item attributes, food, and potions. Optional **Needs Not Necessities** integration adds placed-block comfort and crafted-meal bonuses; it is not required.

See [damage types and matchups](docs/elemental.md), [combat scripting](docs/mechanics.md), and [crafting](docs/crafting.md).

## Skill books and editing

Consumable `mastery:skill_book` items unlock hidden branches through persistent tokens. Their flat book covers display the matching node's icon. They are not equipment and do not inscribe spells. Pack authors choose the token and gated nodes; see [skill-book data](docs/datapacks.md).

Operators can use `/mastery edit_mode true` to enable editing for themselves. This view shows every tree fully expanded in its default layout, including book-gated branches. Right-click nodes to edit definitions, add children or trees, and delete definitions. Visual forms cover nested settings, dependencies, requirements, effects, and lists. The Definitions browser also exposes XP sources and reusable definitions. A syntax-colored JSON view remains available. Saves write world-specific `masteryedits` overrides and refresh only Mastery data. `/mastery edit_mode false` restores the normal view. Use `/mastery export` to save the accepted definitions as a datapack ZIP in your client's `config/exports/` folder. See [the editor](docs/editor.md).

## Installation

Download the mod JAR from [GitHub Releases](https://github.com/CappleApple/Mastery/releases).

Install Mastery on both client and server with:

- Minecraft **1.21.1**, NeoForge **21.1.249 or newer in the 21.1 series**, and Java **21**.
- Iron's Spells 'n Spellbooks **1.21.1-3.16.3**.
- Better Combat **2.4.x**.
- Apothic Attributes **2.10.1 or newer in the 2.x series**, plus Placebo **9.9.0 or newer**.
- Their required dependencies: Curios, GeckoLib, Player Animator, Iron's Lib, and Cloth Config.

**Patchouli is optional.** When installed, the [Mastery Guide](docs/guidebook.md) explains progression, equipment capacity, spells, and the map. Craft it with a book and paper. Mastery works without Patchouli.

## Data and commands

The bundled data provides physical and crafting proficiency trees alongside Iron's nine schools. Pack authors can replace progression rules and define upgrades for registered spells without Java. Classes are not bundled; integrations can grant starting points.

- [Datapack reference](docs/datapacks.md)
- [Purchase costs and depth scaling](docs/costs.md)
- [Configuration and commands](docs/configuration.md)
- [Extension API](docs/api.md)
- [Architecture](docs/architecture.md)
- [Validation](docs/validation.md)

An operator can reveal a starting tree with:

```mcfunction
mastery points add @s mastery:fire 1
```

Use `/mastery reload` after changing Mastery definitions. It also reads freshly written editor overrides without reloading recipes, tags, loot tables, or other mods. Rejected definitions leave the previous accepted graph active.

## Building

```powershell
.\gradlew.bat test build
.\gradlew.bat runGameTestServer
```

Java package: `com.cappleapple.mastery`. Output: `build/libs/mastery-1.2.1.jar`. Use `./gradlew` on other platforms.

`build/distributions/mastery-demo-1.2.1.zip` contains the bundled datapack for customization. Regenerate its definitions with `python scripts/generate_demo.py`; then add the mechanics examples with `python scripts/generate_mechanics_examples.py`; GUI sprites with `python tools/generate_gui_assets.py`; the Skill Book base texture with `python tools/generate_item_assets.py`.

## License

All rights reserved; an open-source license has not been selected. See [LICENSE](LICENSE).
