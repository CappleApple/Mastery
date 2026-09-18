# Validation

## Current charge-stat validation

The charge-stat change passed 210 unit tests and 72 GameTests without Tempo. A hidden, muted client with Tempo 1.3.1 verified the open details panel showed current maximum charges through modifier ranks (1, 3, 7 charges), toggles, active recharge, an attribute change (13 charges), and removing the spell from its slot. The server snapshot was also checked against Tempo's `maxCharges` calculation. Results: `build/extra-charges-smoke/result.json`; build log: `build/charge-stats-validation.log`.

## 1.3.7 validation

On 2026-09-18, `./gradlew test build runGameTestServer` passed **210 unit tests** and **72 GameTests** without Tempo installed. New checks cover charge aggregation, rank scaling, assignment, validation, and restoring moved roots across all eight octants, including promoted roots and missing or stale saved directions.

With Tempo Not Time 1.3.1 installed on the dedicated server and hidden, muted client, `runClientExtraChargesSmoke` observed 1 base charge, 3 at rank 1, and 7 at rank 3. Toggling the modifier restored the original capacity and reapplied the bonus, including during active recharge, without changing spell level. The captured triangle icons were visually inspected. Two targeted server GameTests also exercised charge validation and the public charge event with a preexisting event adjustment.

The layout client harness passed a disconnect/reconnect and a separate fresh launch. Before that launch, the test deliberately replaced the Fire tree direction with its stale default and removed the Flame Blade direction. Saved node coordinates, recovered directions, collapse state, camera position, and zoom matched the saved layout. These are automated live-client checks, not a human combat playtest.

Results: `build/extra-charges-smoke/result.json`, `build/layout-persistence-smoke/save-result.json`, and `build/layout-persistence-smoke/restore-result.json`. Build/server log: `build/extra-charges-validation.log`. Client harnesses and temporary fixture definitions are excluded from the release JAR.

## 1.3.6 validation

On 2026-09-17, `./gradlew test build runGameTestServer` passed **205 unit tests** and **70 GameTests**. New checks cover effective prepared levels after modifier upgrades and toggles, synchronized spell stats, server-side modifier-slot availability, and keyword tooltip field validation.

The hidden, muted `runClientSpellDetailsSmoke` harness connected to the dedicated test server with Tempo Not Time 1.3 and `casting_mode = "SPELL_COOLDOWNS"`. With a linear charge formula in the fixture, level changes produced capacities of 8, 6, and 4. Disabling the modifier restored 8, including while an earlier cast was still recharging. The native HUD exposed the same effective levels. The details panel omitted mana and used Tempo's normalized cooldown of 21.75 seconds instead of the native 25 seconds.

A separate client/server run without Tempo passed the same modifier and keyword checks, showing the native 60 mana cost and 25-second cooldown. Both runs checked native descriptions, current stats, equipped Scorch, removal of redundant labels across every node, free-slot visibility, keyword hover regions, and entering a keyword inside a stationary tooltip. Captures were inspected; these are automated live-client checks, not a human combat playtest.

Results: `build/spell-details-smoke/result.json`, `build/spell-details-smoke/without-tempo-result.json`. Server/unit log: `build/spell-details-validation.log`. The opt-in client harness requires the local operator and spell-capacity fixture and is excluded from the release JAR.

## 1.3.5 validation

On 2026-09-17, `./gradlew test build runGameTestServer` passed **204 unit tests** and **69 GameTests**. Binding checks cover all nine hotbar sets, rejection of fifth assignments and casts, trimming old extra hotbar slots, and retaining extended quick-cast slots.

A hidden, muted client first reproduced the layout failure in normal player mode: the cached preferences had no save-file path after login. After the fix, `runClientLayoutSmoke -PmasteryLayoutSmoke=save` moved an ordinary root, a skill branch, and the promoted Flame Blade root; changed collapse state, orientation, pan, and zoom; closed the map; and disconnected/reconnected. All saved coordinates and view settings matched. A separate client process with `-PmasteryLayoutSmoke=restore` loaded the same layout successfully.

