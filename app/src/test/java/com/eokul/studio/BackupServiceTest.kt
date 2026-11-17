package com.eokul.studio

import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Gerçek dosya sistemi üzerinde yedek alma / inceleme / geri yükleme / temizlik testleri (telefon gerekmez). */
class BackupServiceTest {

    /** Test için klasör tabanlı depolama. */
    private class DirStorage(val dir: File) : BackupStorage {
        init { dir.mkdirs() }
        override fun write(name: String, writer: (OutputStream) -> Unit): StoredFile {
            val f = File(dir, name)
            f.outputStream().use(writer)
            return StoredFile { FileInputStream(f) }
        }
        override fun delete(name: String) { File(dir, name).delete() }
        override fun list(): List<String> = dir.list()?.toList() ?: emptyList()
    }

    /** Bir "uygulama verisi" klasörü: roster.json + photos/. */
    private class Device(val filesDir: File = Files.createTempDirectory("filesdir").toFile()) {
        val store = RosterStore(File(filesDir, "roster.json"))
        val service = BackupService(filesDir, store, Mutex(), "1.1.test") { true }
        fun photo(classId: String, no: String, bytes: ByteArray = byteArrayOf(1, 2, 3, no.hashCode().toByte())) {
            val f = File(filesDir, "photos/$classId/$no.jpg")
            f.parentFile!!.mkdirs()
            f.writeBytes(bytes)
        }
    }

    private fun student(no: String, retake: Boolean, score: Int = 70, box: Box? = Box(10.0, 20.0, 110.0, 150.0)) =
        Student(no = no, name = "Öğrenci $no", captured = true, updatedAt = 1_700_000_000_000L + no.toLong(), box = box,
            boxW = 1600, boxH = 1200, retake = retake, score = score)

    private fun snapshot() = listOf(
        SchoolClass("9-B", "9-B", listOf(student("1001", retake = false, score = 80), student("1002", retake = true, score = 40), Student("1003", "Boş")))
    )

    @Test
    fun yedekAl_incele_geriYukle_duzenlemeKorunur() = runBlocking {
        val src = Device()
        src.photo("9-B", "1001"); src.photo("9-B", "1002")
        val storage = DirStorage(File(Files.createTempDirectory("out").toFile(), "Yedek"))

        val written = src.service.write(storage, snapshot(), "Ydk")
        assertTrue(written.name.startsWith("Ydk_") && written.name.endsWith(".zip"))
        assertEquals(2, written.photos)
        assertEquals(2, written.captured)

        val info = src.service.inspect { FileInputStream(File(storage.dir, written.name)) }
        assertNotNull(info)
        assertEquals(1, info!!.classes); assertEquals(3, info.students); assertEquals(2, info.captured)
        assertEquals(1, info.retake)          // 1002 hâlâ tekrar çekilecek, 1001 düzeltilmiş
        assertEquals(2, info.boxes)
        assertEquals("1.1.test", info.appVersion)

        // Başka bir cihaza (boş veri klasörüne) geri yükle
        val dst = Device()
        val out = dst.service.restore { FileInputStream(File(storage.dir, written.name)) }
        assertEquals(snapshot(), out.classes)
        assertEquals(snapshot(), RosterStore(File(dst.filesDir, "roster.json")).load())
        val s1 = out.classes[0].students[0]
        assertFalse(s1.retake)                // düzenleme geri yüklemede kaybolmadı
        assertEquals(80, s1.score)
        assertEquals(Box(10.0, 20.0, 110.0, 150.0), s1.box)
        assertTrue(File(dst.filesDir, "photos/9-B/1001.jpg").exists())
        assertEquals(0, out.skippedPhotos)
    }

