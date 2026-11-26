package com.eokul.studio

import android.content.Context
import android.net.Uri
import com.eokul.studio.core.RosterParser
import java.io.File
import java.io.IOException

/**
 * Fotoğrafı bir öğrenciden diğerine taşırken dosyaları taşıyan adaptör (engelleyici; IO iş parçacığında çağrılır).
 *
 * Güvenlik: önce her şey belleğe okunur, sonra hedef dosyalar (uygulama içi kopya, çıktı, orijinal) güvenli yazımla
 * ([Output.writeStream]) yazılır; herhangi biri başarısız olursa yazılanlar eski içeriğine döndürülür ve hiçbir şey değişmez.
 * Eski konumdaki dosyalar ancak liste diske kaydedildikten sonra [cleanup] ile silinir.
 */
class PhotoMoveService(private val ctx: Context) {

    /** Bir öğrencinin fotoğraf dosyaları (belleğe alınmış). */
    private class Blobs(val cls: SchoolClass, val st: Student, val priv: ByteArray, val main: ByteArray, val orig: ByteArray?, val origExt: String) {
        val dir: String get() = RosterParser.sanitizeFileName(cls.name)
    }

    /** [execute] sonucu: yeni orijinal adresleri ve [cleanup] için eski dosya bilgisi. */
    class Result internal constructor(
        val dstOrig: String?, val srcOrig: String?, val warnings: List<String>,
        internal val stale: List<Stale>
    )

    /** Silinecek eski dosyalar: uygulama içi kopya, çıktı adı, e-Okul kopyası ve (adresi değişen) eski orijinal. */
    internal class Stale(val classId: String, val no: String, val dir: String, val stem: String?, val deletePrivate: Boolean, val oldOrig: String?)

