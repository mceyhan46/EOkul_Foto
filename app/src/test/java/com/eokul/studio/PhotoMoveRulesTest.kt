package com.eokul.studio

import com.eokul.studio.PhotoMoveRules.Mode
import com.eokul.studio.PhotoMoveRules.Ref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMoveRulesTest {
    private val box = Box(1.0, 2.0, 3.0, 4.0)
    private fun photo(no: String, name: String, orig: String? = "o-$no") = Student(
        no, name, captured = true, updatedAt = 5, box = box, boxW = 1200, boxH = 1600, origUri = orig,
        savedStem = "$no-$name", faceCount = 1, retake = true, score = 88
    )

    private val a = SchoolClass("c1", "9-A", listOf(photo("1", "Ali Veli"), Student("2", "Ayşe Kaya"), photo("3", "Can Er")))
    private val b = SchoolClass("c2", "9-B", listOf(Student("1", "Deniz Ak")))
    private val all = listOf(a, b)
    private val idle: (Ref) -> Boolean = { false }

    private fun st(list: List<SchoolClass>, c: String, no: String) = list.first { it.id == c }.students.first { it.no == no }

    @Test fun sorunlar() {
        assertNull(PhotoMoveRules.problem(all, Ref("c1", "1"), Ref("c1", "2"), idle))
        assertNotNull(PhotoMoveRules.problem(all, Ref("c1", "1"), Ref("c1", "1"), idle))
        assertNotNull(PhotoMoveRules.problem(all, Ref("c1", "2"), Ref("c1", "1"), idle))      // kaynakta fotoğraf yok
        assertNotNull(PhotoMoveRules.problem(all, Ref("c1", "1"), Ref("c1", "99"), idle))     // hedef yok
        assertNotNull(PhotoMoveRules.problem(all, Ref("c9", "1"), Ref("c1", "2"), idle))      // sınıf yok
        assertTrue(PhotoMoveRules.problem(all, Ref("c1", "1"), Ref("c1", "2")) { it.no == "2" }!!.contains("sürüyor"))
        assertNull(PhotoMoveRules.problem(all, Ref("c1", "1"), Ref("c2", "1"), idle))         // aynı numara, başka sınıf
    }

    @Test fun secenekler() {
        assertEquals(listOf(Mode.MOVE), PhotoMoveRules.modes(all, Ref("c1", "2")))
        assertEquals(listOf(Mode.SWAP, Mode.REPLACE), PhotoMoveRules.modes(all, Ref("c1", "3")))
    }

    @Test fun adayListesi() {
        assertEquals(listOf("2", "3"), PhotoMoveRules.candidates(a, "1", "", true).map { it.no })
        assertEquals(listOf("1"), PhotoMoveRules.candidates(b, "1", "", false).map { it.no })   // başka sınıfta aynı numara görünür
        assertEquals(listOf("2"), PhotoMoveRules.candidates(a, "1", "ayşe", true).map { it.no })
        assertEquals(listOf("2"), PhotoMoveRules.candidates(a, "1", "AYŞE", true).map { it.no })
        assertTrue(PhotoMoveRules.candidates(a, "1", "zzz", true).isEmpty())
    }

    @Test fun bosHedefeTasima() {
        val r = PhotoMoveRules.apply(all, Ref("c1", "1"), Ref("c1", "2"), Mode.MOVE, 100, "yeni-orijinal", null)
        val d = st(r, "c1", "2")
        assertTrue(d.captured)
        assertEquals("Ayşe Kaya", d.name)                        // öğrencinin kimliği değişmez
        assertEquals("2-Ayşe Kaya", d.savedStem)
        assertEquals("yeni-orijinal", d.origUri)
        assertEquals(box, d.box); assertEquals(1200, d.boxW); assertEquals(88, d.score); assertTrue(d.retake)
        assertEquals(100L, d.updatedAt)
        val s = st(r, "c1", "1")
        assertFalse(s.captured); assertNull(s.box); assertNull(s.origUri); assertNull(s.savedStem); assertEquals(-1, s.score)
        assertFalse(s.retake); assertEquals(0, s.faceCount)
        assertEquals("Ali Veli", s.name)
    }

    @Test fun baskaSinifaTasima() {
        val r = PhotoMoveRules.apply(all, Ref("c1", "1"), Ref("c2", "1"), Mode.MOVE, 7, "x", null)
        assertTrue(st(r, "c2", "1").captured)
        assertEquals("1-Deniz Ak", st(r, "c2", "1").savedStem)
        assertFalse(st(r, "c1", "1").captured)         // aynı numara: yanlış öğrenci etkilenmemeli
    }

    @Test fun yerDegistirme() {
        val r = PhotoMoveRules.apply(all, Ref("c1", "1"), Ref("c1", "3"), Mode.SWAP, 50, "o3-yeni", "o1-yeni")
        val x = st(r, "c1", "1"); val y = st(r, "c1", "3")
        assertTrue(x.captured && y.captured)
        assertEquals("o1-yeni", x.origUri); assertEquals("o3-yeni", y.origUri)
        assertEquals("1-Ali Veli", x.savedStem); assertEquals("3-Can Er", y.savedStem)
        assertNotEquals(x.updatedAt, y.updatedAt)
    }

    @Test fun ustuneYazma() {
        val r = PhotoMoveRules.apply(all, Ref("c1", "1"), Ref("c1", "3"), Mode.REPLACE, 50, "yeni", null)
        assertTrue(st(r, "c1", "3").captured)
        assertFalse(st(r, "c1", "1").captured)
    }

    @Test fun digerOgrencilereDokunmaz() {
        val r = PhotoMoveRules.apply(all, Ref("c1", "1"), Ref("c1", "2"), Mode.MOVE, 1, null, null)
        assertEquals(st(all, "c1", "3"), st(r, "c1", "3"))
        assertEquals(st(all, "c2", "1"), st(r, "c2", "1"))
        assertEquals(all.size, r.size)
    }

    @Test fun bilinmeyenRefListeyiDegistirmez() {
        assertEquals(all, PhotoMoveRules.apply(all, Ref("c1", "1"), Ref("c1", "99"), Mode.MOVE, 1, null, null))
    }
}
