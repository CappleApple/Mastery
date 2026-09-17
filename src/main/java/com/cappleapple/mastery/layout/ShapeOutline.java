package com.cappleapple.mastery.layout;

import com.cappleapple.mastery.data.NodeAppearance.Shape;
import java.util.*;

/** Pixel ring ordered by distance clockwise around each node shape, starting at twelve o'clock. */
public final class ShapeOutline {
    public record Pixel(int x,int y,double fraction) {}
    private record Key(Shape shape,int radius,int thickness) {}
    private static final Map<Key,List<Pixel>> CACHE=new HashMap<>();
    private ShapeOutline() {}
    public static List<Pixel> pixels(Shape shape,int radius,int thickness) {
        return CACHE.computeIfAbsent(new Key(shape,radius,thickness),key->{
            var result=new ArrayList<Pixel>();
            for(int y=-radius;y<radius;y++)for(int x=-radius;x<radius;x++)
                if(shape.contains(x+.5,y+.5,radius)&&!shape.contains(x+.5,y+.5,Math.max(0,radius-thickness)))
                    result.add(new Pixel(x,y,fraction(shape,x+.5,y+.5,radius)));
            result.sort(Comparator.comparingDouble(Pixel::fraction));return List.copyOf(result);
        });
    }
    public static double fraction(Shape shape,double x,double y,double radius) {
        double angle=Math.atan2(x,-y);if(angle<0)angle+=2*Math.PI;
        if(shape==Shape.CIRCLE)return angle/(2*Math.PI);
        double dx=Math.sin(angle),dy=-Math.cos(angle);
        double scale=switch(shape) {
            case SQUARE -> radius/Math.max(Math.abs(dx),Math.abs(dy));
            case DIAMOND -> radius/(Math.abs(dx)+Math.abs(dy));
            case HEXAGON -> Math.min(radius/(Math.abs(dx)+.5*Math.abs(dy)),Math.abs(dy)<1e-12?Double.POSITIVE_INFINITY:radius/Math.abs(dy));
            default -> radius;
        };
        double px=dx*scale,py=dy*scale;
        double[][] points=switch(shape) {
            case SQUARE -> new double[][]{{0,-radius},{radius,-radius},{radius,radius},{-radius,radius},{-radius,-radius},{0,-radius}};
            case DIAMOND -> new double[][]{{0,-radius},{radius,0},{0,radius},{-radius,0},{0,-radius}};
            default -> new double[][]{{0,-radius},{radius*.5,-radius},{radius,0},{radius*.5,radius},{-radius*.5,radius},{-radius,0},{-radius*.5,-radius},{0,-radius}};
        };
        double total=0,travel=0,bestDistance=Double.POSITIVE_INFINITY,bestTravel=0;
        for(int i=1;i<points.length;i++)total+=Math.hypot(points[i][0]-points[i-1][0],points[i][1]-points[i-1][1]);
        for(int i=1;i<points.length;i++) {
            double ax=points[i-1][0],ay=points[i-1][1],sx=points[i][0]-ax,sy=points[i][1]-ay;
            double length=Math.hypot(sx,sy),t=Math.clamp(((px-ax)*sx+(py-ay)*sy)/(length*length),0,1);
            double distance=Math.hypot(px-(ax+sx*t),py-(ay+sy*t));
            if(distance<bestDistance-1e-9){bestDistance=distance;bestTravel=travel+length*t;}
            travel+=length;
        }
        return Math.clamp(bestTravel/total,0,1);
    }
    public static double experience(double xp,double nextLevel,int level,int cap) {
        if(level>=cap)return 1;
        return Double.isFinite(xp)&&nextLevel>0?Math.clamp(xp/nextLevel,0,1):0;
    }
}
