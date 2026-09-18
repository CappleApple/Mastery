package com.cappleapple.mastery.data;
import com.google.gson.JsonObject;
import java.util.Locale;

/** Inherited node presentation; these settings do not change progression or placement. */
public record NodeAppearance(Shape shape, boolean showName,double scale,double rootScale) {
    public NodeAppearance(Shape shape,boolean showName){this(shape,showName,1,1.3);}
    public NodeAppearance {
        if(!Double.isFinite(scale)||!Double.isFinite(rootScale)||scale<.25||scale>4||rootScale<.25||rootScale>4)
            throw new IllegalArgumentException("appearance scales must be between 0.25 and 4");
    }
    public double scaleFor(boolean root){return scale*(root?rootScale:1);}
    public static final int SIZE = 40;
    public static final NodeAppearance DEFAULT = new NodeAppearance(Shape.CIRCLE, false);
    public enum Shape {
        CIRCLE, SQUARE, DIAMOND, HEXAGON, PENTAGON, TRIANGLE;
        /** Triangle icons follow the filled area centroid, below the bounding-box center. */
        public int iconOffsetY(int radius) { return this==TRIANGLE?Math.round(radius/3f):0; }
        public double halfWidth(double y,double radius) {
            if(Math.abs(y)>radius)return -1;
            return switch(this){case CIRCLE->Math.sqrt(Math.max(0,radius*radius-y*y));case SQUARE->roundedWidth(y,radius);case DIAMOND->radius-Math.abs(y);case HEXAGON->radius-Math.abs(y)*.5;case TRIANGLE->(y+radius)*.5;case PENTAGON->polygonWidth(y,radius,5);};
        }
        private static double roundedWidth(double y,double radius) {
            double corner=radius*.25,dy=Math.max(0,Math.abs(y)-(radius-corner));
            return radius-corner+Math.sqrt(Math.max(0,corner*corner-dy*dy));
        }
        private static double polygonWidth(double y,double radius,int sides) {
            double half=-1;
            for(int i=0;i<sides;i++) {
                double a=-Math.PI/2+i*2*Math.PI/sides,b=-Math.PI/2+(i+1)*2*Math.PI/sides;
                double ax=Math.cos(a)*radius,ay=Math.sin(a)*radius,bx=Math.cos(b)*radius,by=Math.sin(b)*radius;
                if(y>=Math.min(ay,by)&&y<=Math.max(ay,by)&&Math.abs(by-ay)>1e-9)
                    half=Math.max(half,Math.abs(ax+(bx-ax)*(y-ay)/(by-ay)));
            }
            return half;
        }
        public boolean contains(double x,double y,double radius){return Math.abs(y)<=radius&&Math.abs(x)<=halfWidth(y,radius);}
    }
    public static NodeAppearance parse(JsonObject json) {
        Shape shape=Shape.CIRCLE;
        if(json.has("shape"))try{shape=Shape.valueOf(json.get("shape").getAsString().toUpperCase(Locale.ROOT));}
        catch(RuntimeException error){throw new IllegalArgumentException("appearance.shape must be circle, square, diamond, hexagon, pentagon, or triangle");}
        if(json.has("show_name")&&(!json.get("show_name").isJsonPrimitive()||!json.getAsJsonPrimitive("show_name").isBoolean()))
            throw new IllegalArgumentException("appearance.show_name must be true or false");
        return new NodeAppearance(shape,json.has("show_name")&&json.get("show_name").getAsBoolean(),json.has("scale")?json.get("scale").getAsDouble():1,json.has("root_scale")?json.get("root_scale").getAsDouble():1.3);
    }
    public JsonObject toJson(){var json=new JsonObject();json.addProperty("shape",shape.name().toLowerCase(Locale.ROOT));json.addProperty("show_name",showName);json.addProperty("scale",scale);json.addProperty("root_scale",rootScale);return json;}
}
