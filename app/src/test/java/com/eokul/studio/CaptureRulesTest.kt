package com.eokul.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fotoğraf kaydı kuralları: "tekrar çek" işareti, puan, kutu ve geri alma davranışları. */
class CaptureRulesTest {
    private val box = Box(10.0, 20.0, 110.0, 150.0)
    private val flagged = Student(
        "1001", "Ali", captured = true, updatedAt = 100L, box = Box(0.0, 0.0, 50.0, 60.0), boxW = 800, boxH = 600,
        origUri = "content://old", savedStem = "1001-Ali", faceCount = 1, retake = true, score = 40
    )

    @Test
    fun kirpmaDuzenleme_tekrarIsaretiniKaldirirVeKutuyuGuncelle() {
        val s = CaptureRules.optimistic(flagged, box, 1600, 1200, 1, retake = false, newScore = -1, now = 500L)
        assertFalse(s.retake)                 // düzenleme sonrası "çekildi" görünür
        assertEquals(40, s.score)             // yeni puan hesaplanmadıysa eski puan korunur
        assertEquals(box, s.box)
        assertEquals(1600, s.boxW)
        assertEquals(500L, s.updatedAt)
        assertTrue(s.captured)
        assertEquals("content://old", s.origUri)   // dosya bilgisi başarıyla yazılana kadar eski kalır
    }

    @Test
    fun yeniPuanVarsaKullanilir_uyariVarsaTekrarIsaretlenir() {
        val s = CaptureRules.optimistic(Student("5", "Ayşe"), box, 800, 600, 2, retake = true, newScore = 62, now = 1L)
        assertTrue(s.retake)
        assertEquals(62, s.score)
        assertEquals(2, s.faceCount)
    }

    @Test
    fun basariliKayit_yalnizcaDosyaBilgisiniEkler() {
        val optimistic = CaptureRules.optimistic(flagged, box, 1600, 1200, 1, false, 75, 500L)
        val done = CaptureRules.saved(optimistic, "content://new", "1001-Ali Veli", 900L)
        assertEquals("content://new", done.origUri)
        assertEquals("1001-Ali Veli", done.savedStem)
        assertEquals(900L, done.updatedAt)
        assertFalse(done.retake)              // kayıt sonrası işaret geri gelmez
        assertEquals(75, done.score)
        assertEquals(box, done.box)
    }

    @Test
    fun basarisizKayit_cekimAlanlariniGeriAlir_adNumaraDuzenlemesiniKorur() {
        val optimistic = CaptureRules.optimistic(flagged, box, 1600, 1200, 1, false, 90, 500L)
        val renamedMeanwhile = optimistic.copy(name = "Ali Veli", no = "1001")
        val rolled = CaptureRules.rollback(renamedMeanwhile, flagged)
        assertEquals("Ali Veli", rolled.name)       // bu arada yapılan ad düzenlemesi korunur
        assertTrue(rolled.retake)                   // çekimle değişen alanlar eski hâline döner
        assertEquals(40, rolled.score)
        assertEquals(flagged.box, rolled.box)
        assertEquals(100L, rolled.updatedAt)
        assertEquals("content://old", rolled.origUri)
    }

    @Test
    fun kararliSnapshot_bekleyenOgrenciyiDiskteEskiHaliyleTemsilEder() {
        val optimistic = CaptureRules.optimistic(flagged, box, 1600, 1200, 1, false, 90, 500L)
        val other = Student("1002", "Veli", captured = true, retake = true)
        val classes = listOf(SchoolClass("9-B", "9-B", listOf(optimistic, other)), SchoolClass("9-C", "9-C", listOf(optimistic.copy(no = "1001"))))
        val pending = mapOf(CaptureRules.key("9-B", "1001") to flagged)

        val snap = CaptureRules.stableSnapshot(classes, pending)

        assertTrue(snap[0].students[0].retake)                     // bekleyen: diskte eski hâl (henüz yazılmadı)
        assertEquals(flagged, snap[0].students[0])
        assertSame(other, snap[0].students[1])                     // bekleyen olmayanlar aynen
        assertFalse(snap[1].students[0].retake)                    // aynı numara başka sınıfta: etkilenmez
        assertFalse(classes[0].students[0].retake)                 // bellekteki liste değişmez
    }

    @Test
    fun bekleyenYoksa_listeAynenDoner() {
        val classes = listOf(SchoolClass("a", "A", listOf(flagged)))
        assertSame(classes, CaptureRules.stableSnapshot(classes, emptyMap()))
    }
}
