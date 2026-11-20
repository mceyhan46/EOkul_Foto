package com.eokul.studio

/**
 * Kayıt klasörü kuralları (Android'siz, test edilebilir): saklanan değerin biçimi, durum kararı ve ekranda gösterilen yol.
 *
 * Saklanan değer: `<ağaç uri>` (eski kurulumlar) veya `<ağaç uri>#EOkul_Studio` (yeni kurulum). Ağaç, kullanıcının
 * seçtiği klasördür; `#` sonrası, uygulamanın onun içinde kendi açtığı alt klasördür. Ham `#` uri içinde yalnızca
 * bu amaçla bulunur (uri'nin kendisinde `%23` olarak kaçırılır).
 */
object RootRules {
    const val DEFAULT_SUB = "EOkul_Studio"

    enum class State { NOT_SET, READY, PERMISSION_LOST, MISSING, READ_ONLY }

    class Ref(val base: String, val sub: String?)

    fun encode(base: String, sub: String?): String = if (sub.isNullOrEmpty()) base else "$base#$sub"

    fun decode(stored: String): Ref {
        val i = stored.lastIndexOf('#')
        return if (i < 0) Ref(stored, null) else Ref(stored.substring(0, i), stored.substring(i + 1).ifEmpty { null })
    }

    /** Seçilen klasörün adı zaten EOkul_Studio ise doğrudan o kullanılır (null); aksi hâlde içinde EOkul_Studio açılır. */
    fun chooseSub(pickedName: String?): String? = if (pickedName.equals(DEFAULT_SUB, ignoreCase = true)) null else DEFAULT_SUB

    /**
     * [set]: ayarda klasör var mı; [grant]: kalıcı yazma izni duruyor mu; [exists]: klasör (varsa alt klasörü) bulunuyor mu;
     * [canWrite]: yazılabiliyor mu (SD kart çıkarılmış olabilir).
     */
    fun status(set: Boolean, grant: Boolean, exists: Boolean, canWrite: Boolean): State = when {
        !set -> State.NOT_SET
        !grant -> State.PERMISSION_LOST
        !exists -> State.MISSING
        !canWrite -> State.READ_ONLY
        else -> State.READY
    }

    /** Ağaç belge kimliğinden ("primary:Documents", "1A2B-3C4D:Okul") okunur yol: "Dahili depolama/Documents/EOkul_Studio". */
    fun label(treeDocId: String?, sub: String?): String {
        val parts = ArrayList<String>()
        if (treeDocId != null) {
            val c = treeDocId.indexOf(':')
            val vol = if (c < 0) treeDocId else treeDocId.substring(0, c)
            val path = if (c < 0) "" else treeDocId.substring(c + 1)
            parts.add(if (vol == "primary") "Dahili depolama" else "SD kart")
            parts.addAll(path.split('/').filter { it.isNotEmpty() })
        }
        if (sub != null && parts.lastOrNull() != sub) parts.add(sub)
        return parts.joinToString("/").ifEmpty { "Kayıt klasörü" }
    }

    /** Seçilen klasör daha önce bu uygulamayla kullanılmış bir kayıt klasörü gibi mi? (Yedek/ klasörü veya içinde Orijinaller olan klasör) */
    fun looksLikeStudioRoot(childDirs: Map<String, List<String>>, backupDir: String, originalsDir: String): Boolean =
        childDirs.containsKey(backupDir) || childDirs.values.any { originalsDir in it }

    /** Durum için kullanıcıya gösterilecek kısa açıklama; null = sorun yok. */
    fun problem(s: State): String? = when (s) {
        State.NOT_SET -> "Kayıt klasörü kurulmadı"
        State.PERMISSION_LOST -> "Kayıt klasörü izni kaybolmuş"
        State.MISSING -> "Kayıt klasörü bulunamadı (silinmiş veya taşınmış olabilir)"
        State.READ_ONLY -> "Kayıt klasörüne yazılamıyor (SD kart çıkarılmış olabilir)"
        State.READY -> null
    }
}
