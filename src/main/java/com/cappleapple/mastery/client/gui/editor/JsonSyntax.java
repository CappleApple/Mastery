package com.cappleapple.mastery.client.gui.editor;
import java.util.*;
/** Small JSON lexer; malformed text remains editable and strings honor escaped quotes. */
public final class JsonSyntax {
    public record Token(int start,int end,int color){}
    private JsonSyntax(){}
    public static List<Token> tokenize(String text){
        List<Token> result=new ArrayList<>();int i=0;
        while(i<text.length()){
            char c=text.charAt(i);int start=i++;int color=0xD4D4D4;
            if(c=='"'){
                boolean escaped=false;
                while(i<text.length()){char n=text.charAt(i++);if(n=='"'&&!escaped)break;if(n=='\\'&&!escaped)escaped=true;else escaped=false;}
                int next=i;while(next<text.length()&&Character.isWhitespace(text.charAt(next)))next++;
                color=next<text.length()&&text.charAt(next)==':'?0x9CDCFE:0xCE9178;
            }else if(Character.isDigit(c)||c=='-'){
                while(i<text.length()&&"0123456789.eE+-".indexOf(text.charAt(i))>=0)i++;color=0xB5CEA8;
            }else if(Character.isLetter(c)){while(i<text.length()&&Character.isLetter(text.charAt(i)))i++;color=0x569CD6;}
            else if("{}[]".indexOf(c)>=0)color=0xFFD166;
            result.add(new Token(start,i,color));
        }
        return List.copyOf(result);
    }
}
