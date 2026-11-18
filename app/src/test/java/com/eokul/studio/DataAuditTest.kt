package com.eokul.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataAuditTest {
    private val a = SchoolClass("9-A", "9-A", listOf(
        Student("1", "Ali Veli", captured = true),
        Student("2", "Bekleyen"),
        Student("3", "Kayıp Foto", captured = true),
        Student("4", "Bozuk", captured = true)
    ))
    private val b = SchoolClass("9-B", "9-B", listOf(Student("1", "Ayşe", captured = true)))

    @Test
    fun yerel_eksikBozukYetimVeYinelenen() {
        val l = DataAudit.local(
            listOf(a, b),
            photoState = { _, s -> when (s.no) { "3" -> DataAudit.Photo.MISSING; "4" -> DataAudit.Photo.BAD_SIZE; else -> DataAudit.Photo.OK } },
            expectedName = { _, s -> s.no + ".jpg" },
            privateFiles = { c -> if (c.id == "9-A") listOf("1.jpg", "99.jpg") else listOf("1.jpg") }
        )
        assertEquals(4, l.checked)
        assertEquals(listOf("9-A · 3 Kayıp Foto"), l.missing)
        assertEquals(listOf("9-A · 4 Bozuk"), l.broken)
        assertEquals(listOf("9-A · 99.jpg"), l.orphans)
        assertEquals(listOf("1 → 9-A, 9-B"), l.duplicateNumbers)
    }

    @Test
    fun blok_altiSatirdanFazlasiKisaltilir() {
        val s = DataAudit.block("X", (1..8).map { "k$it" })
        assertTrue(s.startsWith("⚠ X: 8"))
        assertTrue(s.contains("… ve 2 tane daha"))
        assertEquals("✓ X: yok", DataAudit.block("X", emptyList()))
    }

    @Test
    fun klasor_yokOrijinalEksikVeGizliDosya() {
        val fs = mapOf<List<String>, List<String>>(
            listOf("9-A") to listOf("1-Ali Veli.jpg", ".tmp-x"),
            listOf("9-A", ORIGINALS_DIR) to listOf("1-Ali Veli.jpg", "3-Kayıp Foto.jpg")
        )
        val t = DataAudit.folderLines(listOf(a, b), "Kayıt", ORIGINALS_DIR) { fs[it] ?: if (it.isEmpty()) listOf("Eski") else null }
        assertTrue(t.contains("⚠ 9-A: 3 çekilmiş · klasörde fotoğraf eksik: 2 · orijinal eksik: 1 · yarım kalmış gizli dosya: 1 (klasörde 2 öğe)"))
        assertTrue(t.contains("9-B: '9-B' klasörü kayıt klasöründe YOK"))
        assertTrue(t.contains("Kökte bulunanlar: Eski"))
    }

    @Test
    fun cekilmisYoksa_mesaj() {
        val t = DataAudit.folderLines(listOf(SchoolClass("x", "x", listOf(Student("1", "A")))), null, ORIGINALS_DIR) { null }
        assertTrue(t.endsWith("Çekilmiş öğrenci yok."))
    }

    @Test
    fun onarim_savedStemKullanir() {
        val c = SchoolClass("c", "c", listOf(Student("1", "Ali Veli", captured = true, savedStem = "1-Eski Ad"), Student("2", "Veli Can", captured = true), Student("3", "Yok")))
        val r = DataAudit.missingInFolder(c, setOf("1-Eski Ad.jpg"))
        assertEquals(listOf("2"), r.map { it.first.no })
        assertEquals("2-Veli Can", r[0].second)
    }
}
