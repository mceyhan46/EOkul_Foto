package com.eokul.studio.core;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Yedek / güvenlik yedeği / rapor dosya adları. Kısa: "Ydk_160726_0816.zip" (önek_gunayyıl_saatdakika).
 * Önek kullanıcı tarafından değiştirilebilir; güvenlik yedeği öneki ("Onc") kullanıcı önekiyle karışmaz.
 */
public final class BackupNames {
    private BackupNames() {}

    public static final String DEFAULT_PREFIX = "Ydk";
    /** Geri yükleme öncesi otomatik güvenlik yedeği öneki. */
    public static final String SAFETY_PREFIX = "Onc";
    public static final String REPORT_PREFIX = "Rpr";
    /** Yedeklerin durduğu alt klasör (kayıt klasörünün içinde). */
    public static final String FOLDER = "Yedek";
    public static final int MAX_PREFIX = 16;

    /** Kullanıcı önekini temizler: yalnızca harf/rakam/-/_ (Türkçe harfler dönüştürülür), en çok 16 karakter; boş ya da ayrılmış ad ise varsayılan. */
    public static String sanitizePrefix(String raw) {
        if (raw == null) return DEFAULT_PREFIX;
        StringBuilder sb = new StringBuilder();
        for (char c : raw.trim().toCharArray()) {
            char m = c;
            switch (c) {
                case 'ç': m = 'c'; break; case 'Ç': m = 'C'; break;
                case 'ğ': m = 'g'; break; case 'Ğ': m = 'G'; break;
                case 'ı': m = 'i'; break; case 'İ': m = 'I'; break;
                case 'ö': m = 'o'; break; case 'Ö': m = 'O'; break;
                case 'ş': m = 's'; break; case 'Ş': m = 'S'; break;
                case 'ü': m = 'u'; break; case 'Ü': m = 'U'; break;
                case ' ': m = '_'; break;
                default: break;
            }
            if ((m >= 'a' && m <= 'z') || (m >= 'A' && m <= 'Z') || (m >= '0' && m <= '9') || m == '-' || m == '_') sb.append(m);
            if (sb.length() >= MAX_PREFIX) break;
        }
        String p = sb.toString();
        while (p.startsWith("_") || p.startsWith("-")) p = p.substring(1);
        if (p.isEmpty() || p.equalsIgnoreCase(SAFETY_PREFIX) || p.equalsIgnoreCase(REPORT_PREFIX)) return DEFAULT_PREFIX;
        return p;
    }

    /** "160726_0816": gün-ay-yıl(2 hane)_saat-dakika. */
    public static String stamp(Date d, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz, Locale.US);
        c.setTime(d);
        return String.format(Locale.US, "%02d%02d%02d_%02d%02d",
            c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR) % 100,
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    public static String name(String prefix, Date d, TimeZone tz, String ext) {
        return prefix + "_" + stamp(d, tz) + "." + ext;
    }

    /** Güvenlik yedeği mi (yeni "Onc_…" veya eski "EOkulStudio_GeriYuklemeOncesi_…")? */
    public static boolean isSafetyBackup(String n) {
        if (n == null || !n.endsWith(".zip")) return false;
        return n.startsWith(SAFETY_PREFIX + "_") || n.startsWith("EOkulStudio_GeriYuklemeOncesi_");
    }

    /**
     * Sıralama anahtarı (büyük = yeni): yeni biçimde "ddMMyy_HHmm" → yyMMddHHmm, eski biçimde "yyyy-MM-dd_HH-mm-ss" → yyMMddHHmmss.
     * Tarih okunamazsa 0 (en eski sayılır).
     */
    public static long sortKey(String n) {
        try {
            if (n.startsWith("EOkulStudio_GeriYuklemeOncesi_")) {
                String s = n.substring("EOkulStudio_GeriYuklemeOncesi_".length(), n.length() - 4);   // 2026-11-12_06-39-10
                String digits = s.replace("-", "").replace("_", "");                                  // 20261112063910
                return Long.parseLong(digits.substring(2));   // 261112063910 (12 hane)
            }
            String s = n.substring(n.indexOf('_') + 1, n.length() - 4);   // 160726_0816
            String[] p = s.split("_");
            String d = p[0], t = p[1];
            return Long.parseLong(d.substring(4, 6) + d.substring(2, 4) + d.substring(0, 2) + t) * 100L;   // yyMMddHHmm00 (12 hane)
        } catch (RuntimeException e) {
            return 0L;
        }
    }
}