Both client runs inspected the hotbar binding screen at 64 spell capacity: exactly four slot widgets, no slot-page arrows, and page keys unable to address another hotbar page. These are automated live-client checks, not a human visual playtest.

Build/server log: `build/layout-hotbar-validation.log`. Reproduction and client results: `build/layout-persistence-smoke/before-fix-result.json`, `save-result.json`, and `restore-result.json`.

## 1.3.4 validation

On 2026-09-17, `./gradlew test build runGameTestServer` passed **202 unit tests** and **68 GameTests**. New server checks cover unassigned trigger modifiers, incompatible spell assignments, slot consumption, native Fireball projectile damage, Lightning Bolt attribution, unrelated melee/elemental/Fire damage, canceled damage, parent disabling, assignment changes after impact, spell-specific kill triggers, and incoming enemy spells. The Scorch chance is set to 100% only in the test fixture; the bundled chance remains 25%.

The dedicated server accepted the graph. A hidden, muted client verified synchronized modifier bindings, active-skill dependencies, and disabled presentation when unassigned. Existing layout, tooltip, and connection checks also passed. These are automated live-client assertions, not a human visual playtest.

The JAR and demo ZIP contain the corrected modifier definitions. Build and server log: `build/modifier-final-validation.log`. Client results: `build/reopen-smoke/result.json`.

## 1.3.3 validation

On 2026-09-17, `./gradlew test build` passed **202 unit tests**. A hidden, muted client connected to the dedicated test server and checked that unlocked Flame Blade and partially ranked Fire Practice omit their initial cost and prerequisites. Locked nodes retain both, upgradeable nodes retain next-rank hover costs, and maxed nodes omit hover costs. These checks inspect the live client content model; no human visual playtest is claimed.

Build log: `build/details-validation.log`. Client results: `build/reopen-smoke/result.json`.

## 1.3.2 validation

On 2026-09-17, `./gradlew test build runGameTestServer` passed **202 unit tests** and **66 GameTests**. A final `test build` passed after adding the XP-condition context field to the editor.

Checks cover independent connection-style inheritance and definition synchronization, promoted-root prerequisite versus child edges, immediate initial/reopened layouts, continued manual transitions, and the 100 ms hold default. The Flame Blade server test exercises the bundled OR condition with Fire damage, two-handed melee damage, ranged Fire damage, and rejected unrelated damage. Matching events award XP once.

A dedicated server accepted the graph. The hidden, muted `runClientClassesSmoke -PmasteryClassesSmokeUser=MasteryC144521 -PmasteryReopenSmoke=true` client checked new and reused screens against their saved target positions, manual collapse/expansion, Scorch and Gathering Thunder triangle defaults, the 100 ms default, and Flame Blade's dashed prerequisite and solid child styles. These are assertions against the live screen, not a human visual playtest.

Logs: `build/reopen-final-validation.log`, `build/reopen-package.log`, `.validation/reopen-client.log`. Client results: `build/reopen-smoke/result.json`.

## 1.3.1 validation

On 2026-09-17, `./gradlew test build runGameTestServer runOptionalGameTestServer` passed **199 unit tests** and **65 GameTests in each server fixture**. The optional fixture included Needs Not Necessities 1.1.10 and Patchouli 1.21.1-93-NEOFORGE. The standard fixture had neither installed.

New checks cover equipped starter armor with a full inventory, preserved displaced gear and components, damage filters on hit/hurt/kill/death, ranged attacks, canceled damage portions, category shape defaults, and outgoing connections during collapse.

A separate muted, hidden Minecraft client connected to the dedicated test server with Tempo Not Time 1.2.6 and Patchouli installed. It checked:

- Class icon-strip scrolling, button placement, detached armor previews, equipped starter armor, displaced inventory, and compact GUI scale.
- Native armor tooltips, next-rank cost visibility, class selection, reload, and reconnect without duplicate rewards.
- The same prepared spell list through the native HUD getter during addon rendering, with the original equipment view restored afterward.
- Tempo charges at 4 before casting, 3 after spending one, and 4 after recovery. Screenshots visibly show the charge counts.
- Outgoing connections retained during collapse and removed afterward, by inspecting the live screen.
- Damage-condition cards and the filter fields in the visual editor.

