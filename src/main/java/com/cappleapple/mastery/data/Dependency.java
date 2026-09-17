package com.cappleapple.mastery.data;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Function;

/** A rank condition or a nested all/any group. Top-level dependencies are ANDed. */
public record Dependency(String node,int rank,List<Dependency> and,List<Dependency> or) {
    public Dependency(String node,int rank){this(node,rank,List.of(),List.of());}
    public Dependency {and=List.copyOf(and);or=List.copyOf(or);}
    public static Dependency group(boolean all,List<Dependency> children){return new Dependency("",0,all?children:List.of(),all?List.of():children);}
    public boolean test(Predicate<Dependency> leaf){
        if(!node.isBlank())return leaf.test(this);
        return !and.isEmpty()?and.stream().allMatch(d->d.test(leaf)):!or.isEmpty()&&or.stream().anyMatch(d->d.test(leaf));
    }
    public List<Dependency> leaves(){
        if(!node.isBlank())return List.of(this);
        return (!and.isEmpty()?and:or).stream().flatMap(d->d.leaves().stream()).toList();
    }
    public com.google.gson.JsonObject toJson(){
        var json=new com.google.gson.JsonObject();
        if(!node.isBlank()){json.addProperty("node",node);json.addProperty("rank",rank);}
        else {var values=new com.google.gson.JsonArray();(!and.isEmpty()?and:or).forEach(d->values.add(d.toJson()));json.add(and.isEmpty()?"or":"and",values);}
        return json;
    }
    public String describe(Function<String,String> name){
        if(!node.isBlank())return name.apply(node)+" rank "+rank;
        return "("+String.join(and.isEmpty()?" OR ":" AND ",(!and.isEmpty()?and:or).stream().map(d->d.describe(name)).toList())+")";
    }
}
