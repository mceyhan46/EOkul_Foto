package com.eokul.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.eokul.studio.core.PdfGrid
import java.io.OutputStream

/**
 * Sınıf fotoğraf listesi PDF'i (A4 dikey): üstte sınıf başlığı ("5. Sınıf / A Şubesi"), altında numara sırasına göre
 * fotoğraflar (her satırda 7), her fotoğrafın altında numara ve ad soyad. Her sınıf yeni sayfada başlar; sığmayan
 * öğrenciler aynı başlıkla ("devam") sonraki sayfaya geçer. Sayfa düzeni [PdfGrid]'te hesaplanır ve birim testlidir.
 */
object ClassPdf {
    private val INK = Color.rgb(0x1B, 0x25, 0x38)
    private val MUTED = Color.rgb(0x56, 0x62, 0x77)

    /** [classes]'i [out]'a yazar; dönen değer sayfa sayısı. Fotoğrafı olmayan öğrenci için boş çerçeve çizilir. */
    fun write(ctx: Context, classes: List<SchoolClass>, out: OutputStream, ratio: Double): Int {
        val spec = PdfGrid.Spec().also { it.ratio = ratio }
        val doc = PdfDocument()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 9f; textAlign = Paint.Align.CENTER }
        val noPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 7.5f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 7f; textAlign = Paint.Align.CENTER }
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xC9, 0xD1, 0xDF); style = Paint.Style.STROKE; strokeWidth = 0.6f }
        val missing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xF1, 0xF4, 0xF9); style = Paint.Style.FILL }
        val missingText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 6.5f; textAlign = Paint.Align.CENTER }
        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val rule = Paint().apply { color = Color.rgb(0xC9, 0xD1, 0xDF); strokeWidth = 0.8f }

        var pageNo = 0
        try {
            for (cls in classes) {
                val sorted = cls.students.toMutableList().also { PdfGrid.sortByNumber(it) { s -> s.no } }
                val heading = PdfGrid.classTitle(cls.name)
                for (pg in PdfGrid.paginate(sorted.size, spec)) {
                    pageNo++
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(spec.pageW.toInt(), spec.pageH.toInt(), pageNo).create())
                    val c: Canvas = page.canvas
                    c.drawText(heading + if (pg.continued) " (devam)" else "", (spec.pageW / 2).toFloat(), (spec.margin + 18).toFloat(), title)
                    c.drawText("${sorted.size} öğrenci", (spec.pageW / 2).toFloat(), (spec.margin + 31).toFloat(), sub)
                    c.drawLine(spec.margin.toFloat(), (spec.margin + spec.titleH - 8).toFloat(), (spec.pageW - spec.margin).toFloat(), (spec.margin + spec.titleH - 8).toFloat(), rule)
                    for (cell in pg.cells) {
                        val st = sorted[cell.index]
                        val r = RectF(cell.x.toFloat(), cell.y.toFloat(), (cell.x + cell.w).toFloat(), (cell.y + cell.h).toFloat())
                        val bmp: Bitmap? = if (st.captured) try {
                            BitmapFactory.decodeFile(Naming.privatePhoto(ctx, cls.id, st.no).path)
                        } catch (_: Throwable) { null } else null
                        if (bmp != null) {
                            c.drawBitmap(bmp, null, r, bmpPaint)
                            bmp.recycle()
                        } else {
                            c.drawRect(r, missing)
                            c.drawText("Fotoğraf yok", r.centerX(), r.centerY(), missingText)
                        }
                        c.drawRect(r, frame)
                        val cx = r.centerX()
                        var y = (cell.captionY + 8).toFloat()
                        c.drawText(st.no, cx, y, noPaint)
                        for (line in PdfGrid.wrap(st.name, cell.w - 2.0, 2) { namePaint.measureText(it).toDouble() }) {
                            y += 8.5f
                            c.drawText(line, cx, y, namePaint)
                        }
                    }
                    doc.finishPage(page)
                }
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
        return pageNo
    }
}