The guide contains 27 entries and 145 pages; 109 pages belong to the edit-mode workshop. Server logs are in `build/final-validation.log`; client results are in `build/classes-smoke/result.json` and `.validation/selector-result.json`. Screenshots use an armor and extra-class fixture. No human balance playtest is claimed.

## 1.3.0 validation

On 2026-09-17, `./gradlew test build` passed **194 unit tests**. `runGameTestServer` and `runOptionalGameTestServer` each passed **63 GameTests**. The optional fixture included Needs Not Necessities 1.1.10 and Patchouli 1.21.1-93-NEOFORGE; the standard fixture had neither installed. Minecraft, NeoForge, Java, and required dependency versions match the 1.2.0 environment below.

New server checks cover one-time class rewards, starting ranks and native item components, restored game modes, saved spectator anchors, class removal, config disabling, queued inventory overflow, death copies, XP attribute/effect composition, and promoted-root prerequisite gates. Unit checks include nested root ownership, saved data format 4, class validation, generated-tree round trips, and stable promoted-root positions across dragging, rotation, and layout reloads.

The dedicated server started and restarted with 22 independent trees, 86 nodes, nine spell bindings, and 27 XP sources. `/mastery graph validate` accepted the live graph. A hidden client with master volume muted and the mouse released passed the `runClientClassesSmoke` checks:

- Opened the class selector in spectator mode and reloaded definitions while selection remained pending.
- Chose Mage through the actual screen and checked its points, starting skill rank, inventory, 10% overall XP bonus, and restored game mode.
- Purchased Flame Blade using its retained requirements and original currency, then displayed its independent XP outline without a duplicate root icon.
- Moved and rotated the promoted root, checking that its children and hidden layout anchor stayed aligned.
- Reduced a prerequisite tree's level, verified the branch disappeared and rejected XP, then restored the level and branch without losing its purchased rank.
- Saved class and root definitions through the visual forms and checked server synchronization.
- Opened the new class, root, and XP Workshop entries, then verified their removal from the ordinary guide view after disabling edit mode.
- Reconnected and verified class choice, root investment, attributes, and starter items persisted without duplicate grants.

The [class selector](images/mastery-class-selector.png), [Flame Blade root](images/mastery-flame-blade-root.png), visual forms, and [class Workshop pages](images/mastery-workshop-classes.png) were visually inspected. Representative new guide pages fit the book bounds. The guide contains 27 entries and 139 pages, including 16 edit-mode Workshop entries with 104 pages; every page was not individually rendered for inspection.

The 1.3.0 production JAR contains the three bundled classes and the Flame Blade definitions, excludes GameTests and client validation harnesses, and does not require Needs Not Necessities. The demo ZIP includes the new definitions. Local evidence is in `build/classes-final-validation.log`, `build/classes-layout-validation.log`, `build/reports/tests/test/`, and `build/classes-smoke/result.json`. The client harness requires the prepared local operator fixture and server. No human balance playtest or concurrent multiplayer load test is claimed.

## 1.2.1 validation

On 2026-09-17, `./gradlew test build runGameTestServer` passed 164 unit tests and 55 GameTests. The loaded enchantment registry contains all nine Aspects in `mastery:exclusive_set/aspects`; Minecraft's native compatibility check rejects every pair of different Aspects. The built JAR includes the shared tag and each enchantment's `exclusive_set` reference.

## 1.2.0 validation

Validated on 2026-09-17 with Minecraft 1.21.1, NeoForge 21.1.249, Java 21, Iron's Spells 'n Spellbooks 1.21.1-3.16.3, Better Combat 2.4.0, Apothic Attributes 2.10.1, and Placebo 9.9.2.

