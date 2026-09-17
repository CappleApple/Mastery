package com.cappleapple.mastery.client.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ExportWriterTest {
    @TempDir Path directory;
    private static byte[] zip(String entry, String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream stream = new ZipOutputStream(bytes)) {
            stream.putNextEntry(new ZipEntry(entry)); stream.write(text.getBytes(StandardCharsets.UTF_8)); stream.closeEntry();
        }
        return bytes.toByteArray();
    }
    @Test void timestampAndCollisionPreserveExistingArchive() throws IOException {
        byte[] first = zip("pack.mcmeta", "first"), second = zip("pack.mcmeta", "second");
        var time = LocalDateTime.of(2026, 9, 16, 21, 5);
        Path one = ExportWriter.write(directory, first, time), two = ExportWriter.write(directory, second, time);
        assertEquals("mastery-21-05-16-09-2026.zip", one.getFileName().toString());
        assertEquals("mastery-21-05-16-09-2026-2.zip", two.getFileName().toString());
        assertArrayEquals(first, Files.readAllBytes(one)); assertArrayEquals(second, Files.readAllBytes(two));
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }
    @Test void createsClientExportDirectoryAndDecodesExactBytes() throws IOException {
        byte[] bytes = zip("pack.mcmeta", "{\"pack\":{\"pack_format\":48}}");
        assertArrayEquals(bytes, ExportWriter.decode(Base64.getEncoder().encodeToString(bytes)));
        Path file = ExportWriter.write(directory.resolve("config/exports"), bytes, LocalDateTime.now());
        assertTrue(Files.isRegularFile(file)); assertEquals(directory.resolve("config/exports"), file.getParent());
    }
    @Test void invalidAndOversizedTransfersFailBeforeWriting() {
        assertThrows(IOException.class, () -> ExportWriter.decode("not!base64"));
        assertThrows(IOException.class, () -> ExportWriter.decode("A".repeat(ExportWriter.MAX_ENCODED_CHARACTERS + 1)));
        assertThrows(IOException.class, () -> ExportWriter.write(directory, new byte[]{'P','K'}, LocalDateTime.now()));
    }
    @Test void rejectsArchivesWithoutDatapackMetadataOrWithUnsafePaths() throws IOException {
        assertThrows(IOException.class, () -> ExportWriter.validate(zip("../pack.mcmeta", "{}")));
        assertThrows(IOException.class, () -> ExportWriter.validate(zip("data/test/mastery/trees/one.json", "{}")));
        assertThrows(IOException.class, () -> ExportWriter.validate(zip("C:/pack.mcmeta", "{}")));
    }
    @Test void detectsTruncatedZipEntry() throws IOException {
        byte[] bytes = zip("pack.mcmeta", "some resource metadata");
        assertThrows(IOException.class, () -> ExportWriter.validate(java.util.Arrays.copyOf(bytes, 45)));
    }
}
