package com.eokul.studio.core;

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.junit.Test;

public class BackupNamesTest {
    private static Date at(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(y, mo - 1, d, h, mi, 0);
        return c.getTime();
    }

    @Test public void kisaAdBicimi() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        assertEquals("Ydk_160726_0816.zip", BackupNames.name("Ydk", at(2026, 7, 16, 8, 16), utc, "zip"));
        assertEquals("Rpr_011226_2359.txt", BackupNames.name("Rpr", at(2026, 12, 1, 23, 59), utc, "txt"));
    }

    @Test public void onekTemizlenir() {
        assertEquals("Ydk", BackupNames.sanitizePrefix(null));
        assertEquals("Ydk", BackupNames.sanitizePrefix("   "));
        assertEquals("Okul_Yedek", BackupNames.sanitizePrefix(" Okul Yedek "));
        assertEquals("Sinif_Yedegi", BackupNames.sanitizePrefix("Sınıf Yedeği"));
        assertEquals("abc", BackupNames.sanitizePrefix("a/b\\c:*?"));
        assertEquals(16, BackupNames.sanitizePrefix("abcdefghijklmnopqrstuvwxyz").length());
    }

    @Test public void ayrilmisOnekler_kullaniciYedeginiSilinmedenKorur() {
        assertEquals("Ydk", BackupNames.sanitizePrefix("Onc"));
        assertEquals("Ydk", BackupNames.sanitizePrefix("onc"));
        assertEquals("Ydk", BackupNames.sanitizePrefix("Rpr"));
        assertFalse(BackupNames.isSafetyBackup("Ydk_160726_0816.zip"));
        assertTrue(BackupNames.isSafetyBackup("Onc_160726_0816.zip"));
        assertTrue(BackupNames.isSafetyBackup("EOkulStudio_GeriYuklemeOncesi_2026-11-12_06-39-10.zip"));
        assertFalse(BackupNames.isSafetyBackup("Onc_160726_0816.zip.tmp"));
    }

    @Test public void siralamaAnahtari() {
        assertTrue(BackupNames.sortKey("Onc_020127_0010.zip") > BackupNames.sortKey("Onc_311226_2359.zip"));
        assertTrue(BackupNames.sortKey("Onc_151126_0700.zip") > BackupNames.sortKey("EOkulStudio_GeriYuklemeOncesi_2026-11-14_23-59-59.zip"));
        assertEquals(0L, BackupNames.sortKey("bozuk.zip"));
    }
}
