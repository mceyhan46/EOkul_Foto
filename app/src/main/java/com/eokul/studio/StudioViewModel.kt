package com.eokul.studio

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eokul.studio.core.BackupZip
import com.eokul.studio.core.CrossClass
import com.eokul.studio.core.Crop
import com.eokul.studio.core.RestoreGuard
import com.eokul.studio.core.RosterParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface Screen {
    data object Home : Screen
    data class ClassDetail(val classId: String) : Screen
    data class Import(val classId: String?) : Screen
    data object MultiImport : Screen
    data class Sheet(val classId: String) : Screen
    data class Camera(val classId: String, val no: String) : Screen
    data object Review : Screen
    data object CaptureEditor : Screen
    data object Settings : Screen
    data object Batch : Screen
}

/** Çekilen (veya seçilen) tek bir fotoğrafın inceleme durumu. */
class CaptureState(
    val classId: String,
    val no: String,
    val source: Uri,
    val temp: File?,
    val fromSaved: Boolean
) {
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var session by mutableStateOf<EditSession?>(null)
    var saving by mutableStateOf(false)
    /** İnceleme ekranında kalite/yüz uyarısı çıktı mı (kayıtta "tekrar çek" işareti olur). */
    var warned by mutableStateOf(false)
    /** İnceleme ekranında hesaplanan kalite puanı; -1 = hesaplanmadı. */
    var score by mutableStateOf(-1)
    /** Kayıtlı kırpmayı düzenleme: inceleme ekranı atlanır, düzenleyici doğrudan açılır; Uygula = kaydet. */
    var directEdit by mutableStateOf(false)
    /** Günlük için: uyarı kodları (virgüllü), otomatik kabul mi, inceleme ekranının açıldığı an. */
    var hardCodes: String = ""
    var softCodes: String = ""
    var auto: Boolean = false
    var shownAt: Long = 0L
}

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val store = RosterStore(File(ctx.filesDir, "roster.json"))
    private val storeMutex = Mutex()
    private val rosterVersion = AtomicLong(0L)
    /** Diske henüz doğrulanmamış fotoğraf kayıtları eski öğrenci durumuyla temsil edilir. */
    private val pendingCaptureOriginals = HashMap<String, Student>()
    private var recoveryBlocked = false
    var isRestoring by mutableStateOf(false)
        private set
    /** Arka plandaki fotoğraf yazmaları sırayla çalışır (bellek ve klasör çakışması olmasın). */
    private val saveMutex = Mutex()

    val classes = mutableStateListOf<SchoolClass>()
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()

    var capture by mutableStateOf<CaptureState?>(null)
        private set
    var rootName by mutableStateOf<String?>(null)
        private set

    val settings: Settings get() = SettingsState.current
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    private var captureJob: Job? = null

    /** Açılışta liste dosyası bozuk çıktıysa gösterilen kurtarma açıklaması. */
    var startupNote by mutableStateOf<String?>(null)
        private set

    fun addStartupNote(msg: String) {
        startupNote = (startupNote?.let { it + "\n\n" } ?: "") + msg
    }

    fun dismissStartupNote() {
        startupNote = null
    }

    init {
        SettingsState.init(ctx)
        // Yarım kalmış çekimlerden artakalan geçici kareleri (6 saatten eski) temizle.
        try {
            val limit = System.currentTimeMillis() - 6L * 3600_000L
            File(ctx.cacheDir, "capture").listFiles()?.forEach { if (it.isFile && it.lastModified() < limit) it.delete() }
        } catch (_: Exception) { }
        var recoveryError: String? = null
        try {
            RestoreTransaction.recover(ctx.filesDir)
        } catch (e: Exception) {
            recoveryBlocked = true
            recoveryError = "Önceki geri yükleme işlemi güvenli biçimde tamamlanamadı. Veri koruması için otomatik kayıt durduruldu. " +
                "Kurtarma dosyalarını silmeyin. Ayrıntı: ${e.message ?: e.javaClass.simpleName}"
            Ops.error("restore_recovery", e)
        }
        classes.addAll(store.load())
        startupNote = listOfNotNull(store.loadNote, recoveryError).joinToString("\n\n").ifBlank { null }
        refreshRootName()
    }

    fun updateSettings(s: Settings) {
        SettingsState.update(s)
    }

    // ------------------------------------------------------------------ PIN kilidi
    var locked by mutableStateOf(SettingsState.current.pinHash != null)
        private set
    private var backgroundedAt = 0L

    fun onBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
    }

    /** Uygulama arka plandan dönerse ve 30 sn'den uzun kaldıysa kilitler. */
    fun onForegrounded() {
        if (PinLock.shouldLock(settings.pinHash != null, backgroundedAt, System.currentTimeMillis())) locked = true
    }

    fun checkPin(pin: String): Boolean {
        val h = settings.pinHash ?: return true
        val salt = settings.pinSalt ?: return false
        return com.eokul.studio.core.PinCrypto.verify(pin, salt, h)
    }

    private val lockPrefs by lazy { ctx.getSharedPreferences("eokul_lock", android.content.Context.MODE_PRIVATE) }
    private val pinLock by lazy {
        PinLock(object : LockStore {
            override var fails: Int
                get() = lockPrefs.getInt("fails", 0)
                set(v) { lockPrefs.edit().putInt("fails", v).apply() }
            override var until: Long
                get() = lockPrefs.getLong("until", 0L)
                set(v) { lockPrefs.edit().putLong("until", v).apply() }
        })
    }

    /** Yanlış denemeler nedeniyle kalan bekleme süresi (sn); 0 = deneme yapılabilir. */
    fun pinWaitSeconds(): Int = pinLock.waitSeconds()

    fun unlock(pin: String): Boolean {
        if (pinWaitSeconds() > 0) return false
        if (!checkPin(pin)) {
            Ops.log("pin_fail", "fails", pinLock.recordFailure())
            return false
        }
        pinLock.reset()
        // Eski biçimle kaydedilmiş PIN doğru girildiğinde yeni biçime sessizce yükseltilir.
        val h = settings.pinHash
        val salt = settings.pinSalt
        if (h != null && salt != null && com.eokul.studio.core.PinCrypto.isLegacy(h)) {
            SettingsState.update(settings.copy(pinHash = com.eokul.studio.core.PinCrypto.hash(pin, salt)))
        }
        locked = false
        return true
    }

    fun setPin(pin: String?) {
        if (pin == null) {
            SettingsState.update(settings.copy(pinHash = null, pinSalt = null))
            locked = false
        } else {
            val salt = java.util.UUID.randomUUID().toString()
            SettingsState.update(settings.copy(pinHash = com.eokul.studio.core.PinCrypto.hash(pin, salt), pinSalt = salt))
        }
    }

    fun toast(msg: String) {
        say(msg)
    }

    private fun say(msg: String) {
        messages.tryEmit(msg)
    }

    // ------------------------------------------------------------------ gezinme
    fun navigate(s: Screen) {
        if (isRestoring) return
        stack.add(s)
    }

    fun back() {
        if (isRestoring) return
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun replace(s: Screen) {
        if (isRestoring) return
        stack[stack.lastIndex] = s
    }

    fun handleBack() {
        if (isRestoring) return
        when (screen) {
            Screen.Review -> discardCapture()
            Screen.CaptureEditor -> cancelCaptureEditor()
            else -> back()
        }
    }

    // ------------------------------------------------------------------ kalıcılık
    private fun captureKey(classId: String, no: String) = CaptureRules.key(classId, no)

    /** Henüz fotoğraf dosyası doğrulanmamış öğrencileri diskte eski durumuyla temsil eder. */
    private fun stableRosterSnapshot(): List<SchoolClass> = CaptureRules.stableSnapshot(classes.toList(), pendingCaptureOriginals)

    /** Her değişiklik sürümlenir; yalnızca en güncel snapshot diske yazılabilir. */
    private fun persist() {
        if (recoveryBlocked) {
            say("⚠ Kurtarma işlemi tamamlanmadığı için otomatik kayıt durduruldu. Uygulamayı kapatmayın.")
            return
        }
        if (isRestoring) return
        val version = rosterVersion.incrementAndGet()
        val snap = stableRosterSnapshot()
        viewModelScope.launch(Dispatchers.IO) {
            val err = storeMutex.withLock {
                if (version != rosterVersion.get()) null else store.save(snap)
            }
            if (err != null) {
                Ops.log("roster_save", "ok", false, "version", version, "err", err.take(180))
                say("⚠ Öğrenci listesi kaydedilemedi: $err")
            } else if (version == rosterVersion.get()) {
                Ops.log("roster_save", "ok", true, "version", version, "students", snap.sumOf { it.students.size })
            }
        }
    }

    fun persistNow() {
        persist()
    }

    /** Activity arka plana geçerken çağrılır; son snapshot diske yazılana kadar bekler. */
    suspend fun persistAndWait() {
        if (recoveryBlocked || isRestoring) return
        while (true) {
            val version = rosterVersion.get()
            val snap = stableRosterSnapshot()
            val result = withContext(Dispatchers.IO) {
                storeMutex.withLock {
                    if (version != rosterVersion.get()) null else store.save(snap)
                }
            }
            if (version == rosterVersion.get()) {
                if (result != null) {
                    Ops.log("roster_save", "ok", false, "version", version, "err", result.take(180))
                    say("⚠ Son öğrenci listesi kaydedilemedi: $result")
                }
                return
            }
        }
    }

    override fun onCleared() {
        // onCleared ana thread'de bloklayıcı dosya yazımı yapmamalı. MainActivity onStop'ta persistAndWait çağırır.
        super.onCleared()
    }

    fun classById(id: String?): SchoolClass? = classes.firstOrNull { it.id == id }

    private fun updateClass(id: String, fn: (SchoolClass) -> SchoolClass) {
        val i = classes.indexOfFirst { it.id == id }
        if (i < 0) return
        classes[i] = fn(classes[i])
        persist()
    }

    /** Sınıftaki tüm "tekrar çekilmeli" işaretlerini kaldırır (eski sürümün hatalı işaretleri için). */
    fun clearRetakeFlags(classId: String) {
        updateClass(classId) { c -> c.copy(students = c.students.map { if (it.retake) it.copy(retake = false) else it }) }
    }

    fun toggleRetake(classId: String, no: String) {
        updateStudent(classId, no) { it.copy(retake = !it.retake) }
    }

    private fun updateStudent(classId: String, no: String, fn: (Student) -> Student) {
        updateClass(classId) { c ->
            c.copy(students = c.students.map { if (it.no == no) fn(it) else it })
        }
    }

    /** Fotoğraf dosyası diske yazılmadan geçici UI durumu günceller; kalıcı kayıt yapılmaz. */
    private fun updateStudentTransient(classId: String, no: String, fn: (Student) -> Student) {
        val index = classes.indexOfFirst { it.id == classId }
        if (index < 0) return
        val cls = classes[index]
        classes[index] = cls.copy(students = cls.students.map { if (it.no == no) fn(it) else it })
    }

    // ------------------------------------------------------------------ kayıt klasörü
    private fun refreshRootName() {
        val u = settings.rootUri?.let { Uri.parse(it) }
        rootName = if (u == null) null else try {
            DocumentFile.fromTreeUri(ctx, u)?.name
        } catch (e: Exception) {
            null
        }
    }

    fun hasRootAccess(): Boolean {
        val u = settings.rootUri?.let { Uri.parse(it) } ?: return false
        return ctx.contentResolver.persistedUriPermissions.any { it.uri == u && it.isWritePermission }
    }

    fun setRootFolder(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            say("Klasör izni alınamadı")
            return
        }
        SettingsState.update(settings.copy(rootUri = uri.toString()))
        refreshRootName()
        say("✓ Kayıt klasörü: ${rootName ?: "seçildi"}")
    }

    // ------------------------------------------------------------------ sınıflar
    private fun newId(): String =
        System.currentTimeMillis().toString(36) + (0..999).random().toString(36)

    /** Sınıf listesini (sıralı hâliyle) yerine koyar ve kaydeder. */
    private fun replaceClasses(next: List<SchoolClass>) {
        classes.clear()
        classes.addAll(next)
        persist()
    }

    /** Yeni sınıf ekler; ad boşsa veya aynı adlı sınıf varsa null döner. */
    fun addClass(name: String): String? {
        val r = RosterRules.addClass(classes.toList(), name, ::newId) ?: return null
        replaceClasses(r.first)
        return r.second
    }

    /** Sınıfı yeniden adlandırır; kayıt klasöründeki klasörleri de (mümkünse) yeniden adlandırır. */
    fun renameClass(id: String, name: String): Boolean {
        val c = classById(id) ?: return false
        val n = RosterParser.normalizeSpaces(name)
        if (n.isEmpty()) return false
        val change = RosterRules.renameClass(classes.toList(), id, n)
        if (!change.ok) {
            say(change.error!!)
            return false
        }
        val oldDir = RosterParser.sanitizeFileName(c.name)
        val newDir = RosterParser.sanitizeFileName(n)
        val root = settings.rootUri?.let { Uri.parse(it) }
        if (root != null && oldDir != newDir) {
            viewModelScope.launch {
                val ok = withContext(Dispatchers.IO) { folders.renameClassDirs(root, oldDir, newDir) }
                if (!ok) say("⚠ Sınıf adı değişti ama kayıt klasörü '$oldDir' yeniden adlandırılamadı (hedef ad dolu olabilir). Dosyalar eski klasörde kaldı.")
            }
        }
        replaceClasses(change.classes)
        return true
    }

    /** [alsoFiles] true ise kayıt klasöründeki sınıf klasörü (çıktılar ve Orijinaller) de içindekilerle silinir. */
    fun deleteClass(id: String, alsoFiles: Boolean = false) {
        val c = classById(id) ?: return
        val root = settings.rootUri?.let { Uri.parse(it) }
        classes.removeAll { it.id == id }
        persist()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { folders.deleteClass(id, c.name, if (alsoFiles) root else null) }
            say(
                when {
                    !alsoFiles -> "“${c.name}” silindi (kayıt klasöründeki dosyalar duruyor)"
                    ok -> "“${c.name}” ve kayıt klasöründeki dosyaları silindi"
                    else -> "⚠ “${c.name}” silindi ama kayıt klasörü tamamen silinemedi"
                }
            )
        }
    }

    // ------------------------------------------------------------------ liste içe aktarma
    /** Sınıfa öğrenci ekler. [classId] null ise [newName] ile yeni sınıf açılır. Sonuç mesajı döner (hata: null). */
    fun importRoster(classId: String?, newName: String, rows: List<RosterParser.Row>, mode: MergeMode): String? {
        val r = RosterRules.importRoster(classes.toList(), classId, newName, rows, mode, ::newId) ?: return null
        replaceClasses(r.classes)
        say(r.message)
        return r.classId
    }

    fun importBranches(plans: List<RosterRules.BranchPlan>): String {
        val r = RosterRules.importBranches(classes.toList(), plans, ::newId)
        replaceClasses(r.classes)
        say(r.message)
        return r.message.removePrefix("✓ ").substringBefore("\n")
    }

    fun addStudent(classId: String, no: String, name: String): String? {
        val r = RosterRules.addStudent(classes.toList(), classId, no, name)
        if (!r.ok) return r.error
        replaceClasses(r.classes)
        r.note?.let { say(it) }
        return null
    }

    /**
     * Öğrencinin numarasını ve/veya adını değiştirir. Numara değişirse uygulama içi küçük kopya ve kayıt klasöründeki
     * dosyalar (fotoğraf, orijinal) yeni numaraya göre yeniden adlandırılır; liste numaraya göre yeniden sıralanır.
     * Hata varsa açıklama, başarılıysa null döner.
     */
    fun editStudent(classId: String, oldNo: String, newNoRaw: String, name: String): String? {
        val newNo = newNoRaw.trim()
        val st = classById(classId)?.students?.firstOrNull { it.no == oldNo }
        val busy = capture?.let { it.classId == classId && it.no == oldNo } == true ||
            pendingCaptureOriginals.containsKey(captureKey(classId, oldNo))
        val r = RosterRules.editStudent(classes.toList(), classId, oldNo, newNo, name, busy)
        if (!r.ok) return r.error
        replaceClasses(r.classes)
        if (newNo == oldNo || st == null) return null
        Ops.log("op", "name", "renumber", "ok", true)
        r.note?.let { say(it) }
        val wasCaptured = st.captured
        viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                try {
                    folders.renumberPrivate(classId, oldNo, newNo)
                } catch (e: Throwable) {
                    Ops.error("renumber_private", e)
                }
            }
            if (wasCaptured) withContext(Dispatchers.Main) { syncFileNames(classId) }
        }
        return null
    }

    /** [alsoFiles] true ise kayıt klasöründeki ilişkili dosyalar (çıktı, Orijinaller) da silinir. */
    fun deleteStudent(classId: String, no: String, alsoFiles: Boolean = false) {
        val cls = classById(classId)
        val st = cls?.students?.firstOrNull { it.no == no }
        val root = settings.rootUri?.let { Uri.parse(it) }
        updateClass(classId) { c -> c.copy(students = c.students.filter { it.no != no }) }
        if (cls != null && st != null) {
            viewModelScope.launch(Dispatchers.IO) { folders.deleteStudent(classId, cls.name, st, if (alsoFiles) root else null) }
        }
        say(if (alsoFiles) "Öğrenci ve kayıt klasöründeki dosyaları silindi" else "Öğrenci listeden çıkarıldı (kayıt klasöründeki dosyalar duruyor)")
    }

    /** Öğrencinin fotoğrafını (kayıt klasöründeki dosyalar dahil) siler; öğrenci listede kalır. */
    fun clearPhoto(classId: String, no: String) {
        val cls = classById(classId) ?: return
        val st = cls.students.firstOrNull { it.no == no } ?: return
        val root = settings.rootUri?.let { Uri.parse(it) }
        updateStudent(classId, no) {
            it.copy(captured = false, box = null, origUri = null, savedStem = null, faceCount = 0, updatedAt = 0L, retake = false, score = -1)
        }
        viewModelScope.launch(Dispatchers.IO) { folders.deletePhoto(classId, cls.name, st, root) }
        say("Fotoğraf silindi")
    }

    // ------------------------------------------------------------------ çekim akışı
    fun startSerial(classId: String) {
        val cls = classById(classId) ?: return
        val next = nextUncaptured(cls, null)
        if (next == null) {
            say("Tüm öğrencilerin fotoğrafı çekilmiş")
            return
        }
        openCamera(classId, next)
    }

    fun openCamera(classId: String, no: String) {
        if (settings.rootUri == null) {
            say("Önce kayıt klasörünü seçin")
            return
        }
        navigate(Screen.Camera(classId, no))
    }

    /** Kamerada önceki / sonraki öğrenciye geç. */
    fun cameraMove(classId: String, no: String, delta: Int) {
        val list = classById(classId)?.students ?: return
        val i = list.indexOfFirst { it.no == no }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        replace(Screen.Camera(classId, list[j].no))
    }

    fun editSavedCrop(classId: String, no: String) {
        val cls = classById(classId) ?: return
        val st = cls.students.firstOrNull { it.no == no } ?: return
        val rootStr = settings.rootUri
        if (rootStr == null) {
            say("Önce kayıt klasörünü seçin")
            return
        }
        viewModelScope.launch {
            // Kayıtlı orijinal gerçekten var mı? Taşınmış/yeniden adlandırılmışsa klasörde numaraya göre ara.
            val found: Uri? = withContext(Dispatchers.IO) {
                val u = st.origUri?.let { Uri.parse(it) }
                if (u != null && Output.exists(ctx, u)) u
                else Output.findFile(
                    ctx, Uri.parse(rootStr),
                    listOf(RosterParser.sanitizeFileName(cls.name), ORIGINALS_DIR),
                    st.savedStem ?: RosterParser.studentStem(st.no, st.name), st.no + "-"
                )
            }
            if (found == null) {
                if (st.origUri != null) updateStudent(classId, no) { it.copy(origUri = null) }
                say("Orijinal fotoğraf bulunamadı (silinmiş veya taşınmış olabilir). Fotoğrafı yeniden çekin.")
                return@launch
            }
            if (found.toString() != st.origUri) updateStudent(classId, no) { it.copy(origUri = found.toString()) }
            onPhotoReady(classId, no, found, null, true, st.box, directEdit = true, savedBoxW = st.boxW, savedBoxH = st.boxH)
        }
    }

    /** Tam çözünürlüklü çalışma görüntüsü + yüzler (inceleme için yalnızca piksel dizisi tutulur). */
    private class Decoded(val w: Int, val h: Int, val px: IntArray, val faces: List<FaceInfo>)

    private var prefetch: Pair<File, kotlinx.coroutines.Deferred<Decoded>>? = null

    private suspend fun decodeFull(uri: Uri): Decoded {
        val bmp = ImageOps.decode(ctx, uri)
        val px = ImageOps.pixels(bmp)
        val faces = ImageOps.detectFaces(bmp, px)
        val w = bmp.width
        val h = bmp.height
        bmp.recycle()   // inceleme için yalnızca piksel dizisi gerekir (≈17 MB tasarruf); düzenleyici açılınca yeniden üretilir
        return Decoded(w, h, px, faces)
    }

    /** Çekilen kareyi arka planda önceden çözer; en fazla bir iş bekler (öncekiler iptal edilir). */
    fun prefetchPhoto(file: File) {
        dropPrefetch()
        prefetch = file to viewModelScope.async(Dispatchers.Default) { decodeFull(Uri.fromFile(file)) }
    }

    fun dropPrefetch() {
        prefetch?.second?.cancel()
        prefetch = null
    }

    fun onPhotoReady(
        classId: String, no: String, source: Uri, temp: File?,
        fromSaved: Boolean = false, savedBox: Box? = null, directEdit: Boolean = false,
        savedBoxW: Int = 0, savedBoxH: Int = 0
    ) {
        captureJob?.cancel()
        capture?.temp?.delete()
        val pf = prefetch
        prefetch = null
        val tReady = android.os.SystemClock.elapsedRealtime()
        val pfState = if (pf == null || temp == null) "none" else if (pf.first == temp && !pf.second.isCancelled) "hit" else "miss"
        val cap = CaptureState(classId, no, source, temp, fromSaved)
        cap.directEdit = directEdit
        capture = cap
        if (screen != Screen.Review) navigate(Screen.Review)
        val set = settings
        captureJob = viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.Default) {
                    // Kamera, kare kaydedilir kaydedilmez çözmeyi önden başlatmış olabilir (prefetchPhoto): hazırsa kullan.
                    val dec: Decoded = if (pf != null && temp != null && pf.first == temp && !pf.second.isCancelled) {
                        try {
                            pf.second.await()
                        } catch (e: CancellationException) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()   // kendi iptalimizse çık
                            decodeFull(source)
                        } catch (e: Throwable) {
                            decodeFull(source)
                        }
                    } else {
                        pf?.second?.cancel()
                        decodeFull(source)
                    }
                    Triple(Pair(dec.w, dec.h), dec.px, dec.faces)
                }
                val dims = r.first
                val px = r.second
                val faces = r.third
                val s = EditSession(no)
                s.pixels = px
                s.iw = dims.first
                s.ih = dims.second
                // "Kırpmayı düzenle": yüz bulunursa kutu, güncel varsayılan kırpma ayarlarına göre yüzün merkezine (göz ortası)
                // hizalı yeniden hesaplanır; böylece her fotoğraf aynı ölçüt ve konumla açılır. Yüz yoksa kayıtlı kutu kullanılır.
                val autoBox = directEdit && faces.isNotEmpty()
                val box = if (savedBox != null && !autoBox) {
                    // Kayıtlı kutu çekim anındaki görüntü boyutundadır; açılan orijinal küçültülmüş olabilir → ölçekle
                    Box.of(Crop.scaleBox(savedBox.toArray(), savedBoxW, savedBoxH, dims.first, dims.second))
                        .fitInside(dims.first, dims.second)
                } else {
                    ImageOps.defaultBox(dims.first, dims.second, faces.firstOrNull(), set)
                }
                s.box = box
                s.startBox = box
                s.faces = faces
                s.faceFound = faces.isNotEmpty()
                s.loading = false
                cap.session = s
                cap.loading = false
                cap.shownAt = android.os.SystemClock.elapsedRealtime()
                Ops.log(
                    "review_ready", "id", Ops.id(no), "ms", cap.shownAt - tReady, "prefetch", pfState,
                    "w", dims.first, "h", dims.second, "faces", faces.size, "saved", fromSaved, "autoBox", autoBox
                )
                if (directEdit && screen == Screen.Review) openCaptureEditor()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Ops.error("review", e)
                cap.error = (e.message ?: e.javaClass.simpleName) +
                    if (e is OutOfMemoryError) " · " + Diag.memInfo() else ""
                cap.loading = false
            }
        }
    }

    fun discardCapture() {
        capture?.let { Ops.log("discard", "id", Ops.id(it.no)) }
        captureJob?.cancel()
        capture?.temp?.delete()
        capture = null
        if (screen == Screen.Review) back()
    }

    fun openCaptureEditor() {
        val s = capture?.session ?: return
        s.startBox = s.box
        if (s.bitmap == null) {
            val px = s.pixels
            if (px != null) {
                try {
                    s.bitmap = android.graphics.Bitmap.createBitmap(px, s.iw, s.ih, android.graphics.Bitmap.Config.ARGB_8888)
                } catch (e: OutOfMemoryError) {
                    say("Düzenleyici için bellek yetersiz: " + Diag.memInfo())
                    return
                }
            }
        }
        navigate(Screen.CaptureEditor)
    }

    fun cancelCaptureEditor() {
        capture?.session?.let { it.box = it.startBox }
        if (screen == Screen.CaptureEditor) back()
        capture?.session?.bitmap = null   // düzenleyiciden çıkınca büyük bitmap serbest kalır
        if (capture?.directEdit == true) discardCapture()   // doğrudan düzenlemede iptal = hiçbir şey değişmez
    }

    fun applyCaptureEditor() {
        if (screen == Screen.CaptureEditor) back()
        capture?.session?.bitmap = null
        if (capture?.directEdit == true) saveCapture()      // doğrudan düzenlemede Uygula = hemen kaydet
    }

    fun captureEditorPickFace(index: Int) {
        val s = capture?.session ?: return
        val f = s.faces.getOrNull(index) ?: return
        s.faceFound = true
        s.moveBox(ImageOps.defaultBox(s.iw, s.ih, f, settings))
    }

    fun captureEditorResetToFace() {
        val s = capture?.session ?: return
        val f = s.faces.firstOrNull()
        s.moveBox(ImageOps.defaultBox(s.iw, s.ih, f, settings))
    }

    // ------------------------------------------------------------------ araçlar (dışa aktarma, yedek, adlar)
    private fun stamp(): String =
        java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())

    /**
     * Tüm sınıfları (veya [classId] verilirse yalnızca onu) fotoğraf listesi PDF'i olarak kayıt klasörü/PDF içine yazar.
     * Düzen: sınıf başlığı, numara sırasıyla 7'li satırlar, fotoğraf altında numara ve ad; her sınıf yeni sayfada başlar.
     */
    fun exportClassesPdf(classId: String? = null) {
        val rootStr = settings.rootUri
        if (rootStr == null) {
            say("Önce kayıt klasörünü seçin")
            return
        }
        val root = Uri.parse(rootStr)
        val snap = classes.toList().filter { classId == null || it.id == classId }
        if (snap.isEmpty() || snap.all { it.students.isEmpty() }) {
            say("PDF için öğrenci yok")
            return
        }
        viewModelScope.launch {
            try {
                val name = com.eokul.studio.core.BackupNames.name("Foto", java.util.Date(), java.util.TimeZone.getDefault(), "pdf")
                val ratio = TARGET_H.toDouble() / TARGET_W
                val pages = withContext(Dispatchers.IO) {
                    var n = 0
                    Output.writeStream(ctx, root, listOf("PDF"), name, "application/pdf") { out ->
                        n = ClassPdf.write(ctx, snap, out, ratio)
                    }
                    n
                }
                Ops.log("op", "name", "pdf", "ok", true, "classes", snap.size, "pages", pages)
                say("✓ PDF hazır: PDF/$name\n${snap.size} sınıf · $pages sayfa")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Ops.log("op", "name", "pdf", "ok", false, "err", (e.message ?: e.javaClass.simpleName).take(200))
                say("PDF oluşturulamadı: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Çekilmiş öğrencilerin fotoğraflarını `12345.jpg` adlarıyla tek bir ZIP'e yazar (e-Okul yüklemesi için). */
    fun exportEokulZip(classId: String) {
        val cls = classById(classId) ?: return
        exportZip(listOf(cls), ExportService.eokulZipName(cls.name), "${cls.name}")
    }

    /**
     * Tüm sınıflar tek ZIP'te, kaydedilen düzenle aynı klasör yapısıyla: `6A/12345-Ad Soyad.jpg`, ayrıca `rapor.csv`
     * (sınıf, numara, ad, durum, kalite puanı). Arşivleme / başka sisteme taşıma ve kontrol için.
     */
    fun exportAllFoldersZip() {
        exportZip(classes.toList(), "Tum-siniflar-klasorlu-${stamp()}.zip", "sınıf klasörleriyle", folders = true)
    }

    private fun exportZip(snap: List<SchoolClass>, zipName: String, label: String, folders: Boolean = false) {
        val rootStr = settings.rootUri
        if (rootStr == null) {
            say("Önce kayıt klasörünü seçin")
            return
        }
        val root = Uri.parse(rootStr)
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    val plan = ExportService.plan(snap, folders) { cls, st -> Naming.privatePhoto(ctx, cls.id, st.no) }
                    if (plan.items.isNotEmpty()) {
                        Output.writeStream(ctx, root, emptyList(), zipName, "application/zip") { out ->
                            ExportService.writeZip(out, plan, snap, withReport = folders)
                        }
                    }
                    plan.items.size to plan.duplicates
                }
                val (n, dups) = res
                say(
                    if (n == 0) "Dışa aktarılacak fotoğraf yok"
                    else "✓ $n fotoğraf → $zipName ($label)" +
                        if (dups.isNotEmpty()) "\n⚠ Aynı numara tekrar ettiği için atlandı: ${dups.take(3).joinToString(", ")}" +
                            if (dups.size > 3) " …(${dups.size})" else "" else ""
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                say("ZIP oluşturulamadı: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Uygulamanın kayıt klasörüne yazdığı dosyalar tek bir önekle ayırt edilir (başka programların yedekleriyle karışmasın):
     * Ydk_160726_0816.zip. Eski "Yedek-….zip" dosyaları geri yüklenebilir (içerik denetimi addan bağımsızdır).
     */
    private fun artifactName(prefix: String, ext: String): String =
        com.eokul.studio.core.BackupNames.name(prefix, java.util.Date(), java.util.TimeZone.getDefault(), ext)

    /** Yedek/geri yükleme servisi (Android'den bağımsız mantık; bkz. BackupService). */
    private val backups by lazy {
        BackupService(
            filesDir = ctx.filesDir,
            store = store,
            storeMutex = storeMutex,
            appVersion = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" },
            isImage = { f ->
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(f.path, o)
                o.outWidth > 0 && o.outHeight > 0
            }
        )
    }

    private fun backupStorage(root: Uri): BackupStorage = SafBackupStorage(ctx, root, BACKUP_DIR)

    /** Eski sürümlerin güvenlik yedekleri kayıt klasörünün kökünde olabilir; temizlikte o da taranır. */
    private suspend fun pruneSafetyBackups(root: Uri) = withContext(Dispatchers.IO) {
        try {
            val deleted = backups.pruneSafetyBackups(
                listOf(backupStorage(root), SafBackupStorage(ctx, root, emptyList())), KEEP_SAFETY_BACKUPS
            )
            if (deleted > 0) Ops.log("op", "name", "prune_safety", "ok", true, "deleted", deleted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Ops.error("prune_safety", e)
        }
    }

    /** Yedeği geri yüklemeden önce içeriğini okur; geçerli bir yedek değilse null (mevcut veriye dokunulmaz). */
    suspend fun inspectBackup(uri: Uri): BackupInfo? = backups.inspect { ctx.contentResolver.openInputStream(uri) }

    /** Tanılama raporu için önerilen dosya adı. */
    fun reportFileName(): String = artifactName(com.eokul.studio.core.BackupNames.REPORT_PREFIX, "txt")

    /** Tanılama raporunu kullanıcının seçtiği konuma (Dosya seçici: İndirilenler vb.) yazar; öğrenci adı içermez. */
    fun saveReportTo(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val text = Ops.reportText()
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("Dosya açılamadı")
                }
                say("✓ Rapor kaydedildi. Dosyayı geliştiriciye iletin; öğrenci adı içermez.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Ops.error("report", e)
                say("Rapor yazılamadı: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Rapor metni (paylaşma için); en fazla ~120 bin karakter. */
    suspend fun reportTextForShare(): String = withContext(Dispatchers.IO) { Ops.reportText().take(120_000) }

    fun clearOpsLog() {
        Ops.clear()
        say("İşlem kaydı temizlendi")
    }

    /** Sınıf listesi + küçük fotoğraflar: kayıt klasörü/Yedek/Ydk_ggaayy_ssdd.zip (akış tabanlı, doğrulamalı). */
    fun backup() {
        val rootStr = settings.rootUri
        if (rootStr == null) {
            say("Önce kayıt klasörünü seçin")
            return
        }
        val root = Uri.parse(rootStr)
        viewModelScope.launch {
            try {
                saveMutex.withLock {
                val tb = android.os.SystemClock.elapsedRealtime()
                val snap = stableRosterSnapshot()
                val name = backups.write(backupStorage(root), snap, settings.backupPrefix)
                Ops.log(
                    "op", "name", "backup", "ok", true, "ms", android.os.SystemClock.elapsedRealtime() - tb, "n", name.photos,
                    "captured", name.captured, "retake", snap.sumOf { c -> c.students.count { it.retake } },
                    "boxes", snap.sumOf { c -> c.students.count { it.box != null } }
                )
                SettingsState.update(settings.copy(lastBackupMs = System.currentTimeMillis()))
                say("✓ Yedek alındı ve doğrulandı: ${name.name}\n${name.photos} fotoğraf · ${name.captured} çekilmiş öğrenci · ${snap.sumOf { c -> c.students.count { it.box != null } }} kırpma kaydı · ${snap.sumOf { c -> c.students.count { it.retake } }} tekrar işaretli.\n" +
                    "Dosya kayıt klasörünüzdeki \"Yedek\" klasöründe durur; uygulamayı silseniz de kalır. Tam çözünürlüklü orijinaller yedeğe girmez, kayıt klasöründe kalır.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Ops.log("op", "name", "backup", "ok", false, "err", (e.message ?: e.javaClass.simpleName).take(200))
                say("Yedek alınamadı: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Yedek ZIP'inden sınıfları ve küçük fotoğrafları geçici alana çıkarır.
     * Öğrenci listesi ve fotoğraf klasörü işlem günlüğüyle birlikte devreye alınır.
     */
    fun restore(uri: Uri) {
        if (pendingCaptureOriginals.isNotEmpty()) {
            say("Fotoğraf kaydı devam ediyor. Kayıt tamamlandıktan sonra geri yüklemeyi yeniden başlatın.")
            return
        }
        if (recoveryBlocked) {
            say("Önceki kurtarma işlemi tamamlanmadığı için geri yükleme başlatılamıyor. Kurtarma dosyalarını koruyun.")
            return
        }
        if (isRestoring) return
        isRestoring = true
        // Başlamış eski kayıt görevlerinin geri yüklenen listeyi sonradan ezmesini engelle.
        rosterVersion.incrementAndGet()
        viewModelScope.launch {
            try {
                saveMutex.withLock {
                var safety: String? = null
                if (classes.isNotEmpty()) {
                    val rootStr = settings.rootUri ?: throw IOException(
                        "Önce kayıt klasörünü seçin (geri yükleme öncesinde mevcut verinin güvenlik yedeği alınır)"
                    )
                    safety = backups.write(
                        backupStorage(Uri.parse(rootStr)), classes.toList(), com.eokul.studio.core.BackupNames.SAFETY_PREFIX
                    ).name
                }

                val restored = backups.restore { ctx.contentResolver.openInputStream(uri) }

                classes.clear()
                classes.addAll(restored.classes)
                rosterVersion.incrementAndGet()
                Ops.log(
                    "op", "name", "restore", "ok", true,
                    "captured", restored.classes.sumOf { c -> c.students.count { it.captured } },
                    "retake", restored.classes.sumOf { c -> c.students.count { it.retake } },
                    "boxes", restored.classes.sumOf { c -> c.students.count { it.box != null } }
                )
                if (safety != null) settings.rootUri?.let { pruneSafetyBackups(Uri.parse(it)) }
                say(
                    "✓ ${restored.classes.size} sınıf geri yüklendi · ${restored.classes.sumOf { c -> c.students.count { it.captured } }} çekilmiş öğrenci · " +
                        "${restored.classes.sumOf { c -> c.students.count { it.box != null } }} kırpma kaydı · " +
                        "${restored.classes.sumOf { c -> c.students.count { it.retake } }} tekrar işaretli" +
                        (if (restored.skippedPhotos > 0) " (${restored.skippedPhotos} bozuk fotoğraf atlandı)" else "") +
                        (if (safety != null) "\nÖnceki verinizin güvenlik kopyası: $safety" else "")
                )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (File(ctx.filesDir, "restore.transaction").exists()) recoveryBlocked = true
                Ops.error("restore", e)
                say("Geri yüklenemedi: ${e.message ?: e.javaClass.simpleName}. Mevcut verinin korunması için işlem geri alındı.")
            } finally {
                isRestoring = false
            }
        }
    }

    // ------------------------------------------------------------------ veri tutarlılığı denetimi
    var auditReport by mutableStateOf<String?>(null)

    fun dismissAudit() {
        auditReport = null
    }

    /** Öğrenci kayıtları ile uygulama içi fotoğraf dosyaları arasındaki tutarsızlıkları raporlar (hiçbir şeyi değiştirmez). */
    fun auditData() {
        val snap = classes.toList()
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                val local = DataAudit.local(
                    snap,
                    photoState = { c, st ->
                        val f = Naming.privatePhoto(ctx, c.id, st.no)
                        if (!f.exists()) DataAudit.Photo.MISSING
                        else {
                            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeFile(f.path, o)
                            if (o.outWidth != TARGET_W || o.outHeight != TARGET_H) DataAudit.Photo.BAD_SIZE else DataAudit.Photo.OK
                        }
                    },
                    expectedName = { c, st -> Naming.privatePhoto(ctx, c.id, st.no).name },
                    privateFiles = { c -> Naming.privateDir(ctx, c.id).listFiles()?.map { it.name } ?: emptyList() }
                )
                val folder = try { folderAudit(snap) } catch (e: Throwable) { "Kayıt klasörü okunamadı: ${e.message ?: e.javaClass.simpleName}" }
                DataAudit.report(local, folder)
            }
            auditReport = report
        }
    }

    /**
     * Kayıt klasörü kontrolü (yalnızca okur): her sınıf için klasör var mı, çekilmiş öğrencilerin fotoğrafı ve orijinali klasörde mi,
     * yarım kalmış gizli (.tmp-/.old-) dosya var mı. Dosyalar beklenen yerde değilse kök klasördeki adlar gösterilir.
     */
    private fun folderAudit(snap: List<SchoolClass>): String {
        val rootStr = settings.rootUri ?: return "⚠ Kayıt klasörü seçilmemiş."
        val root = Uri.parse(rootStr)
        return DataAudit.folderLines(snap, rootName, ORIGINALS_DIR) { path -> Output.listNames(ctx, root, path) }
    }

    /**
     * Kayıt klasöründe eksik olan fotoğrafları uygulama içindeki 133x171 kopyadan yeniden yazar (orijinaller geri getirilemez).
     * Önceki bir hata nedeniyle yazıldıktan hemen sonra silinen dosyalar için.
     */
    fun repairFolderFiles() {
        val rootStr = settings.rootUri ?: run { say("Önce kayıt klasörünü seçin"); return }
        val root = Uri.parse(rootStr)
        val snap = classes.toList()
        viewModelScope.launch {
            val (fixed, lost) = withContext(Dispatchers.IO) {
                saveMutex.withLock {
                    var fixed = 0
                    var lost = 0
                    for (c in snap) {
                        val dirName = RosterParser.sanitizeFileName(c.name)
                        val have = (Output.listNames(ctx, root, listOf(dirName)) ?: emptyList()).toHashSet()
                        for ((s, stem) in DataAudit.missingInFolder(c, have)) {
                            val priv = Naming.privatePhoto(ctx, c.id, s.no)
                            if (!priv.exists() || priv.length() <= 0L) { lost++; continue }
                            try {
                                Output.writeStream(ctx, root, listOf(dirName), "$stem.jpg", "image/jpeg") { out ->
                                    priv.inputStream().use { it.copyTo(out) }
                                }
                                fixed++
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                Ops.error("repair", e)
                                lost++
                            }
                        }
                    }
                    fixed to lost
                }
            }
            Ops.log("op", "name", "repair", "ok", lost == 0, "n", fixed, "err", if (lost > 0) "lost=$lost" else null)
            say(
                if (fixed == 0 && lost == 0) "Kayıt klasöründe eksik fotoğraf yok."
                else "✓ $fixed fotoğraf kayıt klasörüne yeniden yazıldı." + (if (lost > 0) " $lost öğrenci için kopya bulunamadı; yeniden çekin." else "") +
                    " Orijinaller geri getirilemez; “Kırpmayı düzenle” için yeniden çekin."
            )
        }
    }

    /** Adı değişen öğrencilerin kayıt klasöründeki dosya adlarını güncel ad soyada göre yeniden adlandırır. */
    fun staleNameCount(classId: String): Int {
        val c = classById(classId) ?: return 0
        return c.students.count { it.captured && it.savedStem != null && it.savedStem != RosterParser.studentStem(it.no, it.name) }
    }

    fun syncFileNames(classId: String) {
        val cls = classById(classId) ?: return
        val rootStr = settings.rootUri ?: run {
            say("Önce kayıt klasörünü seçin")
            return
        }
        val root = Uri.parse(rootStr)
        viewModelScope.launch {
            var done = 0
            val results = withContext(Dispatchers.IO) { folders.syncNames(root, cls) }
            for ((no, stem, orig) in results) {
                updateStudent(classId, no) { it.copy(savedStem = stem, origUri = orig) }
                done++
            }
            say(if (done == 0) "Güncellenecek dosya yok" else "✓ $done dosya yeniden adlandırıldı")
        }
    }

    // ------------------------------------------------------------------ kaydetme
        private val folders by lazy { FolderService(ctx) }
    private val photoSaver by lazy { PhotoSaver(ctx) }

    fun saveCapture() {
        if (isRestoring) {
            say("Geri yükleme sürerken fotoğraf kaydedilemez")
            return
        }
        val cap = capture ?: return
        val s = cap.session ?: return
        if (cap.saving) return
        val px = s.pixels ?: return
        val rootStr = settings.rootUri
        if (rootStr == null) {
            say("Önce kayıt klasörünü seçin")
            return
        }
        val root = Uri.parse(rootStr)
        val cls = classById(cap.classId) ?: return
        val st = cls.students.firstOrNull { it.no == cap.no } ?: return
        val set = settings
        val box = s.box.fitInside(s.iw, s.ih)
        val iw = s.iw
        val ih = s.ih
        val faceCount = s.faces.size
        val warned = cap.warned
        val newScore = cap.score
        cap.saving = true
        val oid = Ops.id(cap.no)
        Ops.log(
            "save_start", "id", oid, "score", newScore, "hard", cap.hardCodes, "soft", cap.softCodes, "auto", cap.auto,
            "reviewMs", if (cap.shownAt > 0) android.os.SystemClock.elapsedRealtime() - cap.shownAt else -1, "retake", warned
        )

        // Hız: öğrenci hemen "çekildi" sayılıp sıradakine geçilir; dosyalar arka planda (sırayla) yazılır.
        // Yazma başarısız olursa öğrenci eski durumuna döner ve kullanıcı açıkça uyarılır.
        val before = st
        pendingCaptureOriginals[captureKey(cap.classId, cap.no)] = before
        updateStudentTransient(cap.classId, cap.no) { student ->
            CaptureRules.optimistic(student, box, iw, ih, faceCount, warned, newScore, System.currentTimeMillis())
        }
        capture = null
        afterSave(cap.classId, cap.no, set)

        viewModelScope.launch {
            saveMutex.withLock {
                val tSave = android.os.SystemClock.elapsedRealtime()
                try {
                    val saved = withContext(Dispatchers.IO) { photoSaver.save(root, cls, st, cap.source, cap.fromSaved, px, iw, ih, box, set) }
                    Ops.log("save", "id", oid, "ok", true, "ms", android.os.SystemClock.elapsedRealtime() - tSave, "warn", saved.warnings.size)
                    pendingCaptureOriginals.remove(captureKey(cap.classId, cap.no))
                    updateStudent(cap.classId, cap.no) {
                        // updatedAt yenilenir: küçük resim dosya yazıldıktan sonra yeniden okunur
                        CaptureRules.saved(it, saved.origUri, saved.stem, System.currentTimeMillis())
                    }
                    cap.temp?.delete()
                    if (saved.warnings.isNotEmpty()) say("⚠ ${st.name}: fotoğraf kaydedildi ancak " + saved.warnings.joinToString("; "))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Ops.log(
                        "save", "id", oid, "ok", false, "ms", android.os.SystemClock.elapsedRealtime() - tSave,
                        "err", (e.message ?: e.javaClass.simpleName).take(200)
                    )
                    pendingCaptureOriginals.remove(captureKey(cap.classId, cap.no))
                    // Yalnızca çekimle değişen alanlar geri alınır; bu arada yapılan ad/numara düzenlemesi korunur.
                    updateStudent(cap.classId, cap.no) { CaptureRules.rollback(it, before) }
                    say("⚠ ${st.name} (${st.no}) kaydedilemedi: ${e.message ?: e.javaClass.simpleName}. Bu öğrenciyi yeniden çekin.")
                }
            }
        }
    }

    private fun afterSave(classId: String, no: String, set: Settings) {
        if (screen == Screen.Review) back()
        val top = screen
        if (top is Screen.Camera) {
            val cls = classById(classId)
            val next = if (set.serialMode && cls != null) nextUncaptured(cls, no) else null
            if (next != null) {
                replace(Screen.Camera(classId, next))
            } else {
                back()
                say(if (set.serialMode) SaveRules.classSummary(cls) else "✓ Kaydedildi")
            }
        } else {
            say("✓ Kaydedildi")
        }
    }

}

/** Kayıt klasöründe tutulan en yeni "geri yükleme öncesi" güvenlik yedeği sayısı; eskileri otomatik silinir. */
private const val KEEP_SAFETY_BACKUPS = 2

/** Yedek dosyalarının durduğu alt klasör (kayıt klasörünün içinde). */
private val BACKUP_DIR = listOf(com.eokul.studio.core.BackupNames.FOLDER)


