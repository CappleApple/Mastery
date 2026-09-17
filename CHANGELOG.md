# Changelog

## 1.2.1 - 2026-09-17

### Changed

- Aspect enchantments now conflict with one another, allowing only one Aspect per weapon by default. Datapacks can override the shared enchantment conflict tag.

## 1.2.0 - 2026-09-17

### Added

- Data-defined elemental bonus damage, damage conversion, physical damage types, default weapon classifications, overlapping mob groups, and inverse healing matchups.
- Per-type Attunement, Potency, and Mitigation skill attributes.
- Combat triggers with proc chances, health thresholds, keyword stacks, damage over time, nearby targeting, native spell activations, and stack interactions.
- Crafted weapon, food, and potion bonuses, plus optional Needs Not Necessities comfort and meal integration.
- Visual combat scripting and an edit-mode Patchouli workshop.
- Combined skill rewards with multiple spell unlocks, rank-based spell levels, and stacked passive modifiers.
- Nested AND/OR purchase costs using multiple skill currencies, Minecraft XP, and items, with optional depth scaling.
- Searchable resource-pack icon previews and shape-following root XP outlines.

### Changed

- Fire Aspect I-X adds elemental damage without igniting and supports ranged weapons; eight additional school Aspects use the same level scaling.

## 1.1.2 - 2026-09-17

### Changed

- Expanding nodes can overlap during their animation, matching collapsing nodes.
- Batched node borders and tree connections to reduce rendering overhead.
- Focus scrolling and hold-to-level fill update each frame instead of stepping at 20 ticks per second.

## 1.1.1 - 2026-09-16

### Added

- Automatic tree level limits derived from rank costs and point-award schedules.
- Nested AND/OR prerequisite groups with visual editor controls.
- Required Apothic Attributes integration for ranged damage and velocity bonuses.

### Changed

- Nodes default to icon-only circles, with optional square, diamond, or hexagon shapes and names.
- Extended zoom-out to 0.01% while retaining the zoom-in limit.
- Skill-node tooltips show stat bonuses; tree XP and point balances appear only on roots.
- Active spell details include Iron's Scroll Forge descriptions.
- Weapon-tree attribute bonuses apply only while using the matching combat context.
- Reduced the default minimum node width to 20 GUI pixels.
- Hold-to-level feedback waits 500 ms by default, then plays eight rising dings and beacon power-select on completion. The delay supports global, tree, and node overrides.
- Collapsing nodes can overlap during their animation.
- Hotbar bindings show four slots per page with their current controls below them.
- Charged spells show a filling percentage bar with charge-level segments.
- Middle-click toggles an unlocked node without changing its descendants.

### Fixed

- Spell icons remain visible in bound slots and the spell picker without hovering.
- Newly unlocked nodes start enabled; new modifiers activate when their spell has room.

## 1.1.0 - 2026-09-16

### Added

- Tree outline and connection themes with inner/outer colors and adjustable gradients.
- Hold-to-level feedback with shaking, directional fill, rising sounds, and a completion sound.
- Hotbar-position spell sets, shared quick-cast mode, and native spell-slot textures.
- Native spell charge upgrades and level-based modifier-slot growth with global, tree, spell, and node defaults.
- Visual definition forms, nested controls, searchable ID pickers, and a syntax-colored JSON editor.
- Skill level-up HUD toasts and sound.
- Remappable F shortcut to center the selected node.
- Deep zoom with configurable minimum node size and overlap prevention.
- Client settings for branch animation speed and enabled state.

### Changed

- Iron's standard HUD now shows the active spell set.
- Right-click expands or collapses nodes; short left-click selects details.
- Purchased nodes can be disabled independently without disabling descendants.
- Shared children respond more strongly to nearby parents; root orientation changes carry custom placements.
- Collapsed connections follow the nearest visible prerequisite ancestors.
- Hover tooltips show only name, level, points, and XP.

### Removed

- Center toolbar button and map-center label.
- Weapon-type spell binding selection and generic declined-cast messages.

## 1.0.1 - 2026-09-16

### Added

- Consumable Skill Books that reveal hidden branches and preserve learned tokens through death.
- An operator-only editor for tree and node definitions, with a separate fully expanded view and world-specific overrides.
- Skill Book covers display the matching node icon on a flat, resource-pack-replaceable book model.
- A **Require Skill Book** editor toggle and a command-copy button for matching consumable books.
- `/mastery export` saves merged definitions and accepted editor overrides as a datapack ZIP on the calling operator's client.
- Eight compass growth directions and a toggle for map sector boundaries.
- An optional Patchouli field guide covering progression, native spells, equipment capacity, and map controls.

### Changed

- Spell unlocks and upgrades now use existing Iron's Spells. Iron's Spells and Better Combat are required.
- Nodes now default to prerequisite-based discovery, with repeated purchases up to their configured rank and child unlocks at specified parent ranks.
- School XP follows actual registered school damage types, and every bundled proficiency level awards one point.
- Native spellbook and equipment capacity supplies Mastery's player-owned preparations; assignments leave item inscriptions unchanged.
- Two-handed classification uses Better Combat's resolved weapon attributes exclusively.
- `/mastery reload` and editor saves refresh only Mastery data. Editor overrides are stored under `masteryedits`.
- Root growth follows the fixed map center, so panning and zooming cannot change its direction.

### Fixed

- Native spellbook and sword shortcuts can no longer bypass Mastery spell unlocks and prepared slots.
- Invalid definition edits retain the accepted graph and restore the previous override files.

## 1.0.0 - 2026-09-16

### Added

- Independent usage-based proficiency, levels, and skill points for each specialization.
- A shared skill map that reveals trees when a player first receives a point.
- Draggable roots and child nodes, with descendants following their parents and layouts saved per client, world, and player.
- Datapack progression rules, cross-tree prerequisites, attribute upgrades, and operator progression commands.
