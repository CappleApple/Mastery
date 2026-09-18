# Configuration and commands

## Server policy

NeoForge writes `config/mastery-server.toml` in the instance directory. A file at `<world>/serverconfig/mastery-server.toml` overrides it for that world.

| Key | Default | Behavior |
| --- | --- | --- |
| `baseActiveCapacity` | `0` | Assignable spells added to native equipment capacity and progression bonuses |
| `defaultWorldTier` | `-1` | No provider means uncapped; set `0` to enforce tier-zero caps |
| `creativeUsageXp` | `false` | Whether creative players earn usage XP |
| `classSelectorEnabled` | `true` | Require players without a class to choose one on join, temporarily holding them in spectator mode |

The class selector applies to both new players and existing players who have no saved class choice. It keeps players at their original location while they choose. Disabling `classSelectorEnabled` restores pending players' previous game modes on the next server tick; an empty class definition set also releases them. Chosen classes and their attribute modifiers remain active. See [classes](classes.md) for starting rewards and persistence.

Native book capacity is read from Iron's equipment containers. No Mastery item tag or fixed per-book capacity substitutes for that value. Slot assignments never write into those containers.

A world-tier provider takes precedence over the fallback. Entering a lower tier restricts effective effects and loadouts without deleting earned proficiency. XP above the current level cap is discarded rather than banked. Losing capacity can clear excess assignments; prepare them again after capacity returns.

## Local layout

`config/mastery/layouts/<hash>.json` lives in the client's game directory. The key includes the server address or singleplayer save path, persistent world UUID, and player UUID. Player layouts save on closing the map or disconnecting, and restore on rejoining or restarting the client. Custom root positions, node offsets, orientations, expanded branches, camera position, and zoom are retained. Edit mode uses a separate temporary layout.

Child offsets are relative to the first same-tree dependency by sorted ID, or the owning tree if there is none. This stable parent is a storage reference. Movement follows all prerequisite parents: inverse-square distance weights make a nearby parent influence a shared child more strongly. Synergy offsets use their owning tree as the storage reference.

Dragging a parent moves its dependency descendants and preserves their relative offsets. Default growth follows eight compass sectors: `north`, `northeast`, `east`, `southeast`, `south`, `southwest`, `west`, and `northwest`. Root dragging chooses the sector relative to the fixed logical map center. Camera movement does not change orientation. Changing the root sector rotates custom child offsets with the branch. On load, saved root positions determine growth direction before branches are generated, including promoted roots and layouts with missing or stale orientation entries. Existing custom node offsets are retained.

The **Sectors** toolbar button toggles radial boundary lines. This preference is saved as `show_sectors` in the local layout file; it does not hide dependency or synergy connectors.

Resource packs can replace `assets/mastery/textures/gui/sprites/graph/*.png` and their `.png.mcmeta` nine-slice metadata. Item icons use item IDs; custom texture icons use resource locations naming loaded PNG images. The icon picker browses images from every loaded namespace and resource pack, as well as item and native spell icons.

The generic Skill Book uses `assets/mastery/models/item/skill_book.json` and the 16x16 base texture `assets/mastery/textures/item/skill_book.png`. Its token selects a matching node icon for a flat cover stamp; the base keeps Minecraft's generated one-pixel item thickness. Resource packs can replace the base texture and icon resources.

## Client display preferences

`config/mastery-client.toml` controls presentation only:

| Key | Default | Behavior |
| --- | --- | --- |
| `treeAnimations` | `true` | Animate expansion and collapse |
| `treeAnimationSpeed` | `1.0` | Speed multiplier; 1 takes 250 ms, range 0.1-10 |
| `minimumNodeWidth` | `20` | Minimum node width in GUI pixels, range 8-224; icons scale with nodes |

Zoom ranges from 0.0001 to 2.5. The extra range is on the zoom-out side; the zoom-in limit is unchanged. Once nodes reach their minimum size, further zoom reduces spacing. A screen-space layout separates overlapping nodes, including their outlines and optional names. This projection does not change saved positions, parent offsets, or growth sectors. Dense maps still require panning once there is no room to shrink further without overlap. Expanding and collapsing nodes may overlap during transitions; only their settled destinations are separated. Focus and pointer hit testing use displayed positions.

