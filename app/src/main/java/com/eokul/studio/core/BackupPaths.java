package com.eokul.studio.core;

import java.io.File;
import java.io.IOException;

/** Yedek ZIP girdi adları. Dosya yolunun sembolik bağlantılı olup olmamasından bağımsız, her zaman "photos/sınıf/no.jpg" biçimi. */
public final class BackupPaths {
    private BackupPaths() {}

    /** [file], [base] içindeyse base'e göre göreli, '/' ayraçlı ad; dışındaysa IOException. Her iki yol da kanonikleştirilir. */
    public static String entryName(File base, File file) throws IOException {
        String b = base.getCanonicalPath();
        String f = file.getCanonicalPath();
        String prefix = b.endsWith(File.separator) ? b : b + File.separator;
        if (!f.startsWith(prefix)) throw new IOException("Dosya yedek köküne ait değil");
        return f.substring(prefix.length()).replace('\\', '/');
    }

    /** Android'in ZipInputStream'inin de reddedeceği adları baştan eler: mutlak yol, "..", boş ya da NUL içeren ad. */
    public static boolean isSafeEntryName(String name) {
        if (name == null || name.isEmpty() || name.indexOf('\0') >= 0) return false;
        if (name.startsWith("/") || name.startsWith("\\") || name.indexOf(':') >= 0) return false;
        for (String part : name.split("[/\\\\]", -1)) {
            if (part.equals("..")) return false;
        }
        return true;
    }
}
