package com.eokul.studio

import com.eokul.studio.core.RosterParser

/**
 * Veri denetimi ve onarım kuralları (Android'siz, test edilebilir). Dosya sistemine/SAF'a erişim lambda ile verilir;
 * bu dosya yalnızca "neyin tutarsız olduğunu" belirler ve metni üretir.
 */
object DataAudit {

    enum class Photo { OK, MISSING, BAD_SIZE }

    /** Uygulama içi fotoğraf denetimi sonucu. */
    class Local(
        val checked: Int,
        val missing: List<String>,
        val broken: List<String>,
        val orphans: List<String>,
        val duplicateNumbers: List<String>
    )

    /**
     * [photoState]: çekilmiş öğrencinin uygulama içi kopyasının durumu; [privateFiles]: sınıfın uygulama içi klasöründeki dosya adları
     * (beklenen ad [expectedName] ile eşleşmeyenler yetim sayılır).
     */
    fun local(
        snap: List<SchoolClass>,
        photoState: (SchoolClass, Student) -> Photo,
        expectedName: (SchoolClass, Student) -> String,
        privateFiles: (SchoolClass) -> List<String>
    ): Local {
        val missing = ArrayList<String>()
        val broken = ArrayList<String>()
        val orphans = ArrayList<String>()
        val dupNo = LinkedHashMap<String, MutableList<String>>()
        var checked = 0
        for (c in snap) {
            val expected = HashSet<String>()
            for (s in c.students) {
                dupNo.getOrPut(s.no) { ArrayList() }.add(c.name)
                expected.add(expectedName(c, s))
                if (!s.captured) continue
                checked++
                when (photoState(c, s)) {
                    Photo.MISSING -> missing.add("${c.name} · ${s.no} ${s.name}")
                    Photo.BAD_SIZE -> broken.add("${c.name} · ${s.no} ${s.name}")
                    Photo.OK -> {}
                }
            }
            for (n in privateFiles(c)) if (n !in expected) orphans.add("${c.name} · $n")
        }
        val dups = dupNo.filter { it.value.size > 1 }.map { "${it.key} → ${it.value.joinToString(", ")}" }
        return Local(checked, missing, broken, orphans, dups)
    }

    private fun isHidden(n: String) = n.startsWith(".tmp-") || n.startsWith(".old-")

    /**
     * Kayıt klasörü satırları. [list] bir yolun (boş = kök) öğe adlarını verir, klasör yoksa null.
     */
    fun folderLines(snap: List<SchoolClass>, rootName: String?, originalsDir: String, list: (List<String>) -> List<String>?): String {
        val lines = ArrayList<String>()
        lines.add("Kayıt klasörü: ${rootName ?: "?"}")
        var rootNames: List<String>? = null
        for (c in snap) {
            val captured = c.students.filter { it.captured }
            if (captured.isEmpty()) continue
            val dirName = RosterParser.sanitizeFileName(c.name)
            val main = list(listOf(dirName))
            if (main == null) {
                if (rootNames == null) rootNames = list(emptyList()) ?: emptyList()
                lines.add(
                    "⚠ ${c.name}: '$dirName' klasörü kayıt klasöründe YOK (${captured.size} fotoğraflı öğrenci). Kökte bulunanlar: " +
                        rootNames.take(15).joinToString(", ").ifEmpty { "(boş)" }
                )
                continue
            }
            val origNames = list(listOf(dirName, originalsDir))
            val mainSet = main.toHashSet()
            var noMain = 0
            var noOrig = 0
            for (s in captured) {
                val stem = s.savedStem ?: RosterParser.studentStem(s.no, s.name)
                if ("$stem.jpg" !in mainSet) noMain++
                if (origNames == null || origNames.none { it.startsWith("$stem.") }) noOrig++
            }
            val hidden = main.count(::isHidden) + (origNames?.count(::isHidden) ?: 0)
            lines.add(
                (if (noMain == 0 && noOrig == 0) "✓ " else "⚠ ") + "${c.name}: ${captured.size} çekilmiş · klasörde fotoğraf eksik: $noMain · " +
                    (if (origNames == null) "Orijinaller klasörü YOK" else "orijinal eksik: $noOrig") +
                    (if (hidden > 0) " · yarım kalmış gizli dosya: $hidden" else "") +
                    " (klasörde ${main.size} öğe)"
            )
        }
        if (lines.size == 1) lines.add("Çekilmiş öğrenci yok.")
        return lines.joinToString("\n")
    }

    fun block(title: String, items: List<String>): String =
        if (items.isEmpty()) "✓ $title: yok"
        else "⚠ $title: ${items.size}\n" + items.take(6).joinToString("\n") { "   • $it" } +
            if (items.size > 6) "\n   … ve ${items.size - 6} tane daha" else ""

    fun report(l: Local, folder: String): String = listOf(
        "${l.checked} fotoğraflı öğrenci kontrol edildi.",
        folder,
        block("Kaydı var, fotoğrafı yok", l.missing),
        block("Bozuk veya yanlış boyutlu fotoğraf", l.broken),
        block("Öğrencisi olmayan fotoğraf dosyası", l.orphans),
        block("Birden çok sınıfta geçen numara", l.duplicateNumbers)
    ).joinToString("\n\n")

    /** Kayıt klasöründe dosyası eksik olan çekilmiş öğrenciler ve yazılacak dosya adı (uzantısız). */
    fun missingInFolder(c: SchoolClass, have: Set<String>): List<Pair<Student, String>> =
        c.students.filter { it.captured }
            .map { it to (it.savedStem ?: RosterParser.studentStem(it.no, it.name)) }
            .filter { (_, stem) -> "$stem.jpg" !in have }
}
