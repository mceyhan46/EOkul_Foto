package com.eokul.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRulesTest {
    private val box = Box(10.0, 20.0, 143.0, 191.0)
    private fun rec(w: Int = 1000, h: Int = 1000) = CropStore.Rec(w, h, box, face = true, faceCount = 2, edited = true, skipped = true)

    @Test fun kayit_boyutAynisaKullanilir() {
        val a = BatchRules.fromRecord(rec(), 1000, 1000)!!
        assertTrue(a.faceFound); assertEquals(2, a.faceCount); assertTrue(a.edited); assertTrue(a.skipped)
        assertEquals(box.x1, a.box.x1, 1.0)
    }

    @Test fun kayit_boyutFarkliysaVeyaYoksaNull() {
        assertNull(BatchRules.fromRecord(rec(), 800, 1000))
        assertNull(BatchRules.fromRecord(rec(), 1000, 900))
        assertNull(BatchRules.fromRecord(null, 1000, 1000))
    }

    @Test fun kayit_kutuGoruntuDisindaysaIceriOturtulur() {
        val big = CropStore.Rec(100, 100, Box(0.0, 0.0, 400.0, 514.0), true, 1, false, false)
        val a = BatchRules.fromRecord(big, 100, 100)!!
        assertTrue(a.box.x2 <= 100.0 + 1e-6 && a.box.y2 <= 100.0 + 1e-6)
    }

    @Test fun durumMetinleri() {
        assertEquals("⏳ Yükleniyor… 3/10", BatchRules.progress("Yükleniyor", 3, 10))
        assertEquals("✅ 10 fotoğraf hazır", BatchRules.loadDone(10, 0))
        assertEquals("✅ 8 fotoğraf hazır  ·  ⚠ 2 okunamadı", BatchRules.loadDone(10, 2))
        assertEquals("✅ 7 fotoğraf yeniden kırpıldı", BatchRules.recropDone(7))
        assertEquals("💾 Kaydediliyor… 1/4", BatchRules.saveProgress(1, 4))
    }

    @Test fun kaydetmeSonucu() {
        val ok = BatchRules.saveOutcome(5, emptyList(), false, "Klasör/Çıktı")
        assertEquals("✅ 5 fotoğraf kaydedildi → Klasör/Çıktı", ok.status)
        assertEquals("✓ 5 fotoğraf kaydedildi → Klasör/Çıktı", ok.toast)
        assertTrue(ok.failures.isEmpty())

        val f = BatchRules.saveOutcome(3, listOf("a.jpg" to "hata"), false, "x")
        assertEquals("⚠ 3 başarılı, 1 hatalı", f.status)
        assertNull(f.toast)
        assertEquals(1, f.failures.size)

        val c = BatchRules.saveOutcome(2, listOf("a" to "b"), true, "x")   // iptal, hata listesinden önce gelir
        assertEquals("⏹ İptal edildi — 2 kaydedildi", c.status)
        assertNotNull(c.toast)
        assertTrue(c.failures.isEmpty())
    }

    @Test fun geriAl_sinirAsilincaEnEskiAtilir() {
        val l = ArrayList<Int>()
        for (i in 1..25) BatchRules.pushCapped(l, i, 20)
        assertEquals(20, l.size)
        assertEquals(6, l.first())
        assertEquals(25, l.last())
    }
}
