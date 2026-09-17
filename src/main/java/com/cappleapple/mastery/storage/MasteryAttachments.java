package com.cappleapple.mastery.storage;

import com.cappleapple.mastery.progression.PlayerProgress;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

public final class MasteryAttachments {
    public static final Codec<PlayerProgress> CODEC = Codec.STRING.xmap(text -> PlayerProgress.fromJson(JsonParser.parseString(text).getAsJsonObject()), state -> state.toJson().toString());
    public static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "mastery");
    public static final Supplier<AttachmentType<PlayerProgress>> PROGRESS = TYPES.register("progress", () -> AttachmentType.builder(PlayerProgress::new).serialize(CODEC).copyOnDeath().build());
    private MasteryAttachments() {}
}
