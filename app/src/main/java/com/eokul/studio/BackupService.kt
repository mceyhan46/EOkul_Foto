package com.eokul.studio

import com.eokul.studio.core.BackupNames
import com.eokul.studio.core.BackupRetention
import com.eokul.studio.core.BackupZip
import com.eokul.studio.core.RestoreGuard
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import java.util.TimeZone
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Yedek ZIP'inin özeti (geri yüklemeden önce kullanıcıya gösterilir). */
data class BackupInfo(
    val classes: Int, val students: Int, val captured: Int, val photos: Int, val createdMs: Long?,
    val retake: Int = 0, val boxes: Int = 0, val appVersion: String = "", val originals: Int = 0
)

/** Yedeğe eklenecek bir orijinal fotoğraf: eşleşme bilgisi ve içeriği açan işlev. */
class OriginalFile(val item: OriginalsRules.Item, val open: () -> InputStream?)

/** Geri yüklemede çıkarılan bir orijinal: geçici dosya ve ait olduğu öğrenci. Çağıran kayıt klasörüne yazıp [file]'ı siler. */
class RestoredOriginal(val item: OriginalsRules.Item, val file: File)

/** Yazılan yedeğin özeti. */
data class BackupWritten(val name: String, val photos: Int, val captured: Int, val originals: Int = 0)

/** Geri yükleme sonucu: devreye alınan sınıflar, atlanan bozuk fotoğraf sayısı, ZIP girdi sayısı. */
data class RestoreOutcome(
    val classes: List<SchoolClass>, val skippedPhotos: Int, val entries: Int, val originals: List<RestoredOriginal> = emptyList()
)

/**
 * Yedek alma, yedeği inceleme, geri yükleme ve güvenlik yedeklerini temizleme.
 *
 * Android'e doğrudan bağlı değildir: dosya erişimi [BackupStorage] ve açılış işlevleriyle, fotoğraf doğrulaması
 * [isImage] ile verilir; bu yüzden gerçek dosya sistemiyle birim testleri yazılabilir. Eşzamanlılık kuralları
 * (kayıt kilidi, geri yükleme bayrağı) çağıran ViewModel'dedir; bu sınıf yalnızca [storeMutex] ile dosya yazımını sıralar.
 */
