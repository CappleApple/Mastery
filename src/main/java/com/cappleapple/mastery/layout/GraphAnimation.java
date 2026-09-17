package com.cappleapple.mastery.layout;
import java.util.*;
import java.util.function.Function;

/** Reversible branch transitions. Animated coordinates never become saved placement coordinates. */
public final class GraphAnimation {
    private record Motion(GraphLayout.Point from,GraphLayout.Point to,long start,double duration,boolean leaving){}
    private final Map<String,Motion> motions=new HashMap<>();
    public void update(Map<String,GraphLayout.Point> target,Function<String,GraphLayout.Point> origin,long now,boolean enabled,double speed) {
        if (!enabled) { motions.clear(); target.forEach((id, point) -> motions.put(id, new Motion(point, point, now, 0, false))); return; }
        var current=frame(now);double duration=enabled?250/Math.clamp(speed,.1,10):0;
        for(String id:new HashSet<>(motions.keySet()))if(!target.containsKey(id)&&!motions.get(id).leaving) {
            motions.put(id,new Motion(current.get(id),origin.apply(id),now,duration,true));
        }
        target.forEach((id,point)->{
            Motion previous=motions.get(id);
            if(previous==null||previous.leaving||!previous.to.equals(point))
                motions.put(id,new Motion(current.getOrDefault(id,origin.apply(id)),point,now,duration,false));
        });
    }
    public boolean leaving(String id){var motion=motions.get(id);return motion!=null&&motion.leaving;}
    public Map<String,GraphLayout.Point> frame(long now) {
        Map<String,GraphLayout.Point> result=new HashMap<>();
        var iterator=motions.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();var motion=entry.getValue();double progress=motion.duration==0?1:Math.clamp((now-motion.start)/motion.duration,0,1);
            if(motion.leaving&&progress>=1){iterator.remove();continue;}
            double smooth=progress*progress*(3-2*progress);
            result.put(entry.getKey(),new GraphLayout.Point(motion.from.x()+(motion.to.x()-motion.from.x())*smooth,motion.from.y()+(motion.to.y()-motion.from.y())*smooth));
        }
        return result;
    }
}