`./gradlew test build runGameTestServer runOptionalGameTestServer --continue` passed **164 unit tests** and **55 GameTests in each server configuration**. The standard GameTest instance had neither Needs Not Necessities nor Patchouli. The optional instance had Needs Not Necessities 1.1.10 and Patchouli 1.21.1-93-NEOFORGE installed. Optional-instance mod JARs are local test fixtures, not bundled dependencies.

The new checks cover typed weapon bonuses and conversion, school resistance, overlapping matchups and inverse native healing, projectile snapshots, one critical roll per split attack, absorption aggregation, shields, hurt cooldowns, keyword thresholds, proc attribution and recursion, native spell activation, crafting extraction and brewing, optional meal/comfort providers, multiple spell rewards, and atomic nested purchase costs. The timed Firebolt check observes native projectile spawn events and verifies charge delay, mana and cooldown; it does not depend on projectiles remaining inside ticking chunks.

The dedicated server loaded 21 trees, 84 nodes (including four synergy nodes), nine spell bindings and 26 XP sources. It completed startup and a clean restart with optional Patchouli installed. The production JAR contains 12 damage-type definitions and excludes GameTests and client validation harnesses. Its metadata does not require Needs Not Necessities.

A hidden Minecraft client with master volume muted and the mouse released passed the `runClientMechanicsSmoke` checks against that server:

- Authored trigger conditions/actions and both keyword action lists through the visual editor, round-tripped JSON, saved, and checked server synchronization.
- Found a full resource path in the texture picker, saved the icon and a hexagonal root, and displayed a 50% XP outline following that shape.
- Authored nested point/XP/item cost alternatives and depth scaling, including inherited-default editing, and saved the node.
- Checked Patchouli Workshop entries absent in ordinary mode, present in edit mode, and absent again after disabling it. Opened actual elemental and purchase-cost documentation pages.
- Restored the edited tree and removed temporary definitions.

The [script editor](images/mastery-combat-editor.png), [keyword editor](images/mastery-keyword-editor.png), [texture picker](images/mastery-icon-browser.png), [root XP outline](images/mastery-root-xp.png), [cost editor](images/mastery-cost-editor.png), [elemental Workshop pages](images/mastery-workshop-elements.png), and [cost Workshop pages](images/mastery-workshop-costs.png) were visually inspected. Representative Workshop pages fit the book bounds; this was not a visual inspection of every page. The Workshop contains 13 entries and 81 pages.

Local evidence is in `build/final-validation.log`, `build/reports/tests/test/`, `build/mechanics-smoke/result.json`, and `.validation/mechanics-server.log`. The opt-in client harness requires the prepared local server and operator fixture. No human combat/balance playtest or multiplayer load test is claimed.

## Earlier validation

The following sections retain the validation evidence from versions 1.0.1 through 1.1.2.

Validated locally on 2026-09-16 with Minecraft 1.21.1, NeoForge 21.1.249, Java 21, Iron's Spells 'n Spellbooks 1.21.1-3.16.3, and Better Combat 2.4.0, Apothic Attributes 2.10.1, and Placebo 9.9.2.

## Automated checks

`./gradlew test build runGameTestServer` passed with **130 unit tests and 26 Minecraft GameTests**. They exercise:

- Independent XP, points, purchases, prerequisites, world-tier limits, serialization, reconciliation, and hotbar/shared assignments.
- Eight-direction layout, fixed map-center orientation, weighted shared-child movement, custom-offset rotation, collapsed-edge projection, animation, and minimum-size collision avoidance.
- Native spell capacity, timed casting, mana admission, cooldowns, charged Fireball growth, Firebolt bursts, inherited modifier slots, and independent node toggles without writing inscriptions.
- Actual school damage XP, rejected damage, and exclusion of ordinary vanilla fire damage.
- Consumable skill books, duplicate use, hidden branches, and persistence through death.
- Operator-only editor sessions, definition validation, isolated reloads, and rejection of invalid overrides.
- Repeat limits and rank thresholds across child and grandchild branches, first-reveal requirements, and persistence.
- Merged datapack ZIPs, deletion markers, original namespaces, transfer limits, client filenames and collisions, and export permissions.
- Skill Book toggle defaults and custom tokens, plus deterministic node-icon lookup.

