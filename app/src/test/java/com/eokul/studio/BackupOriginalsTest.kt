package com.eokul.studio

import com.eokul.studio.core.BackupZip
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Yedekte orijinal fotoğraflar: yazma, inceleme, geri yükleme ve eşleştirme. */
class BackupOriginalsTest {
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

    private class Device(val filesDir: File = Files.createTempDirectory("fd").toFile()) {
        val store = RosterStore(File(filesDir, "roster.json"))
        val service = BackupService(filesDir, store, Mutex(), "1.1.test") { true }
        fun photo(classId: String, no: String) {
            val f = File(filesDir, "photos/$classId/$no.jpg")
            f.parentFile!!.mkdirs()
            f.writeBytes(byteArrayOf(1, 2, 3, no.hashCode().toByte()))
        }
    }

    private val a = Student("1001", "Ali Veli", captured = true, updatedAt = 1L)
    private val b = Student("1002", "Ayşe Kaya", captured = true, updatedAt = 2L, savedStem = "1002-Eski Ad")
    private val c = Student("1003", "Boş")
    private val classes = listOf(SchoolClass("9-B", "9-B", listOf(a, b, c)))

    private fun origs(vararg pairs: Pair<Student, ByteArray>) = pairs.map { (st, bytes) ->
        OriginalFile(OriginalsRules.item(classes[0], st, OriginalsRules.fileName(st, "x.JPG"))) { ByteArrayInputStream(bytes) }
    }

    @Test fun orijinallerYedegeGirer_incelenir_geriYuklenirVeEslesir() = runBlocking {
        val src = Device(); src.photo("9-B", "1001"); src.photo("9-B", "1002")
        val storage = DirStorage(File(Files.createTempDirectory("o").toFile(), "Yedek"))
        val bytesA = ByteArray(5000) { it.toByte() }
        val bytesB = ByteArray(300) { (it * 3).toByte() }
        val w = src.service.write(storage, classes, "Ydk", originals = origs(a to bytesA, b to bytesB))
        assertEquals(2, w.originals)
        assertEquals(2, w.photos)

        val info = src.service.inspect { storage.list().first().let { n -> FileInputStream(File(storage.dir, n)) } }!!
        assertEquals(2, info.originals)

        val dst = Device()
        val r = dst.service.restore { FileInputStream(File(storage.dir, storage.list().first())) }
        assertEquals(2, r.originals.size)
        val byNo = r.originals.associateBy { it.item.no }
        assertEquals("1001-Ali Veli.jpg", byNo["1001"]!!.item.file)             // uzantı küçük harfe çevrildi
        assertEquals("1002-Eski Ad.jpg", byNo["1002"]!!.item.file)               // kayıtlı dosya adı korunur
        assertEquals("9-B", byNo["1001"]!!.item.classDir)
        assertArrayEquals(bytesA, byNo["1001"]!!.file.readBytes())
        assertArrayEquals(bytesB, byNo["1002"]!!.file.readBytes())
    }

    @Test fun orijinalsizEskiYedek_gecerliVeBos() = runBlocking {
        val src = Device(); src.photo("9-B", "1001")
        val storage = DirStorage(File(Files.createTempDirectory("o").toFile(), "Yedek"))
        src.service.write(storage, classes, "Ydk")
        val dst = Device()
        val r = dst.service.restore { FileInputStream(File(storage.dir, storage.list().first())) }
        assertTrue(r.originals.isEmpty())
        assertEquals(0, src.service.inspect { FileInputStream(File(storage.dir, storage.list().first())) }!!.originals)
    }

    @Test fun okunamayanOrijinal_yedegiBasarisizYapar_dosyaKalmaz() = runBlocking {
        val src = Device(); src.photo("9-B", "1001")
        val storage = DirStorage(File(Files.createTempDirectory("o").toFile(), "Yedek"))
        val bad = OriginalFile(OriginalsRules.item(classes[0], a, "1001-Ali Veli.jpg")) { null }
        try { src.service.write(storage, classes, "Ydk", originals = listOf(bad)); fail() } catch (_: IOException) { }
        assertTrue(storage.list().isEmpty())
    }

    @Test fun manifestOrijinalSayisiTutmazsa_geriYuklemeReddedilir() = runBlocking {
        val dev = Device(); dev.photo("9-B", "1001")
        val rosterBytes = dev.store.encode(classes).toByteArray()
        val manifest = JSONObject().put("app", BackupService.APP_ID).put("format", 1).put("classes", 1)
            .put("students", 3).put("photos", 1).put("originals", 5).toString().toByteArray()
        val extra = object : BackupZip.Extra {
            override fun name() = "originals/9-B/1001-Ali Veli.jpg"
            override fun open(): InputStream = ByteArrayInputStream(byteArrayOf(9))
        }
        val out = ByteArrayOutputStream()
        BackupZip.write(out, manifest, rosterBytes, dev.filesDir, listOf(File(dev.filesDir, "photos/9-B/1001.jpg")), listOf(extra))
        val dst = Device()
        try { dst.service.restore { ByteArrayInputStream(out.toByteArray()) }; fail() } catch (_: IOException) { }
        assertFalse(File(dst.filesDir, BackupService.ORIGINALS_STAGE).exists())
    }

    @Test fun eslesmeyenOrijinalYoksayilir() {
        assertNull(OriginalsRules.match(classes, "originals/9-B/9999-Yok.jpg"))
        assertNull(OriginalsRules.match(classes, "originals/Başka/1001-Ali Veli.jpg"))
        assertNull(OriginalsRules.match(classes, "originals/9-B/../1001-Ali Veli.jpg"))
        assertNull(OriginalsRules.match(classes, "originals/1001-Ali Veli.jpg"))
        assertNull(OriginalsRules.match(classes, "originals/9-B/1003-Boş.jpg"))          // çekilmemiş öğrenci
        assertNotNull(OriginalsRules.match(classes, "originals/9-B/1001-Ali Veli.png"))
    }

    @Test fun dosyaAdiMimeVeAdresIsleme() {
        assertEquals("1001-Ali Veli.jpg", OriginalsRules.fileName(a, null))
        assertEquals("1001-Ali Veli.heic", OriginalsRules.fileName(a, "IMG.HEIC"))
        assertEquals("1001-Ali Veli.jpg", OriginalsRules.fileName(a, "noext"))
        assertEquals("image/png", OriginalsRules.mime("a.png"))
        assertEquals("image/jpeg", OriginalsRules.mime("a.jpg"))
        val next = OriginalsRules.applyUris(classes, mapOf(("9-B" to "1001") to "content://x/1"))
        assertEquals("content://x/1", next[0].students[0].origUri)
        assertNull(next[0].students[1].origUri)
        assertEquals(classes, OriginalsRules.applyUris(classes, emptyMap()))
    }
}