    @Test
    fun bozukYedek_mevcutVeriyeDokunmaz() = runBlocking {
        val dev = Device()
        dev.photo("9-B", "1001")
        assertNull(dev.store.save(snapshot()))
        val before = File(dev.filesDir, "roster.json").readText()
        val bad = File(Files.createTempDirectory("bad").toFile(), "bozuk.zip").apply { writeBytes(ByteArray(64) { it.toByte() }) }

        assertNull(dev.service.inspect { FileInputStream(bad) })
        try {
            dev.service.restore { FileInputStream(bad) }
            fail("Bozuk yedek reddedilmeliydi")
        } catch (_: Exception) {
        }
        assertEquals(before, File(dev.filesDir, "roster.json").readText())
        assertTrue(File(dev.filesDir, "photos/9-B/1001.jpg").exists())
        assertFalse(File(dev.filesDir, "photos.new").exists())          // geçici klasör temizlendi
    }

    @Test
    fun geriYukle_mevcutVerinin_yerineGecer() = runBlocking {
        val src = Device()
        src.photo("9-B", "1001"); src.photo("9-B", "1002")
        val storage = DirStorage(File(Files.createTempDirectory("out").toFile(), "Yedek"))
        val written = src.service.write(storage, snapshot(), "Ydk")

        val dst = Device()
        dst.photo("5-A", "7")
        assertNull(dst.store.save(listOf(SchoolClass("5-A", "5-A", listOf(student("7", retake = true))))))
        val out = dst.service.restore { FileInputStream(File(storage.dir, written.name)) }
        assertEquals(listOf("9-B"), out.classes.map { it.id })
        assertFalse(File(dst.filesDir, "photos/5-A/7.jpg").exists())    // eski fotoğraf klasörü yedeğin içeriğiyle değişti
        assertTrue(File(dst.filesDir, "photos/9-B/1002.jpg").exists())
    }

    @Test
    fun bozukFotografAtlanir_sayilir() = runBlocking {
        val src = Device()
        src.photo("9-B", "1001"); src.photo("9-B", "1002")
        val storage = DirStorage(File(Files.createTempDirectory("out").toFile(), "Yedek"))
        val written = src.service.write(storage, snapshot(), "Ydk")
        val dst = Device()
        val picky = BackupService(dst.filesDir, dst.store, Mutex(), "x") { f -> f.name != "1002.jpg" }
        val out = picky.restore { FileInputStream(File(storage.dir, written.name)) }
        assertEquals(1, out.skippedPhotos)
        assertFalse(File(dst.filesDir, "photos/9-B/1002.jpg").exists())
        assertTrue(File(dst.filesDir, "photos/9-B/1001.jpg").exists())
    }

    @Test
    fun guvenlikYedekleri_enYeniIkisiKalir_kullaniciYedegineDokunulmaz() {
        val dev = Device()
        val yedek = DirStorage(Files.createTempDirectory("y").toFile())
        val kok = DirStorage(Files.createTempDirectory("k").toFile())
        listOf("Ydk_100126_1000.zip", "Onc_111126_1000.zip", "Onc_121126_1000.zip", "Onc_131126_1000.zip").forEach { yedek.write(it) { o -> o.write(1) } }
        listOf("EOkulStudio_GeriYuklemeOncesi_2026-11-10_10-00-00.zip", "EOkulStudio_Yedek_2026-11-10_10-00-00.zip").forEach { kok.write(it) { o -> o.write(1) } }

        val deleted = dev.service.pruneSafetyBackups(listOf(yedek, kok), 2)

        assertEquals(2, deleted)
        assertEquals(setOf("Ydk_100126_1000.zip", "Onc_121126_1000.zip", "Onc_131126_1000.zip"), yedek.list().toSet())
        assertEquals(setOf("EOkulStudio_Yedek_2026-11-10_10-00-00.zip"), kok.list().toSet())
    }

    @Test
    fun listeDogrulama_yinelenenNumaraVeBosAdReddedilir() {
        BackupService.validateRoster(snapshot())
        val dup = listOf(SchoolClass("a", "A", listOf(Student("1", "x"), Student("1", "y"))))
        val noName = listOf(SchoolClass("a", "A", listOf(Student("1", " "))))
        val dupClass = listOf(SchoolClass("a", "A"), SchoolClass("a", "B"))
        for (bad in listOf(dup, noName, dupClass)) {
            try { BackupService.validateRoster(bad); fail("Reddedilmeliydi") } catch (_: java.io.IOException) { }
        }
    }
}
