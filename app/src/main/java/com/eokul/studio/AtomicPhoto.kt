package com.eokul.studio

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Uygulama içi fotoğraf kopyasını güvenle yazar (Android'siz): geçici dosya → fsync → eskiyi .old'a al → taşı → doğrula.
 * Herhangi bir adım başarısız olursa eski dosya geri konur; hedef asla yarım kalmaz.
 * [validate] yazılan dosyayı doğrular (örn. boyut); geçersizse IOException fırlatmalıdır.
 */
object AtomicPhoto {

    fun write(target: File, bytes: ByteArray, validate: (File) -> Unit = {}) {
        target.parentFile?.mkdirs()
        val token = UUID.randomUUID().toString().replace("-", "")
        val tmp = File(target.parentFile, ".${target.name}.$token.tmp")
        val old = File(target.parentFile, ".${target.name}.$token.old")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            if (tmp.length() != bytes.size.toLong()) throw IOException("Geçici fotoğraf eksik yazıldı")
            if (target.exists() && !target.renameTo(old)) {
                throw IOException("Eski uygulama içi fotoğraf korunamadı; mevcut dosya değiştirilmedi")
            }
            try {
                try {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: Exception) {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                if (target.length() != bytes.size.toLong()) throw IOException("Kaydedilen fotoğraf doğrulanamadı")
                validate(target)
                old.delete()
            } catch (e: Exception) {
                target.delete()
                if (old.exists() && !old.renameTo(target)) {
                    throw IOException("Yeni fotoğraf kaydedilemedi; eski fotoğraf .old dosyasında korundu", e)
                }
                throw e
            }
        } finally {
            tmp.delete()
        }
    }
}
