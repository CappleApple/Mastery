package com.cappleapple.mastery.network;

import com.cappleapple.mastery.MasteryRuntime;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;
import java.util.function.BiConsumer;

/** Bounded payloads. Definitions are transferred only at login or accepted reloads. */
public final class MasteryNetwork {
    private static BiConsumer<String,String> clientReceiver=(kind,json)->{};
    private static final Map<String,Assembly> ASSEMBLIES=new HashMap<>();
    private static int sequence;
    private static final int CHUNK=24000, MAX_PARTS=512;
    public static final int MAX_TRANSFER_CHARACTERS=CHUNK*MAX_PARTS;
    private MasteryNetwork() {}
    public static void setClientReceiver(BiConsumer<String,String> receiver) { clientReceiver=receiver; }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("4");
        registrar.playToClient(Snapshot.TYPE,Snapshot.CODEC,(payload,context)->context.enqueueWork(()->accept(payload)));
        registrar.playToServer(Action.TYPE,Action.CODEC,(payload,context)->context.enqueueWork(()-> {
            if(context.player() instanceof ServerPlayer player)MasteryRuntime.handleAction(player,payload.action,payload.id,payload.value,payload.number);
        }));
    }
    public static void sendAction(String action,String id,String value,int number) {
        PacketDistributor.sendToServer(new Action(action,id,value,number));
    }
    public static void send(ServerPlayer player,String kind,String json) {
        if(player.connection==null || player.connection.getConnection().channel()==null || !player.connection.hasChannel(Snapshot.TYPE))return;
        int total=Math.max(1,(json.length()+CHUNK-1)/CHUNK);
        if(total>MAX_PARTS)throw new IllegalArgumentException("Mastery snapshot exceeds 12 MB transfer limit");
        int serial=++sequence;
        for(int part=0;part<total;part++)PacketDistributor.sendToPlayer(player,new Snapshot(kind,serial,part,total,json.substring(part*CHUNK,Math.min(json.length(),(part+1)*CHUNK))));
    }
    private static void accept(Snapshot packet) {
        if(packet.total<1||packet.total>MAX_PARTS||packet.part<0||packet.part>=packet.total)return;
        if(!Set.of("definitions","progress","relayout","editor_definition","editor_status","export","charge").contains(packet.kind))return;
        var a=ASSEMBLIES.get(packet.kind);
        if(a==null||a.serial!=packet.serial) {
            if(packet.part!=0)return;
            a=new Assembly(packet.serial,packet.total); ASSEMBLIES.put(packet.kind,a);
        }
        if(a.parts.length!=packet.total||a.parts[packet.part]!=null)return;
        a.parts[packet.part]=packet.json; a.count++;
        if(a.count==a.parts.length) { ASSEMBLIES.remove(packet.kind); clientReceiver.accept(packet.kind,String.join("",a.parts)); }
    }
    private static final class Assembly {
        final int serial; final String[] parts; int count;
        Assembly(int serial,int total){this.serial=serial;parts=new String[total];}
    }
    public record Snapshot(String kind,int serial,int part,int total,String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("mastery","snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)-> {
            b.writeUtf(p.kind,24);b.writeVarInt(p.serial);b.writeVarInt(p.part);b.writeVarInt(p.total);b.writeUtf(p.json,CHUNK);
        },b->new Snapshot(b.readUtf(24),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(CHUNK)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Action(String action,String id,String value,int number) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("mastery","action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)-> {
            b.writeUtf(p.action,24);b.writeUtf(p.id,256);b.writeUtf(p.value,8192);b.writeVarInt(p.number);
        },b->new Action(b.readUtf(24),b.readUtf(256),b.readUtf(8192),b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
