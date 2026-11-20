package com.eokul.studio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.eokul.studio.core.BackupNames
import java.io.IOException

/**
 * Kayıt klasörü (SAF) kurulumu ve denetimi. Kullanıcı bir klasör seçer ("Belgeler" önerilir); uygulama onun içinde
 * EOkul_Studio klasörünü kendisi açar ve her şeyi orada tutar. Engelleyicidir: IO iş parçacığında çağrılmalıdır.
 */
class RootFolder(private val ctx: Context) {

    /** [encoded] ayarda saklanacak değerdir; [reused] seçilen yerde daha önce kullanılmış dosyalar bulunduysa true. */
    class Setup(val encoded: String, val reused: Boolean)

    /** Seçilen klasörü kurar; sorun varsa Türkçe açıklamalı IOException fırlatır. */
    fun setup(picked: Uri): Setup {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                picked, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            throw IOException("Klasör izni alınamadı; başka bir klasör seçin", e)
        }
        val base = DocumentFile.fromTreeUri(ctx, picked)
        if (base == null || !base.isDirectory) throw IOException("Klasöre erişilemiyor; başka bir klasör seçin")
        if (!base.canWrite()) throw IOException("Bu klasöre yazılamıyor; başka bir klasör seçin")

        val children = base.listFiles()
        val existingSub = children.firstOrNull { it.isDirectory && it.name.equals(RootRules.DEFAULT_SUB, ignoreCase = true) }
        // Eski kurulum: kullanıcı doğrudan eski kayıt klasörünü seçtiyse (içinde Yedek/ veya Orijinaller/ var) olduğu gibi kullanılır.
        val legacy = existingSub == null && looksLikeStudio(children)
        val sub = if (legacy) null else RootRules.chooseSub(base.name)
        val dir = when {
            sub == null -> base
            existingSub != null -> existingSub
            else -> base.createDirectory(sub) ?: throw IOException("$sub klasörü oluşturulamadı")
        }
        val reused = dir.listFiles().isNotEmpty()   // seçilen/açılan klasörde daha önce dosya var (yeniden kurulum, eski klasör)
        probe(dir)
        return Setup(RootRules.encode(picked.toString(), sub), reused)
    }

    private fun looksLikeStudio(children: Array<DocumentFile>): Boolean {
        val dirs = children.filter { it.isDirectory }
        val map = HashMap<String, List<String>>()
        for (d in dirs.take(60)) map[d.name ?: continue] = d.listFiles().mapNotNull { if (it.isDirectory) it.name else null }
        return RootRules.looksLikeStudioRoot(map, BackupNames.FOLDER, ORIGINALS_DIR)
    }

    /** Gerçekten dosya yazılabiliyor mu? (Bazı sağlayıcılar canWrite=true deyip yazdırmaz.) */
    private fun probe(dir: DocumentFile) {
        val f = dir.createFile("application/octet-stream", ".eokul_probe") ?: throw IOException("Klasörde dosya oluşturulamıyor; başka bir klasör seçin")
        try {
            ctx.contentResolver.openOutputStream(f.uri, "w")?.use { it.write(1) } ?: throw IOException("Klasöre yazılamıyor; başka bir klasör seçin")
        } finally {
            try { f.delete() } catch (_: Exception) { }
        }
    }

    /** Kayıtlı klasörün güncel durumu. */
    fun status(encoded: String?): RootRules.State {
        if (encoded == null) return RootRules.State.NOT_SET
        val ref = RootRules.decode(encoded)
        val base = Uri.parse(ref.base)
        val grant = ctx.contentResolver.persistedUriPermissions.any { it.uri == base && it.isWritePermission }
        var exists = false
        var writable = false
        if (grant) try {
            val b = DocumentFile.fromTreeUri(ctx, base)
            var d: DocumentFile? = if (b != null && b.exists() && b.isDirectory) b else null
            if (d != null && ref.sub != null) d = d.findFile(ref.sub)?.takeIf { it.isDirectory }
            exists = d != null
            writable = d?.canWrite() == true
        } catch (_: Exception) { }
        return RootRules.status(true, grant, exists, writable)
    }

    /** Alt klasör silinmiş ama ana klasör duruyorsa EOkul_Studio'yu yeniden açar; başarılıysa true. */
    fun repair(encoded: String): Boolean = try {
        val ref = RootRules.decode(encoded)
        val sub = ref.sub
        val b = DocumentFile.fromTreeUri(ctx, Uri.parse(ref.base))
        if (sub == null || b == null || !b.exists() || !b.canWrite()) false
        else b.findFile(sub)?.takeIf { it.isDirectory } != null || b.createDirectory(sub) != null
    } catch (_: Exception) {
        false
    }

    /** Ekranda gösterilecek yol, örn. "Dahili depolama/Documents/EOkul_Studio". */
    fun label(encoded: String?): String? {
        if (encoded == null) return null
        val ref = RootRules.decode(encoded)
        val id = try { DocumentsContract.getTreeDocumentId(Uri.parse(ref.base)) } catch (_: Exception) { null }
        return RootRules.label(id, ref.sub)
    }

    /** Eski ağaç iznini bırakır (klasör değiştirilirken). */
    fun release(encoded: String?) {
        if (encoded == null) return
        try {
            ctx.contentResolver.releasePersistableUriPermission(
                Uri.parse(RootRules.decode(encoded).base),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) { }
    }

    companion object {
        /** Seçici "Belgeler" klasöründe açılsın. */
        fun documentsHint(): Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
    }
}
