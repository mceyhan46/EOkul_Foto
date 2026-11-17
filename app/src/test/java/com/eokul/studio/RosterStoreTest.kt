package com.eokul.studio

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Öğrenci listesinin kaydı/okunması (yedek de aynı serileştiriciyi kullanır): kırpma kutusu, "tekrar çekilmeli" işareti,
 * puan ve zaman damgası hiçbir yolda kaybolmamalı.
 */
class RosterStoreTest {
    private fun tmpDir(): File = Files.createTempDirectory("roster").toFile()

    private fun edited() = Student(
        no = "1015", name = "Ayşe Yılmaz", captured = true, updatedAt = 1_700_000_000_123L,
        box = Box(120.5, 80.25, 520.75, 600.0), boxW = 1600, boxH = 1200,
        origUri = "content://x/orig/1015.jpg", savedStem = "1015-Ayşe Yılmaz", faceCount = 1, retake = false, score = 75
    )

    private fun flagged() = edited().copy(no = "1016", name = "Ali Veli", retake = true, score = 41)

    private fun classes() = listOf(SchoolClass("9-B", "9-B", listOf(edited(), flagged(), Student("1017", "Boş Öğrenci"))))

    @Test
    fun encodeParse_tumAlanlariKorur() {
        val store = RosterStore(File(tmpDir(), "roster.json"))
        val back = store.parseStrict(store.encode(classes()))
        assertEquals(classes(), back)
    }

    @Test
    fun duzenlenmisOgrenci_tekrarIsareti_yedekRoundTripteDegismez() {
        val store = RosterStore(File(tmpDir(), "roster.json"))
        val text = store.encode(classes())
        val s = store.parseStrict(text)[0].students
        assertFalse(s[0].retake)       // düzenlenmiş: işaret kalktı
        assertEquals(75, s[0].score)
        assertTrue(s[1].retake)        // hâlâ tekrar çekilecek
        assertEquals(41, s[1].score)
        assertNotNull(s[0].box)
        assertEquals(1600, s[0].boxW)
        assertNull(s[2].box)
    }

    @Test
    fun kaydetOku_sonDuzenlemeKazanir() {
        val dir = tmpDir()
        val store = RosterStore(File(dir, "roster.json"))
        val first = listOf(SchoolClass("9-B", "9-B", listOf(flagged().copy(no = "1015", name = "Ayşe Yılmaz"))))
        assertNull(store.save(first))
        // Kullanıcı "Kırpmayı düzenle" ile düzeltti: işaret kalkar, puan ve kutu güncellenir
        val fixed = listOf(SchoolClass("9-B", "9-B", listOf(edited().copy(updatedAt = 1_700_000_999_000L))))
        assertNull(store.save(fixed))
        val loaded = RosterStore(File(dir, "roster.json")).load()
        assertEquals(fixed, loaded)
        assertFalse(loaded[0].students[0].retake)
    }

    @Test
    fun anaDosyaBozuksa_sonSaglamYedektenOkunur() {
        val dir = tmpDir()
        val f = File(dir, "roster.json")
        val store = RosterStore(f)
        assertNull(store.save(classes()))
        assertNull(store.save(classes()))           // ikinci kayıtta .bak oluşur
        f.writeText("{bozuk")
        val s2 = RosterStore(f)
        assertEquals(classes(), s2.load())
        assertNotNull(s2.loadNote)
    }

    @Test
    fun bozukMetin_parseStrictHataVerir_parseBosDoner() {
        val store = RosterStore(File(tmpDir(), "roster.json"))
        assertTrue(store.parse("{bozuk").isEmpty())
        var threw = false
        try { store.parseStrict("{bozuk") } catch (e: Exception) { threw = true }
        assertTrue(threw)
    }
}
