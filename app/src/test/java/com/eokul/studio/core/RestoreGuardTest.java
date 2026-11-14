package com.eokul.studio.core;

import static org.junit.Assert.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;

public class RestoreGuardTest {
    private static void rejects(String name) {
        try {
            RestoreGuard.photoRelativePath(name);
            fail("kabul edildi: " + name);
        } catch (IOException expected) { }
    }

    @Test public void photoPathAccepted() throws Exception {
        assertEquals("c1/12.jpg", RestoreGuard.photoRelativePath("photos/c1/12.jpg"));
    }

    @Test public void badPhotoPathsRejected() {
        for (String n : new String[]{"photos/", "photos//x.jpg", "photos/../x.jpg", "photos/a/./b.jpg", "photos//abs.jpg",
                "photos/a\\b.jpg", "photos/a/b/", "photos/ /x.jpg", "photos/a\0b.jpg"}) rejects(n);
    }

    @Test public void classifyKinds() {
        assertEquals(RestoreGuard.Kind.ROSTER, RestoreGuard.classify("roster.json"));
        assertEquals(RestoreGuard.Kind.MANIFEST, RestoreGuard.classify("yedek.json"));
        assertEquals(RestoreGuard.Kind.PHOTO, RestoreGuard.classify("photos/c/1.jpg"));
        assertEquals(RestoreGuard.Kind.OTHER, RestoreGuard.classify("evil.sh"));
    }

    @Test public void safeTargetStaysInside() throws Exception {
        File dir = Files.createTempDirectory("rg").toFile();
        File t = RestoreGuard.safeTarget(dir, "c1/1.jpg");
        assertTrue(t.getPath().startsWith(dir.getCanonicalPath()));
        try {
            RestoreGuard.safeTarget(dir, "../escape.jpg");
            fail();
        } catch (IOException expected) { }
    }

    @Test public void duplicateNamesRejected() throws Exception {
        RestoreGuard.Budget b = new RestoreGuard.Budget();
        b.enter("roster.json");
        try {
            b.enter("roster.json");
            fail();
        } catch (IOException expected) { }
    }

    @Test public void entryCountLimit() throws Exception {
        RestoreGuard.Budget b = new RestoreGuard.Budget();
        for (int i = 0; i < RestoreGuard.MAX_ENTRIES; i++) b.enter("photos/c/" + i + ".jpg");
        try {
            b.enter("photos/c/last.jpg");
            fail();
        } catch (IOException expected) { }
    }

    @Test public void perKindSizeLimits() throws Exception {
        RestoreGuard.Budget b = new RestoreGuard.Budget();
        b.enter("photos/c/1.jpg");
        b.add((int) RestoreGuard.MAX_PHOTO);
        try { b.add(1); fail(); } catch (IOException expected) { }

        RestoreGuard.Budget m = new RestoreGuard.Budget();
        m.enter("roster.json");
        m.add((int) RestoreGuard.MAX_META);
        try { m.add(1); fail(); } catch (IOException expected) { }

        RestoreGuard.Budget o = new RestoreGuard.Budget();
        o.enter("other.bin");
        o.add((int) RestoreGuard.MAX_PHOTO + 10); // tanınmayan içerik tek dosya sınırına değil yalnızca toplama tabi
    }

    @Test public void totalBudgetLimit() throws Exception {
        RestoreGuard.Budget b = new RestoreGuard.Budget();
        long left = RestoreGuard.MAX_TOTAL;
        int i = 0;
        while (left > 0) {
            b.enter("x" + i++);
            int chunk = (int) Math.min(left, 100L * 1024 * 1024);
            b.add(chunk);
            left -= chunk;
        }
        b.enter("y");
        try { b.add(1); fail(); } catch (IOException expected) { }
    }

    @Test public void currentSizeResetsPerEntry() throws Exception {
        RestoreGuard.Budget b = new RestoreGuard.Budget();
        b.enter("photos/c/1.jpg");
        b.add((int) RestoreGuard.MAX_PHOTO);
        b.enter("photos/c/2.jpg");
        b.add((int) RestoreGuard.MAX_PHOTO);
    }
}
