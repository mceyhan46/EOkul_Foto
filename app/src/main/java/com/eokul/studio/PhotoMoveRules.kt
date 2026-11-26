package com.eokul.studio

import com.eokul.studio.core.RosterParser
import java.util.Locale

/**
 * Yanlış öğrenciye çekilen fotoğrafı doğru öğrenciye taşıma kuralları (Android'siz, test edilebilir).
 *
 * Üç durum: hedef boşsa [Mode.MOVE]; hedefin fotoğrafı varsa [Mode.SWAP] (iki öğrencinin fotoğrafı yer değiştirir)
 * veya [Mode.REPLACE] (hedefin eski fotoğrafı silinir). Fotoğrafa bağlı her şey (kırpma kutusu, puan, yüz sayısı, tekrar
 * işareti, orijinal) fotoğrafla birlikte gider; öğrencinin ad/numarası değişmez.
 */
object PhotoMoveRules {
    enum class Mode { MOVE, SWAP, REPLACE }

    class Ref(val classId: String, val no: String)

    private fun find(classes: List<SchoolClass>, r: Ref): Student? =
        classes.firstOrNull { it.id == r.classId }?.students?.firstOrNull { it.no == r.no }

    /** Taşımayı engelleyen ilk sorun; yoksa null. [busy]: o öğrenci için çekim/kayıt sürüyor mu. */
    fun problem(classes: List<SchoolClass>, src: Ref, dst: Ref, busy: (Ref) -> Boolean): String? {
        val s = find(classes, src) ?: return "Taşınacak öğrenci bulunamadı"
        val d = find(classes, dst) ?: return "Hedef öğrenci bulunamadı"
        if (src.classId == dst.classId && src.no == dst.no) return "Aynı öğrenciyi seçtiniz"
        if (!s.captured) return "Bu öğrencinin taşınacak fotoğrafı yok"
        if (busy(src) || busy(dst)) return "Bu öğrenci için kayıt sürüyor; birkaç saniye sonra tekrar deneyin"
        if (d.no.isEmpty()) return "Hedef öğrenci geçersiz"
        return null
    }

    /** Hedefe göre sunulacak seçenekler (ilki önerilen). */
    fun modes(classes: List<SchoolClass>, dst: Ref): List<Mode> =
        if (find(classes, dst)?.captured == true) listOf(Mode.SWAP, Mode.REPLACE) else listOf(Mode.MOVE)

    /** Hedef listesi: kaynak hariç, [query] numara veya ada göre süzülür (Türkçe büyük/küçük harf duyarsız). */
    fun candidates(cls: SchoolClass, srcNo: String?, query: String, isSameClass: Boolean): List<Student> {
        val q = query.trim().lowercase(TR)
        return cls.students.filter { st ->
            !(isSameClass && st.no == srcNo) &&
                (q.isEmpty() || st.no.contains(q) || st.name.lowercase(TR).contains(q))
        }
    }

    private fun carry(from: Student, to: Student, origUri: String?, updatedAt: Long): Student = to.copy(
        captured = true, updatedAt = updatedAt, box = from.box, boxW = from.boxW, boxH = from.boxH,
        origUri = origUri, savedStem = RosterParser.studentStem(to.no, to.name),
        faceCount = from.faceCount, retake = from.retake, score = from.score
    )

    private fun cleared(st: Student): Student = st.copy(
        captured = false, updatedAt = 0L, box = null, boxW = 0, boxH = 0, origUri = null, savedStem = null,
        faceCount = 0, retake = false, score = -1
    )

    /**
     * Taşımayı listeye uygular. [dstOrig]/[srcOrig]: dosyalar yazıldıktan sonra öğrencilerin yeni orijinal adresleri
     * (orijinal yoksa null; [srcOrig] yalnızca SWAP'ta kullanılır). Zaman damgaları farklıdır ki liste küçük resimleri yenilensin.
     */
    fun apply(
        classes: List<SchoolClass>, src: Ref, dst: Ref, mode: Mode, now: Long, dstOrig: String?, srcOrig: String?
    ): List<SchoolClass> {
        val s = find(classes, src) ?: return classes
        val d = find(classes, dst) ?: return classes
        return classes.map { c ->
            c.copy(students = c.students.map { st ->
                when {
                    c.id == dst.classId && st.no == dst.no -> carry(s, st, dstOrig, now)
                    c.id == src.classId && st.no == src.no ->
                        if (mode == Mode.SWAP) carry(d, st, srcOrig, now + 1) else cleared(st)
                    else -> st
                }
            })
        }
    }

    private val TR = Locale.forLanguageTag("tr")
}
