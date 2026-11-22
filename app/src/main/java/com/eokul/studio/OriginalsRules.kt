package com.eokul.studio

import com.eokul.studio.core.RestoreGuard
import com.eokul.studio.core.RosterParser

/**
 * Yedekteki orijinal fotoğraf girdilerinin adlandırması ve öğrenciyle eşleştirilmesi (Android'siz, test edilebilir).
 * Girdi adı: `originals/<sınıf klasörü>/<öğrenci dosya adı>.<uzantı>` — kayıt klasöründeki `Sınıf/Orijinaller/` düzeniyle aynıdır.
 */
object OriginalsRules {

    /** Yedekteki bir orijinal: [entry] ZIP girdi adı, [file] kayıt klasöründeki dosya adı. */
    class Item(val classId: String, val no: String, val classDir: String, val file: String) {
        val entry: String get() = RestoreGuard.ORIGINALS_PREFIX + classDir + "/" + file
    }

    fun classDir(cls: SchoolClass): String = RosterParser.sanitizeFileName(cls.name)

    /** Öğrencinin kayıtlı dosya kökü ("12345-Ad Soyad"): kaydedilmiş ad varsa o, yoksa güncel ad. */
    fun stem(st: Student): String = st.savedStem ?: RosterParser.studentStem(st.no, st.name)

    /** [fileName] (uzantılı) için bir yedek öğesi üretir. */
    fun item(cls: SchoolClass, st: Student, fileName: String) = Item(cls.id, st.no, classDir(cls), fileName)

    /** Girdi adını sınıf ve öğrenciyle eşleştirir; eşleşme yoksa veya ad güvensizse null. */
    fun match(classes: List<SchoolClass>, entryName: String): Item? {
        val rel = try { RestoreGuard.originalRelativePath(entryName) } catch (_: Exception) { return null }
        val dir = rel.substringBefore('/')
        val file = rel.substringAfter('/')
        val stem = file.substringBeforeLast('.', file)
        for (c in classes) {
            if (classDir(c) != dir) continue
            val st = c.students.firstOrNull { it.captured && stem(it) == stem } ?: continue
            return Item(c.id, st.no, dir, file)
        }
        return null
    }

    /** Orijinal dosyanın yedekteki adı: öğrencinin dosya kökü + kaynak dosyanın uzantısı ("12345-Ad Soyad.jpg"). */
    fun fileName(st: Student, sourceDisplayName: String?): String {
        val ext = (sourceDisplayName ?: "").substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.length <= 5 } ?: "jpg"
        return stem(st) + "." + ext
    }

    fun mime(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        else -> "image/jpeg"
    }

    /** Kayıt klasörüne yazılan orijinallerin yeni adreslerini öğrencilere işler; diğer alanlara dokunmaz. */
    fun applyUris(classes: List<SchoolClass>, uris: Map<Pair<String, String>, String>): List<SchoolClass> =
        if (uris.isEmpty()) classes else classes.map { c ->
            c.copy(students = c.students.map { st -> uris[c.id to st.no]?.let { st.copy(origUri = it) } ?: st })
        }
}
