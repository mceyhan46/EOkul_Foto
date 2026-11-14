package com.eokul.studio.core;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

public class BackupZipTest {
    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private File[] twoPhotos(Path base) throws IOException {
        Path d = Files.createDirectories(base.resolve("photos/c1"));
        return new File[]{Files.write(d.resolve("1.jpg"), b("aaa")).toFile(), Files.write(d.resolve("2.jpg"), b("bbbb")).toFile()};
    }

    @Test public void writeThenReadRoundTrips() throws Exception {
        Path base = Files.createTempDirectory("fd");
        List<File> photos = Arrays.asList(twoPhotos(base));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BackupZip.write(out, b("{\"m\":1}"), b("{\"r\":2}"), base.toFile(), photos);
        Set<String> exp = BackupZip.expectedNames(base.toFile(), photos);
        assertEquals(4, exp.size());
        assertTrue(exp.contains("photos/c1/1.jpg"));
        BackupZip.Content c = BackupZip.read(new ByteArrayInputStream(out.toByteArray()), exp);
        assertEquals(4, c.entries);
        assertEquals("{\"m\":1}", c.manifest);
        assertEquals("{\"r\":2}", c.roster);
    }

    @Test public void emptyPhotoSetWorks() throws Exception {
        Path base = Files.createTempDirectory("fd");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BackupZip.write(out, b("m"), b("r"), base.toFile(), java.util.Collections.<File>emptyList());
        BackupZip.Content c = BackupZip.read(new ByteArrayInputStream(out.toByteArray()),
            BackupZip.expectedNames(base.toFile(), java.util.Collections.<File>emptyList()));
        assertEquals(2, c.entries);
    }

    @Test(expected = IOException.class) public void missingEntryIsDetected() throws Exception {
        Path base = Files.createTempDirectory("fd");
        List<File> photos = Arrays.asList(twoPhotos(base));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BackupZip.write(out, b("m"), b("r"), base.toFile(), photos.subList(0, 1));
        BackupZip.read(new ByteArrayInputStream(out.toByteArray()), BackupZip.expectedNames(base.toFile(), photos));
    }

    @Test(expected = IOException.class) public void unexpectedEntryIsDetected() throws Exception {
        Path base = Files.createTempDirectory("fd");
        List<File> photos = Arrays.asList(twoPhotos(base));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BackupZip.write(out, b("m"), b("r"), base.toFile(), photos);
        Set<String> exp = BackupZip.expectedNames(base.toFile(), photos);
        exp.remove("photos/c1/2.jpg");
        BackupZip.read(new ByteArrayInputStream(out.toByteArray()), exp);
    }

    @Test(expected = IOException.class) public void traversalEntryIsRejected() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (String n : new String[]{"yedek.json", "roster.json", "photos/../../../x.jpg"}) {
                z.putNextEntry(new ZipEntry(n)); z.write(b("x")); z.closeEntry();
            }
        }
        Set<String> exp = new HashSet<>(Arrays.asList("yedek.json", "roster.json", "photos/../../../x.jpg"));
        BackupZip.read(new ByteArrayInputStream(out.toByteArray()), exp);
    }

    @Test(expected = IOException.class) public void truncatedZipIsDetected() throws Exception {
        Path base = Files.createTempDirectory("fd");
        List<File> photos = Arrays.asList(twoPhotos(base));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BackupZip.write(out, b("m"), b("r"), base.toFile(), photos);
        byte[] all = out.toByteArray();
        BackupZip.read(new ByteArrayInputStream(Arrays.copyOf(all, all.length / 4)), BackupZip.expectedNames(base.toFile(), photos));
    }
}
