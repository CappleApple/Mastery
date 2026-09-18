package com.cappleapple.mastery.mechanics;
import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.spells.SpellService;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import static com.cappleapple.mastery.mechanics.MechanicsRules.*;

/** Rank-scaled keyword bonuses combine flat additions, then additive percentage increases. */
public final class KeywordModifiers {
    private KeywordModifiers() {}
    public static double adjust(ServerPlayer owner,String keyword,String stat,double base,DamageContext source) {
        double flat=0,percent=0;
        for(var node:MasteryRuntime.definitions().nodes().values()) {
            int rank=EffectService.effectiveRank(owner,node);
            if(rank<=0||!TreeModifiers.matches(node,source))continue;
            if(node.spellModifier()&&(!owner.getUUID().equals(source.caster())||!SpellService.activeModifiers(owner,source.spell()).contains(node.id())))continue;
            for(var raw:node.effects()) {
                JsonObject effect=raw;
                for(int depth=0;effect!=null&&effect.has("ref")&&depth<16;depth++)effect=MasteryRuntime.definitions().effects().get(effect.get("ref").getAsString());
                if(effect==null||!text(effect,"type","").equals("mastery:keyword_modifier")||!text(effect,"keyword","").equals(keyword))continue;
                flat+=number(effect,stat,0)*rank;percent+=number(effect,stat+"_percent",0)*rank;
            }
        }
        return Math.clamp((base+flat)*Math.max(0,1+percent),0,1000000);
    }
}
