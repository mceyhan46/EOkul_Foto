package com.eokul.studio

/** Toplu kırpma (Galeri) akışının Android'siz kuralları: kayıttan geri yükleme kararı, durum metinleri, geri al sınırı. */
object BatchRules {

    /** Bir fotoğrafın çözümlenmiş durumu. */
    class Analysis(val box: Box, val faceFound: Boolean, val faceCount: Int, val edited: Boolean, val skipped: Boolean)

    /**
     * Önceki oturumdan kalan kayıt, görüntü boyutu değişmediyse ([w]x[h]) olduğu gibi kullanılır (kutu görüntü içine oturtulur);
     * boyut farklıysa veya kayıt yoksa null: yeniden yüz bulunup varsayılan kutu hesaplanır.
     */
    fun fromRecord(rec: CropStore.Rec?, w: Int, h: Int): Analysis? =
        if (rec != null && rec.w == w && rec.h == h)
            Analysis(rec.box.fitInside(w, h), rec.face, rec.faceCount, rec.edited, rec.skipped)
        else null

    fun progress(prefix: String, done: Int, total: Int) = "⏳ $prefix… $done/$total"

    fun loadDone(total: Int, failed: Int): String =
        "✅ ${total - failed} fotoğraf hazır" + if (failed > 0) "  ·  ⚠ $failed okunamadı" else ""

    fun recropDone(okCount: Int): String = "✅ $okCount fotoğraf yeniden kırpıldı"

    fun saveProgress(done: Int, total: Int) = "💾 Kaydediliyor… $done/$total"

    /** Kaydetme bitişi: durum çubuğu metni, varsa kısa bildirim ve hata listesi. */
    class SaveOutcome(val status: String, val toast: String?, val failures: List<Pair<String, String>>)

    fun saveOutcome(ok: Int, fails: List<Pair<String, String>>, cancelled: Boolean, where: String): SaveOutcome = when {
        cancelled -> SaveOutcome("⏹ İptal edildi — $ok kaydedildi", "⏹ İptal edildi — $ok fotoğraf kaydedildi", emptyList())
        fails.isNotEmpty() -> SaveOutcome("⚠ $ok başarılı, ${fails.size} hatalı", null, fails)
        else -> SaveOutcome("✅ $ok fotoğraf kaydedildi → $where", "✓ $ok fotoğraf kaydedildi → $where", emptyList())
    }

    /** Geri al listesine ekler; en eski girdi [max] aşılınca atılır. */
    fun <T> pushCapped(list: MutableList<T>, item: T, max: Int) {
        list.add(item)
        while (list.size > max) list.removeAt(0)
    }
}
