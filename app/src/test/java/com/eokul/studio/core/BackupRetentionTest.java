package com.eokul.studio.core;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class BackupRetentionTest {
    private static final String P = BackupRetention.SAFETY_PREFIX;

    @Test
    public void enYeniIkisiKalir() {
        List<String> names = Arrays.asList(
            P + "2026-11-12_06-39-10.zip", P + "2026-11-15_07-00-00.zip", P + "2026-11-13_08-00-00.zip", P + "2026-11-14_09-00-00.zip");
        List<String> del = BackupRetention.safetyBackupsToDelete(names, 2);
        assertEquals(Arrays.asList(P + "2026-11-13_08-00-00.zip", P + "2026-11-12_06-39-10.zip"), del);
    }

    @Test
    public void kullaniciYedeklerineVeDigerDosyalaraDokunulmaz() {
        List<String> names = Arrays.asList(
            "EOkulStudio_Yedek_2026-11-10_10-00-00.zip", "EOkulStudio_Rapor_2026-11-10_10-00-00.txt", "9-A", "Orijinaller",
            P + "2026-11-11_10-00-00.zip", P + "2026-11-12_10-00-00.zip", P + "2026-11-13_10-00-00.zip", P + "2026-11-13_10-00-00.zip.tmp");
        List<String> del = BackupRetention.safetyBackupsToDelete(names, 1);
        assertEquals(Arrays.asList(P + "2026-11-12_10-00-00.zip", P + "2026-11-11_10-00-00.zip"), del);
    }

    @Test
    public void azSayidaDosyadaHicbirSeySilinmez() {
        assertTrue(BackupRetention.safetyBackupsToDelete(Arrays.asList(P + "2026-11-11_10-00-00.zip"), 2).isEmpty());
        assertTrue(BackupRetention.safetyBackupsToDelete(Arrays.<String>asList(), 2).isEmpty());
        assertEquals(1, BackupRetention.safetyBackupsToDelete(Arrays.asList(P + "a.zip"), 0).size());
    }
}
