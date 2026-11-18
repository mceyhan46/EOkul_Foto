package com.eokul.studio

/**
 * Bir fotoğraf kaydının öğrenci kaydını nasıl değiştirdiğini belirleyen saf (Android'siz, test edilebilir) kurallar.
 *
 * Akış (StudioViewModel.saveCapture):
 *  1. [optimistic]: öğrenci hemen "çekildi" sayılır (arayüz), eski hâli "bekleyen" listesine alınır; diske yazılmaz.
 *  2. Dosyalar arka planda yazılır. Bu sürede diske giden her liste [stableSnapshot] ile alınır: bekleyen öğrenci
 *     diskte hâlâ eski hâliyle durur, böylece yazılmamış bir fotoğraf "çekildi" diye yedeğe/listeye girmez.
 *  3. Başarılıysa [saved]; başarısızsa [rollback] uygulanır.
 */
object CaptureRules {
    fun key(classId: String, no: String): String = "$classId|$no"

    /** Kayıt başlarken arayüzde görünen hâl. Yeni puan yoksa (-1) önceki puan korunur; "tekrar" işareti kayıttaki uyarıdan gelir. */
    fun optimistic(
        student: Student, box: Box, iw: Int, ih: Int, faceCount: Int, retake: Boolean, newScore: Int, now: Long
    ): Student = student.copy(
        captured = true, updatedAt = now, box = box, boxW = iw, boxH = ih, faceCount = faceCount,
        retake = retake,
        score = if (newScore >= 0) newScore else student.score
    )

    /** Dosyalar yazıldıktan sonra: yalnızca dosya bilgileri ve zaman damgası eklenir; kutu/işaret/puan değişmez. */
    fun saved(student: Student, origUri: String?, stem: String?, now: Long): Student =
        student.copy(updatedAt = now, origUri = origUri, savedStem = stem)

    /** Yazma başarısız olursa yalnızca çekimle değişen alanlar eski hâline döner; bu arada yapılan ad/numara düzenlemesi korunur. */
    fun rollback(current: Student, before: Student): Student = current.copy(
        captured = before.captured, updatedAt = before.updatedAt, box = before.box, boxW = before.boxW,
        boxH = before.boxH, faceCount = before.faceCount, retake = before.retake, score = before.score,
        origUri = before.origUri, savedStem = before.savedStem
    )

    /** Diske/yedeğe giden liste: dosyası henüz doğrulanmamış (bekleyen) öğrenciler eski hâlleriyle temsil edilir. */
    fun stableSnapshot(classes: List<SchoolClass>, pending: Map<String, Student>): List<SchoolClass> =
        if (pending.isEmpty()) classes
        else classes.map { cls ->
            cls.copy(students = cls.students.map { st -> pending[key(cls.id, st.no)] ?: st })
        }
}

/** Çekim sonrası saf kurallar: orijinal dosya uzantısı/boyut kararı ve sınıf bitiş özeti. */
object SaveRules {
    const val ORIG_MAX_SIDE = 1600
    const val ORIG_MAX_BYTES = 900_000

    fun originalExt(mime: String): String = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        else -> "jpg"
    }

    /** Orijinal çok büyükse JPEG olarak küçültülüp saklanır. */
    fun needsCompaction(bytes: Int): Boolean = bytes > ORIG_MAX_BYTES

    /** Uzun kenarı [ORIG_MAX_SIDE]'a indirecek ölçek (1.0 = küçültme yok). */
    fun originalScale(w: Int, h: Int): Double = minOf(1.0, ORIG_MAX_SIDE.toDouble() / maxOf(w, h))

    /** Sınıf bitince kısa özet: kaç fotoğraf, kalite ortalaması, düşük puanlılar. */
    fun classSummary(cls: SchoolClass?): String {
        if (cls == null) return "🎉 Sınıftaki tüm fotoğraflar çekildi"
        val total = cls.students.size
        val scored = cls.students.filter { it.captured && it.score >= 0 }
        val low = cls.students.count { it.captured && it.score in 0 until 70 }
        val sb = StringBuilder("🎉 ${cls.name}: $total/$total fotoğraf çekildi")
        if (scored.isNotEmpty()) sb.append("\nKalite ortalaması ${scored.map { it.score }.average().toInt()}")
        if (low > 0) sb.append("\n⚠ $low düşük puanlı fotoğraf var; sınıf ekranında \"⚠ Düşük\" çipinden bakıp yeniden çekebilirsiniz.")
        else if (scored.isNotEmpty()) sb.append("\nDüşük puanlı fotoğraf yok.")
        return sb.toString()
    }
}

/** "Kırpmayı düzenle"/inceleme açılırken başlangıç kırpma kutusunun seçimi (saf kural). */
object InitialBox {
    class Choice(val box: Box, val auto: Boolean)

    /**
     * Doğrudan düzenlemede yüz bulunduysa ([directEdit] && [hasFace]) kutu güncel varsayılan ayarlardan ([defaultBox]) hesaplanır;
     * aksi hâlde kayıtlı kutu, çekim anındaki boyuttan ([savedW]x[savedH]) açılan görüntüye ölçeklenir. Kayıtlı kutu yoksa varsayılan.
     */
    fun choose(
        directEdit: Boolean, hasFace: Boolean, saved: Box?, savedW: Int, savedH: Int,
        iw: Int, ih: Int, defaultBox: () -> Box
    ): Choice {
        val auto = directEdit && hasFace
        val box = if (saved != null && !auto) {
            Box.of(com.eokul.studio.core.Crop.scaleBox(saved.toArray(), savedW, savedH, iw, ih)).fitInside(iw, ih)
        } else defaultBox()
        return Choice(box, auto)
    }
}
