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

    /** Kök dizin kipi: değer bir dosya yoludur ("file:///storage/emulated/0/EOkul_Studio"). */
    fun isFileRoot(base: String): Boolean = base.startsWith("file:")

    /** Dosya yolundan okunur ad: "/storage/emulated/0/EOkul_Studio" → "Dahili depolama/EOkul_Studio". */
    fun fileLabel(path: String?): String {
        if (path.isNullOrEmpty()) return "Kayıt klasörü"
        val internal = "/storage/emulated/0"
        val rest = if (path == internal || path.startsWith("$internal/")) path.removePrefix(internal).trim('/') else null
        return if (rest != null) listOf("Dahili depolama", rest).filter { it.isNotEmpty() }.joinToString("/") else path
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

    /** Yeni klasörde bulunanlar: sınıf klasörü, 133x171 fotoğraf, orijinal ve yedek sayıları. */
    class Found(val classFolders: Int, val photos: Int, val originals: Int, val backups: Int) {
        val isEmpty: Boolean get() = classFolders == 0 && photos == 0 && originals == 0 && backups == 0
    }

    /** Klasör değiştirirken kullanıcıya gösterilen bulgu özeti. */
    fun foundSummary(f: Found): String {
        if (f.isEmpty) return "Bu klasör boş görünüyor; mevcut dosyalarınızı buraya kopyalayabilirsiniz."
        val parts = ArrayList<String>()
        if (f.classFolders > 0) parts.add("${f.classFolders} sınıf klasörü")
        if (f.photos > 0) parts.add("${f.photos} fotoğraf")
        if (f.originals > 0) parts.add("${f.originals} orijinal")
        if (f.backups > 0) parts.add("${f.backups} yedek")
        return "Bu klasörde bulundu: " + parts.joinToString(", ") + "."
    }

    /** Öğrenci dosya adı ("12345-Ad Soyad.jpg") bu numaraya mı ait? */
    fun belongsTo(no: String, fileName: String): Boolean = fileName.startsWith("$no-") && !fileName.startsWith(".")
}
