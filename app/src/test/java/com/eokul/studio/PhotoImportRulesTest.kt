package com.eokul.studio

import com.eokul.studio.PhotoImportRules.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoImportRulesTest {
    private val nos = setOf("101", "102", "20345", "007")
    private fun f(n: String) = File("content://x/$n", n)

    @Test fun numaraCikarma() {
        assertEquals("101", PhotoImportRules.numberFor("101.jpg", nos))
        assertEquals("101", PhotoImportRules.numberFor("101-Ali Veli.jpg", nos))
        assertEquals("101", PhotoImportRules.numberFor("101_foto.JPG", nos))
        assertEquals("102", PhotoImportRules.numberFor("foto 102.png", nos))
        assertEquals("007", PhotoImportRules.numberFor("7.jpg", nos))
    }

    @Test fun belirsizVeEslesmeyen() {
        assertNull(PhotoImportRules.numberFor("IMG_20240101_123456.jpg", nos))
        assertNull(PhotoImportRules.numberFor("manzara.jpg", nos))
        assertNull(PhotoImportRules.numberFor("foto 101 102.jpg", nos))   // iki sınıf numarası, baştaki sayı yok: belirsiz
        assertNull(PhotoImportRules.numberFor("999.jpg", nos))             // listede yok
    }

    @Test fun basSifirlar() {
        assertEquals("007", PhotoImportRules.numberFor("0007.jpg", nos))
        assertEquals("102", PhotoImportRules.numberFor("0102.jpg", nos))
    }

    @Test fun uzantiKontrolu() {
        assertTrue(PhotoImportRules.looksLikeImage("a.JPG"))
        assertTrue(PhotoImportRules.looksLikeImage("a.heic"))
        assertTrue(PhotoImportRules.looksLikeImage("a"))
        assertFalse(PhotoImportRules.looksLikeImage("a.pdf"))
    }

    private val cls = SchoolClass(
        "c", "9-A", listOf(
            Student("101", "Ali Veli"), Student("102", "Ayşe Kaya", captured = true), Student("103", "Can Er")
        )
    )

    @Test fun plan() {
        val p = PhotoImportRules.plan(cls, listOf(f("103.jpg"), f("101-ali.jpg"), f("102.jpg"), f("999.jpg"), f("liste.pdf"), f("manzara.jpg")))
        assertEquals(listOf("101", "102", "103"), p.matches.map { it.no })            // öğrenci sırası
        assertTrue(p.matches[0].selected)
        assertFalse(p.matches[1].selected)                                            // fotoğrafı var → seçili değil
        assertTrue(p.matches[1].hasPhoto)
        assertTrue(p.matches[2].selected)
        assertEquals(3, p.unmatched.size)                                             // 999, pdf, manzara
        assertEquals(2, p.selectedCount)
    }

    @Test fun ayniOgrenciIcinBirdenCokDosya() {
        val p = PhotoImportRules.plan(cls, listOf(f("101.jpg"), f("101-b.jpg")))
        assertEquals(2, p.matches.size)
        assertTrue(p.matches.none { it.selected })
        assertTrue(p.matches.all { it.note != null })
    }

    @Test fun kuyrukAyniOgrenciyiTekrarlamaz() {
        val p = PhotoImportRules.plan(cls, listOf(f("101.jpg"), f("101-b.jpg")))
        val q = PhotoImportRules.queue(p.matches)
        assertEquals(1, q.size)
        assertEquals("101", q[0].first)
    }

    @Test fun bosSecim() {
        val p = PhotoImportRules.plan(cls, emptyList())
        assertTrue(p.matches.isEmpty() && p.unmatched.isEmpty())
        assertTrue(PhotoImportRules.queue(emptyList()).isEmpty())
    }
}
