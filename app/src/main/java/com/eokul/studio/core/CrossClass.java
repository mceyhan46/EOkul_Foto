package com.eokul.studio.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Aynı öğrenci numarasının birden çok sınıfta bulunmasını tespit eder (engellemez, uyarı içindir). */
public final class CrossClass {
    private CrossClass() {}

    /** [no] numarasını içeren, [exceptKey] dışındaki sınıf anahtarları (girdi sırasıyla). */
    public static List<String> classesWith(String no, Map<String, Set<String>> byClass, String exceptKey) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : byClass.entrySet()) {
            if (e.getKey().equals(exceptKey)) continue;
            if (e.getValue().contains(no)) out.add(e.getKey());
        }
        return out;
    }

    /** [nos] içinden, [exceptKey] dışındaki herhangi bir sınıfta da bulunan numara sayısı (tekrarlar bir kez sayılır). */
    public static int countShared(Collection<String> nos, Map<String, Set<String>> byClass, String exceptKey) {
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        int n = 0;
        for (String no : nos) {
            if (!seen.add(no)) continue;
            if (!classesWith(no, byClass, exceptKey).isEmpty()) n++;
        }
        return n;
    }
}
