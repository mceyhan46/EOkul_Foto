package com.eokul.studio

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SaveRulesTest {
    private val dir: File = Files.createTempDirectory("ap").toFile()

    @Test fun uzanti_ve_kucultme_karari() {
        assertEquals("png", SaveRules.originalExt("image/png"))
        assertEquals("heic", SaveRules.originalExt("image/heif"))
        assertEquals("jpg", SaveRules.originalExt("application/x"))
        assertFalse(SaveRules.needsCompaction(900_000))
        assertTrue(SaveRules.needsCompaction(900_001))
        assertEquals(1.0, SaveRules.originalScale(1000, 800), 0.0)
        assertEquals(0.5, SaveRules.originalScale(3200, 2400), 1e-9)
    }

    @Test fun sinifOzeti() {
        assertTrue(SaveRules.classSummary(null).startsWith("🎉"))
        val c = SchoolClass("c", "9-A", listOf(
            Student("1", "A A", captured = true, score = 90), Student("2", "B B", captured = true, score = 50)
        ))
        val t = SaveRules.classSummary(c)
        assertTrue(t.contains("9-A: 2/2"))
        assertTrue(t.contains("Kalite ortalaması 70"))
        assertTrue(t.contains("1 düşük puanlı"))
        val ok = SaveRules.classSummary(c.copy(students = listOf(Student("1", "A A", captured = true, score = 95))))
        assertTrue(ok.contains("Düşük puanlı fotoğraf yok."))
    }

    @Test fun atomikYazim_yeniDosyaVeUzerineYazma() {
        val t = File(dir, "p/1.jpg")
        AtomicPhoto.write(t, byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), t.readBytes())
        AtomicPhoto.write(t, byteArrayOf(9, 9))
        assertArrayEquals(byteArrayOf(9, 9), t.readBytes())
        assertEquals(listOf("1.jpg"), t.parentFile.list()!!.toList())   // artık geçici/.old dosya yok
    }

    @Test fun atomikYazim_dogrulamaBasarisizsaEskiDosyaGeriGelir() {
        val t = File(dir, "q/2.jpg")
        AtomicPhoto.write(t, byteArrayOf(1))
        try {
            AtomicPhoto.write(t, byteArrayOf(7, 7)) { throw IOException("boyut yanlış") }
            fail("istisna bekleniyordu")
        } catch (e: IOException) {
            assertEquals("boyut yanlış", e.message)
        }
        assertArrayEquals(byteArrayOf(1), t.readBytes())
        assertEquals(listOf("2.jpg"), t.parentFile.list()!!.toList())
    }

    @Test fun atomikYazim_dogrulamaBasarisizVeEskiYoksa_hedefOlusmaz() {
        val t = File(dir, "r/3.jpg")
        try { AtomicPhoto.write(t, byteArrayOf(5)) { throw IOException("x") } } catch (_: IOException) {}
        assertFalse(t.exists())
    }
}
