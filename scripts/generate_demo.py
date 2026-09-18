"""Generate Mastery's bundled progression data for existing Iron's Spells content.

The managed directories below contain only this generator's demo definitions.
This script does not create spells, entities, statuses, or item-slot assignments.
"""
import json
from pathlib import Path
ROOT = (Path(__file__).resolve().parents[1] / 'src/main/resources/data').resolve()


def write(path, data):
    path = ROOT / path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


def resource(kind, name, data, namespace='mastery'):
    if kind == 'trees' and name in ('one_handed', 'two_handed', 'dual_wield', 'bow', 'crossbow'):
        data['effect_context'] = 'mastery:' + name
    if kind == 'trees' and name in schools:
        data['damage_filter'] = dict(elements=['irons_spellbooks:' + name])
    write(f'{namespace}/mastery/{kind}/{name}.json', data)


# Resolve every cleanup target under the declared generated resource root first.
for relative in ['mastery/mastery/trees', 'mastery/mastery/nodes', 'mastery/mastery/synergies',
                 'mastery/mastery/abilities', 'mastery/mastery/contexts', 'mastery/mastery/xp_sources',
                 'irons_spellbooks/mastery/spells']:
    directory = (ROOT / relative).resolve()
    if not directory.is_relative_to(ROOT):
        raise ValueError(f'Unsafe generated resource directory: {directory}')
    if directory.exists():
        for path in directory.rglob('*.json'):
            if not path.resolve().is_relative_to(ROOT):
                raise ValueError(f'Unsafe generated resource file: {path}')
            path.unlink()
for relative in ['mastery/tags/item/two_handed_weapons.json', 'mastery/tags/item/wands.json',
                 'mastery/tags/item/staves.json', 'mastery/tags/item/capacity_books.json',
                 'curios/tags/item/spellbook.json', 'mastery/recipe/technique_book.json']:
    path = (ROOT / relative).resolve()
    if not path.is_relative_to(ROOT):
        raise ValueError(f'Unsafe obsolete demo resource: {path}')
    path.unlink(missing_ok=True)

# Names, schools and spell IDs checked against Iron's Spells 1.21.1-3.16.3 sources.
schools = {
    'fire': ('Fire', 'blaze_powder', 'west', 'fireball', 'Fireball'),
    'ice': ('Ice', 'snowball', 'north', 'icicle', 'Icicle'),
    'lightning': ('Lightning', 'lightning_rod', 'north', 'lightning_bolt', 'Lightning Bolt'),
    'holy': ('Holy', 'golden_apple', 'north', 'guiding_bolt', 'Guiding Bolt'),
    'ender': ('Ender', 'ender_pearl', 'east', 'magic_missile', 'Magic Missile'),
    'blood': ('Blood', 'redstone', 'west', 'blood_slash', 'Blood Slash'),
    'evocation': ('Evocation', 'emerald', 'east', 'fang_strike', 'Fang Strike'),
    'nature': ('Nature', 'oak_sapling', 'south', 'poison_arrow', 'Poison Arrow'),
    'eldritch': ('Eldritch', 'echo_shard', 'north', 'eldritch_blast', 'Eldritch Blast'),
}
trees = {
    'one_handed': ('One-Handed', 'iron_sword', 'west'),
    'two_handed': ('Two-Handed', 'iron_sword', 'west'),
    'dual_wield': ('Dual Wield', 'golden_sword', 'west'),
    'shields': ('Shields', 'shield', 'west'),
    'bow': ('Bow', 'bow', 'east'),
    'crossbow': ('Crossbow', 'crossbow', 'east'),
    'mobility': ('Mobility', 'feather', 'east'),
    'mining': ('Mining', 'iron_pickaxe', 'south'),
    'survival': ('Survival', 'campfire', 'east'),
    'smithing': ('Smithing', 'anvil', 'south'),
    'alchemy': ('Alchemy', 'brewing_stand', 'south'),
    'engineering': ('Engineering', 'redstone', 'south'),
}
trees.update({school: fields[:3] for school, fields in schools.items()})
for id, (name, icon, section) in trees.items():
    description = (f'Deal damage classified by Iron\'s Spells as {name} magic to earn XP.' if id in schools
                   else f'Practice {name.lower()} to develop this proficiency.')
    resource('trees', id, dict(name=name, description=description, icon='minecraft:' + icon,
        unlock=dict(fill_direction="default", progress_sound="default", complete_sound="default"),
        section=section, xp_base=25, xp_growth=10, point_every=1, points_per_award=1,
        tier_caps=[dict(tier=t, max_level=min(100, (t + 1) * 20), max_rank=5,
                        max_depth=4, modifier_slots=2 + t, active_capacity=4 + t * 2) for t in range(5)]))

