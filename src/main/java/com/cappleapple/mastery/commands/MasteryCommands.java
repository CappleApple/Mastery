package com.cappleapple.mastery.commands;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.spells.SpellService;
import com.cappleapple.mastery.api.ProficiencyChangedEvent;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.graph.GraphValidator;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.cappleapple.mastery.progression.ProgressionService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import static net.minecraft.commands.Commands.*;

public final class MasteryCommands {
    private MasteryCommands(){}
    public static void register(RegisterCommandsEvent event) {
        var root=literal("mastery");
        root.then(literal("tree")
            .then(literal("list").executes(c->say(c,String.join(", ",MasteryRuntime.definitions().trees().keySet()))))
            .then(literal("info").then(tree().executes(c->{
                var tree=MasteryRuntime.definitions().trees().get(net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"tree").toString());
                return tree==null?fail(c,"Unknown tree"):say(c,tree.name()+": max level "+tree.maxLevel()+", base XP "+tree.xpBase()+", "+tree.pointsPerAward()+" points every "+tree.pointEvery()+" levels, initial section "+tree.section());
            }))));
        root.then(literal("xp").requires(s->s.hasPermission(2))
            .then(literal("get").then(player().then(tree().executes(c->{var p=target(c);var id=net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"tree").toString();var state=MasteryRuntime.progress(p).tree(id);return say(c,p.getScoreboardName()+" / "+id+": "+state.xp()+" XP (lifetime "+state.lifetimeXp()+")");}))))
            .then(literal("add").then(player().then(tree().then(argument("amount",DoubleArgumentType.doubleArg(0,1.0E12)).executes(c->result(c,MasteryRuntime.grantXpExact(target(c),net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"tree").toString(),DoubleArgumentType.getDouble(c,"amount")))))))));
        root.then(literal("level").requires(s->s.hasPermission(2))
            .then(literal("get").then(player().then(tree().executes(c->say(c,"Level: "+MasteryRuntime.progress(target(c)).tree(net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"tree").toString()).level())))))
            .then(literal("set").then(player().then(tree().then(argument("level",IntegerArgumentType.integer(0,1000000)).executes(c->{
                var p=target(c);String id=net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"tree").toString();int before=MasteryRuntime.progress(p).tree(id).level();
                var change=ProgressionService.setLevel(MasteryRuntime.definitions(),MasteryRuntime.progress(p),id,IntegerArgumentType.getInteger(c,"level"),MasteryRuntime.worldTier(p));
                if(change.success())NeoForge.EVENT_BUS.post(new ProficiencyChangedEvent(p,id,before,MasteryRuntime.progress(p).tree(id).level()));
                changed(p);return result(c,change);
            }))))));
        root.then(literal("points").requires(s->s.hasPermission(2))
            .then(literal("get").then(player().then(tree().executes(c->say(c,net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"tree").toString()+" Points: "+MasteryRuntime.progress(target(c)).tree(net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"tree").toString()).points())))))
            .then(literal("add").then(player().then(tree().then(argument("amount",IntegerArgumentType.integer(0,1000000)).executes(c->result(c,MasteryRuntime.grantPoints(target(c),net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"tree").toString(),IntegerArgumentType.getInteger(c,"amount")))))))));
        root.then(literal("node").requires(s->s.hasPermission(2))
            .then(literal("unlock").then(player().then(node().executes(c->rank(c,1)))))
            .then(literal("rank").then(player().then(node().then(argument("rank",IntegerArgumentType.integer(0)).executes(c->rank(c,IntegerArgumentType.getInteger(c,"rank"))))))));
        root.then(literal("reset").requires(s->s.hasPermission(2))
            .then(literal("tree").then(player().then(tree().executes(c->{
                var p=target(c);String id=net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"tree").toString();
                if(!MasteryRuntime.definitions().trees().containsKey(id))return fail(c,"Unknown tree");
                ProgressionService.resetTree(MasteryRuntime.definitions(),MasteryRuntime.progress(p),id);changed(p);return say(c,"Reset "+id+" for "+p.getScoreboardName());
            }))))
            .then(literal("all").then(player().executes(c->{var p=target(c);ProgressionService.resetAll(MasteryRuntime.progress(p));changed(p);return say(c,"Reset all Mastery progression for "+p.getScoreboardName());}))));
        root.then(literal("graph")
            .then(literal("validate").requires(s->s.hasPermission(2)).executes(c->{
                var errors=GraphValidator.validate(MasteryRuntime.definitions()).errors();
                if(!MasteryRuntime.lastReloadErrors.isEmpty())return fail(c,"Last reload was rejected: "+String.join("; ",MasteryRuntime.lastReloadErrors));
                return errors.isEmpty()?say(c,"Mastery graph valid: "+MasteryRuntime.definitions().trees().size()+" trees, "+MasteryRuntime.definitions().nodes().size()+" nodes"):fail(c,String.join("; ",errors));
            }))
            .then(literal("relayout").executes(c->{MasteryNetwork.send(c.getSource().getPlayerOrException(),"relayout","{}");return say(c,"Reorganized your local skill map");})));
        root.then(literal("reload").requires(s->s.hasPermission(2)).executes(c->{
            var loaded=com.cappleapple.mastery.data.MasteryReloadListener.reloadOnly(c.getSource().getServer());
            return loaded.valid()?say(c,"Reloaded Mastery definitions only"):fail(c,"Mastery reload rejected; previous graph retained: "+String.join("; ",loaded.errors()));
        }));
        root.then(literal("export").requires(source->source.hasPermission(2)).executes(context->{
            var player=context.getSource().getPlayerOrException();
            try {
                String payload=com.cappleapple.mastery.export.MasteryDatapackExport.encode(
                        com.cappleapple.mastery.data.MasteryReloadListener.exportResources(),MasteryNetwork.MAX_TRANSFER_CHARACTERS);
                MasteryNetwork.send(player,"export",payload);
                return say(context,"Mastery export sent to your client; it will save the ZIP in config/exports");
            } catch(java.io.IOException|IllegalArgumentException error) {
                return fail(context,"Mastery export failed: "+error.getMessage());
            }
        }));
        root.then(literal("edit_mode").requires(source->source.hasPermission(2))
            .then(argument("enabled",BoolArgumentType.bool()).executes(context->{
                var player=context.getSource().getPlayerOrException();
                boolean enabled=BoolArgumentType.getBool(context,"enabled");
                com.cappleapple.mastery.editor.EditorService.setEnabled(player,enabled);
                return say(context,"Mastery edit mode "+(enabled?"enabled":"disabled")+" for you");
            })));
        event.getDispatcher().register(root);
    }
    private static RequiredArgumentBuilder<CommandSourceStack,net.minecraft.commands.arguments.selector.EntitySelector> player(){return argument("player",EntityArgument.player());}
    private static RequiredArgumentBuilder<CommandSourceStack,net.minecraft.resources.ResourceLocation> tree(){return argument("tree",net.minecraft.commands.arguments.ResourceLocationArgument.id()).suggests((c,b)->SharedSuggestionProvider.suggest(MasteryRuntime.definitions().trees().keySet(),b));}
    private static RequiredArgumentBuilder<CommandSourceStack,net.minecraft.resources.ResourceLocation> node(){return argument("node",net.minecraft.commands.arguments.ResourceLocationArgument.id()).suggests((c,b)->SharedSuggestionProvider.suggest(MasteryRuntime.definitions().nodes().keySet(),b));}
    private static ServerPlayer target(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException{return EntityArgument.getPlayer(c,"player");}
    private static int rank(CommandContext<CommandSourceStack> c,int rank) throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var p=target(c);String nodeId=net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"node").toString();boolean first=MasteryRuntime.progress(p).rank(nodeId)==0&&rank>0;var change=ProgressionService.setRank(MasteryRuntime.definitions(),MasteryRuntime.progress(p),net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"node").toString(),rank,MasteryRuntime.worldTier(p));changed(p);if(change.success()&&first){com.cappleapple.mastery.spells.SpellService.toggleNode(p,MasteryRuntime.definitions().nodes().get(nodeId),true);changed(p);}return result(c,change);
    }
    private static void changed(ServerPlayer p){SpellService.interrupt(p,"administrative change");SpellService.reconcile(p);EffectService.rebuild(p);MasteryRuntime.sync(p);}
    private static int result(CommandContext<CommandSourceStack> c,ProgressionService.Change change){return change.success()?say(c,change.message()):fail(c,change.message());}
    private static int say(CommandContext<CommandSourceStack> c,String text){c.getSource().sendSuccess(()->Component.literal(text),false);return Command.SINGLE_SUCCESS;}
    private static int fail(CommandContext<CommandSourceStack> c,String text){c.getSource().sendFailure(Component.literal(text));return 0;}
}