    private fun readUri(uri: Uri): ByteArray? = try {
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    private fun load(root: Uri, cls: SchoolClass, st: Student): Blobs {
        val dir = RosterParser.sanitizeFileName(cls.name)
        val stem = OriginalsRules.stem(st)
        val priv = Naming.privatePhoto(ctx, cls.id, st.no).takeIf { it.exists() }?.readBytes()
        val mainUri = Output.findFile(ctx, root, listOf(dir), stem, "${st.no}-")
        val main = mainUri?.let { readUri(it) } ?: priv
        ?: throw IOException("${st.no} numaralı öğrencinin fotoğraf dosyası bulunamadı")
        val origUri = st.origUri?.let { Uri.parse(it) }?.takeIf { Output.exists(ctx, it) }
            ?: Output.findFile(ctx, root, listOf(dir, ORIGINALS_DIR), stem, "${st.no}-")
        val orig = origUri?.let { readUri(it) }
        val ext = origUri?.let { Output.displayName(ctx, it) }?.substringAfterLast('.', "jpg")?.lowercase()?.takeIf { it.length in 1..5 } ?: "jpg"
        return Blobs(cls, st, priv ?: main, main, orig, ext)
    }

    private fun writePrivate(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
    }

    /** Hedef öğrenciye [from]'un dosyalarını yazar; geri alma işlemlerini [undo]'ya ekler. Yeni orijinal adresini döndürür. */
    private fun place(root: Uri, from: Blobs, target: Student, targetCls: SchoolClass, old: Blobs?, undo: MutableList<() -> Unit>): String? {
        val dir = RosterParser.sanitizeFileName(targetCls.name)
        val stem = RosterParser.studentStem(target.no, target.name)
        val priv = Naming.privatePhoto(ctx, targetCls.id, target.no)
        val oldPrivBytes = priv.takeIf { it.exists() }?.readBytes()
        undo.add { if (oldPrivBytes != null) writePrivate(priv, oldPrivBytes) else priv.delete() }
        writePrivate(priv, from.priv)

        val oldMain = old?.main
        val mainUri = Output.writePath(ctx, root, listOf(dir), "$stem.jpg", "image/jpeg", from.main)
        undo.add {
            if (oldMain != null) Output.writePath(ctx, root, listOf(dir), "$stem.jpg", "image/jpeg", oldMain) else Output.deleteUri(ctx, mainUri)
        }
        val ob = from.orig ?: return null
        val name = "$stem.${from.origExt}"
        val u = Output.writePath(ctx, root, listOf(dir, ORIGINALS_DIR), name, OriginalsRules.mime(name), ob)
        undo.add { Output.deleteUri(ctx, u) }
        return u.toString()
    }

    /**
     * Taşımayı yapar. Hata olursa yazılanlar geri alınır ve [IOException] fırlatılır (liste/dosyalar eski hâlinde kalır).
     * [dst] doluysa ([PhotoMoveRules.Mode.SWAP] veya REPLACE) eski içeriği geri alma için okunur.
     */
    fun execute(
        root: Uri, srcCls: SchoolClass, src: Student, dstCls: SchoolClass, dst: Student, mode: PhotoMoveRules.Mode
    ): Result {
        val a = load(root, srcCls, src)
        val b = if (dst.captured) runCatching { load(root, dstCls, dst) }.getOrNull() else null
        if (mode == PhotoMoveRules.Mode.SWAP && b == null) throw IOException("Hedef öğrencinin fotoğraf dosyası okunamadı; yer değiştirilemez")
        val undo = ArrayList<() -> Unit>()
        try {
            val dstOrig = place(root, a, dst, dstCls, b, undo)
            val srcOrig = if (mode == PhotoMoveRules.Mode.SWAP) place(root, b!!, src, srcCls, a, undo) else null
            val stale = ArrayList<Stale>()
            val warnings = ArrayList<String>()
            if (a.orig == null) warnings.add("kaynak fotoğrafın orijinali bulunamadı; kırpma düzenleme sınırlı olabilir")
            // Eski dosyalar: taşınan kaynak (MOVE/REPLACE) tamamen silinir; yer değiştirmede adresi değişen eski orijinaller silinir.
            if (mode != PhotoMoveRules.Mode.SWAP) {
                stale.add(Stale(srcCls.id, src.no, a.dir, OriginalsRules.stem(src), true, src.origUri))
                // Hedefin eski orijinali (adresi değiştiyse) ve çıktı dışı kopyaları
                if (dst.captured) stale.add(Stale(dstCls.id, dst.no, "", null, false, dst.origUri.takeIf { it != dstOrig }))
            } else {
                stale.add(Stale(srcCls.id, src.no, a.dir, null, false, src.origUri.takeIf { it != srcOrig }))
                stale.add(Stale(dstCls.id, dst.no, b!!.dir, null, false, dst.origUri.takeIf { it != dstOrig }))
            }
            return Result(dstOrig, srcOrig, warnings, stale)
        } catch (e: Throwable) {
            for (u in undo.asReversed()) try { u() } catch (_: Throwable) { }
            throw if (e is IOException) e else IOException(e.message ?: e.javaClass.simpleName, e)
        }
    }

    /** Liste kaydedildikten sonra çağrılır: eski dosyaları siler, hedeflerin bayat e-Okul kopyalarını temizler. */
    fun cleanup(root: Uri, r: Result, srcCls: SchoolClass, dstCls: SchoolClass, src: Student, dst: Student) {
        for (s in r.stale) {
            if (s.deletePrivate) Naming.privatePhoto(ctx, s.classId, s.no).delete()
            if (s.stem != null) Output.deletePath(ctx, root, listOf(s.dir), "${s.stem}.jpg")
            s.oldOrig?.let { Output.deleteUri(ctx, Uri.parse(it)) }
        }
        // e-Okul ZIP klasöründeki kopyalar fotoğrafla birlikte bayatlar; dışa aktarma yeniden üretir.
        for ((c, st) in listOf(srcCls to src, dstCls to dst)) {
            Output.deletePath(ctx, root, listOf(EOKUL_DIR, RosterParser.sanitizeFileName(c.name)), "${st.no}.jpg")
        }
    }
}
