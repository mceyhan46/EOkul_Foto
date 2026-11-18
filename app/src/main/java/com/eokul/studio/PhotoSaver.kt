package com.eokul.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.eokul.studio.core.RosterParser
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Bir öğrencinin fotoğrafını diske yazan Android bağdaştırıcısı (SAF + uygulama içi kopya).
 * Sıra: çıktıyı üret ve doğrula → kayıt klasörüne yaz ve doğrula → orijinali sakla (başarısızsa uyarı) →
 * eski adlı dosyayı sil → uygulama içi kopyayı atomik yaz (başarısızsa uyarı). Ana çıktı yazılamazsa istisna fırlatır.
 */
class PhotoSaver(private val ctx: Context) {

    class Saved(val origUri: String?, val stem: String, val warnings: List<String> = emptyList())

    fun save(
        root: Uri, cls: SchoolClass, st: Student, source: Uri, fromSaved: Boolean, px: IntArray,
        iw: Int, ih: Int, box: Box, set: Settings
    ): Saved {
        val stem = RosterParser.studentStem(st.no, st.name)
        val clsDir = RosterParser.sanitizeFileName(cls.name)
        val jpeg = ImageOps.render(ctx, px, iw, ih, box, set)
        checkSize(jpeg, "Çıktı doğrulanamadı")   // kaydetmeden önce gerçekten 133x171 JPEG mi?

        val mainUri = Output.writePath(ctx, root, listOf(clsDir), "$stem.jpg", "image/jpeg", jpeg)
        // Yazım sonrası doğrulama: sessiz silinme/kaybolma hatalarına karşı.
        if (!Output.exists(ctx, mainUri)) throw IOException("Fotoğraf yazıldı ancak klasörde bulunamadı: $stem.jpg")

        // Ana çıktı güvenle yazıldı; bundan sonraki adımlar başarısız olursa kayıt iptal edilmez, uyarı verilir.
        val warnings = ArrayList<String>()
        var origUri = st.origUri
        if (fromSaved || !set.keepOriginals) Ops.log("orig", "ok", false, "err", if (fromSaved) "skipped:fromSaved" else "skipped:keepOriginalsOff")
        if (!fromSaved && set.keepOriginals) try {
            var bytes = ctx.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: throw IOException("Orijinal okunamadı")
            var mime = ctx.contentResolver.getType(source) ?: "image/jpeg"
            var ext = SaveRules.originalExt(mime)
            // Orijinal kopya kompakt tutulur: uzun kenar en fazla 1600 px, JPEG %85 (~300-500 KB).
            if (SaveRules.needsCompaction(bytes.size)) {
                bytes = compactOriginal(px, iw, ih)
                mime = "image/jpeg"
                ext = "jpg"
            }
            val newUri = Output.writePath(ctx, root, listOf(clsDir, ORIGINALS_DIR), "$stem.$ext", mime, bytes)
            if (!Output.exists(ctx, newUri)) throw IOException("Orijinal yazıldı ancak klasörde bulunamadı")
            val old = st.origUri
            if (old != null && old != newUri.toString()) Output.deleteUri(ctx, Uri.parse(old))
            origUri = newUri.toString()
            Ops.log("orig", "ok", true, "bytes", bytes.size, "ext", ext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Ops.log("orig", "ok", false, "err", (e.toString()).take(200))
            warnings.add("orijinal fotoğraf saklanamadı (${e.message ?: e.javaClass.simpleName})")
        }

        val oldStem = st.savedStem
        if (oldStem != null && oldStem != stem) {
            Output.deletePath(ctx, root, listOf(clsDir), "$oldStem.jpg")
        }

        try {
            AtomicPhoto.write(Naming.privatePhoto(ctx, cls.id, st.no), jpeg) { f ->
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, o)
                if (o.outWidth != TARGET_W || o.outHeight != TARGET_H) throw IOException("Kaydedilen küçük fotoğrafın boyutu geçersiz")
            }
        } catch (e: Exception) {
            warnings.add("uygulama içi kopya güvenli biçimde yazılamadı; küçük resim ve ZIP için yeniden çekin (${e.message ?: "hata"})")
        }
        return Saved(origUri, stem, warnings)
    }

    private fun checkSize(jpeg: ByteArray, msg: String) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
        if (o.outWidth != TARGET_W || o.outHeight != TARGET_H) throw IOException("$msg (${o.outWidth}x${o.outHeight})")
    }

    private fun compactOriginal(px: IntArray, w: Int, h: Int): ByteArray {
        val k = SaveRules.originalScale(w, h)
        val ow = maxOf(1, Math.round(w * k).toInt())
        val oh = maxOf(1, Math.round(h * k).toInt())
        // Arşiv kopyası: yerel (native) ölçekleme + JPEG, Java Lanczos'tan çok daha hızlı
        val full = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val bmp = if (k < 1.0) Bitmap.createScaledBitmap(full, ow, oh, true) else full
        if (bmp !== full) full.recycle()
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        bmp.recycle()
        return bos.toByteArray()
    }
}