contexts = {
    'shield': (100, {'blocking': True, 'or': [{'item_tag': 'mastery:shields'}, {'offhand_tag': 'mastery:shields'}]}),
    'two_handed': (80, {'provider_only': True}),
    'dual_wield': (75, {'dual_wield': True}),
    'bow': (70, {'item_tag': 'mastery:bows'}),
    'crossbow': (70, {'item_tag': 'mastery:crossbows'}),
    'staff': (60, {'item_tag': 'irons_spellbooks:staff'}),
    'throwing': (50, {'item_tag': 'mastery:throwing_weapons'}),
    'one_handed': (40, {'item_tag': 'mastery:one_handed_weapons'}),
    'unarmed': (0, {'empty': True}),
    'spells': (-100, {}),
}
for id, (priority, condition) in contexts.items():
    resource('contexts', id, dict(name=id.replace('_', ' ').title(), priority=priority, condition=condition))

tags = {
    'one_handed_weapons': ['#minecraft:swords'], 'shields': ['minecraft:shield'],
    'bows': ['minecraft:bow'], 'crossbows': ['minecraft:crossbow'], 'throwing_weapons': ['minecraft:trident'],
    'smithing_materials': ['minecraft:iron_ingot', 'minecraft:gold_ingot', 'minecraft:copper_ingot'],
    'engineering_items': ['minecraft:piston', 'minecraft:sticky_piston', 'minecraft:observer',
                          'minecraft:dispenser', 'minecraft:dropper', 'minecraft:repeater', 'minecraft:comparator'],
}
for tag, values in tags.items():
    write(f'mastery/tags/item/{tag}.json', dict(replace=False, values=values))
write('mastery/tags/block/mineable.json', dict(replace=False, values=['#minecraft:mineable/pickaxe']))


def node(id, tree, name, **fields):
    data = dict(tree='mastery:' + tree, name=name, description=fields.pop('description', name + '.'),
                icon='minecraft:' + trees[tree][1], type='passive', cost=1, max_rank=1, level=0,
                dependencies=[], effects=[], visibility="available")
    data.update(fields)
    resource('nodes', id, data)


def attribute(id, amount):
    return dict(type='mastery:attribute', attribute=id, amount=amount, operation='add_value')


attributes = {
    'one_handed': ('minecraft:generic.attack_damage', .5),
    'two_handed': ('minecraft:generic.attack_damage', 1),
    'dual_wield': ('minecraft:generic.attack_speed', .1),
    'shields': ('minecraft:generic.armor', 1),
    'bow': ('apothic_attributes:arrow_damage', .05),
    'crossbow': ('apothic_attributes:arrow_velocity', .05),
    'mobility': ('minecraft:generic.movement_speed', .003),
    'mining': ('minecraft:player.block_break_speed', .08),
    'survival': ('minecraft:generic.max_health', 1),
    'smithing': ('minecraft:generic.armor_toughness', .5),
    'alchemy': ('minecraft:generic.max_health', 1),
    'engineering': ('minecraft:player.block_interaction_range', .2),
}
attributes.update({school: ('irons_spellbooks:' + school + '_spell_power', .05) for school in schools})
for tree, (attribute_id, amount) in attributes.items():
    node(tree + '/foundation', tree, trees[tree][0] + ' Practice', max_rank=3,
         effects=[attribute(attribute_id, amount)],
         description=f'Each rank adds {amount:g} to {attribute_id.split(":")[-1].replace("_", " ")}.')

