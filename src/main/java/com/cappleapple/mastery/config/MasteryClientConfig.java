package com.cappleapple.mastery.config;
import net.neoforged.neoforge.common.ModConfigSpec;
/** Local display preferences, never synchronized as progression policy. */
public final class MasteryClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ANIMATIONS;
    public static final ModConfigSpec.DoubleValue ANIMATION_SPEED;
    public static final ModConfigSpec.IntValue MINIMUM_NODE_WIDTH;
    static {
        var builder=new ModConfigSpec.Builder();
        ANIMATIONS=builder.comment("Animate skill branches expanding and collapsing.").define("treeAnimations",true);
        ANIMATION_SPEED=builder.comment("Animation speed multiplier. 1 takes 250 ms; larger values are faster.").defineInRange("treeAnimationSpeed",1.0,.1,10.0);
        MINIMUM_NODE_WIDTH=builder.comment("Minimum node diameter/width in GUI pixels when zoomed out. Icons scale with nodes; nearby nodes stay separated.").defineInRange("minimumNodeWidth",20,8,224);
        SPEC=builder.build();
    }
    private MasteryClientConfig(){}
}