Rendering and visual motion follow the client frame rate, without a separate Mastery FPS cap. Focus movement and the hold-to-level fill use elapsed time rather than whole game ticks. Minecraft's frame limit and VSync still apply.

**Focus selected skill** defaults to F and is remappable in Minecraft Controls. It applies inside the skill screen while Search is unfocused. Left-click selects details, holding left-click buys a rank, right-click expands or collapses a branch, and middle-click toggles an unlocked node. Root hover tooltips contain name, level, available points, and XP. Child tooltips contain name, rank, and stat bonuses. Active spell details include Iron's localized Scroll Forge description. Proficiency increases show a HUD toast and play the vanilla level-up sound; initial synchronization does not replay past levels.

Charge, modifier capacity, themes, and purchase feedback are gameplay/data settings rather than TOML preferences. See [inherited data settings](datapacks.md#inherited-settings-and-tree-presentation).

## Commands

XP, level, point, node, reset, validation, reload, export, and editor commands require permission level 2, including reads of your own progression. Tree listing, tree information, and rearranging your own map do not.

| Command | Result |
| --- | --- |
| `/mastery tree list` | List loaded tree IDs |
| `/mastery tree info <tree>` | Inspect level curve and initial section |
| `/mastery xp get <player> <tree>` | Current and lifetime XP |
| `/mastery xp add <player> <tree> <amount>` | Grant an exact nonnegative XP amount, without XP gain modifiers |
| `/mastery level get <player> <tree>` | Read proficiency level |
| `/mastery level set <player> <tree> <level>` | Set level within tier caps |
| `/mastery points get <player> <tree>` | Read that tree's balance |
| `/mastery points add <player> <tree> <amount>` | Grant points and discover the tree |
| `/mastery node unlock <player> <node>` | Assign rank 1 administratively |
| `/mastery node rank <player> <node> <rank>` | Assign a rank within node/tier maxima |
| `/mastery reset tree <player> <tree>` | Clear that tree's progression and discovery; preserve learned book tokens and class data |
| `/mastery reset all <player>` | Clear progression, assignments, and learned skill-book tokens; preserve class data |
| `/mastery graph validate` | Validate the live graph and report rejected reload errors |
| `/mastery graph relayout` | Reorganize your local presentation |
| `/mastery reload` | Reload only Mastery definitions, including fresh `masteryedits` overrides |
| `/mastery export` | Send the accepted Mastery datapack ZIP to the calling operator's client |
| `/mastery edit_mode <enabled>` | Enable or disable the calling operator's editor view |

`/mastery reload` and editor saves do not reload Minecraft recipes, tags, loot tables, or other mods. Editor state is private to the calling operator and ends on logout or loss of permissions; saved definitions affect the world. See [the editor](editor.md) for override paths.

`/mastery export` must be run by a player and does not require edit mode. It saves under the client game directory at `config/exports/mastery-HH-mm-dd-MM-yyyy.zip`, using local client time. Existing filenames get `-2`, `-3`, and later suffixes. Chat reports the saved path or an error. Exporting does not trigger a reload.

Administrative rank assignment bypasses price and purchase prerequisites. Effective effects still obey runtime requirements. Reducing and restoring levels does not award the same level's points twice. A reset intentionally clears that history.

Progression resets preserve the selected class, its attribute modifiers, the one-time starting-reward receipt, and any queued starter items. A reset can remove class-granted points and skills, but it does not reopen selection or grant the starting package again. There is no class-change command.

Gameplay and `MasteryAPI.grantXp` awards use the player's overall and per-tree XP modifiers. `/mastery xp add` supplies the exact requested amount to progression; tree and world-tier caps still apply. Direct skill-point grants are never multiplied. See [XP modifiers](experience.md).

IDs are namespaced, for example `mastery:fire`, `mastery:fireball`, and the existing spell `irons_spellbooks:fireball`.