for school, (name, icon, section, spell, title) in schools.items():
    native = 'irons_spellbooks:' + spell
    resource('spells', spell, dict(spell=native, tree='mastery:' + school, name=title,
        description=f'Prepare {title} in a spell slot.',
        icon='minecraft:' + icon, contexts=['mastery:' + context for context in contexts],
        modifier_slots='default', level=1, charge='default'), namespace='irons_spellbooks')
    node(spell, school, title, type='active', spell=native, max_rank=5 if spell=='fireball' else 1,
         description=f'Unlock {title} at level 1.' + (' Further ranks let you charge a larger fireball.' if spell=='fireball' else ''))
    node(spell + '/efficiency', school, title + ' Efficiency', type='modifier', spell=native,
         modifier='mastery:native_spell', max_rank=3, dependencies=['mastery:' + spell], toggleable=True,
         effects=[dict(type='mastery:spell_modifier', mana_multiplier=.9, cooldown_multiplier=.9,
                       cast_time_multiplier=.9)],
         description='Each enabled rank multiplies mana cost, cooldown and cast time by 0.9.')
    node(spell + '/empowered', school, title + ' Spell Level', type='modifier', spell=native,
         modifier='mastery:native_spell', max_rank=3, dependencies=['mastery:' + spell], toggleable=True,
         effects=[dict(type='mastery:spell_modifier', spell_level=1)],
         description='Each enabled rank adds one native casting level, composing with native spell-level bonuses.')
node('fire/extra_slot', 'fire', 'Additional Prepared Spell', dependencies=['mastery:fire/foundation'],
     effects=[dict(type='mastery:bonus', key='active_capacity', amount=1)],
     description='Add one prepared-spell capacity beyond capacity supplied by native books and equipment.')

node('fire/secret_studies', 'fire', 'Secret Fire Studies', dependencies=['mastery:fire/foundation'],
     book_token='mastery:fire_secrets', effects=[attribute('irons_spellbooks:fire_spell_power', .1)],
     description='A skill book reveals this branch. Purchase it to gain 0.1 Fire spell power.')
node('fire/secret_capacity', 'fire', 'Secret Spell Preparation', dependencies=['mastery:fire/secret_studies'],
     effects=[dict(type='mastery:bonus', key='active_capacity', amount=1)],
     description='An additional prepared-spell capacity within the branch revealed by the Fire Secrets skill book.')

synergies = [
    ('spellblade_practice', 'Flame Blade', 'two_handed', 'fire',
     attribute('minecraft:generic.attack_damage', .5)),
    ('steady_aim', 'Steady Aim', 'bow', 'crossbow', attribute('minecraft:generic.knockback_resistance', .05)),
    ('arcane_resilience', 'Arcane Resilience', 'holy', 'shields', attribute('irons_spellbooks:spell_resist', .05)),
    ('ore_mastery', 'Ore Mastery', 'mining', 'smithing', attribute('minecraft:player.block_break_speed', .2)),
]
for id, name, owner, other, effect in synergies:
    value = dict(tree='mastery:' + owner, name=name,
        description=f'A passive bonus earned through {trees[owner][0]} and {trees[other][0]} practice.',
        icon='minecraft:' + trees[other][1], type='synergy', cost=1, max_rank=1, level=3, visibility='available',
        dependencies=[{'node': 'mastery:' + owner + '/foundation', 'rank': 1},
                      {'node': 'mastery:' + other + '/foundation', 'rank': 1}],
        requirements=[{'type': 'mastery:tree_level', 'tree': 'mastery:' + other, 'level': 3}], effects=[effect])
    if id == 'spellblade_practice':
        value['description'] = 'Combine Two-Handed and Fire practice to unlock a separate Flame Blade specialization.'
        value['description'] = ''
        value['root_tree'] = dict(id='mastery:flame_blade', xp_base=25, xp_growth=10, point_every=1,
                                  xp_attribute='mastery:flame_blade_experience_gain', section='southeast')
        value['effects'] = [dict(attribute('mastery:fire_weapon_damage', .1), context='mastery:two_handed', display_name='Two-Handed Weapon Fire Damage')]
    resource('synergies', id, value)

