package com.eokul.studio.core;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Geri yüklenen (güvenilmeyen) yedek ZIP'i için sınırlar ve yol denetimi. Hem içerik önizlemesi hem gerçek geri yükleme aynı kuralları kullanır.
 * Girdi sayısı, toplam boyut, tek dosya boyutu, yinelenen ad ve fotoğraf yolu burada denetlenir.
 */
public final class RestoreGuard {
    public static final int MAX_ENTRIES = 50_000;
    public static final long MAX_PHOTO = 5L * 1024 * 1024;
    public static final long MAX_META = 30L * 1024 * 1024;
    public static final long MAX_TOTAL = 800L * 1024 * 1024;

    public enum Kind { ROSTER, MANIFEST, PHOTO, OTHER }

    private RestoreGuard() {}

    public static Kind classify(String name) {
        if (name.equals(BackupZip.ROSTER)) return Kind.ROSTER;
        if (name.equals(BackupZip.MANIFEST)) return Kind.MANIFEST;
        if (name.startsWith("photos/")) return Kind.PHOTO;
        return Kind.OTHER;
    }

    /** "photos/" sonrası göreli yol; boş, mutlak, ters bölü ya da "." / ".." / boş parça içeriyorsa IOException. */
    public static String photoRelativePath(String entryName) throws IOException {
        String rel = entryName.startsWith("photos/") ? entryName.substring("photos/".length()) : entryName;
        if (rel.trim().isEmpty() || rel.startsWith("/") || rel.indexOf('\\') >= 0 || rel.indexOf('\0') >= 0) {
            throw new IOException("Geçersiz fotoğraf yolu");
        }
        for (String part : rel.split("/", -1)) {
            if (part.trim().isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Geçersiz fotoğraf yolu");
        }
        return rel;
    }

    /** [dir] içinde kalan hedef dosya; kanonik yol [dir] dışına çıkarsa IOException. */
    public static File safeTarget(File dir, String relative) throws IOException {
        File root = dir.getCanonicalFile();
        File target = new File(root, relative).getCanonicalFile();
        if (!target.getPath().startsWith(root.getPath() + File.separator)) throw new IOException("Yedekte geçersiz dosya yolu");
        return target;
    }

    /** Bir ZIP okuması boyunca girdi sayısı, yinelenen ad ve bayt bütçesini izler. */
    public static final class Budget {
        private int entries;
        private long total;
        private long current;
        private Kind currentKind = Kind.OTHER;
        private final Set<String> seen = new HashSet<>();

        /** Yeni girdi başlar. Sayı sınırı ve yinelenen ad denetlenir; girdinin türünü döndürür. */
        public Kind enter(String name) throws IOException {
            if (++entries > MAX_ENTRIES) throw new IOException("Yedekte çok fazla dosya var");
            if (!seen.add(name)) throw new IOException("Yedekte yinelenen dosya yolu: " + name);
            currentKind = classify(name);
            current = 0;
            return currentKind;
        }

        /** Okunan [n] bayt işlenir; toplam ve türüne göre tek dosya sınırı aşılırsa IOException. */
        public void add(int n) throws IOException {
            current += n;
            total += n;
            if (total > MAX_TOTAL) throw new IOException("Yedek toplam boyut sınırını aşıyor");
            if ((currentKind == Kind.ROSTER || currentKind == Kind.MANIFEST) && current > MAX_META) {
                throw new IOException("Liste veya manifest dosyası beklenenden büyük");
            }
            if (currentKind == Kind.PHOTO && current > MAX_PHOTO) throw new IOException("Fotoğraf dosyası beklenenden büyük");
        }

        public int entries() { return entries; }
    }
}