Reload tests retain the same resource, recipe, and advancement manager instances. Invalid Mastery definitions leave the previous accepted graph active. Synthetic fixtures include a 612-node layout and a 3,000-node graph; these check correctness rather than rendered frame rate.

GameTests run without Patchouli. Local reports are generated under `build/reports/tests/test/`; the final build and GameTest output is in `.validation/latest-validation.log`.

The tests also cover calculated level caps, point-award intervals and amounts, nested AND/OR prerequisites, conditional book gates, weapon-scoped attributes, and Apothic scaling of native arrow damage and velocity.
## Dedicated server

The dedicated server loaded 21 trees, 55 nodes, nine existing-spell bindings, and 26 XP sources. It completed startup and a clean restart with optional Patchouli 1.21.1-93-NEOFORGE installed. A server-only `masteryedits` override changed the Fire tree name; the edited name and selected editor datapack survived the restart. The graph validated after startup.

## Live client

An automated Minecraft client connected to the local dedicated server with its window hidden and volume muted. It exercised the real client, network handlers, screens, and server progression code.

Earlier 1.0.1 client checks verified:

- A fresh character initially saw no undiscovered trees. Server point grants revealed separate trees.
- Purchases and contextual assignments synchronized. An equipped native copper spellbook supplied five slots; assigning Fireball left the complete book item data unchanged.
- Pointer-driven root and child dragging, descendant movement, and wheel zoom worked.
- Progression and layout survived reconnecting, a Mastery-only reload, a dimension change, and death/respawn.
- An unassigned Mastery hotkey passed through to its ordinary action.
- Editor mode showed every tree expanded in its default layout, independently of the player's saved positions. Pan, zoom, and sector visibility worked.
- Right-click menus opened the definition form. Creating, editing, and deleting a temporary tree applied immediately and wrote the separate `masteryedits` directory. Leaving the editor restored the personal layout.
- Normal map entries and search excluded 50 unavailable nodes, including two gated by a skill book.
- The actual **Require Skill Book** toggle saved a five-rank node with its matching token. A server-given generic book rendered the node's diamond icon over the new base, then changed to a custom resource texture after an editor save.
- `/mastery export` wrote a 124-entry ZIP in the calling client's `config/exports/` folder. The ZIP contained the server-only Fire name, current edited book-gated node, and native spell IDs under ordinary `mastery` paths, with no duplicate entries or `masteryedits` paths. No export directory was created on the server.
- The optional Patchouli guide loaded all eight entries and 22 pages without book-loading errors and rendered in the client.

See the captured [skill map](images/mastery-map.png), [editor](images/mastery-editor.png), [book toggle](images/mastery-book-editor.png), [item-icon book](images/mastery-skill-book.png), [texture-icon book](images/mastery-skill-book-texture.png), and [guide](images/mastery-guide.png). The captures were inspected for readable rendering, viewport clipping, and correctly placed book overlays. Repeated live saves also verified the fix for definitions and editor revisions arriving in separate packets.

The development harness is `src/main/java/com/cappleapple/mastery/client/validation/ClientSmoke.java`. It runs only with `runClientSmoke` and is excluded from the distributed mod JAR, along with GameTest classes. It requires a prepared local server and RCON-driven checkpoints; running the task alone is not an unattended end-to-end test. Live results and captures are preserved locally under `.validation/native-client/`.

## Current client checks

The `runClientFeatureSmoke` harness exercised the 1.1.1 screens against the dedicated server with a hidden window, muted master volume, and the mouse released. Its recorded assertions passed for:

