package com.cappleapple.mastery.mixin.client;
import com.cappleapple.mastery.client.gui.KeywordHover;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Font.class)
public abstract class KeywordFontMixin {
    @Inject(method="drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",at=@At("HEAD"))
    private void mastery$keywordGlyphs(FormattedCharSequence text,float x,float y,int color,boolean shadow,Matrix4f matrix,MultiBufferSource buffers,Font.DisplayMode mode,int background,int light,CallbackInfoReturnable<Integer> callback) {
        KeywordHover.capture((Font)(Object)this,text,x,y,matrix);
    }
    @Inject(method="drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I",at=@At("HEAD"))
    private void mastery$keywordString(String text,float x,float y,int color,boolean shadow,Matrix4f matrix,MultiBufferSource buffers,Font.DisplayMode mode,int background,int light,boolean bidi,CallbackInfoReturnable<Integer> callback) {
        KeywordHover.capture((Font)(Object)this,FormattedCharSequence.forward(text,Style.EMPTY),x,y,matrix);
    }
}
