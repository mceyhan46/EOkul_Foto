package com.eokul.studio

import com.eokul.studio.core.CrossClass
import com.eokul.studio.core.RosterParser

/**
 * Sınıf/öğrenci listesi kuralları (Android'siz, saf): doğrulama, ekleme, yeniden adlandırma, numara değiştirme, içe aktarma.
 * Girdi listesini değiştirmez; yeni listeyi döndürür. Dosya/SAF işleri StudioViewModel'de kalır.
 */
object RosterRules {

    /** Hata varsa [error] dolu ve [classes] değişmemiş listedir; [note] kullanıcıya gösterilecek uyarıdır. */
    class Change(val classes: List<SchoolClass>, val error: String? = null, val note: String? = null) {
        val ok: Boolean get() = error == null
    }

    class BranchPlan(val name: String, val rows: List<RosterParser.Row>)

    class ImportOutcome(val classes: List<SchoolClass>, val classId: String, val message: String)

    class BranchOutcome(val classes: List<SchoolClass>, val message: String)

    fun folderKey(name: String): String = RosterParser.sanitizeFileName(name).lowercase()

    fun sortClasses(list: List<SchoolClass>): List<SchoolClass> = list.sortedWith { a, b -> compareNatural(a.name, b.name) }

    private fun sortStudents(list: List<Student>): List<Student> = list.sortedWith { a, b -> RosterParser.compareNo(a.no, b.no) }

    private val NO_RE = Regex("\\d{1,11}")

    fun validateNo(no: String): String? = if (NO_RE.matches(no.trim())) null else "Numara 1–11 haneli sayı olmalı"

    fun validateName(name: String): String? = if (RosterParser.normalizeSpaces(name).length >= 2) null else "Ad soyad girin"

    // ---------------------------------------------------------------- sınıf

    /** Yeni sınıf: ad boşsa veya aynı klasör adı varsa null. Kimliği [newId] üretir. */
    fun addClass(classes: List<SchoolClass>, name: String, newId: () -> String): Pair<List<SchoolClass>, String>? {
        val n = RosterParser.normalizeSpaces(name)
        if (n.isEmpty()) return null
        if (classes.any { folderKey(it.name) == folderKey(n) }) return null
        val c = SchoolClass(newId(), n)
        return sortClasses(classes + c) to c.id
    }

    fun renameClass(classes: List<SchoolClass>, id: String, name: String): Change {
        if (classes.none { it.id == id }) return Change(classes, "Sınıf bulunamadı")
        val n = RosterParser.normalizeSpaces(name)
        if (n.isEmpty()) return Change(classes, "Sınıf adı boş olamaz")
        if (classes.any { it.id != id && folderKey(it.name) == folderKey(n) }) return Change(classes, "Bu adla başka bir sınıf var")
        return Change(sortClasses(classes.map { if (it.id == id) it.copy(name = n) else it }))
    }

    // ---------------------------------------------------------------- öğrenci

    fun crossClassWarning(classes: List<SchoolClass>, no: String, classId: String): String? {
        val ids = CrossClass.classesWith(no, numbersByClass(classes), classId)
        if (ids.isEmpty()) return null
        val names = ids.mapNotNull { id -> classes.firstOrNull { it.id == id }?.name }
        val shown = names.take(3).joinToString(", ") + if (names.size > 3) " +${names.size - 3}" else ""
        return "⚠ $no numarası başka sınıfta da var: $shown"
    }

    private fun numbersByClass(classes: List<SchoolClass>): Map<String, Set<String>> =
        classes.associate { c -> c.id to c.students.mapTo(HashSet()) { it.no } }

    private fun sharedCount(classes: List<SchoolClass>, rows: List<RosterParser.Row>, classId: String): Int =
        CrossClass.countShared(rows.filter { it.error == null }.map { it.no }, numbersByClass(classes), classId)

    private fun update(classes: List<SchoolClass>, id: String, fn: (SchoolClass) -> SchoolClass) =
        classes.map { if (it.id == id) fn(it) else it }

    fun addStudent(classes: List<SchoolClass>, classId: String, noRaw: String, name: String): Change {
        val no = noRaw.trim()
        validateNo(no)?.let { return Change(classes, it) }
        validateName(name)?.let { return Change(classes, it) }
        val c = classes.firstOrNull { it.id == classId } ?: return Change(classes, "Sınıf bulunamadı")
        if (c.students.any { it.no == no }) return Change(classes, "Bu numara zaten var")
        val list = sortStudents(c.students + Student(no, RosterParser.normalizeSpaces(name)))
        val next = update(classes, classId) { it.copy(students = list) }
        return Change(next, note = crossClassWarning(next, no, classId))
    }

