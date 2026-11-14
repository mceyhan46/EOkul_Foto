package com.eokul.studio.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Yedek ZIP'inin yazılması ve geri okunup yapısının doğrulanması. Android'e bağlı değildir. */
public final class BackupZip {
    public static final String MANIFEST = "yedek.json";
    public static final String ROSTER = "roster.json";

    private BackupZip() {}

    public static final class Content {
        public final int entries;
        public final String manifest;
        public final String roster;

        Content(int entries, String manifest, String roster) {
            this.entries = entries;
            this.manifest = manifest;
            this.roster = roster;
        }
    }

    /** Beklenen girdi adları: manifest, roster ve her fotoğrafın [base]'e göre adı. */
    public static Set<String> expectedNames(File base, List<File> photos) throws IOException {
        Set<String> names = new HashSet<>();
        names.add(MANIFEST);
        names.add(ROSTER);
        for (File f : photos) names.add(BackupPaths.entryName(base, f));
        return names;
    }

    public static void write(OutputStream out, byte[] manifest, byte[] roster, File base, List<File> photos) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, MANIFEST, manifest);
            put(zip, ROSTER, roster);
            byte[] buf = new byte[16 * 1024];
            for (File f : photos) {
                zip.putNextEntry(new ZipEntry(BackupPaths.entryName(base, f)));
                try (InputStream in = new FileInputStream(f)) {
                    int n;
                    while ((n = in.read(buf)) >= 0) zip.write(buf, 0, n);
                }
                zip.closeEntry();
            }
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    /**
     * ZIP'i sonuna kadar okur (CRC kontrolü için), girdi adlarını [expected] ile karşılaştırır ve manifest ile roster metnini döndürür.
     * Yinelenen, güvensiz ya da beklenmeyen/eksik ad bulunursa IOException.
     */
    public static Content read(InputStream in, Set<String> expected) throws IOException {
        int entries = 0;
        String manifest = null, roster = null;
        Set<String> seen = new HashSet<>();
        byte[] sink = new byte[16 * 1024];
        try (ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                entries++;
                String name = e.getName();
                if (!BackupPaths.isSafeEntryName(name)) throw new IOException("Yedekte güvensiz ZIP girdisi");
                if (!seen.add(name)) throw new IOException("Yedekte yinelenen ZIP girdisi");
                ByteArrayOutputStream bos = null;
                boolean keep = name.equals(MANIFEST) || name.equals(ROSTER);
                if (keep) bos = new ByteArrayOutputStream();
                int n;
                while ((n = z.read(sink)) >= 0) if (bos != null) bos.write(sink, 0, n);
                if (name.equals(MANIFEST)) manifest = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                else if (name.equals(ROSTER)) roster = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            }
        }
        if (entries != expected.size() || !seen.equals(expected)) {
            throw new IOException("Yedek girdileri doğrulanamadı (" + entries + "/" + expected.size() + ")");
        }
        if (roster == null) throw new IOException("Yedek roster dosyası okunamadı");
        if (manifest == null) throw new IOException("Yedek manifest okunamadı");
        return new Content(entries, manifest, roster);
    }
}