class BackupService(
    private val filesDir: File,
    private val store: RosterStore,
    private val storeMutex: Mutex,
    private val appVersion: String,
    private val isImage: (File) -> Boolean
) {
    /**
     * Yedek ZIP'ini [storage]'a yazar ve geri okuyup doğrular: roster.json + photos + yedek.json (özet).
     * Doğrulama başarısızsa dosya silinir ve IOException fırlatılır.
     */
    suspend fun write(
        storage: BackupStorage, snap: List<SchoolClass>, prefix: String, now: Date = Date(), originals: List<OriginalFile> = emptyList()
    ): BackupWritten =
        withContext(Dispatchers.IO) {
            val rosterBytes = store.encode(snap).toByteArray(Charsets.UTF_8)
            store.parseStrict(String(rosterBytes, Charsets.UTF_8))
            val photosDir = File(filesDir, "photos").canonicalFile
            val photos = if (photosDir.exists()) photosDir.walkTopDown().filter { f ->
                f.isFile && !f.name.startsWith(".") && !f.name.endsWith(".tmp") && !f.name.endsWith(".old") &&
                    f.canonicalFile.path.startsWith(photosDir.path + File.separator)
            }.toList() else emptyList()
            val captured = snap.sumOf { c -> c.students.count { it.captured } }
            val students = snap.sumOf { it.students.size }
            val manifest = JSONObject()
                .put("app", APP_ID)
                .put("format", 1)
                .put("appVersion", appVersion)
                .put("created", now.time)
                .put("classes", snap.size)
                .put("students", students)
                .put("captured", captured)
                .put("cropBoxes", snap.sumOf { c -> c.students.count { it.box != null } })
                .put("photos", photos.size)
                .put("originals", originals.size)
                .toString()
            val extras = originals.map { o ->
                object : BackupZip.Extra {
                    override fun name() = o.item.entry
                    override fun open(): InputStream? = o.open()
                }
            }
            val name = BackupNames.name(prefix, now, TimeZone.getDefault(), "zip")
            val stored = try {
                storage.write(name) { out: OutputStream ->
                    BackupZip.write(out, manifest.toByteArray(Charsets.UTF_8), rosterBytes, filesDir, photos, extras)
                }
            } catch (e: Exception) {
                // Yazım yarıda kalırsa (ör. bir orijinal okunamadı) yarım yedek dosyası bırakılmaz.
                try { storage.delete(name) } catch (_: Exception) { /* silinemeyen yarım dosya kritik değil */ }
                throw e
            }
            // Yazılan ZIP yeniden açılır; tüm girdiler, manifest ve roster snapshot'ı doğrulanır.
            try {
                val content = stored.open()?.use { ins ->
                    BackupZip.read(ins, BackupZip.expectedNames(filesDir, photos, extras))
                } ?: throw IOException("Yedek dosyası geri okunamadı")
                val restoredSnapshot = store.parseStrict(content.roster)
                if (store.encode(restoredSnapshot) != store.encode(snap)) throw IOException("Yedek öğrenci listesi snapshot ile uyuşmuyor")
                val m = JSONObject(content.manifest)
                if (m.optInt("classes", -1) != snap.size || m.optInt("students", -1) != students ||
                    m.optInt("photos", -1) != photos.size || m.optInt("originals", 0) != originals.size
                ) throw IOException("Yedek manifest doğrulaması başarısız")
            } catch (e: Exception) {
                try { storage.delete(name) } catch (_: Exception) { /* silinemeyen yarım dosya kritik değil */ }
                throw e
            }
            BackupWritten(name, photos.size, captured, originals.size)
        }

    /** Yedeği geri yüklemeden önce içeriğini okur; geçerli bir yedek değilse null (mevcut veriye dokunulmaz). */
    suspend fun inspect(open: () -> InputStream?): BackupInfo? = withContext(Dispatchers.IO) {
        try {
            var json: String? = null
            var manifest: String? = null
            var photos = 0
            var originals = 0
            val budget = RestoreGuard.Budget()
            open()?.use { ins ->
                ZipInputStream(ins).use { zip ->
                    val sink = ByteArray(16 * 1024)
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.isDirectory) continue
                        val kind = budget.enter(e.name)
                        if (kind == RestoreGuard.Kind.PHOTO) {
                            RestoreGuard.photoRelativePath(e.name)
                            photos++
                        } else if (kind == RestoreGuard.Kind.ORIGINAL) {
                            RestoreGuard.originalRelativePath(e.name)
                            originals++
                        }
                        val keep = kind == RestoreGuard.Kind.ROSTER || kind == RestoreGuard.Kind.MANIFEST
                        val bos = if (keep) ByteArrayOutputStream() else null
                        while (true) {
                            val n = zip.read(sink)
                            if (n < 0) break
                            budget.add(n)
                            bos?.write(sink, 0, n)
                        }
                        if (kind == RestoreGuard.Kind.ROSTER) json = bos?.toString("UTF-8")
                        else if (kind == RestoreGuard.Kind.MANIFEST) manifest = bos?.toString("UTF-8")
                    }
                }
            } ?: throw IOException("Dosya açılamadı")
            val parsed = store.parseStrict(json ?: throw IOException("roster.json yok"))
            if (parsed.isEmpty()) return@withContext null
            validateRoster(parsed)
            val m = try {
                JSONObject(manifest ?: throw IOException("yedek.json yok"))
            } catch (_: Exception) {
                return@withContext null
            }
            if (m.optString("app") != APP_ID) return@withContext null
            if (m.optInt("classes", -1) != parsed.size ||
                m.optInt("students", -1) != parsed.sumOf { it.students.size } ||
                m.optInt("photos", -1) != photos ||
                m.optInt("originals", 0) != originals
            ) return@withContext null
            BackupInfo(
                parsed.size, parsed.sumOf { it.students.size }, parsed.sumOf { c -> c.students.count { it.captured } }, photos,
                m.optLong("created", 0L).takeIf { it > 0 },
                retake = parsed.sumOf { c -> c.students.count { it.retake } },
                boxes = parsed.sumOf { c -> c.students.count { it.box != null } },
                appVersion = m.optString("appVersion", ""), originals = originals
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Yedek ZIP'ini geçici alana açar, doğrular ve işlem günlüğüyle (roster.json + photos birlikte) devreye alar.
     * Hata olursa geçici dosyalar temizlenir ve mevcut veri değişmez. Çağıran, kayıt görevlerini durdurmaktan sorumludur.
     */
    suspend fun restore(open: () -> InputStream?): RestoreOutcome = withContext(Dispatchers.IO) {
        storeMutex.withLock {
            val newDir = File(filesDir, "photos.new")
            val preparedRoster = File(filesDir, "roster.json.prepared")
            val origDir = File(filesDir, ORIGINALS_STAGE)
            newDir.deleteRecursively()
            origDir.deleteRecursively()
            preparedRoster.delete()
            if (!newDir.mkdirs() && !newDir.isDirectory) throw IOException("Geçici fotoğraf klasörü oluşturulamadı")

            var json: String? = null
            var manifestText: String? = null
            var photoEntries = 0
            var originalEntries = 0
            val stagedOriginals = ArrayList<Pair<String, File>>()
            var skipped = 0
            val budget = RestoreGuard.Budget()
            try {
                val input = open() ?: throw IOException("Yedek dosyası açılamadı")
                ZipInputStream(input).use { zip ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val kind = budget.enter(entry.name)
                        if (kind == RestoreGuard.Kind.OTHER) {
                            // Tanınmayan içerik yazılmaz; yalnızca toplam boyut sınırına dahildir.
                            while (true) {
                                val n = zip.read(buf)
                                if (n < 0) break
                                budget.add(n)
                            }
                            continue
                        }
                        val isPhoto = kind == RestoreGuard.Kind.PHOTO
                        val isOriginal = kind == RestoreGuard.Kind.ORIGINAL
                        if (isPhoto) photoEntries++
                        if (isOriginal) originalEntries++
                        val target = when {
                            isPhoto -> RestoreGuard.safeTarget(newDir, RestoreGuard.photoRelativePath(entry.name))
                            isOriginal -> RestoreGuard.safeTarget(origDir, RestoreGuard.originalRelativePath(entry.name).replace('/', '_') + "." + stagedOriginals.size)
                            else -> null
                        }
                        if (isOriginal && target != null) stagedOriginals.add(entry.name to target)
                        target?.parentFile?.mkdirs()
                        val sink: OutputStream = target?.outputStream() ?: ByteArrayOutputStream()
                        sink.use { output ->
                            while (true) {
                                val n = zip.read(buf)
                                if (n < 0) break
                                budget.add(n)
                                output.write(buf, 0, n)
                            }
                            if (kind == RestoreGuard.Kind.ROSTER) json = (output as ByteArrayOutputStream).toString("UTF-8")
                            if (kind == RestoreGuard.Kind.MANIFEST) manifestText = (output as ByteArrayOutputStream).toString("UTF-8")
                        }
                        if (isPhoto && target != null && !isImage(target)) {
                            if (!target.delete()) throw IOException("Bozuk fotoğraf temizlenemedi: ${entry.name}")
                            skipped++
                        }
                    }
                }

                val text = json ?: throw IOException("Yedekte roster.json bulunamadı")
                val parsed = store.parseStrict(text)
                if (parsed.isEmpty()) throw IOException("Yedekte sınıf bulunamadı")
                validateRoster(parsed)
                val manifest = JSONObject(manifestText ?: throw IOException("Yedekte yedek.json bulunamadı"))
                if (manifest.optString("app") != APP_ID ||
                    manifest.optInt("classes", -1) != parsed.size ||
                    manifest.optInt("students", -1) != parsed.sumOf { it.students.size } ||
                    manifest.optInt("photos", -1) != photoEntries ||
                    manifest.optInt("originals", 0) != originalEntries
                ) throw IOException("Yedek özeti ile dosya içeriği uyuşmuyor")
                val stagedRoster = store.prepare(parsed)
                RestoreTransaction.commit(filesDir, store, newDir, stagedRoster)
                // Orijinaller yalnızca öğrenciyle eşleşenler için teslim edilir; eşleşmeyenlerin geçici dosyası silinir.
                val delivered = ArrayList<RestoredOriginal>()
                for ((entryName, file) in stagedOriginals) {
                    val m = OriginalsRules.match(parsed, entryName)
                    if (m != null) delivered.add(RestoredOriginal(m, file)) else file.delete()
                }
                RestoreOutcome(parsed, skipped, budget.entries(), delivered)
            } catch (e: Throwable) {
                origDir.deleteRecursively()
                newDir.deleteRecursively()
                preparedRoster.delete()
                throw e
            }
        }
    }

    /**
     * Güvenlik yedeklerinden (Onc_… ve eski adlı) en yeni [keep] tanesi dışındakileri, verilen klasörlerin tümüne bakarak siler.
     * Kullanıcının kendi yedeklerine dokunmaz. Silinen dosya sayısını döndürür.
     */
    fun pruneSafetyBackups(storages: List<BackupStorage>, keep: Int): Int {
        val listings = storages.map { s -> s to s.list() }
        val doomed = BackupRetention.safetyBackupsToDelete(listings.flatMap { it.second }, keep).toSet()
        var deleted = 0
        for ((storage, names) in listings) for (n in names) if (n in doomed) {
            try { storage.delete(n); deleted++ } catch (_: Exception) { /* silinemeyen dosya kritik değil */ }
        }
        return deleted
    }

    companion object {
        const val ORIGINALS_STAGE = "restore_originals"
        const val APP_ID = "E-Okul Foto Studio"

        /** Yedekten gelen listenin temel tutarlılığı: boş kimlik/ad, yinelenen sınıf kimliği veya öğrenci numarası kabul edilmez. */
        fun validateRoster(list: List<SchoolClass>) {
            val classIds = HashSet<String>()
            for (cls in list) {
                if (cls.id.isBlank() || cls.name.isBlank()) throw IOException("Yedekte sınıf kimliği veya adı boş")
                if (!classIds.add(cls.id)) throw IOException("Yedekte yinelenen sınıf kimliği: ${cls.id}")
                val numbers = HashSet<String>()
                for (student in cls.students) {
                    if (student.no.isBlank() || student.name.isBlank()) throw IOException("Yedekte öğrenci numarası veya adı boş")
                    if (!numbers.add(student.no)) throw IOException("${cls.name} sınıfında yinelenen öğrenci numarası: ${student.no}")
                }
            }
        }
    }
}