node('flame_blade/flame_edge', 'two_handed', 'Flame Edge', dependencies=['mastery:spellblade_practice'],
     max_rank=3, icon='minecraft:blaze_powder', effects=[attribute('mastery:fire_weapon_damage', .1)],
     description='Each rank adds 10% of weapon damage as Fire.')
node('flame_blade/tempered_flame', 'two_handed', 'Tempered Flame', dependencies=['mastery:flame_blade/flame_edge'],
     max_rank=3, icon='minecraft:blaze_powder', effects=[attribute('mastery:fire_damage', .1)], description='Each rank adds 10% Fire damage.')


resource('nodes', 'flame_blade/flaming_strike', dict(tree='mastery:flame_blade', name='Flaming Strike',
    description='', icon='irons_spellbooks:textures/gui/spell_icons/flaming_strike.png', type='active',
    cost=1, max_rank=5, level=0, dependencies=[dict(node='mastery:spellblade_practice', rank=1)],
    effects=[dict(type='mastery:unlock_spell', spell='irons_spellbooks:flaming_strike', level=1, levels_per_rank=1)],
    visibility='available', spell='irons_spellbooks:flaming_strike'))
resource('spells', 'flaming_strike', dict(spell='irons_spellbooks:flaming_strike', tree='mastery:flame_blade',
    name='Flaming Strike', description='', icon='irons_spellbooks:textures/gui/spell_icons/flaming_strike.png',
    contexts=['mastery:' + context for context in contexts], modifier_slots='default', level=1, charge='default'),
    namespace='irons_spellbooks')


def xp(id, tree, event, amount, condition, scale='none', **extra):
    resource('xp_sources', id, dict(tree='mastery:' + tree, event=event, amount=amount,
                                   scale=scale, condition=condition, **extra))


for tree, context in [('one_handed', 'one_handed'), ('two_handed', 'two_handed'), ('dual_wield', 'dual_wield')]:
    xp(tree + '_damage', tree, 'damage', 1, {'context': 'mastery:' + context, 'projectile': False, 'school': ''}, 'damage')
    xp(tree + '_kills', tree, 'kill', 6, {'context': 'mastery:' + context, 'projectile': False, 'school': ''})
xp('bow_damage', 'bow', 'damage', 1, {'projectile': True, 'weapon_context': 'mastery:bow', 'school': ''}, 'damage')
xp('bow_range', 'bow', 'damage', 2, {'projectile': True, 'weapon_context': 'mastery:bow', 'school': '', 'min_distance': 20})
xp('crossbow_damage', 'crossbow', 'damage', 1, {'projectile': True, 'weapon_context': 'mastery:crossbow', 'school': ''}, 'damage')
xp('shield_blocks', 'shields', 'block', 1, {}, 'damage')
for school in schools:
    xp(school + '_damage', school, 'damage', 1, {'school': 'irons_spellbooks:' + school}, 'damage')
xp('flame_blade_damage', 'flame_blade', 'damage', 1, {'context': 'mastery:two_handed', 'melee': True, 'projectile': False, 'school': 'irons_spellbooks:fire'}, 'damage')
xp('mining_blocks', 'mining', 'block_break', 2, {'block_tag': 'mastery:mineable'})
xp('smithing_craft', 'smithing', 'craft', 4, {'item_tag': 'mastery:smithing_materials'}, 'amount')
xp('smithing_smelt', 'smithing', 'smelt', 3, {'item_tag': 'mastery:smithing_materials'}, 'amount')
xp('engineering_craft', 'engineering', 'craft', 4, {'item_tag': 'mastery:engineering_items'}, 'amount')
xp('alchemy_brew', 'alchemy', 'brew', 5, {})
xp('mobility_travel', 'mobility', 'travel', 1, {'sprinting': True}, 'distance')
xp('survival_food', 'survival', 'consume', 2, {})
print(f'Generated {len(trees)} independent trees, 57 progression nodes plus the promoted Flame Blade tree and {len(schools)} native spell bindings.')
