# Validation

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
