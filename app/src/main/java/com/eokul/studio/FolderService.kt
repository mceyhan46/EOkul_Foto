package com.eokul.studio

import android.content.Context
import android.net.Uri
import com.eokul.studio.core.RosterParser

/**
 * Kayıt klasörü (SAF) ve uygulama içi fotoğraf dosyaları üzerindeki işlemler. Engelleyicidir: IO iş parçacığında çağrılmalıdır.
 * Hangi öğrencinin/sınıfın ne zaman değişeceği kararı StudioViewModel/RosterRules'ta; burası yalnızca dosyaları taşır/siler.
 */
class FolderService(private val ctx: Context) {

    /** Sınıf klasörünü (çıktılar + e-Okul ZIP klasörü) yeniden adlandırır; ikisi de başarılıysa true. */
    fun renameClassDirs(root: Uri, oldDir: String, newDir: String): Boolean {
        val a = Output.renameDir(ctx, root, emptyList(), oldDir, newDir)
        val b = Output.renameDir(ctx, root, listOf(EOKUL_DIR), oldDir, newDir)
        return a && b
    }

    /** Sınıfın uygulama içi kopyalarını siler; [root] verilirse kayıt klasöründeki sınıf klasörünü de siler. */
    fun deleteClass(classId: String, className: String, root: Uri?): Boolean {
        Naming.privateDir(ctx, classId).deleteRecursively()
        if (root == null) return true
        val dir = RosterParser.sanitizeFileName(className)
        return Output.deleteDir(ctx, root, emptyList(), dir) && Output.deleteDir(ctx, root, listOf(EOKUL_DIR), dir)
    }

    /** Öğrencinin uygulama içi kopyasını siler; [root] verilirse kayıt klasöründeki çıktı, e-Okul kopyası ve orijinal de silinir. */
    fun deleteStudent(classId: String, className: String, st: Student, root: Uri?) {
        Naming.privatePhoto(ctx, classId, st.no).delete()
        if (root == null) return
        val dir = RosterParser.sanitizeFileName(className)
        st.savedStem?.let { Output.deletePath(ctx, root, listOf(dir), "$it.jpg") }
        Output.deletePath(ctx, root, listOf(EOKUL_DIR, dir), "${st.no}.jpg")
        st.origUri?.let { Output.deleteUri(ctx, Uri.parse(it)) }
    }

    /**
     * Fotoğraf silme (öğrenci listede kalır): uygulama içi kopya, kayıt klasöründeki çıktı ve e-Okul kopyası; orijinal her durumda silinir.
     */
    fun deletePhoto(classId: String, className: String, st: Student, root: Uri?) {
        Naming.privatePhoto(ctx, classId, st.no).delete()
        if (root != null) {
            val dir = RosterParser.sanitizeFileName(className)
            st.savedStem?.let { Output.deletePath(ctx, root, listOf(dir), "$it.jpg") }
            Output.deletePath(ctx, root, listOf(EOKUL_DIR, dir), "${st.no}.jpg")
        }
        st.origUri?.let { Output.deleteUri(ctx, Uri.parse(it)) }
    }

    /** Numara değişince uygulama içi kopyayı yeni adına taşır. */
    fun renumberPrivate(classId: String, oldNo: String, newNo: String) {
        val from = Naming.privatePhoto(ctx, classId, oldNo)
        val to = Naming.privatePhoto(ctx, classId, newNo)
        if (!from.exists()) return
        to.parentFile?.mkdirs()
        if (to.exists()) to.delete()
        if (!from.renameTo(to)) { from.copyTo(to, overwrite = true); from.delete() }
    }

    /** Adı değişen öğrencilerin dosyalarını yeniden adlandırır; (no, yeni stem, yeni orijinal uri) döndürür. */
    fun syncNames(root: Uri, cls: SchoolClass): List<Triple<String, String, String?>> {
        val dir = RosterParser.sanitizeFileName(cls.name)
        val out = ArrayList<Triple<String, String, String?>>()
        for (st in cls.students) {
            val old = st.savedStem ?: continue
            val stem = RosterParser.studentStem(st.no, st.name)
            if (!st.captured || old == stem) continue
            if (Output.renameFile(ctx, root, listOf(dir), "$old.jpg", "$stem.jpg")) {
                val nu = st.origUri?.let { Output.renameUri(ctx, Uri.parse(it), stem) }
                out.add(Triple(st.no, stem, nu?.toString() ?: st.origUri))
            }
        }
        return out
    }
}
