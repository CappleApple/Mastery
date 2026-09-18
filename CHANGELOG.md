# Changelog

## 1.4.0 - 2026-09-18

### Added

- Tree modifiers apply to matching damage and inherit into sub-trees by default.
- Keyword modifiers adjust applied stacks, damage, and duration; keywords support delayed stack decay and stack-loss action chains.
- A tree’s unlocked skills grant that tree XP from their damage regardless of damage type, without duplicate same-tree damage awards.
- Flaming Strike is available in Flame Blade.
- Spell stats show current maximum charges when Tempo Not Time is active, including level changes, charge modifiers, and unslotted skills.

- Held spell stages increase native level above the modified base by default. Pack authors can disable this through charge settings.
- Yellow charge-bar markers show where held casts cost another Tempo charge. Held casts pay those charges and stop scaling beyond their available charge budget.

### Changed

- Adopted CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers; downloads include the license.
- Flame Blade’s external XP source requires two-handed melee Fire damage. Its root grants +10% Two-Handed Weapon Fire Damage.
- Square skill nodes have rounded corners. Tree roots are 30% larger by default, with size controls in data and the visual editor.

### Fixed

- Removing a node during a live datapack reload no longer crashes an open skill map.

## 1.3.7 - 2026-09-18

### Added

- Spell modifiers can add Tempo Not Time charges per enabled rank through `extra_charges`. Changes update the charge HUD before casting and during recharge.

### Fixed

- Triangle node icons sit lower within the shape in the skill map and class preview.
- Moved trees recover their expansion direction from their saved root position on first open after joining a world.

## 1.3.6 - 2026-09-17

### Fixed

- Slotted spell levels and Tempo charge capacity update when spell-level modifiers are enabled, disabled, or upgraded, including while earlier casts recharge.
- Spell details show the Scroll Forge description, current rank and native stats, then equipped modifier effects. Mana cost is hidden when Tempo disables mana.

### Changed

- Removed duplicate enabled-state text, assignment counts, modifier-slot counts, next-rank previews, and repeated control hints from skill details.
- Unbought modifier nodes appear only while their spell has a free modifier slot; purchased nodes remain visible.

### Added

- Hover explanations for named keywords in screen text and tooltips, with stack limits, duration, periodic actions, and threshold effects. Keyword descriptions can be edited in the visual editor.

## 1.3.5 - 2026-09-17

### Fixed

- Player-customized tree layouts now save to the correct world/player file after login and retain collapsed branches on reconnect.
- Hotbar sets are limited to four spell slots each. Removed their slot-page arrows, page number, and paging hint; page keys no longer change hotbar-set bindings.

## 1.3.4 - 2026-09-17

### Fixed

- Scorch modifies Fireball and Gathering Thunder modifies Lightning Bolt. Their triggers require assignment and an available modifier slot, and only react to damage from their assigned spell.
- Choosing the modifier node type now enables modifier assignment rules instead of only changing its shape.

## 1.3.3 - 2026-09-17

### Fixed

- Unlocked nodes no longer repeat their initial purchase cost and unlock requirements in the details panel. Eligible next-rank costs remain in hover tooltips.

## 1.3.2 - 2026-09-17

### Changed

- Parent prerequisite and child branch line styles can be edited independently. Defaults are dashed and solid, respectively.
- Scorch and Gathering Thunder are modifier nodes, using triangular shapes by default.
- The default delay before hold-to-invest begins is now 100 ms.
- Flame Blade gains XP from either Fire damage or two-handed melee damage, using a data-defined OR condition.

### Fixed

- Children of promoted roots such as Flame Blade use branch lines instead of cross-tree prerequisite lines.
- Opening or returning to the skill map restores node positions immediately instead of replaying expansion animations. Manual expansion and collapse still animate.

## 1.3.1 - 2026-09-17

### Added

- Damage filters for combat triggers and action conditions, with in-game selectors for categories, damage types, schools, and tags.
- Pentagon and triangle node shapes. Roots, passives, actives, and modifiers now default to pentagons, circles, squares, and triangles respectively.

### Changed

- Class selection now shows an armored player preview, starting skills, a portrait inventory, hover tooltips, and a scrollable class icon strip.
- Starter armor equips on selection, preserving displaced gear in inventory or the saved overflow queue.
- Fire Infusion and Fire Conversion now belong to Flame Blade. Root nodes display their available tree points.
- Node details omit balance dumps and repeated control instructions. Upgradeable node tooltips show the next-rank cost.

### Fixed

- Connections remain visible during collapse animations and disappear when fully collapsed.
- Native HUD addon overlays receive Mastery's active spell list. Tempo Not Time can display charges for prepared skill spells before their first cast.

## 1.3.0 - 2026-09-17

### Added

- Data-defined classes with icons, starting skill points, unlocked skill ranks, item stacks, and persistent attribute modifiers.
- A join-time class selector, enabled by default through server config, that holds players in spectator mode until they choose a class.
- Warrior, Ranger, and Mage starting classes, with saved overflow-item delivery and one-time starter rewards.
- Overall and per-tree proficiency XP attributes, plus tree-filtered XP effects for custom trees.
- Node promotion into independent tree roots with separate XP sources and point currencies while retaining purchase prerequisites.
- In-game class and promoted-root editing, Patchouli documentation, and repository references.

### Changed

- Spellblade is now Flame Blade, with its own progression branch and point currency.
- Administrative XP grants use the exact requested amount; gameplay and API XP awards apply the player's gain modifiers.

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
