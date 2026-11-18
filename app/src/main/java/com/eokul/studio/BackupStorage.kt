package com.eokul.studio

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/** Yazılmış bir dosyanın geri okunabilmesi (yazım sonrası doğrulama için). */
fun interface StoredFile {
    fun open(): InputStream?
}

/**
 * Yedek dosyalarının yazıldığı yer. Üretimde kayıt klasörü (SAF); testlerde basit bir klasör.
 * Servis katmanı Android'e (Uri, DocumentFile) bağlı kalmasın diye yalnızca ad ve akışla çalışır.
 */
interface BackupStorage {
    /** [name] adlı dosyayı güvenli biçimde yazar; hata olursa istisna fırlatır. */
    fun write(name: String, writer: (OutputStream) -> Unit): StoredFile
    fun delete(name: String)
    /** Klasördeki dosya adları; klasör yoksa boş liste. */
    fun list(): List<String>
}

/** Kayıt klasörünün (SAF) altındaki [segments] yoluna yazan üretim uygulaması. */
class SafBackupStorage(
    private val ctx: Context,
    private val root: Uri,
    private val segments: List<String>
) : BackupStorage {
    override fun write(name: String, writer: (OutputStream) -> Unit): StoredFile {
        val uri = Output.writeStream(ctx, root, segments, name, "application/zip", writer)
        return StoredFile { ctx.contentResolver.openInputStream(uri) }
    }

    override fun delete(name: String) = Output.deletePath(ctx, root, segments, name)

    override fun list(): List<String> = Output.listNames(ctx, root, segments) ?: emptyList()
}
