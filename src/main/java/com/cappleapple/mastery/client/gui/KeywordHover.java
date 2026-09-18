package com.cappleapple.mastery.client.gui;

import com.cappleapple.mastery.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.*;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import java.util.*;
import java.util.regex.Pattern;

/** Keyword hit regions follow rendered glyphs, including native item tooltips and guide pages. */
@EventBusSubscriber(modid="mastery",value=Dist.CLIENT)
public final class KeywordHover {
    private KeywordHover() {}
    private record Hit(String id,float left,float top,float right,float bottom) {
        boolean contains(double x,double y){return x>=left&&x<=right&&y>=top&&y<=bottom;}
    }
    private record Term(String id,Pattern pattern) {}
    private record TooltipFrame(Font font,List<ClientTooltipComponent> components,int x,int y,int width,int height,int anchorX,int anchorY) {
        boolean contains(int mx,int my) {
            boolean box=mx>=x-5&&mx<=x+width+5&&my>=y-5&&my<=y+height+5;
            boolean bridge=mx>=Math.min(anchorX,x)-5&&mx<=Math.max(anchorX,x)+5&&my>=Math.min(anchorY,y)-5&&my<=Math.max(anchorY,y)+height+5;
            return box||bridge;
        }
    }
    private static final List<Hit> HITS=new ArrayList<>();
    private static List<Term> terms=List.of();
    private static Object definitions;
    private static Screen screen;
    private static GuiGraphics graphics;
    private static TooltipFrame retained,pending;
    private static boolean sticky,replaying,explaining;
    private static int pendingStart;
    @SubscribeEvent public static void begin(ScreenEvent.Render.Pre event) {
        if(screen!=event.getScreen()){screen=event.getScreen();retained=null;}
        graphics=event.getGuiGraphics();HITS.clear();pending=null;
        sticky=retained!=null&&retained.contains(event.getMouseX(),event.getMouseY());
        if(!sticky)retained=null;
        if(definitions!=ClientState.definitions()) {
            definitions=ClientState.definitions();terms=ClientState.definitions().keywords().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e->new Term(e.getKey(),Pattern.compile("(?<![\\p{L}\\p{N}_])"+Pattern.quote(ScriptText.keywordName(e.getKey()))+"(?![\\p{L}\\p{N}_])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE))).toList();
        }
    }
    @SubscribeEvent public static void tooltip(RenderTooltipEvent.Pre event) {
        if(graphics==null||explaining||replaying)return;
        if(sticky){event.setCanceled(true);return;}
        int width=event.getComponents().stream().mapToInt(c->c.getWidth(event.getFont())).max().orElse(0);
        int height=event.getComponents().stream().mapToInt(c->c.getHeight()).sum()+(event.getComponents().size()==1?-2:0);
        var position=event.getTooltipPositioner().positionTooltip(event.getScreenWidth(),event.getScreenHeight(),event.getX(),event.getY(),width,height);
        pendingStart=HITS.size();
        pending=new TooltipFrame(event.getFont(),List.copyOf(event.getComponents()),position.x(),position.y(),width,height,event.getX(),event.getY());
    }
    public static void capture(Font font,FormattedCharSequence line,float x,float y,Matrix4f matrix) {
        if(graphics==null||explaining||terms.isEmpty())return;
        StringBuilder text=new StringBuilder();var offsets=new ArrayList<Float>();float[] width={0};
        line.accept((index,style,codepoint)->{
            String value=new String(Character.toChars(codepoint));
            for(int i=0;i<value.length();i++){text.append(style.isObfuscated()?' ':value.charAt(i));offsets.add(width[0]);}
            width[0]+=font.width(FormattedCharSequence.forward(value,style));return true;
        });offsets.add(width[0]);
        for(var term:terms) {
            var matches=term.pattern.matcher(text);
            while(matches.find()) {
                var start=matrix.transformPosition(x+offsets.get(matches.start()),y,0,new org.joml.Vector3f());
                var end=matrix.transformPosition(x+offsets.get(matches.end()),y+font.lineHeight,0,new org.joml.Vector3f());
                if(graphics.containsPointInScissor((int)start.x,(int)start.y))HITS.add(new Hit(term.id,start.x,start.y,end.x,end.y));
            }
        }
    }
    @SubscribeEvent public static void finish(ScreenEvent.Render.Post event) {
        if(sticky&&retained!=null) {
            replaying=true;
            try {
                var cached=retained;
                ((com.cappleapple.mastery.mixin.client.KeywordTooltipAccessor)(Object)graphics).mastery$renderTooltip(cached.font,cached.components,cached.anchorX,cached.anchorY,(w,h,x,y,tw,th)->new org.joml.Vector2i(cached.x,cached.y));
            }finally{replaying=false;}
        } else if(pending!=null) {
            var frame=pending;
            if(HITS.subList(Math.min(pendingStart,HITS.size()),HITS.size()).stream().anyMatch(h->h.left>=frame.x&&h.top>=frame.y&&h.right<=frame.x+frame.width&&h.bottom<=frame.y+frame.height+3))retained=frame;
        }
        String keyword=keywordAt(event.getMouseX(),event.getMouseY());
        if(!keyword.isBlank()) {
            explaining=true;
            graphics.pose().pushPose();graphics.pose().translate(0,0,400);
            try{graphics.renderComponentTooltip(Minecraft.getInstance().font,ScriptText.keyword(keyword),event.getMouseX(),retained==null?event.getMouseY():retained.y+retained.height+16);}
            finally{graphics.pose().popPose();explaining=false;}
        }
        graphics=null;
    }
    public static String keywordAt(double x,double y){return HITS.stream().filter(h->h.contains(x,y)).map(Hit::id).findFirst().orElse("");}
}
