package com.eokul.studio

import android.content.Context
import android.net.Uri
import com.eokul.studio.core.BackupNames
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Kayıt klasörü değişirken yeni klasörün taranması, orijinallerin öğrencilere yeniden bağlanması ve dosyaların kopyalanması.
 * Engelleyicidir (IO iş parçacığı); [root] değerleri ayarda saklanan kodlu klasör değeridir ([RootRules.encode]).
 */
class RootMigration(private val ctx: Context) {

    /** Kopyalama sonucu: [copied] kopyalanan, [failed] kopyalanamayan dosya sayısı; [uris] yeni orijinal adresleri. */
    class Report(val copiedOriginals: Int, val copiedBackups: Int, val failed: Int, val uris: Map<Pair<String, String>, String>)

    /** Klasörde ne var? (yalnızca okur) */
    fun inspect(root: Uri, snap: List<SchoolClass>): RootRules.Found {
        var folders = 0
        var photos = 0
        var originals = 0
        for (c in snap) {
            val dir = OriginalsRules.classDir(c)
            val names = Output.listNames(ctx, root, listOf(dir)) ?: continue
            folders++
            val nums = c.students.map { it.no }
            photos += nums.count { no -> names.any { it.endsWith(".jpg", true) && RootRules.belongsTo(no, it) } }
            val orig = Output.listNames(ctx, root, listOf(dir, ORIGINALS_DIR)) ?: continue
            originals += nums.count { no -> orig.any { RootRules.belongsTo(no, it) } }
        }
        val backups = Output.listNames(ctx, root, listOf(BackupNames.FOLDER))?.count { it.endsWith(".zip", true) } ?: 0
        return RootRules.Found(folders, photos, originals, backups)
    }

    /** Yeni klasördeki orijinalleri öğrencilere bağlar: (sınıf kimliği, no) → adres. */
    fun relink(root: Uri, snap: List<SchoolClass>): Map<Pair<String, String>, String> {
        val map = HashMap<Pair<String, String>, String>()
        for (c in snap) for (st in c.students) {
            if (!st.captured) continue
            val u = Output.findFile(ctx, root, listOf(OriginalsRules.classDir(c), ORIGINALS_DIR), OriginalsRules.stem(st), "${st.no}-") ?: continue
            map[c.id to st.no] = u.toString()
        }
        return map
    }

    /**
     * Eski klasördeki orijinalleri ve yedekleri yenisine kopyalar (yeni yerde zaten olanlar atlanır). Orijinallerin yeni
     * adresleri döner; eski dosyalar silinmez.
     */
    fun copy(oldRoot: Uri, newRoot: Uri, snap: List<SchoolClass>): Report {
        var orig = 0
        var bak = 0
        var failed = 0
        val uris = HashMap<Pair<String, String>, String>()
        for (c in snap) for (st in c.students) {
            if (!st.captured) continue
            val dir = OriginalsRules.classDir(c)
            val path = listOf(dir, ORIGINALS_DIR)
            val have = Output.findFile(ctx, newRoot, path, OriginalsRules.stem(st), "${st.no}-")
            if (have != null) { uris[c.id to st.no] = have.toString(); continue }
            val src = st.origUri?.let { Uri.parse(it) }?.takeIf { Output.exists(ctx, it) }
                ?: Output.findFile(ctx, oldRoot, path, OriginalsRules.stem(st), "${st.no}-")
                ?: continue
            val name = OriginalsRules.fileName(st, Output.displayName(ctx, src))
            try {
                val u = Output.writeStream(ctx, newRoot, path, name, OriginalsRules.mime(name)) { out ->
                    (ctx.contentResolver.openInputStream(src) ?: throw IOException("Açılamadı: $name")).use { it.copyTo(out) }
                }
                uris[c.id to st.no] = u.toString()
                orig++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Ops.error("root_copy_original", e)
                failed++
            }
        }
        val haveBackups = (Output.listNames(ctx, newRoot, listOf(BackupNames.FOLDER)) ?: emptyList()).toHashSet()
        for ((name, src) in Output.listFiles(ctx, oldRoot, listOf(BackupNames.FOLDER)) ?: emptyList()) {
            if (!name.endsWith(".zip", true) || name in haveBackups) continue
            try {
                Output.writeStream(ctx, newRoot, listOf(BackupNames.FOLDER), name, "application/zip") { out ->
                    (ctx.contentResolver.openInputStream(src) ?: throw IOException("Açılamadı: $name")).use { it.copyTo(out) }
                }
                bak++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Ops.error("root_copy_backup", e)
                failed++
            }
        }
        return Report(orig, bak, failed, uris)
    }
}