- Hold-to-level purchases, a silent initial 500 ms delay, eight rising sound pitches (0.8 through 1.5), and one beacon power-select completion sound.
- Short left-click selection and right-click expansion/collapse, with modifiers hiding and reappearing.
- Middle-click disable/enable controls and retained descendant investment.
- Four-line root tooltips, child stat bonuses without tree XP/points, Scroll Forge spell descriptions, and the F focus shortcut.
- Visual theme, hexagon shape, and fill-direction edits, immediate server application, and round trips through the syntax-colored JSON view.
- Searching for dependency nodes, setting required ranks, nesting OR groups, reordering entries, the Skill Book toggle and copy command, and the definition library.
- Four keyed slots per hotbar page, visible bound and picker spell icons, and shared assignment screens, Iron's HUD active-set filtering, and exclusion of inactive hotbar sets.
- A proficiency level-up toast and the vanilla player-level-up sound event.
- Zooming to 0.0001, non-overlapping displayed bounds, focus, and pointer hit testing at distant zoom.

Captures were inspected: [visual forms](images/mastery-visual-editor.png), [JSON editor](images/mastery-json-editor.png), [deep zoom](images/mastery-deep-zoom.png), and [level-up toast](images/mastery-level-up.png). The result file is generated locally at `build/feature-smoke/result.json`. The opt-in harness requires the isolated local server, operator fixture, and equipped book; it is excluded from the distributed JAR. Sound event IDs and pitches were checked while output remained muted.

## Remaining limits

- No human combat or balance playtest has been completed. Native timed/charged casting has server GameTest coverage; client checks exercise controls, forms, map presentation, assignments, notifications, and HUD filtering. Charged projectiles have not had a human visual combat review.
- Better Combat weapon classification is covered by code and tests; its combat animations were not manually observed.
- Player isolation has GameTest coverage. Concurrent multi-client load and latency have not been profiled.
- Large-graph correctness tests do not replace frame-time profiling on different GPUs or resource packs.

## 1.1.2 rendering checks

On 2026-09-17, `./gradlew test build` passed all 130 unit tests. The hidden, muted client smoke run also passed after batching node borders and connections. Captures retained the node outlines, icons, and hold overlay.

The client verified that expanding children start overlapping their parent and retain their interpolated positions without collision correction. Render callbacks observed both focus movement and hold progress changing between consecutive frames within the same game tick. Settled deep-zoom nodes remained separated. Mastery applies no separate GUI FPS cap; the validation instance used Minecraft's 60 FPS setting. This confirms frame-based updates, not a guaranteed frame rate on every system.


## 1.4.0 tree modifiers, keywords, and held charges

On 2026-09-18, `./gradlew test build runGameTestServer` passed **214 unit tests** and **76 GameTests**. The final dedicated server started successfully and accepted the graph. The JAR and demo ZIP were checked for the new definitions; the JAR excludes validation harnesses and keeps Tempo Not Time and Needs Not Necessities optional.

The automated checks cover Fire-only tree triggers, inheritance from Fire into Flame Blade, subtree opt-out, flat and percentage keyword stacks/damage/duration, reapplication-delayed decay, loss callbacks on decay/expiry/consumption, and no duplicate final-loss callback. Flame Blade tests cover the two-handed melee Fire source, automatic credit from its own skills with a different damage type, disabled skills, the data opt-out, authored XP/point rewards, and Flaming Strike base levels.

With Tempo Not Time 1.3.1 installed, dedicated-server tests verified additional-charge event composition and whole-charge payment for held spells, including limiting a held spell when only one charge remains. Without Tempo, the standard suite verifies held native levels and final-level mana costs.

The hidden, muted `runClientExtraChargesSmoke` client passed current charge updates (4 → 6 → 10), toggles during recharge, an attribute-driven increase to 13, unslotted stats, held-charge payment, and removal of a node during an open-map definition reload. It confirmed Flaming Strike uses Fire on the live server. Screenshots were inspected for the thick yellow charge marker, rounded active node, larger pentagon root, current Flaming Strike stats, and both keyword-loss editor lanes. The fixture used an existing edited test world; its root override was backed up and restored after checking the new bundled root effect.

Reports: `build/extra-charges-smoke/result.json`, `build/tree-validation.log`, and `build/final-1.4.0-validation.log`. Gameplay balance and third-party addon spells beyond the tested native spells have not been manually tested.
