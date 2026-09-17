package com.cappleapple.mastery.client.gui.editor;
import com.cappleapple.mastery.mixin.client.MultiLineEditBoxAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Native text input, scrolling, selection and clipboard behavior with JSON syntax colors. */
public final class JsonCodeEditor extends MultiLineEditBox {
    private final Font font;private String cached="";private List<JsonSyntax.Token> tokens=List.of();
    public JsonCodeEditor(Font font,int x,int y,int width,int height){super(font,x,y,width,height,Component.literal("Definition JSON"),Component.literal("JSON editor"));this.font=font;}
    @Override public void setValue(String value){super.setValue(value);setScrollAmount(0);}
    @Override protected void renderContents(GuiGraphics g,int mx,int my,float tick){
        String text=getValue();if(!text.equals(cached)){cached=text;tokens=JsonSyntax.tokenize(text);}
        var field=((MultiLineEditBoxAccessor)(Object)this).mastery$textField();int y=getY()+innerPadding(),baseX=getX()+innerPadding();
        for(var line:TextViews.lines(field)){
            if(withinContentAreaTopBottom(y,y+9)){
                int x=baseX;
                for(var token:tokens){int from=Math.max(line[0],token.start()),to=Math.min(line[1],token.end());if(from>=to)continue;
                    String part=text.substring(from,to);g.drawString(font,part,x,y,token.color(),false);x+=font.width(part);
                }
                if(field.hasSelection()){
                    var selected=TextViews.selection(field);int from=Math.max(line[0],selected[0]),to=Math.min(line[1],selected[1]);
                    if(from<to){int a=baseX+font.width(text.substring(line[0],from)),b=baseX+font.width(text.substring(line[0],to));g.fill(RenderType.guiTextHighlight(),a,y,b,y+9,0xFF0000FF);}
                }
                if(isFocused()&&net.minecraft.Util.getMillis()/300%2==0&&field.cursor()>=line[0]&&field.cursor()<=line[1]){
                    int cursor=baseX+font.width(text.substring(line[0],field.cursor()));g.fill(cursor,y-1,cursor+1,y+10,0xFFEAEAEA);
                }
            }y+=9;
        }
    }
    private static final class TextViews extends net.minecraft.client.gui.components.MultilineTextField {
        private TextViews(Font font){super(font,0);}
        static List<int[]> lines(net.minecraft.client.gui.components.MultilineTextField field){
            var result=new ArrayList<int[]>();for(StringView line:field.iterateLines())result.add(new int[]{line.beginIndex(),line.endIndex()});return result;
        }
        static int[] selection(net.minecraft.client.gui.components.MultilineTextField field){var selected=field.getSelected();return new int[]{selected.beginIndex(),selected.endIndex()};}
    }
}
