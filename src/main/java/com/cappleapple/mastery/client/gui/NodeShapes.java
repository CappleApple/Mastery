package com.cappleapple.mastery.client.gui;
import com.cappleapple.mastery.data.NodeAppearance.Shape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/** Pixel-aligned spans queued into one GUI batch. Callers flush before drawing icons or text. */
public final class NodeShapes {
    private NodeShapes(){}
    public static void fill(GuiGraphics graphics,Shape shape,int radius,int color){fillProgress(graphics,shape,radius,color,1,false);}
    public static void fillProgress(GuiGraphics graphics,Shape shape,int radius,int color,double progress,boolean horizontal){
        var vertices=graphics.bufferSource().getBuffer(RenderType.gui());
        var pose=graphics.pose().last().pose();
        int limit=(int)Math.round(-radius+2*radius*Math.clamp(progress,0,1));
        for(int y=-radius;y<radius;y++){
            if(!horizontal&&y< -limit)continue;
            double half=shape.halfWidth(y+.5,radius);
            int left=(int)Math.ceil(-half-.5),right=(int)Math.ceil(half-.5);
            if(horizontal)right=Math.min(right,limit);
            if(right>left)rectangle(vertices,pose,left,y,right,y+1,color);
        }
    }
    public static void outlineProgress(GuiGraphics graphics,Shape shape,int radius,int thickness,int color,double progress) {
        if(progress<=0)return;
        var vertices=graphics.bufferSource().getBuffer(RenderType.gui());var pose=graphics.pose().last().pose();
        for(var pixel:com.cappleapple.mastery.layout.ShapeOutline.pixels(shape,radius,thickness)) {
            if(pixel.fraction()>progress)break;
            rectangle(vertices,pose,pixel.x(),pixel.y(),pixel.x()+1,pixel.y()+1,color);
        }
    }
    public static void rectangle(GuiGraphics graphics,int left,int top,int right,int bottom,int color) {
        rectangle(graphics.bufferSource().getBuffer(RenderType.gui()),graphics.pose().last().pose(),left,top,right,bottom,color);
    }
    private static void rectangle(VertexConsumer vertices,Matrix4f pose,int left,int top,int right,int bottom,int color) {
        vertices.addVertex(pose,right,bottom,0).setColor(color);
        vertices.addVertex(pose,right,top,0).setColor(color);
        vertices.addVertex(pose,left,top,0).setColor(color);
        vertices.addVertex(pose,left,bottom,0).setColor(color);
    }
}
