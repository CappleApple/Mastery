package com.cappleapple.mastery.layout;

import java.util.*;

/** Pure presentation layout. Inputs and output contain no Minecraft or progression mutations. */
public final class GraphLayout {
    public enum Kind { GROUP, TREE, NODE, MODIFIER, SYNERGY }
    public record Point(double x, double y) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y); }
    }
    public record Entry(String id, String parent, String tree, Kind kind, List<String> dependencies,
                        Set<String> relatedTrees, long purchaseOrder, long unlockOrder, String section) {
        public Entry(String id, String parent, String tree, Kind kind, List<String> dependencies, Set<String> relatedTrees, long purchaseOrder, long unlockOrder) {
            this(id, parent, tree, kind, dependencies, relatedTrees, purchaseOrder, unlockOrder, "south");
        }
        public Entry {
            dependencies = List.copyOf(dependencies);
            relatedTrees = Set.copyOf(relatedTrees);
        }
        public boolean organizational() { return kind == Kind.GROUP || kind == Kind.TREE; }
        public long chronology() { return purchaseOrder > 0 ? purchaseOrder : Long.MAX_VALUE; }
    }
    public record Edge(String from, String to, boolean synergy) {}
    public record Result(Map<String, Point> anchors, Map<String, Integer> depths, List<Edge> edges) {}

    private GraphLayout() {}

    /** Existing organizational anchors have complete inertia; only changed progression rows are placed again. */
    public static Result arrange(Collection<Entry> source, Map<String, Point> saved, Set<String> dirtyTrees) {
        return arrange(source, saved, dirtyTrees, Map.of(), Set.of());
    }

    public static Result arrange(Collection<Entry> source, Map<String, Point> saved, Set<String> dirtyTrees,
                                 Map<String, String> orientations, Set<String> manualAnchors) {
        var entries = new TreeMap<String, Entry>();
        source.forEach(e -> entries.put(e.id(), e));
        var anchors = new LinkedHashMap<String, Point>();
        saved.forEach((id, p) -> { if (entries.containsKey(id) && p.finite()) anchors.put(id, p); });
        var organizational = entries.values().stream().filter(Entry::organizational).toList();
        var roots = organizational.stream().filter(e -> e.parent().isBlank() || !entries.containsKey(e.parent())).toList();
        Map<String, Long> sectionTotals = new HashMap<>();
        roots.forEach(root -> sectionTotals.merge(root.section(), 1L, Long::sum));
        Map<String, Integer> sectionCounts = new HashMap<>();
        for (Entry root : roots) {
            int index = sectionCounts.merge(root.section(), 1, Integer::sum) - 1;
            if (anchors.containsKey(root.id())) continue;
            Point outward = direction(root.section());
            double across = (index - (sectionTotals.get(root.section()) - 1) / 2.0) * 660;
            // Parallel lanes stay inside their fixed map sector even when several roots share it.
            double advance = Math.max(520, Math.abs(across) / Math.tan(Math.PI / 8) + 220);
            Point p = project(new Point(0, 0), outward, advance, across);
            while (overlaps(p, anchors.values(), 200)) {
                advance += 220;
                p = project(new Point(0, 0), outward, advance, across);
            }
            anchors.put(root.id(), p);
        }
        for (int pass = 0; pass <= organizational.size(); pass++) {
            boolean changed = false;
            for (var parent : organizational) {
                Point p = anchors.get(parent.id());
                if (p == null) continue;
                var children = organizational.stream().filter(e -> e.parent().equals(parent.id())).toList();
                for (int i = 0; i < children.size(); i++) {
                    Entry child = children.get(i);
                    if (anchors.containsKey(child.id())) continue;
                    double angle = Math.atan2(p.y(), p.x()) + (i - (children.size() - 1) / 2.0) * 0.64;
                    double distance = child.kind() == Kind.TREE ? 340 : 270;
                    Point candidate = new Point(p.x() + Math.cos(angle) * distance, p.y() + Math.sin(angle) * distance);
                    anchors.put(child.id(), avoidOverlap(candidate, anchors.values(), 116));
                    changed = true;
                }
            }
            if (!changed) break;
        }
        var depths = new HashMap<String, Integer>();
        for (Entry e : entries.values()) depth(e.id(), entries, depths, new HashSet<>());
        var rows = new TreeMap<String, List<Entry>>();
        for (Entry e : entries.values()) {
            if (e.organizational() || e.kind() == Kind.SYNERGY) continue;
            rows.computeIfAbsent(e.tree() + "\u0000" + depths.getOrDefault(e.id(), 0), key -> new ArrayList<>()).add(e);
        }
        for (List<Entry> row : rows.values()) {
            row.sort(Comparator.comparingLong(Entry::chronology)
                    .thenComparingLong(e -> e.unlockOrder() > 0 ? e.unlockOrder() : Long.MAX_VALUE)
                    .thenComparing(Entry::id));
            Point tree = anchors.getOrDefault(row.getFirst().tree(), new Point(0, 0));
            for (int i = 0; i < row.size(); i++) {
                Entry e = row.get(i);
                if (anchors.containsKey(e.id()) && (!dirtyTrees.contains(e.tree()) || manualAnchors.contains(e.id()))) continue;
                Entry root = entries.get(e.tree());
                String direction = orientations.getOrDefault(e.tree(), root == null ? "south" : root.section());
                double across = (i - (row.size() - 1) / 2.0) * 132;
                double advance = 138 * (depths.getOrDefault(e.id(), 0) + 1);
                Point placed = project(tree, direction(direction), advance, across);
                anchors.put(e.id(), placed);
            }
        }
        for (Entry e : entries.values()) {
            if (e.kind() != Kind.SYNERGY || (anchors.containsKey(e.id()) && (!dirtyTrees.contains(e.tree()) || manualAnchors.contains(e.id())))) continue;
            var connected = (e.dependencies().isEmpty()?e.relatedTrees().stream():e.dependencies().stream()).map(anchors::get).filter(Objects::nonNull).toList();
            if (connected.isEmpty()) connected = List.of(anchors.getOrDefault(e.tree(), new Point(0, 0)));
            double x = connected.stream().mapToDouble(Point::x).average().orElse(0);
            double y = connected.stream().mapToDouble(Point::y).average().orElse(0) - 82;
            anchors.remove(e.id());
            anchors.put(e.id(), avoidOverlap(new Point(x, y), anchors.values(), 132));
        }
        var edges = new ArrayList<Edge>();
        for (Entry e : entries.values()) {
            if (e.organizational()) {
                if (anchors.containsKey(e.parent())) edges.add(new Edge(e.parent(), e.id(), false));
            } else {
                if (e.dependencies().isEmpty()) edges.add(new Edge(e.tree(), e.id(), e.kind() == Kind.SYNERGY));
                for (String dependency : e.dependencies()) {
                    Entry from = entries.get(dependency);
                    edges.add(new Edge(dependency, e.id(), e.kind() == Kind.SYNERGY || from != null && !from.tree().equals(e.tree())));
                }
                if (e.kind() == Kind.SYNERGY && e.dependencies().isEmpty()) for (String tree : e.relatedTrees()) if(!tree.equals(e.tree())) edges.add(new Edge(tree, e.id(), true));
            }
        }
        return new Result(Collections.unmodifiableMap(anchors), Map.copyOf(depths), List.copyOf(edges));
    }

    /** Eight equal sectors around fixed logical map origin (0,0); boundary ties turn clockwise. */
    /** Rotates stored descendants around their root, preserving every relative distance and custom offset. */
    public static Set<String> rotateSubtree(String root,Collection<Entry> entries,Map<String,Point> anchors,String oldSection,String newSection) {
        Point origin=anchors.get(root);if(origin==null||oldSection.equals(newSection))return Set.of();
        Point before=direction(oldSection),after=direction(newSection);
        double angle=Math.atan2(after.y(),after.x())-Math.atan2(before.y(),before.x()),cos=Math.cos(angle),sin=Math.sin(angle);
        Map<String,Double> influence=movementInfluence(root,entries,anchors);Set<String> moved=new HashSet<>();
        for(var entry:influence.entrySet()) {
            String id=entry.getKey();double weight=entry.getValue();if(id.equals(root)||weight<=0)continue;
            Point p=anchors.get(id);if(p==null)continue;double x=p.x()-origin.x(),y=p.y()-origin.y();
            Point rotated=new Point(origin.x()+x*cos-y*sin,origin.y()+x*sin+y*cos);
            anchors.put(id,new Point(p.x()+(rotated.x()-p.x())*weight,p.y()+(rotated.y()-p.y())*weight));moved.add(id);
        }
        return moved;
    }

    public static String sectionAt(double graphX, double graphY) {
        if (!Double.isFinite(graphX) || !Double.isFinite(graphY) || graphX == 0 && graphY == 0) return "south";
        double angle = Math.toDegrees(Math.atan2(graphY, graphX));
        int sector = (int)Math.floor((angle + 360 + 22.5) / 45 + 1e-12) % 8;
        return switch (sector) {
            case 0 -> "east";
            case 1 -> "southeast";
            case 2 -> "south";
            case 3 -> "southwest";
            case 4 -> "west";
            case 5 -> "northwest";
            case 6 -> "north";
            default -> "northeast";
        };
    }

    private static Point direction(String section) {
        double diagonal = Math.sqrt(.5);
        return switch (section) {
            case "north" -> new Point(0, -1);
            case "northeast" -> new Point(diagonal, -diagonal);
            case "east" -> new Point(1, 0);
            case "southeast" -> new Point(diagonal, diagonal);
            case "southwest" -> new Point(-diagonal, diagonal);
            case "west" -> new Point(-1, 0);
            case "northwest" -> new Point(-diagonal, -diagonal);
            default -> new Point(0, 1);
        };
    }

    private static Point project(Point origin, Point outward, double advance, double across) {
        return new Point(origin.x() + outward.x() * advance + outward.y() * across,
                origin.y() + outward.y() * advance - outward.x() * across);
    }

    private static boolean overlaps(Point point, Collection<Point> occupied, double spacing) {
        return occupied.stream().anyMatch(other -> Math.abs(other.x() - point.x()) < spacing && Math.abs(other.y() - point.y()) < spacing);
    }
    private static int depth(String id, Map<String, Entry> entries, Map<String, Integer> depths, Set<String> active) {
        if (depths.containsKey(id)) return depths.get(id);
        Entry e = entries.get(id);
        if (e == null || e.organizational() || !active.add(id)) return -1;
        int d = 0;
        for (String dep : e.dependencies()) {
            Entry parent = entries.get(dep);
            if (parent != null && parent.tree().equals(e.tree())) d = Math.max(d, depth(dep, entries, depths, active) + 1);
        }
        active.remove(id);
        depths.put(id, d);
        return d;
    }

    private static Point avoidOverlap(Point initial, Collection<Point> occupied, double spacing) {
        Point result = initial;
        for (int attempt = 0; attempt < 400; attempt++) {
            final Point p = result;
            if (occupied.stream().noneMatch(o -> Math.abs(o.x() - p.x()) < spacing && Math.abs(o.y() - p.y()) < spacing)) return result;
            double angle = attempt * 2.39996323;
            double distance = spacing * Math.sqrt(attempt + 1);
            result = new Point(initial.x() + Math.cos(angle) * distance, initial.y() + Math.sin(angle) * distance);
        }
        return result;
    }

    /** Translate a root or node and all same-tree descendants without touching progression. */
    public static Set<String> translateSubtree(String id, Collection<Entry> entries, Map<String, Point> anchors, double dx, double dy) {
        Map<String, Entry> byId = new HashMap<>(); entries.forEach(e -> byId.put(e.id(), e));
        Entry root = byId.get(id);
        if (root == null) return Set.of();
        Map<String,Double> influence=movementInfluence(id,entries,anchors);Set<String> moved=new HashSet<>();
        for(var entry:influence.entrySet())if(entry.getValue()>0&&anchors.containsKey(entry.getKey())) {
            double weight=entry.getValue();anchors.computeIfPresent(entry.getKey(),(key,p)->new Point(p.x()+dx*weight,p.y()+dy*weight));moved.add(entry.getKey());
        }
        return Set.copyOf(moved);
    }

    /** Inverse-square distance gives close parents stronger influence; common ancestors never move a node twice. */
    public static Map<String,Double> movementInfluence(String source,Collection<Entry> entries,Map<String,Point> anchors) {
        Map<String,List<String>> parents=new HashMap<>(),children=new HashMap<>();Map<String,Integer> pending=new HashMap<>();
        for(Entry entry:entries) {
            List<String> dependencies=entry.organizational()?(entry.parent().isBlank()?List.of():List.of(entry.parent())):
                    entry.dependencies().isEmpty()?List.of(entry.tree()):entry.dependencies();
            var actual=dependencies.stream().filter(anchors::containsKey).filter(id->!id.equals(entry.id())).distinct().toList();
            if(entry.id().equals(source))actual=List.of();
            parents.put(entry.id(),actual);pending.put(entry.id(),actual.size());
            for(String parent:actual)children.computeIfAbsent(parent,k->new ArrayList<>()).add(entry.id());
        }
        ArrayDeque<String> ready=new ArrayDeque<>();pending.forEach((id,count)->{if(count==0)ready.add(id);});
        Map<String,Double> result=new HashMap<>();
        while(!ready.isEmpty()) {
            String id=ready.remove();Point point=anchors.get(id);double weighted=0,total=0;
            if(point!=null)for(String parent:parents.getOrDefault(id,List.of())) {
                Point origin=anchors.get(parent);double dx=point.x()-origin.x(),dy=point.y()-origin.y(),weight=1/Math.max(1,dx*dx+dy*dy);
                total+=weight;weighted+=weight*result.getOrDefault(parent,0.0);
            }
            result.put(id,id.equals(source)?1:total==0?0:weighted/total);
            for(String child:children.getOrDefault(id,List.of()))if(pending.merge(child,-1,Integer::sum)==0)ready.add(child);
        }
        return result;
    }

    /** Multi-parent nodes anchor to the first same-tree prerequisite; bridge nodes anchor to their owner tree. */
    public static Map<String, String> primaryParents(Collection<Entry> entries) {
        Map<String, Entry> byId = new HashMap<>(); entries.forEach(e -> byId.put(e.id(), e));
        Map<String, String> result = new HashMap<>();
        for (Entry e : entries) {
            if (e.organizational()) { if (!e.parent().isBlank()) result.put(e.id(), e.parent()); continue; }
            String parent = e.kind() == Kind.SYNERGY ? e.tree() : e.dependencies().stream()
                    .filter(id -> byId.containsKey(id) && byId.get(id).tree().equals(e.tree())).sorted().findFirst().orElse(e.tree());
            result.put(e.id(), parent);
        }
        return result;
    }

    public static Map<String, Point> relativeOffsets(Map<String, Point> anchors, Map<String, String> parents) {
        Map<String, Point> result = new HashMap<>();
        parents.forEach((id, parent) -> {
            Point p = anchors.get(id), origin = anchors.get(parent);
            if (p != null && origin != null) result.put(id, new Point(p.x() - origin.x(), p.y() - origin.y()));
        });
        return result;
    }

    /** Resolve saved local coordinates parent-first, independent of map iteration order. */
    public static void applyRelativeOffsets(Map<String, Point> anchors, Map<String, String> parents,
                                            Map<String, Point> offsets, Set<String> manual) {
        Set<String> done = new HashSet<>();
        for (String id : manual) resolveRelative(id, anchors, parents, offsets, manual, done, new HashSet<>());
    }
    private static void resolveRelative(String id, Map<String, Point> anchors, Map<String, String> parents,
                                        Map<String, Point> offsets, Set<String> manual, Set<String> done, Set<String> active) {
        if (!manual.contains(id) || done.contains(id) || !active.add(id)) return;
        String parent = parents.get(id);
        if (parent == null) return;
        resolveRelative(parent, anchors, parents, offsets, manual, done, active);
        Point origin = anchors.get(parent), delta = offsets.get(id);
        if (origin != null && delta != null) anchors.put(id, new Point(origin.x() + delta.x(), origin.y() + delta.y()));
        active.remove(id); done.add(id);
    }

    /** Conservative segment culling retains edges crossing a viewport with both endpoints outside. */
    public static boolean edgeInViewport(Point a, Point b, double left, double top, double right, double bottom) {
        return Math.max(a.x(), b.x()) >= left && Math.min(a.x(), b.x()) <= right
                && Math.max(a.y(), b.y()) >= top && Math.min(a.y(), b.y()) <= bottom;
    }
}
