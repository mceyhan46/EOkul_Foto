package com.eokul.studio

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupProgressTest {
    @Test fun oran() {
        assertNull(BackupProgress.fraction(10, 0))
        assertEquals(0.5f, BackupProgress.fraction(50, 100)!!, 0.001f)
        assertEquals(0.99f, BackupProgress.fraction(500, 100)!!, 0.001f)
        assertEquals(50, BackupProgress.percent(0.5f))
    }

    @Test fun yazilar() {
        assertTrue(BackupProgress(BackupProgress.Stage.PREPARE).label.contains("hazırlanıyor"))
        assertEquals("Yedek yazılıyor · %42", BackupProgress(BackupProgress.Stage.WRITE, 0.42f).label)
        assertEquals("Yedek yazılıyor…", BackupProgress(BackupProgress.Stage.WRITE, null).label)
        assertTrue(BackupProgress(BackupProgress.Stage.VERIFY).label.contains("doğrulanıyor"))
    }

    @Test fun sayacBaytlariGecirir() {
        val sink = ByteArrayOutputStream()
        var last = 0L
        CountingOutputStream(sink) { last = it }.use { it.write(byteArrayOf(1, 2, 3)); it.write(9); it.write(ByteArray(10), 2, 5) }
        assertEquals(9L, last)
        assertEquals(9, sink.size())
    }
}
