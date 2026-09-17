package com.cappleapple.mastery.client.export;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.zip.ZipInputStream;

/** Stores a bounded ZIP without extracting entries or accepting a server-supplied filename. */
public final class ExportWriter {
    public static final int MAX_ENCODED_CHARACTERS = 12_288_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("HH-mm-dd-MM-yyyy");
    private ExportWriter() {}

    public static byte[] decode(String base64) throws IOException {
        if (base64.length() > MAX_ENCODED_CHARACTERS) throw new IOException("Export exceeds the transfer limit.");
        try { return Base64.getDecoder().decode(base64); }
        catch (IllegalArgumentException exception) { throw new IOException("The server sent an invalid export transfer.", exception); }
    }
    public static void validate(byte[] bytes) throws IOException {
        if (bytes.length > MAX_ENCODED_CHARACTERS / 4 * 3 || bytes.length < 4
                || bytes[0] != 'P' || bytes[1] != 'K' || bytes[2] != 3 || bytes[3] != 4)
            throw new IOException("The server did not send a valid ZIP archive.");
        boolean metadata = false; int count = 0; long expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[8192];
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++count > 50000) throw new IOException("Export has too many entries.");
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("\\") || name.contains(":")
                        || java.util.Arrays.asList(name.split("/")).contains(".."))
                    throw new IOException("Export contains an unsafe resource path.");
                metadata |= name.equals("pack.mcmeta") && !entry.isDirectory();
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > MAX_UNCOMPRESSED_BYTES) throw new IOException("Expanded export exceeds 64 MiB.");
                }
            }
        }
        if (!metadata) throw new IOException("Export is missing pack.mcmeta.");
    }
    public static synchronized Path write(Path directory, byte[] zip, LocalDateTime time) throws IOException {
        validate(zip);
        Path folder = directory.toAbsolutePath().normalize();
        Files.createDirectories(folder);
        String stem = "mastery-" + TIMESTAMP.format(time);
        Path target;
        for (int suffix = 1; ; suffix++) {
            target = folder.resolve(stem + (suffix == 1 ? "" : "-" + suffix) + ".zip");
            try { Files.createFile(target); break; }
            catch (FileAlreadyExistsException ignored) { }
        }
        Path temporary = null;
        try {
            temporary = Files.createTempFile(folder, ".mastery-export-", ".tmp");
            Files.write(temporary, zip);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            return target;
        } catch (IOException failure) {
            Files.deleteIfExists(target);
            throw failure;
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }
}
