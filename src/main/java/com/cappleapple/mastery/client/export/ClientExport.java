package com.cappleapple.mastery.client.export;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/** Receives the calling player's server export and writes it only inside this client's config/exports. */
public final class ClientExport {
    private static volatile Path lastExport;
    private static volatile String lastFailure = "";
    private ClientExport() {}
    public static Path lastExport() { return lastExport; }
    public static String lastFailure() { return lastFailure; }
    public static void accept(String base64) {
        Minecraft mc = Minecraft.getInstance();
        Path directory = mc.gameDirectory.toPath().resolve("config/exports");
        LocalDateTime requestedAt = LocalDateTime.now();
        lastFailure = "";
        CompletableFuture.runAsync(() -> {
            try {
                Path file = ExportWriter.write(directory, ExportWriter.decode(base64), requestedAt);
                lastExport = file;
                LogUtils.getLogger().info("Exported Mastery definitions to {}", file);
                mc.execute(() -> {
                    Component path = Component.literal(file.toString()).withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                            .withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.toString())));
                    mc.gui.getChat().addMessage(Component.literal("Mastery export saved: ").append(path));
                });
            } catch (Exception exception) {
                lastFailure = exception.getMessage() == null ? "Could not write the export file." : exception.getMessage();
                LogUtils.getLogger().error("Could not save Mastery export", exception);
                String message = lastFailure;
                mc.execute(() -> mc.gui.getChat().addMessage(Component.literal("Mastery export failed: " + message).withStyle(ChatFormatting.RED)));
            }
        }, Util.ioPool());
    }
}
