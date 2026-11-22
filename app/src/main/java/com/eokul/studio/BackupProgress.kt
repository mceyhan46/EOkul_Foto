package com.eokul.studio

import java.io.FilterOutputStream
import java.io.OutputStream

/** Yedekleme sırasında kullanıcıya gösterilen durum: aşama ve (biliniyorsa) 0..1 ilerleme. */
class BackupProgress(val stage: Stage, val fraction: Float? = null) {
    enum class Stage { PREPARE, WRITE, VERIFY }

    /** Kısa durum yazısı. */
    val label: String
        get() = when (stage) {
            Stage.PREPARE -> "Dosyalar hazırlanıyor…"
            Stage.WRITE -> if (fraction == null) "Yedek yazılıyor…" else "Yedek yazılıyor · %${percent(fraction)}"
            Stage.VERIFY -> "Yedek doğrulanıyor…"
        }

    companion object {
        fun percent(f: Float): Int = (f * 100f).toInt().coerceIn(0, 100)

        /** Yazılan bayt / beklenen bayt; yazma bitmeden %100 gösterilmez, toplam bilinmiyorsa null. */
        fun fraction(written: Long, total: Long): Float? =
            if (total <= 0L) null else (written.toDouble() / total).coerceIn(0.0, 0.99).toFloat()
    }
}

/** Geçen baytları sayıp [onBytes]'a bildiren çıktı akışı (ilerleme göstergesi için). */
class CountingOutputStream(out: OutputStream, private val onBytes: (Long) -> Unit) : FilterOutputStream(out) {
    private var total = 0L
    override fun write(b: Int) { out.write(b); total += 1; onBytes(total) }
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); total += len; onBytes(total) }
}
