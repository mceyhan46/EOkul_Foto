package com.eokul.studio

/**
 * Dışarıdan (galeri, İndirilenler, başka uygulama) seçilen fotoğrafları sınıf listesindeki öğrencilerle eşleştirme kuralları
 * (Android'siz, test edilebilir). Eşleştirme dosya adındaki öğrenci numarasına göre yapılır; sonuç kullanıcıya önizlenir,
 * emin olunmayan her durum (çakışma, listede olmayan numara, fotoğrafı zaten olan öğrenci) varsayılan olarak seçilmez.
 */
object PhotoImportRules {

    /** Seçilen bir dosya: [uri] adres, [name] görünen ad (uzantılı). */
    class File(val uri: String, val name: String)

    /** Eşleşen satır. [selected]: varsayılan olarak eklenecek mi; [note]: seçilmeme/uyarı nedeni. */
    class Match(val no: String, val studentName: String, val file: File, val hasPhoto: Boolean, val selected: Boolean, val note: String?)

    class Plan(val matches: List<Match>, val unmatched: List<File>) {
        val selectedCount: Int get() = matches.count { it.selected }
    }

    private val DIGITS = Regex("\\d{1,11}")
    private val ANY_DIGITS = Regex("\\d+")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")

    /** Dosya adı bir resim uzantısı taşıyor mu (uzantı yoksa tür sistemden gelir; burada kabul edilir). */
    fun looksLikeImage(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext.isEmpty() || ext in IMAGE_EXT
    }

    /**
     * Dosya adından öğrenci numarası çıkarır. Öncelik: (1) adın tamamı sayı ("12345.jpg"), (2) baştaki sayı
     * ("12345-Ali Veli.jpg", "12345_foto.jpg"), (3) adda geçen sayılardan sınıftaki numarayla eşleşen TEK sayı.
     * Birden çok sınıf numarası geçiyorsa veya hiçbiri eşleşmiyorsa null (belirsiz).
     */
    fun numberFor(fileName: String, studentNos: Set<String>): String? {
        val base = fileName.substringBeforeLast('.', fileName).trim()
        if (DIGITS.matches(base)) return resolve(base, studentNos)
        Regex("^(\\d{1,11})(?!\\d)").find(base)?.let { m -> resolve(m.groupValues[1], studentNos)?.let { return it } }
        val hits = ANY_DIGITS.findAll(base).mapNotNull { resolve(it.value, studentNos) }.toSet()
        return if (hits.size == 1) hits.first() else null
    }

    /** Sayıyı sınıf numarasıyla eşler; birebir yoksa baştaki sıfırlar yok sayılır (tek aday varsa). */
    private fun resolve(digits: String, nos: Set<String>): String? {
        if (digits in nos) return digits
        val t = digits.trimStart('0')
        val cands = nos.filter { it.trimStart('0') == t && t.isNotEmpty() }
        return if (cands.size == 1) cands.first() else null
    }

    /** Seçilen dosyaları sınıf öğrencileriyle eşleştirir (sıra: öğrenci sırası). */
    fun plan(cls: SchoolClass, files: List<File>): Plan {
        val byNo = cls.students.associateBy { it.no }
        val nos = byNo.keys
        val grouped = LinkedHashMap<String, MutableList<File>>()
        val unmatched = ArrayList<File>()
        for (f in files.sortedWith { a, b -> compareNatural(a.name, b.name) }) {
            if (!looksLikeImage(f.name)) { unmatched.add(f); continue }
            val no = numberFor(f.name, nos)
            if (no == null) unmatched.add(f) else grouped.getOrPut(no) { ArrayList() }.add(f)
        }
        val out = ArrayList<Match>()
        for (st in cls.students) {
            val fs = grouped[st.no] ?: continue
            val first = fs.first()
            when {
                fs.size > 1 -> {
                    for (f in fs) out.add(Match(st.no, st.name, f, st.captured, false, "aynı öğrenci için ${fs.size} dosya var; yalnızca birini seçin"))
                }
                st.captured -> out.add(Match(st.no, st.name, first, true, false, "fotoğrafı var; işaretlerseniz değiştirilir"))
                else -> out.add(Match(st.no, st.name, first, false, true, null))
            }
        }
        return Plan(out, unmatched)
    }

    /** Kullanıcının işaretlediği satırlardan eklenecek (öğrenci → dosya) listesi; aynı öğrenci için yalnızca ilk işaretli alınır. */
    fun queue(selected: List<Match>): List<Pair<String, File>> {
        val seen = HashSet<String>()
        val out = ArrayList<Pair<String, File>>()
        for (m in selected) if (seen.add(m.no)) out.add(m.no to m.file)
        return out
    }
}
