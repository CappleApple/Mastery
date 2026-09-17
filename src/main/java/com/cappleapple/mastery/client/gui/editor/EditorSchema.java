package com.cappleapple.mastery.client.gui.editor;

import com.google.gson.*;
import java.util.*;

/** Field catalog for visual editing; unknown extension fields remain editable. */
public final class EditorSchema {
    private EditorSchema() {}
    private static JsonObject object(String json){return JsonParser.parseString(json).getAsJsonObject();}
    public static JsonObject fields(String kind,String path,JsonObject current) {
        String leaf=path.substring(path.lastIndexOf('/')+1);
        if(ScriptSchema.scripted(kind)) {
            if(path.isBlank())return ScriptSchema.root(kind);
            if(leaf.equals("conditions"))return ScriptSchema.conditionFields(current);
            if(leaf.endsWith("actions"))return ScriptSchema.actionFields(current);
        }
        if(path.contains("costs")) {
            if(current.has("and")||current.has("or"))return new JsonObject();
            String type=current.has("type")?current.get("type").getAsString():"points";
            return switch(type) {
                case "experience" -> object("{\"type\":\"experience\",\"amount\":100,\"depth_percent\":0}");
                case "item" -> object("{\"type\":\"item\",\"item\":\"minecraft:diamond\",\"item_tag\":\"\",\"amount\":1,\"depth_percent\":0}");
                default -> object("{\"type\":\"points\",\"tree\":\"\",\"amount\":1,\"depth_percent\":0}");
            };
        }
        if(leaf.equals("appearance"))return object("{\"shape\":\"circle\",\"show_name\":false}");
        if(leaf.equals("theme"))return object("{\"inner_color\":\"#D3AD5B\",\"outer_color\":\"#604522\",\"gradient\":1}");
        if(leaf.equals("unlock"))return object("{\"fill_direction\":\"default\",\"progress_sound\":\"default\",\"complete_sound\":\"default\",\"hold_delay_ms\":500}");
        if(leaf.equals("charge"))return object("{\"enabled\":false,\"ticks_per_level\":20,\"instant_base_ticks\":10,\"max_levels\":16,\"spell_levels_per_stage\":0,\"fireball_size_per_stage\":0,\"fireball_radius_per_stage\":0,\"extra_casts_per_stage\":0,\"burst_interval_ticks\":3}");
        if(leaf.equals("modifier_slots"))return object("{\"base\":2,\"per_level\":1,\"max\":64}");
        if(path.contains("dependencies")) {
            if(current.has("and")||current.has("or"))return new JsonObject();
            return object("{\"node\":\"\",\"rank\":1}");
        }
        if(leaf.equals("tier_caps"))return object("{\"tier\":0,\"max_level\":-1,\"max_rank\":-1,\"max_depth\":-1,\"modifier_slots\":-1,\"active_capacity\":-1}");
        if(leaf.equals("point_milestones"))return new JsonObject();
        if(path.contains("requirements")||path.contains("condition")||path.isBlank()&&kind.equals("requirements"))return requirementFields(current);
        if(path.contains("effects")||path.isBlank()&&kind.equals("effects"))return effectFields(current);
        if(!path.isBlank())return new JsonObject();
        JsonObject result=switch(kind) {
            case "trees" -> object("{\"name\":\"New specialization\",\"description\":\"\",\"icon\":\"minecraft:book\",\"section\":\"south\",\"xp_base\":100,\"xp_growth\":20,\"point_every\":1,\"points_per_award\":1,\"point_milestones\":{},\"point_formula\":\"\",\"tier_caps\":[]}");
            case "nodes","synergies" -> object("{\"name\":\"New skill\",\"description\":\"\",\"icon\":\"minecraft:book\",\"tree\":\"\",\"type\":\"passive\",\"dependencies\":[],\"max_rank\":1,\"cost\":1,\"level\":0,\"world_tier\":0,\"requirements\":[],\"effects\":[],\"visibility\":\"available\",\"exclusions\":[],\"toggleable\":true,\"spell\":\"\",\"modifier\":\"\",\"book_token\":\"\"}");
            case "spells" -> object("{\"name\":\"Spell upgrade\",\"description\":\"\",\"icon\":\"minecraft:book\",\"spell\":\"\",\"tree\":\"\",\"level\":1,\"contexts\":[]}");
            case "contexts" -> object("{\"name\":\"Combat classification\",\"priority\":0,\"condition\":{}}");
            case "xp_sources" -> object("{\"tree\":\"\",\"event\":\"damage\",\"amount\":1,\"scale\":\"damage\",\"condition\":{},\"points\":0,\"once\":false}");
            case "elements" -> object("{\"damage_type\":\"\",\"damage_modifiers\":{},\"attunement_attribute\":\"mastery:fire_attunement\",\"potency_attribute\":\"mastery:fire_potency\",\"mitigation_attribute\":\"mastery:fire_mitigation\",\"school\":\"irons_spellbooks:fire\",\"enchantment\":\"minecraft:fire_aspect\",\"damage_per_level\":0.1,\"weapon_attribute\":\"mastery:fire_weapon_damage\",\"power_attribute\":\"mastery:fire_damage\",\"conversion_attribute\":\"mastery:fire_conversion\"}");
            case "mob_types" -> object("{\"name\":\"New mob group\",\"entities\":[],\"entity_tags\":[]}");
            case "weapon_types" -> object("{\"items\":[],\"item_tags\":[],\"element\":\"mastery:slashing\",\"priority\":0}");
            case "groups" -> object("{\"name\":\"Group\",\"description\":\"\",\"icon\":\"minecraft:book\",\"parent\":\"\"}");
            default -> new JsonObject();
        };
        if(List.of("trees","nodes","synergies","spells","settings").contains(kind))
            for(String field:List.of("theme","unlock","charge","modifier_slots","appearance"))result.add(field,new JsonObject());
        if(List.of("trees","nodes","synergies","settings").contains(kind)) {
            result.addProperty("effect_context", "");
            result.add("costs",object("{\"type\":\"points\",\"amount\":1}"));
            result.addProperty("cost_depth_percent",0);
        }
        return result;
    }
    private static JsonObject effectFields(JsonObject current) {
        var result=object("{\"type\":\"mastery:attribute\",\"ref\":\"\",\"description\":\"\"}");
        String type=current.has("type")?current.get("type").getAsString():"mastery:attribute";
        var detail=switch(type) {
            case "mastery:attribute" -> object("{\"context\":\"\",\"attribute\":\"minecraft:generic.attack_damage\",\"amount\":1,\"operation\":\"add_value\"}");
            case "mastery:spell_modifier" -> object("{\"spell\":\"\",\"spell_level\":0,\"mana_multiplier\":1,\"cooldown_multiplier\":1,\"cast_time_multiplier\":1}");
            case "mastery:bonus" -> object("{\"key\":\"active_capacity\",\"spell\":\"\",\"amount\":1}");
            case "mastery:unlock_spell" -> object("{\"spell\":\"\",\"level\":1,\"levels_per_rank\":0}");
            case "mastery:unlock_context" -> object("{\"context\":\"\"}");
            case "mastery:on_usage" -> object("{\"event\":\"damage\",\"condition\":{},\"ignite_ticks\":0,\"effect\":\"minecraft:speed\",\"duration\":60,\"amplifier\":0}");
            case "mastery:trigger" -> object("{\"trigger\":\"\"}");
            case "mastery:crafting_attribute" -> object("{\"attribute\":\"minecraft:generic.attack_damage\",\"amount\":1,\"operation\":\"add_value\",\"slot\":\"mainhand\",\"chance\":1,\"item\":\"\",\"item_tag\":\"\"}");
            case "mastery:crafting_food" -> object("{\"nutrition_bonus\":0,\"saturation_bonus\":0,\"buff_strength_bonus\":0,\"buff_duration_bonus\":0,\"meal_strength_bonus\":0,\"meal_duration_bonus\":0,\"chance\":1,\"item\":\"\",\"item_tag\":\"\"}");
            case "mastery:crafting_potion" -> object("{\"amplifier_bonus\":0,\"duration_bonus\":0,\"added_effect\":\"\",\"added_duration\":200,\"added_amplifier\":0,\"chance\":1,\"item\":\"\",\"item_tag\":\"\"}");
            case "mastery:placed_comfort" -> object("{\"amount\":1,\"radius\":8,\"comfort_type\":\"mastery_crafted\",\"block\":\"\",\"block_tag\":\"\",\"chance\":1}");
            default -> new JsonObject();
        };detail.entrySet().forEach(e->result.add(e.getKey(),e.getValue()));return result;
    }
    private static JsonObject requirementFields(JsonObject current) {
        return object("{\"type\":\"mastery:condition\",\"ref\":\"\",\"tree\":\"\",\"level\":1,\"node\":\"\",\"rank\":1,\"tier\":0,\"id\":\"\",\"token\":\"\",\"and\":[],\"or\":[],\"not\":{},\"school\":\"\",\"spell\":\"\",\"item\":\"\",\"entity\":\"\",\"block\":\"\",\"damage_type\":\"\",\"dimension\":\"\",\"biome\":\"\",\"item_tag\":\"\",\"offhand\":\"\",\"offhand_tag\":\"\",\"entity_tag\":\"\",\"block_tag\":\"\",\"damage_tag\":\"\",\"biome_tag\":\"\",\"min_damage\":0,\"max_damage\":0,\"min_distance\":0,\"max_distance\":0,\"min_amount\":0,\"max_amount\":0,\"projectile\":false,\"critical\":false,\"blocking\":false,\"empty\":false,\"dual_wield\":false,\"provider_only\":false,\"requires_unlock\":false}");
    }
    public static JsonElement entry(String path) {
        if(path.contains("costs"))return object("{\"type\":\"points\",\"amount\":1}");
        if(path.contains("dependencies"))return object("{\"node\":\"\",\"rank\":1}");
        String leaf=path.substring(path.lastIndexOf('/')+1);
        return switch(leaf) {
            case "dependencies" -> object("{\"node\":\"\",\"rank\":1}");
            case "effects" -> object("{\"type\":\"mastery:attribute\",\"attribute\":\"minecraft:generic.attack_damage\",\"amount\":1,\"operation\":\"add_value\"}");
            case "requirements","and","or" -> object("{\"type\":\"mastery:tree_level\",\"tree\":\"\",\"level\":1}");
            case "tier_caps" -> object("{\"tier\":0,\"max_level\":20}");
            case "point_milestones" -> new JsonPrimitive(1);
            case "actions","tick_actions","threshold_actions" -> ScriptSchema.action("damage");
            case "conditions" -> ScriptSchema.condition("health");
            default -> new JsonPrimitive("");
        };
    }
    public static List<String> choices(String kind,String path,String key) {
        if(path.contains("costs")&&key.equals("type"))return List.of("points","experience","item");
        if(ScriptSchema.scripted(kind)) {
            if(key.equals("type"))return path.endsWith("conditions/type")?List.of("health","keyword"):ScriptSchema.ACTIONS;
            if(key.equals("event"))return List.of("hit","kill","hurt","death");
            if(key.equals("target"))return path.contains("conditions")?List.of("self","target"):List.of("self","target","nearby","aim");
            if(key.equals("center"))return List.of("self","target");
            if(key.equals("unit"))return List.of("points","fraction");
        }
        return switch(key) {
            case "effect_context", "context" -> com.cappleapple.mastery.client.ClientState.definitions().contexts().keySet().stream().sorted().toList();
            case "shape" -> List.of("default","circle","square","diamond","hexagon");
            case "section" -> List.of("north","northeast","east","southeast","south","southwest","west","northwest");
            case "visibility" -> List.of("available","always","discovered","invested","hidden");
            case "fill_direction" -> List.of("default","vertical","horizontal");
            case "operation" -> List.of("add_value","add_multiplied_base","add_multiplied_total");
            case "modifier" -> List.of("","mastery:native_spell");
            case "key" -> List.of("active_capacity","modifier_slots");
            case "event" -> List.of("damage","kill","block","block_break","craft","smelt","brew","consume","travel","advancement");
            case "slot" -> List.of("mainhand","offhand","hand","head","chest","legs","feet","armor","body","any");
            case "scale" -> List.of("none","damage","distance","amount");
            case "type" -> path.contains("effects")||kind.equals("effects")?List.of("mastery:attribute","mastery:bonus","mastery:spell_modifier","mastery:unlock_spell","mastery:unlock_context","mastery:on_usage","mastery:trigger","mastery:crafting_attribute","mastery:crafting_food","mastery:crafting_potion","mastery:placed_comfort"):
                    path.contains("requirements")||path.contains("condition")||kind.equals("requirements")?List.of("mastery:tree_level","mastery:node_rank","mastery:world_tier","mastery:advancement","mastery:book_unlocked","mastery:condition"):
                    List.of("passive","active","modifier","synergy","keystone","capstone","utility");
            default -> List.of();
        };
    }
    public static String label(String key) {
        if(key.equals("and"))return "All of (AND)";
        if(key.equals("or"))return "Any of (OR)";
        if(key.equals("book_token"))return "Skill Book unlock token";
        if(key.equals("level"))return "Level";
        String text=key.replace('_',' ');return text.isEmpty()?"Entry":Character.toUpperCase(text.charAt(0))+text.substring(1);
    }
    public static String hint(String key) {
        return switch(key) {
            case "icon" -> "Choose any loaded PNG resource, registered item, or native spell icon. Textures from resource packs and all namespaces are included.";
            case "costs" -> "Nested AND/OR purchase costs: specialization points, vanilla experience points, and inventory items. Reset inherits parent costs or the legacy point cost.";
            case "cost_depth_percent","depth_percent" -> "Additional cost fraction per graph depth, rounded up. 0.1 adds 10% per depth; leaf depth_percent overrides the inherited value.";
            case "effect_context" -> "Attribute bonuses apply only while this combat context is active. Empty applies with any weapon.";
            case "dependencies" -> "Add node/rank conditions or nested AND/OR groups. Top-level conditions must all be met.";
            case "points_per_award" -> "Points granted every point_every levels. Maximum level is calculated from all rank costs.";
            case "point_every" -> "Levels between point awards. Use 5 for one award every five levels.";
            case "charge" -> "Extra hold time and changes to the existing native spell.";
            case "modifier_slots" -> "Base slots plus growth per spell level, limited by the maximum.";
            case "hold_delay_ms" -> "Milliseconds held before shaking, fill, and dings begin. Default: 500. The fill then takes one second.";
            case "appearance" -> "Node shape and optional name label. Default nodes are circles showing only an icon.";
            case "show_name" -> "Show a name beneath the node. Hover tooltips always include the name.";
            case "theme" -> "External node outlines and connections. Internal borders keep their node type.";
            case "unlock" -> "Hold-to-level fill direction and sounds. Default inherits the parent setting.";
            case "levels_per_rank" -> "Additional native spell levels for each purchased rank after the first.";
            case "max_rank" -> "Maximum number of purchases of this node.";
            case "book_token" -> "Consume a matching Skill Book to reveal this branch.";
            case "shape" -> "Node outline: circle, square, diamond, or hexagon.";
            case "section" -> "Initial placement; growth points away from the map center.";
            case "gradient" -> "0 creates a sharp boundary; 1 blends across the whole border.";
            case "ticks_per_level","instant_base_ticks","burst_interval_ticks" -> "Duration in game ticks. 20 ticks = 1 second.";
            case "contexts" -> "Legacy combat classifications; spell sets now follow the selected hotbar position.";
            case "chance" -> "0 to 1 probability: 0.25 means 25%. Trigger and crafting rolls also add mastery:proc_chance; action chances are independent.";
            case "cooldown" -> "Minimum game ticks between successful uses of this trigger. 20 ticks = 1 second.";
            case "conditions" -> "Every condition must pass. Health can use raw points or a fraction of maximum health.";
            case "actions","tick_actions","threshold_actions" -> "Actions run in list order. Choose a target, then configure the action; nested conditions restrict it further.";
            case "target" -> "Self is the skill owner. Target is the combat counterpart or keyword bearer. Nearby selects around its center; aim follows the owner look direction.";
            case "unit" -> "points uses health points (2 = one heart). fraction uses 0 to 1 (0.25 = 25% of maximum).";
            case "threshold" -> "Reaching this number of stacks runs the keyword's threshold actions.";
            case "consume_stacks" -> "Remove the threshold stacks when threshold actions run.";
            case "duration","tick_interval","added_duration" -> "Time in game ticks. 20 ticks = one second.";
            case "per_stack" -> "Scale this damage action's amount by the current keyword stacks.";
            case "nutrition_bonus","saturation_bonus","buff_strength_bonus","buff_duration_bonus","meal_strength_bonus","meal_duration_bonus","duration_bonus" -> "Additional fraction per active skill rank. 0.25 adds 25%; 1 adds 100%.";
            case "amplifier_bonus" -> "Additional potion strength tiers per active skill rank. 1 turns level I into level II.";
            case "damage_per_level" -> "Fraction of weapon damage added per enchantment level. 0.1 means +10% per level.";
            case "weapon_attribute" -> "Registered attribute that adds a fraction of weapon damage as this element.";
            case "power_attribute" -> "Registered attribute that increases this school's weapon damage and native Iron spells. mastery:elemental_damage boosts every school.";
            case "damage_modifiers" -> "Map mob-group definition IDs to signed fractions. 0.25 adds 25% damage; -0.25 reduces it 25%.";
            case "potency_attribute" -> "Registered attribute that increases positive mob-group modifiers only.";
            case "mitigation_attribute" -> "Registered attribute that reduces negative mob-group modifiers only. 1 fully cancels a penalty.";
            case "attunement_attribute" -> "Registered attribute that amplifies both this damage type bonuses and penalties against mob groups.";
            case "element" -> "Existing damage-type definition or native school. Weapon classifications use it for unconverted weapon damage; actions use it for damage or healing.";
            case "conversion_attribute" -> "Fraction of physical weapon damage converted to this element. Multiple fractions above 100% are normalized together.";
            case "comfort_type" -> "Comfort category passed to optional Needs Not Necessities integration. No hard dependency is required.";
            default -> "Optional fields can be removed to use their default. Changes apply when you save the definition.";
        };
    }
}