    /**
     * Numara ve/veya ad değişikliği. [busy] true ise (öğrencinin fotoğrafı işleniyor) numara değişimi reddedilir.
     * Dönen Change'te [Change.note] numara başka sınıfta da varsa uyarıdır.
     */
    fun editStudent(classes: List<SchoolClass>, classId: String, oldNo: String, newNoRaw: String, name: String, busy: Boolean): Change {
        val newNo = newNoRaw.trim()
        validateNo(newNo)?.let { return Change(classes, it) }
        validateName(name)?.let { return Change(classes, it) }
        val nm = RosterParser.normalizeSpaces(name)
        val c = classes.firstOrNull { it.id == classId } ?: return Change(classes, "Sınıf bulunamadı")
        if (c.students.none { it.no == oldNo }) return Change(classes, "Öğrenci bulunamadı")
        if (newNo == oldNo) return Change(update(classes, classId) { cl -> cl.copy(students = cl.students.map { if (it.no == oldNo) it.copy(name = nm) else it }) })
        if (c.students.any { it.no == newNo }) return Change(classes, "Bu numara bu sınıfta zaten var")
        if (busy) return Change(classes, "Bu öğrencinin fotoğrafı şu an işleniyor/kaydediliyor; birkaç saniye sonra tekrar deneyin")
        val next = update(classes, classId) { cl ->
            cl.copy(students = sortStudents(cl.students.map { if (it.no == oldNo) it.copy(no = newNo, name = nm) else it }))
        }
        return Change(next, note = crossClassWarning(next, newNo, classId))
    }

    // ---------------------------------------------------------------- içe aktarma

    private fun mergeMessage(r: MergeResult): String {
        val parts = ArrayList<String>()
        if (r.added > 0) parts.add("${r.added} eklendi")
        if (r.updated > 0) parts.add("${r.updated} güncellendi")
        if (r.removed > 0) parts.add("${r.removed} çıkarıldı")
        return if (parts.isEmpty()) "Değişiklik yok" else parts.joinToString(", ")
    }

    private fun sharedNote(shared: Int) =
        if (shared > 0) "\n⚠ $shared numara başka sınıflarda da var (Veri denetimi'nde listelenir)" else ""

    /** [classId] null ise [newName] ile yeni sınıf açılır. Satır yoksa veya sınıf açılamazsa null. */
    fun importRoster(
        classes: List<SchoolClass>, classId: String?, newName: String,
        rows: List<RosterParser.Row>, mode: MergeMode, newId: () -> String
    ): ImportOutcome? {
        if (rows.isEmpty()) return null
        var list = classes
        val id = classId ?: run {
            val added = addClass(list, newName, newId) ?: return null
            list = added.first
            added.second
        }
        val cls = list.firstOrNull { it.id == id } ?: return null
        val r = mergeStudents(cls.students, rows, mode)
        list = update(list, id) { it.copy(students = r.students) }
        return ImportOutcome(list, id, "✓ " + mergeMessage(r) + sharedNote(sharedCount(list, rows, id)))
    }

    /** Aynı adlı sınıf varsa öğrencileri güncellenir (UPSERT), yoksa yeni sınıf açılır (ADD). */
    fun importBranches(classes: List<SchoolClass>, plans: List<BranchPlan>, newId: () -> String): BranchOutcome {
        var list = classes
        var created = 0
        var merged = 0
        var students = 0
        var shared = 0
        for (p in plans) {
            if (p.rows.isEmpty()) continue
            val n = RosterParser.normalizeSpaces(p.name)
            if (n.isEmpty()) continue
            val existing = list.firstOrNull { folderKey(it.name) == folderKey(n) }
            if (existing != null) {
                val r = mergeStudents(existing.students, p.rows, MergeMode.UPSERT)
                list = update(list, existing.id) { it.copy(students = r.students) }
                shared += sharedCount(list, p.rows, existing.id)
                merged++
            } else {
                val added = addClass(list, n, newId) ?: continue
                list = added.first
                val cls = list.first { it.id == added.second }
                val r = mergeStudents(cls.students, p.rows, MergeMode.ADD)
                list = update(list, added.second) { it.copy(students = r.students) }
                shared += sharedCount(list, p.rows, added.second)
                created++
            }
            students += p.rows.size
        }
        val parts = ArrayList<String>()
        if (created > 0) parts.add("$created sınıf eklendi")
        if (merged > 0) parts.add("$merged sınıf güncellendi")
        val msg = if (parts.isEmpty()) "Değişiklik yok" else parts.joinToString(", ") + " · $students öğrenci"
        return BranchOutcome(list, "✓ $msg" + sharedNote(shared))
    }
}
