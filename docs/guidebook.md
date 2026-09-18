# Mastery Field Guide

Installing Patchouli adds the optional **Mastery Field Guide**. Craft one with a vanilla book and paper in any arrangement. It also appears in the Tools & Utilities creative tab.

The item is `patchouli:guide_book` with the `patchouli:book` component set to `mastery:guide`. The guide uses Patchouli's existing item. Without Patchouli, the recipe is skipped and progression works normally.

Operators can give the guide directly:

```mcfunction
/give @s patchouli:guide_book[patchouli:book="mastery:guide"]
```

The guide covers class selection and starting rewards, overall and per-tree proficiency XP bonuses, skills that open independent trees, consumable Skill Books that reveal sealed branches, the draggable map with eight outward growth directions and a sector-line toggle, native Iron's spells, hotbar and shared quick-cast sets, native spellbook capacity, Better Combat weapon classification, visual operator editing, and client-side datapack export. The workshop covers damage filters, type-based node shapes, and class equipment previews. Its key labels follow the player's remapped Mastery controls.

## Resource-pack content

The book definition is [book.json](../src/main/resources/data/mastery/patchouli_books/guide/book.json). English categories and entries live under `assets/mastery/patchouli_books/guide/en_us/`. Resource packs can replace those files or add another language folder.

The book uses Patchouli's text and spotlight pages. Its definition sets `use_resource_pack: true`, as required by the inspected Patchouli 1.21.1 format; the metadata remains under `data/mastery/patchouli_books/guide/`. See the [Patchouli book reference](https://vazkiimods.github.io/Patchouli/docs/reference/book-json/) for the format.

The format and item component were checked against Patchouli `1.21.1-93-NEOFORGE` and Iron's Spells 'n Spellbooks `1.21.1-3.16.3` packaged guide resources.

## Operator Workshop

Enabling `/mastery edit_mode true` reveals the **Operator Workshop** category in the same book. It contains walkthroughs for the editor, class authoring and native starting-item components, promoted skill roots and XP sources, overall and per-tree XP modifiers, skill branches, attributes and conversion, visual combat scripts, health and keyword conditions, action targeting, stacking DOTs, threshold interactions, crafted bonuses, optional comfort integration, arbitrary texture icons, shape-following XP outlines, nested purchase costs, validation, and export. Scorch and stored thunder have dedicated examples.

The client publishes Patchouli's `mastery_edit_mode` config flag from the server-authorized Mastery edit state and reloads this book's contents when it changes. Both the category and its entries require that flag. Disabling editing, losing permission, or logging out clears the flag; the next world's book load starts from its own synchronized state. This hides authoring documentation from the ordinary player view without requiring a separate guide item.

Patchouli remains optional. The integration uses its public `setConfigFlag` API and the inspected `Book.reloadContents(Level, boolean)` implementation in Patchouli `1.21.1-93-NEOFORGE`. No Patchouli classes are required to load Mastery without the mod. The internal reload bridge may need an update for another Patchouli API version; integration failures are logged without disabling Mastery.

The file-based equivalent is [the in-game editor reference](editor.md), with detailed [classes](classes.md), [XP modifiers](experience.md), [promoted roots](roots.md), [damage types](elemental.md), [combat scripts](mechanics.md), and [crafting/comfort](crafting.md) references.

## Classes and new specializations

The ordinary player view explains the class selector, spectator waiting state, preserved inventory, queued starter supplies, and the one-time reward rules. It also explains how XP bonuses combine and how buying a skill such as Flame Blade can open another specialization with its own XP and points.

The Workshop adds separate entries for creating classes, defining starter skills and native item components, selecting XP attributes, using the `mastery:experience_gain` effect for custom trees, and promoting nodes through `root_tree`. Promoted-root instructions include retained prerequisites, descendant ownership, independent XP sources, nested roots, and export behavior. These authoring entries use the same `mastery_edit_mode` flag as the existing Workshop.
