package com.eokul.studio.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Uygulamanın kendi aldığı güvenlik yedeklerinin (GeriYuklemeOncesi) saklama kuralı. Kullanıcının yedeklerine dokunmaz. */
public final class BackupRetention {
    private BackupRetention() {}

    /** Geri yükleme öncesi güvenlik yedeklerinin ad öneki. */
    public static final String SAFETY_PREFIX = "EOkulStudio_GeriYuklemeOncesi_";

    /**
     * [names] içinden silinecek güvenlik yedeklerini döndürür: yalnızca "EOkulStudio_GeriYuklemeOncesi_*.zip" adlı
     * dosyalar değerlendirilir, adındaki tarih damgasına göre en yeni [keep] tanesi korunur. Diğer dosyalara dokunulmaz.
     */
    public static List<String> safetyBackupsToDelete(List<String> names, int keep) {
        List<String> safety = new ArrayList<>();
        for (String n : names) {
            if (n != null && n.startsWith(SAFETY_PREFIX) && n.endsWith(".zip")) safety.add(n);
        }
        Collections.sort(safety, Collections.reverseOrder());   // tarih damgası sıralı: yeni önce
        int k = Math.max(0, keep);
        return safety.size() <= k ? new ArrayList<String>() : new ArrayList<>(safety.subList(k, safety.size()));
    }
}
