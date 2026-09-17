package com.cappleapple.mastery.data;
import com.google.gson.JsonObject;
import java.util.Locale;

/** Inherited node presentation; these settings do not change progression or placement. */
public record NodeAppearance(Shape shape, boolean showName) {
    public static final int SIZE = 40;
    public static final NodeAppearance DEFAULT = new NodeAppearance(Shape.CIRCLE, false);
    public enum Shape {
        CIRCLE, SQUARE, DIAMOND, HEXAGON;
        public double halfWidth(double y,double radius) {
            if(Math.abs(y)>radius)return -1;
            return switch(this){case CIRCLE->Math.sqrt(Math.max(0,radius*radius-y*y));case SQUARE->radius;case DIAMOND->radius-Math.abs(y);case HEXAGON->radius-Math.abs(y)*.5;};
        }
        public boolean contains(double x,double y,double radius){return Math.abs(y)<=radius&&Math.abs(x)<=halfWidth(y,radius);}
    }
    public static NodeAppearance parse(JsonObject json) {
        Shape shape=Shape.CIRCLE;
        if(json.has("shape"))try{shape=Shape.valueOf(json.get("shape").getAsString().toUpperCase(Locale.ROOT));}
        catch(RuntimeException error){throw new IllegalArgumentException("appearance.shape must be circle, square, diamond, or hexagon");}
        if(json.has("show_name")&&(!json.get("show_name").isJsonPrimitive()||!json.getAsJsonPrimitive("show_name").isBoolean()))
            throw new IllegalArgumentException("appearance.show_name must be true or false");
        return new NodeAppearance(shape,json.has("show_name")&&json.get("show_name").getAsBoolean());
    }
    public JsonObject toJson(){var json=new JsonObject();json.addProperty("shape",shape.name().toLowerCase(Locale.ROOT));json.addProperty("show_name",showName);return json;}
}
