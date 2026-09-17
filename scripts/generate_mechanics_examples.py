"""Create the named mechanics examples without deleting or replacing existing definitions."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/data/mastery/mastery/nodes'
def generate():
    added=0
    def node(id,tree,name,effects,description,ranks=1,parent=None):
        nonlocal added
        path=ROOT/(id+'.json')
        if path.exists():return
        value=dict(tree='mastery:'+tree,name=name,description=description,icon='minecraft:book',type='passive',cost=1,max_rank=ranks,level=0,dependencies=[parent or 'mastery:'+tree+'/foundation'],effects=effects,visibility='available',toggleable=True)
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8');added+=1
    def attr(id,amount):return dict(type='mastery:attribute',attribute='mastery:'+id,amount=amount,operation='add_value')
    for school in ['fire','ice','lightning','holy','ender','blood','evocation','nature','eldritch']:
        node(school+'/weapon_aspect',school,school.title()+' Infusion',[attr(school+'_weapon_damage',.1)],'Each rank adds 10% of base weapon damage as '+school+' damage.',5)
        node(school+'/conversion',school,school.title()+' Conversion',[attr(school+'_conversion',.2)],'Each rank replaces 20% of base weapon damage with '+school+' damage.',5,'mastery:'+school+'/weapon_aspect')
    for key,description in [('attunement','Amplify favorable and unfavorable Holy matchups by 10% per rank.'),('potency','Amplify favorable Holy matchups by 10% per rank.'),('mitigation','Reduce unfavorable Holy matchup penalties by 10% per rank.')]:
        node('holy/'+key,'holy','Holy '+key.title(),[attr('holy_'+key,.1)],description,5)
    node('evocation/elemental_power','evocation','Elemental Power',[attr('elemental_damage',.05)],'Each rank adds 5% elemental damage and typed healing across all schools.',5)
    node('evocation/proc_chance','evocation','Opportune Casting',[attr('proc_chance',.02)],'Each rank adds two percentage points to combat and crafting proc chances.',5)
    node('fire/scorch','fire','Scorch',[dict(type='mastery:trigger',trigger='mastery:scorch_on_hit')],'Hits can stack Scorch. Its damage over time builds toward a fiery burst.')
    node('lightning/thunder','lightning','Gathering Thunder',[dict(type='mastery:trigger',trigger='mastery:thunder_on_hit')],'Hits can gather Thunder on you. Full stacks discharge at nearby enemies.')
    node('smithing/tempered_edge','smithing','Tempered Edge',[dict(type='mastery:crafting_attribute',item_tag='minecraft:swords',attribute='minecraft:generic.attack_damage',amount=.1,operation='add_multiplied_base',chance=.25)],'A 25% chance to add 10% weapon attack damage per rank to crafted swords.',3)
    node('alchemy/potent_brewing','alchemy','Potent Brewing',[dict(type='mastery:crafting_potion',amplifier_bonus=1,duration_bonus=.1,chance=.1)],'A 10% chance to add one potion tier and 10% duration per rank when extracting a brewed potion.',2)
    node('survival/prepared_meals','survival','Prepared Meals',[dict(type='mastery:crafting_food',nutrition_bonus=.1,saturation_bonus=.1,buff_duration_bonus=.1,meal_strength_bonus=.1,meal_duration_bonus=.1,chance=.5)],'A 50% chance to improve crafted food. Also improves Needs Not Necessities meal buffs when installed.',3)
    node('engineering/comfortable_construction','engineering','Comfortable Construction',[dict(type='mastery:placed_comfort',block_tag='minecraft:beds',amount=1,radius=8,comfort_type='mastery_crafted',chance=1)],'Placed beds provide one extra comfort per rank within eight blocks when Needs Not Necessities is installed.',3)
    return added
if __name__=='__main__':print(f'Added {generate()} mechanics example nodes; existing definitions were preserved.')
