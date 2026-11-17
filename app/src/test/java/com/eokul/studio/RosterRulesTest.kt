package com.eokul.studio

import com.eokul.studio.core.RosterParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterRulesTest {
    private var seq = 0
    private fun id() = "id${seq++}"
    private fun rows(text: String) = RosterParser.parse(text, RosterParser.Delimiter.AUTO, false, RosterParser.CaseMode.KEEP).rows

    private val base = listOf(
        SchoolClass("a", "9-A", listOf(Student("10", "Ali Veli", captured = true), Student("20", "Can"))),
        SchoolClass("b", "9-B", listOf(Student("10", "Ayşe Kaya")))
    )

    @Test fun sinifEkle_siraliVeEsDeger() {
        val (l, nid) = RosterRules.addClass(base, "  10-A ", ::id)!!
        assertEquals(listOf("9-A", "9-B", "10-A"), l.map { it.name })
        assertEquals(nid, l.last().id)
        assertNull(RosterRules.addClass(base, "9-a", ::id))      // aynı klasör adı
        assertNull(RosterRules.addClass(base, "   ", ::id))
    }

    @Test fun sinifAdiDegistir() {
        assertEquals("Bu adla başka bir sınıf var", RosterRules.renameClass(base, "a", "9-B").error)
        val ok = RosterRules.renameClass(base, "a", "9-C")
        assertTrue(ok.ok)
        assertEquals(listOf("9-B", "9-C"), ok.classes.map { it.name })
        assertEquals("Sınıf bulunamadı", RosterRules.renameClass(base, "zz", "X").error)
    }

    @Test fun ogrenciEkle_dogrulamaSiraVeUyari() {
        assertEquals("Numara 1–11 haneli sayı olmalı", RosterRules.addStudent(base, "a", "12a", "Ad Soyad").error)
        assertEquals("Ad soyad girin", RosterRules.addStudent(base, "a", "5", "A").error)
        assertEquals("Bu numara zaten var", RosterRules.addStudent(base, "a", "10", "Ad Soyad").error)
        val r = RosterRules.addStudent(base, "a", " 5 ", "Yeni  Öğrenci")
        assertTrue(r.ok)
        assertEquals(listOf("5", "10", "20"), r.classes[0].students.map { it.no })
        assertEquals("Yeni Öğrenci", r.classes[0].students[0].name)
        assertNull(r.note)
        val w = RosterRules.addStudent(base, "a", "30", "Başka Biri").note
        assertNull(w)
        assertNotNull(RosterRules.addStudent(base, "b", "20", "Veli Can").note)   // 20 9-A'da var
    }

    @Test fun ogrenciDuzenle() {
        assertEquals("Öğrenci bulunamadı", RosterRules.editStudent(base, "a", "99", "99", "Ad Soyad", false).error)
        assertEquals("Bu numara bu sınıfta zaten var", RosterRules.editStudent(base, "a", "10", "20", "Ad Soyad", false).error)
        // yalnızca ad: meşgul olsa da geçer
        val nm = RosterRules.editStudent(base, "a", "10", "10", "Yeni Ad", true)
        assertTrue(nm.ok); assertEquals("Yeni Ad", nm.classes[0].students[0].name)
        // numara değişimi: meşgulse reddedilir
        assertTrue(RosterRules.editStudent(base, "a", "10", "30", "Ali Veli", true).error!!.contains("işleniyor"))
        val r = RosterRules.editStudent(base, "a", "10", "30", "Ali Veli", false)
        assertEquals(listOf("20", "30"), r.classes[0].students.map { it.no })
        assertTrue(r.classes[0].students[1].captured)    // durum korunur
        // 10 numarası 9-B'de de vardı: numara 10'a geri değişince uyarı
        val back = RosterRules.editStudent(r.classes, "a", "30", "10", "Ali Veli", false)
        assertNotNull(back.note)
    }

    @Test fun listeIceAktar_yeniSinifVeBirlestirme() {
        val r = RosterRules.importRoster(base, null, "7-C", rows("1 Bir Kişi\n2 İki Kişi"), MergeMode.UPSERT, ::id)!!
        assertEquals("✓ 2 eklendi", r.message)
        assertEquals(2, r.classes.first { it.id == r.classId }.students.size)
        val u = RosterRules.importRoster(base, "a", "", rows("10 Ali Yeni\n40 Dört Kişi"), MergeMode.UPSERT, ::id)!!
        assertTrue(u.message.startsWith("✓ 1 eklendi, 1 güncellendi"))
        assertTrue(u.message.contains("1 numara başka sınıflarda da var"))   // 10 → 9-B
        assertNull(RosterRules.importRoster(base, "a", "", emptyList(), MergeMode.UPSERT, ::id))
    }

    @Test fun subeleriTopluEkle() {
        val plans = listOf(
            RosterRules.BranchPlan("9-A", rows("50 Elli Kişi")),       // var → güncelle
            RosterRules.BranchPlan("11-D", rows("60 Altmış Kişi")),    // yeni
            RosterRules.BranchPlan("  ", rows("70 Yok Kişi"))          // boş ad atlanır
        )
        val r = RosterRules.importBranches(base, plans, ::id)
        assertTrue(r.message.startsWith("✓ 1 sınıf eklendi, 1 sınıf güncellendi · 2 öğrenci"))
        assertEquals(3, r.classes.size)
        assertEquals(3, r.classes.first { it.name == "9-A" }.students.size)
        assertEquals("✓ Değişiklik yok", RosterRules.importBranches(base, emptyList(), ::id).message)
    }
}
