package com.eokul.studio.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Uygulamanın kendi aldığı güvenlik yedeklerinin (GeriYuklemeOncesi) saklama kuralı. Kullanıcının yedeklerine dokunmaz. */
public final class BackupRetention {
    private BackupRetention() {}

    /**
     * [names] içinden silinecek güvenlik yedeklerini döndürür: yalnızca güvenlik yedeği adlı (Onc_… / eski EOkulStudio_GeriYuklemeOncesi_…)
     * dosyalar değerlendirilir, adındaki tarih damgasına göre en yeni [keep] tanesi korunur. Diğer dosyalara dokunulmaz.
     */
    public static List<String> safetyBackupsToDelete(List<String> names, int keep) {
        List<String> safety = new ArrayList<>();
        for (String n : names) {
            if (BackupNames.isSafetyBackup(n)) safety.add(n);
        }
        // Yeni ve eski ad biçimleri karışık olabilir: tarih damgasına göre yeni önce.
        Collections.sort(safety, (x, y) -> {
            int c = Long.compare(BackupNames.sortKey(y), BackupNames.sortKey(x));
            return c != 0 ? c : y.compareTo(x);
        });
        int k = Math.max(0, keep);
        return safety.size() <= k ? new ArrayList<String>() : new ArrayList<>(safety.subList(k, safety.size()));
    }
}
