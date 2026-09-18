package com.cappleapple.mastery.data;

import com.google.gson.JsonObject;
import java.util.Locale;

/** Incoming cross-tree prerequisites and outgoing branch connections have separate styles. */
public record ConnectionPresentation(Style parentLineStyle, Style childLineStyle) {
    public enum Style { DASHED, SOLID }
    public static final ConnectionPresentation DEFAULT = new ConnectionPresentation(Style.DASHED, Style.SOLID);
    public static ConnectionPresentation parse(JsonObject json) {
        return new ConnectionPresentation(style(json,"parent_line_style",DEFAULT.parentLineStyle),
                style(json,"child_line_style",DEFAULT.childLineStyle));
    }
    private static Style style(JsonObject json,String key,Style fallback) {
        if(!json.has(key))return fallback;
        try{return Style.valueOf(json.get(key).getAsString().toUpperCase(Locale.ROOT));}
        catch(RuntimeException error){throw new IllegalArgumentException("connections."+key+" must be dashed or solid");}
    }
    public JsonObject toJson() {
        var json=new JsonObject();json.addProperty("parent_line_style",parentLineStyle.name().toLowerCase(Locale.ROOT));
        json.addProperty("child_line_style",childLineStyle.name().toLowerCase(Locale.ROOT));return json;
    }
}
