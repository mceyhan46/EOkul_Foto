package com.eokul.studio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportServiceTest {
    private val dir: File = Files.createTempDirectory("exp").toFile()

    private fun photoFile(cls: SchoolClass, st: Student): File =
        File(dir, "${cls.id}_${st.no}.jpg")

    private fun makePhotos(vararg pairs: Pair<String, String>) {
        for ((c, n) in pairs) File(dir, "${c}_$n.jpg").writeText("jpg-$c-$n")
    }

    private val classes = listOf(
        SchoolClass("9-A", "9-A", listOf(Student("1001", "Ali Veli", captured = true), Student("1002", "Bekleyen"), Student("1003", "Dosyasız", captured = true))),
        SchoolClass("9-B", "9-B", listOf(Student("1001", "Ayşe Kaya", captured = true, retake = true, score = 41)))
    )

    @Test
    fun duzPlan_yinelenenNumaraAtlanirVeBildirilir() {
        makePhotos("9-A" to "1001", "9-B" to "1001")           // 1002 çekilmedi, 1003'ün dosyası yok
        val plan = ExportService.plan(classes, folders = false, photo = ::photoFile)
        assertEquals(listOf("1001.jpg"), plan.items.map { it.first })
        assertEquals(listOf("1001 (9-B)"), plan.duplicates)
    }

    @Test
    fun klasorluPlan_ayniNumaraFarkliSinifta_gecerli() {
        makePhotos("9-A" to "1001", "9-B" to "1001")
        val plan = ExportService.plan(classes, folders = true, photo = ::photoFile)
        assertEquals(listOf("9-A/1001-Ali Veli.jpg", "9-B/1001-Ayşe Kaya.jpg"), plan.items.map { it.first })
        assertTrue(plan.duplicates.isEmpty())
    }

    @Test
    fun rapor_csv_kacisVeDurum() {
        val csv = ExportService.reportCsv(
            listOf(SchoolClass("x", "7;A", listOf(Student("5", "Ad \"Takma\" Soyad", captured = true, retake = true, score = 55), Student("6", "Bekleyen"))))
        )
        val lines = csv.removePrefix("﻿").split("\r\n")
        assertEquals("Sınıf;No;Ad Soyad;Durum;Kalite puanı;Tekrar çekilmeli", lines[0])
        assertEquals("\"7;A\";5;\"Ad \"\"Takma\"\" Soyad\";Çekildi;55;Evet", lines[1])
        assertEquals("\"7;A\";6;Bekleyen;Bekliyor;;", lines[2])
        assertTrue(csv.startsWith("﻿"))
    }

    @Test
    fun zipYazimi_raporVeFotograflarOkunur() {
        makePhotos("9-A" to "1001", "9-B" to "1001")
        val plan = ExportService.plan(classes, folders = true, photo = ::photoFile)
        val bos = ByteArrayOutputStream()
        ExportService.writeZip(bos, plan, classes, withReport = true)
        val got = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bos.toByteArray())).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                got[e.name] = z.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(listOf("rapor.csv", "9-A/1001-Ali Veli.jpg", "9-B/1001-Ayşe Kaya.jpg"), got.keys.toList())
        assertEquals("jpg-9-A-1001", got["9-A/1001-Ali Veli.jpg"])
    }

    @Test
    fun eokulZipAdi() {
        assertEquals("9-A-eokul.zip", ExportService.eokulZipName("9-A"))
    }
}
