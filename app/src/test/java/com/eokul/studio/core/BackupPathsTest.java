package com.eokul.studio.core;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class BackupPathsTest {
    /** Geçen sürümdeki hata: kök sembolik bağlantı (/data/user/0 -> /data/data), dosya kanonik yolla bulunur. */
    @Test public void entryNameIsStableWhenBaseIsASymlink() throws Exception {
        Path real = Files.createTempDirectory("real");
        Path photos = Files.createDirectories(real.resolve("photos/c1"));
        Path f = Files.write(photos.resolve("633.jpg"), new byte[]{1});
        Path link = Files.createTempDirectory("lnk").resolve("files");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return; // sembolik bağlantı desteklenmiyorsa atla
        }
        assertEquals("photos/c1/633.jpg", BackupPaths.entryName(link.toFile(), f.toFile()));
        assertEquals("photos/c1/633.jpg", BackupPaths.entryName(real.toFile(), link.resolve("photos/c1/633.jpg").toFile()));
    }

    @Test(expected = java.io.IOException.class)
    public void fileOutsideBaseIsRejected() throws Exception {
        Path a = Files.createTempDirectory("a");
        Path b = Files.createTempDirectory("b");
        BackupPaths.entryName(a.toFile(), Files.write(b.resolve("x"), new byte[]{1}).toFile());
    }

    @Test public void unsafeEntryNamesDetected() {
        assertTrue(BackupPaths.isSafeEntryName("photos/c1/633.jpg"));
        assertTrue(BackupPaths.isSafeEntryName("yedek.json"));
        assertFalse(BackupPaths.isSafeEntryName("../../../../data/x/photos/c/1.jpg"));
        assertFalse(BackupPaths.isSafeEntryName("photos/../../x"));
        assertFalse(BackupPaths.isSafeEntryName("/etc/passwd"));
        assertFalse(BackupPaths.isSafeEntryName("C:\\x"));
        assertFalse(BackupPaths.isSafeEntryName(""));
        assertFalse(BackupPaths.isSafeEntryName(null));
    }
}
