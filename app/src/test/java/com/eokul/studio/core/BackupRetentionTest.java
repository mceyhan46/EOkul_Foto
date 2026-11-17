package com.eokul.studio.core;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class BackupRetentionTest {
    private static final String OLD = "EOkulStudio_GeriYuklemeOncesi_";

    @Test
    public void enYeniIkisiKalir_yeniAdBicimi() {
        List<String> names = Arrays.asList("Onc_121126_0639.zip", "Onc_151126_0700.zip", "Onc_131126_0800.zip", "Onc_141126_0900.zip");
        assertEquals(Arrays.asList("Onc_131126_0800.zip", "Onc_121126_0639.zip"), BackupRetention.safetyBackupsToDelete(names, 2));
    }

    @Test
    public void ayVeYilSiniriDogruSiralanir() {
        // 31 Aralık 26 (yeni), 02 Ocak 27 (en yeni): gün başta olsa da yıl/ay/gün sırasıyla karşılaştırılır
        List<String> names = Arrays.asList("Onc_311226_2359.zip", "Onc_020127_0010.zip", "Onc_301126_1200.zip");
        assertEquals(Arrays.asList("Onc_301126_1200.zip"), BackupRetention.safetyBackupsToDelete(names, 2));
    }

    @Test
    public void eskiVeYeniAdlarKarisikSiralanir() {
        List<String> names = Arrays.asList(OLD + "2026-11-12_06-39-10.zip", "Onc_151126_0700.zip", OLD + "2026-11-14_09-00-00.zip");
        assertEquals(Arrays.asList(OLD + "2026-11-12_06-39-10.zip"), BackupRetention.safetyBackupsToDelete(names, 2));
    }

    @Test
    public void kullaniciYedeklerineVeDigerDosyalaraDokunulmaz() {
        List<String> names = Arrays.asList(
            "Ydk_101126_1000.zip", "EOkulStudio_Yedek_2026-11-10_10-00-00.zip", "Rpr_101126_1000.txt", "9-A", "Orijinaller",
            "Onc_111126_1000.zip", "Onc_121126_1000.zip", "Onc_131126_1000.zip", "Onc_131126_1000.zip.tmp");
        assertEquals(Arrays.asList("Onc_121126_1000.zip", "Onc_111126_1000.zip"), BackupRetention.safetyBackupsToDelete(names, 1));
    }

    @Test
    public void azSayidaDosyadaHicbirSeySilinmez() {
        assertTrue(BackupRetention.safetyBackupsToDelete(Arrays.asList("Onc_111126_1000.zip"), 2).isEmpty());
        assertTrue(BackupRetention.safetyBackupsToDelete(Arrays.<String>asList(), 2).isEmpty());
        assertEquals(1, BackupRetention.safetyBackupsToDelete(Arrays.asList("Onc_111126_1000.zip"), 0).size());
    }
}
