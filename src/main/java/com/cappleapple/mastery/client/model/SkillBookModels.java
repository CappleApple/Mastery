package com.cappleapple.mastery.client.model;

import com.cappleapple.mastery.client.ClientState;
import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.items.SkillBookItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** A generated book remains a thin item; ordinary baked passes stamp the current node icon on both covers. */
@EventBusSubscriber(modid="mastery",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class SkillBookModels {
    private static final ResourceLocation BOOK=ResourceLocation.fromNamespaceAndPath("mastery","skill_book");
    private static final float LEFT=5/16f,RIGHT=12/16f,BOTTOM=5/16f,TOP=12/16f;
    private SkillBookModels() {}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        var key=ModelResourceLocation.inventory(BOOK);
        var original=event.getModels().get(key);
        if(original!=null) event.getModels().put(key,new DynamicBook(original));
    }
    private static class BookWrapper extends BakedModelWrapper<BakedModel> {
        BookWrapper(BakedModel original) { super(original); }
        @Override public BakedModel applyTransform(ItemDisplayContext context,PoseStack pose,boolean leftHand) {
            originalModel.applyTransform(context,pose,leftHand); return this;
        }
    }
    private static final class DynamicBook extends BookWrapper {
        private DefinitionSet definitions;
        private final Map<String,BakedModel> variants=new LinkedHashMap<>(32,.75f,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String,BakedModel> eldest) { return size()>512; }
        };
        private final ItemOverrides overrides=new ItemOverrides() {
            @Override public BakedModel resolve(BakedModel model,ItemStack stack,@Nullable ClientLevel level,@Nullable LivingEntity entity,int seed) {
                DefinitionSet latest=ClientState.definitions();
                if(definitions!=latest) { definitions=latest; variants.clear(); }
                String token=SkillBookItem.token(stack);
                if(token.isBlank()) return originalModel;
                return variants.computeIfAbsent(token,key -> SkillBookIconLookup.find(latest,key)
                        .map(node -> stamped(originalModel,node.icon())).orElse(originalModel));
            }
        };
        DynamicBook(BakedModel original) { super(original); }
        @Override public ItemOverrides getOverrides() { return overrides; }
    }
    private static BakedModel stamped(BakedModel base,String icon) {
        ResourceLocation id=ResourceLocation.tryParse(icon);
        if(id==null) return base;
        Minecraft minecraft=Minecraft.getInstance();
        List<BakedModel> stamps=new ArrayList<>();
        if(BuiltInRegistries.ITEM.containsKey(id)) {
            ItemStack item=new ItemStack(BuiltInRegistries.ITEM.get(id));
            if(item.isEmpty()) return base;
            BakedModel model=minecraft.getItemRenderer().getModel(item,minecraft.level,null,0);
            for(BakedModel pass:model.getRenderPasses(item,false)) {
                var random=RandomSource.create(42);
                Set<BakedQuad> faces=new LinkedHashSet<>(pass.getQuads(null,null,random));
                random.setSeed(42); faces.addAll(pass.getQuads(null,Direction.SOUTH,random));
                List<BakedQuad> quads=new ArrayList<>();
                for(BakedQuad face:faces) if(face.getDirection()==Direction.SOUTH) {
                    int color=face.isTinted()?minecraft.getItemColors().getColor(item,face.getTintIndex()):-1;
                    quads.add(flatten(face,color,false,quads.size()/2));
                    quads.add(flatten(face,color,true,quads.size()/2));
                }
                if(!quads.isEmpty()) stamps.add(new Stamp(base,quads,pass.getRenderTypes(item,false)));
            }
            // Special item renderers may expose no face geometry; their particle sprite is the available flat image.
            if(stamps.isEmpty()) stamps.add(spriteStamp(base,model.getParticleIcon(),TextureAtlas.LOCATION_BLOCKS));
        } else if(com.cappleapple.mastery.client.gui.IconResources.texture(icon)!=null) {
            stamps.add(spriteStamp(base,UnitTextureAtlasSprite.INSTANCE,com.cappleapple.mastery.client.gui.IconResources.texture(icon)));
        }
        if(stamps.isEmpty()) return base;
        return new BookWrapper(base) {
            @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous) {
                var passes=new ArrayList<>(originalModel.getRenderPasses(stack,fabulous)); passes.addAll(stamps); return passes;
            }
            @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
        };
    }
    private static BakedModel spriteStamp(BakedModel base,TextureAtlasSprite sprite,ResourceLocation texture) {
        return new Stamp(base,List.of(face(sprite,false),face(sprite,true)),List.of(RenderType.entityCutout(texture)));
    }
    private static final class Stamp extends BookWrapper {
        private final List<BakedQuad> quads;
        private final List<RenderType> renderTypes;
        Stamp(BakedModel original,List<BakedQuad> quads,List<RenderType> renderTypes) {
            super(original); this.quads=List.copyOf(quads); this.renderTypes=List.copyOf(renderTypes);
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource random) { return side==null?quads:List.of(); }
        @Override public List<RenderType> getRenderTypes(ItemStack stack,boolean fabulous) { return renderTypes; }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous) { return List.of(this); }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
    private static BakedQuad face(TextureAtlasSprite sprite,boolean back) {
        int[] vertices=new int[32];
        float[] xs={LEFT,RIGHT,RIGHT,LEFT},ys={BOTTOM,BOTTOM,TOP,TOP};
        float[] us={sprite.getU0(),sprite.getU1(),sprite.getU1(),sprite.getU0()},vs={sprite.getV1(),sprite.getV1(),sprite.getV0(),sprite.getV0()};
        for(int i=0;i<4;i++) {
            int start=i*8;
            vertices[start]=Float.floatToRawIntBits(back?LEFT+RIGHT-xs[i]:xs[i]);
            vertices[start+1]=Float.floatToRawIntBits(ys[i]); vertices[start+2]=Float.floatToRawIntBits(depth(back,0));
            vertices[start+3]=-1; vertices[start+4]=Float.floatToRawIntBits(us[i]); vertices[start+5]=Float.floatToRawIntBits(vs[i]);
        }
        Direction direction=back?Direction.NORTH:Direction.SOUTH;
        ClientHooks.fillNormal(vertices,direction);
        return new BakedQuad(vertices,-1,direction,sprite,false,false);
    }
    private static BakedQuad flatten(BakedQuad source,int tint,boolean back,int layer) {
        int[] vertices=source.getVertices().clone(); int stride=vertices.length/4;
        for(int i=0;i<4;i++) {
            int start=i*stride;
            float x=LEFT+(RIGHT-LEFT)*Math.clamp(Float.intBitsToFloat(vertices[start]),0,1);
            float y=BOTTOM+(TOP-BOTTOM)*Math.clamp(Float.intBitsToFloat(vertices[start+1]),0,1);
            vertices[start]=Float.floatToRawIntBits(back?LEFT+RIGHT-x:x); vertices[start+1]=Float.floatToRawIntBits(y);
            vertices[start+2]=Float.floatToRawIntBits(depth(back,layer));
            vertices[start+3]=tinted(vertices[start+3],tint);
        }
        Direction direction=back?Direction.NORTH:Direction.SOUTH;
        ClientHooks.fillNormal(vertices,direction);
        return new BakedQuad(vertices,-1,direction,source.getSprite(),false,false);
    }
    private static float depth(boolean back,int layer) { return ((back?7.5f:8.5f)+(back?-1:1)*(.002f+Math.min(layer,32)*.001f))/16f; }
    private static int tinted(int abgr,int argb) {
        int a=((abgr>>>24)&255)*((argb>>>24)&255)/255;
        int r=(abgr&255)*((argb>>>16)&255)/255;
        int g=((abgr>>>8)&255)*((argb>>>8)&255)/255;
        int b=((abgr>>>16)&255)*(argb&255)/255;
        return a<<24|b<<16|g<<8|r;
    }
}
