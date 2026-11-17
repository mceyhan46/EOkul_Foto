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
