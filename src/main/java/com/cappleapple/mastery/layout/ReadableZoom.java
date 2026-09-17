package com.cappleapple.mastery.layout;

import java.util.*;

/** Screen-space collision avoidance. Returned coordinates never replace saved graph positions. */
public final class ReadableZoom {
    public static final double MIN_ZOOM = 0.0001;
    public record Bounds(double width,double height) {}
    private record Box(double x,double y,double width,double height) {}
    private record Cell(long x, long y) {}
    private record Candidate(double x, double y, double distance) {}
    private ReadableZoom() {}

    public static Map<String, GraphLayout.Point> project(Map<String, GraphLayout.Point> anchors,
            double zoom, double minimumWidth) {
        return project(anchors,zoom,minimumWidth,Map.of());
    }
    public static Map<String,GraphLayout.Point> project(Map<String,GraphLayout.Point> anchors,
            double zoom,double minimumWidth,Map<String,Bounds> bounds) {
        double scale=Math.max(zoom,minimumWidth/40.0);
        double cellWidth=bounds.values().stream().mapToDouble(Bounds::width).max().orElse(52)*scale+4;
        double cellHeight=bounds.values().stream().mapToDouble(Bounds::height).max().orElse(52)*scale+4;
        cellWidth=Math.max(cellWidth,52*scale+4);cellHeight=Math.max(cellHeight,52*scale+4);
        Map<Cell,List<Box>> occupied=new HashMap<>();
        Map<String, GraphLayout.Point> result = new LinkedHashMap<>();
        var ids = anchors.keySet().stream().sorted(Comparator.<String>comparingDouble(id -> {
            var p = anchors.get(id); return Math.hypot(p.x(), p.y());
        }).thenComparing(id -> id)).toList();
        double rightmost = 0;
        for (String id : ids) {
            var anchor = anchors.get(id);
            var size=bounds.getOrDefault(id,new Bounds(52,52));
            double width=size.width()*scale+4,height=size.height()*scale+4;
            double x = anchor.x() * zoom, y = anchor.y() * zoom;
            var candidates = new PriorityQueue<Candidate>(Comparator.comparingDouble(Candidate::distance)
                    .thenComparingDouble(Candidate::x).thenComparingDouble(Candidate::y));
            candidates.add(new Candidate(x, y, 0));
            Set<String> visited = new HashSet<>();
            GraphLayout.Point accepted = null;
            int attempts = 0;
            while (!candidates.isEmpty() && attempts++ < 4096) {
                var c = candidates.remove();
                if (!visited.add(Math.round(c.x * 1000) + ":" + Math.round(c.y * 1000))) continue;
                var collisions = collisions(occupied, c.x, c.y, width, height,cellWidth,cellHeight);
                if (collisions.isEmpty()) { accepted = new GraphLayout.Point(c.x, c.y); break; }
                for (var obstacle : collisions) {
                    for (var p : List.of(new GraphLayout.Point(obstacle.x() - (width+obstacle.width())/2, c.y),
                            new GraphLayout.Point(obstacle.x() + (width+obstacle.width())/2, c.y),
                            new GraphLayout.Point(c.x, obstacle.y() - (height+obstacle.height())/2),
                            new GraphLayout.Point(c.x, obstacle.y() + (height+obstacle.height())/2))) {
                        double dx = p.x() - x, dy = p.y() - y;
                        candidates.add(new Candidate(p.x(), p.y(), dx * dx + dy * dy));
                    }
                }
            }
            if (accepted == null) accepted = new GraphLayout.Point(rightmost + width/2, y);
            rightmost = Math.max(rightmost, accepted.x()+width/2);
            occupied.computeIfAbsent(cell(accepted.x(), accepted.y(), cellWidth, cellHeight), ignored -> new ArrayList<>()).add(new Box(accepted.x(),accepted.y(),width,height));
            result.put(id, new GraphLayout.Point(accepted.x() / zoom, accepted.y() / zoom));
        }
        return Collections.unmodifiableMap(result);
    }
    private static Cell cell(double x, double y, double width, double height) {
        return new Cell((long)Math.floor(x / width), (long)Math.floor(y / height));
    }
    private static List<Box> collisions(Map<Cell, List<Box>> occupied,
            double x, double y, double width, double height,double cellWidth,double cellHeight) {
        var cell = cell(x, y, cellWidth, cellHeight);
        var result = new ArrayList<Box>();
        for (long dx = -1; dx <= 1; dx++) for (long dy = -1; dy <= 1; dy++)
            for (var p : occupied.getOrDefault(new Cell(cell.x + dx, cell.y + dy), List.of()))
                if (Math.abs(p.x() - x) < (width+p.width())/2 - 0.00001 && Math.abs(p.y() - y) < (height+p.height())/2 - 0.00001) result.add(p);
        return result;
    }
}
