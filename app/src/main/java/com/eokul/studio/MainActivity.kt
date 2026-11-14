package com.eokul.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val studio: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kenardan kenara görünüm (Android 15+ / targetSdk 35 için zorunlu); boşluklar AppRoot'ta ayarlanır.
        enableEdgeToEdge()
        Diag.install(this)
        Ops.init(this)
        Ops.appStart(studio.settings)
        Diag.report(this)?.let { r ->
            studio.addStartupNote("Önceki çalışmada sorun kaydedildi (bu metni geliştiriciye iletin):\n" + r)
        }
        setContent {
            // PIN açıkken son uygulamalar ekranında ve ekran görüntülerinde öğrenci bilgileri görünmesin.
            val secure = studio.settings.pinHash != null
            androidx.compose.runtime.SideEffect {
                if (secure) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
            EOkulTheme {
                AppRoot(vm, studio)
            }
        }
    }

    override fun onStop() {
        vm.persistNow()
        studio.onBackgrounded()
        // Öğrenci listesi için son snapshot yazımı bitmeden Activity yaşam döngüsü devam eder.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                studio.persistAndWait()
            } catch (e: Exception) {
                Ops.error("activity_stop_persist", e)
            }
        }
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        studio.onForegrounded()
    }
}

/**
 * Tasarım belirteçleri (açık/koyu). Vurgu rengi tek: mavi; yeşil/amber/kırmızı yalnızca durum bildirir.
 * Metin/zemin çiftleri 4,5:1 ve üzeri hedeflenir. Koyu temada saf siyah yerine lacivert zemin kullanılır.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F5FC4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE7FB),
    onPrimaryContainer = Color(0xFF0F2A66),
    secondary = Color(0xFF566277),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4EAF5),
    onSecondaryContainer = Color(0xFF0F1B2D),
    tertiary = Color(0xFF92400E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCEBCB),
    onTertiaryContainer = Color(0xFF5A2A06),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF0F1B2D),
    surface = Color(0xFFF4F7FB),
    onSurface = Color(0xFF0F1B2D),
    surfaceVariant = Color(0xFFE7EDF7),
    onSurfaceVariant = Color(0xFF566277),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEF2F9),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFF7C889C),
    outlineVariant = Color(0xFFD5DDEA),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE0E0),
    onErrorContainer = Color(0xFF5F1111)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8CAEFF),
    onPrimary = Color(0xFF0B1E4A),
    primaryContainer = Color(0xFF22345C),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFFA5B0C5),
    onSecondary = Color(0xFF14203A),
    secondaryContainer = Color(0xFF1F2A41),
    onSecondaryContainer = Color(0xFFE8EDF7),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF3A2E12),
    onTertiaryContainer = Color(0xFFFFE2A8),
    background = Color(0xFF0E1524),
    onBackground = Color(0xFFE8EDF7),
    surface = Color(0xFF0E1524),
    onSurface = Color(0xFFE8EDF7),
    surfaceVariant = Color(0xFF1F2A41),
    onSurfaceVariant = Color(0xFFA5B0C5),
    surfaceContainerLowest = Color(0xFF0B101C),
    surfaceContainerLow = Color(0xFF131C2E),
    surfaceContainer = Color(0xFF172033),
    surfaceContainerHigh = Color(0xFF1C2740),
    surfaceContainerHighest = Color(0xFF172033),
    outline = Color(0xFF7D8AA3),
    outlineVariant = Color(0xFF2B3852),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF3E1D22),
    onErrorContainer = Color(0xFFFFD9D9)
)

/** Yumuşak köşeler: etiket 8, alan 12, kart 16, büyük yüzey 20 dp. Gölge yok; kartlar ince çerçeveyle ayrılır. */
private val AppShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

/** Tipografi hiyerarşisi: sayı/başlık kalın, gövde 15, ikincil 13, etiket 12 sp (12 sp altına inilmez). */
private val AppTypography = androidx.compose.material3.Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = t.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = t.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontSize = 12.sp)
    )
}

@Composable
fun EOkulTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}

@Composable
fun AppRoot(vm: AppViewModel, studio: StudioViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }
    LaunchedEffect(Unit) {
        studio.messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    // Ana geri tuşu: sınıf akışında yığını geri sarar. Toplu kırpma içindeki düzenleyici / ayarlar kendi
    // BackHandler'larını (daha derinde oldukları için öncelikli) kullanır.
    BackHandler(enabled = studio.stack.size > 1) { studio.handleBack() }

    // Koyu zeminli ekranlar (kamera, inceleme, düzenleyici) sistem çubuklarının arkasında da koyu kalır.
    val sc0 = studio.screen
    val darkScreen = !studio.locked && (
        sc0 is Screen.Camera || sc0 is Screen.Review || sc0 is Screen.CaptureEditor ||
            (sc0 is Screen.Batch && vm.route == Route.Editor)
        )
    val rootColor = when {
        sc0 is Screen.Camera -> Color.Black
        darkScreen -> Color(0xFF101014)
        else -> MaterialTheme.colorScheme.background
    }
    val sysDark = isSystemInDarkTheme()
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val w = (view.context as? android.app.Activity)?.window
        if (w != null) {
            val c = androidx.core.view.WindowCompat.getInsetsController(w, view)
            val lightBars = !darkScreen && !sysDark
            c.isAppearanceLightStatusBars = lightBars
            c.isAppearanceLightNavigationBars = lightBars
        }
    }
    // Ana, sınıf ve liste ekleme ekranlarında üst başlık durum çubuğunun arkasına uzanır (kendi içinde boşluk bırakır); diğerlerinde tüm güvenli alan.
    val insets = if ((sc0 is Screen.Home || sc0 is Screen.ClassDetail || sc0 is Screen.Import || sc0 is Screen.MultiImport) && !studio.locked) {
        WindowInsets.safeDrawing.only(
            androidx.compose.foundation.layout.WindowInsetsSides.Horizontal + androidx.compose.foundation.layout.WindowInsetsSides.Bottom
        )
    } else {
        WindowInsets.safeDrawing
    }

    Surface(modifier = Modifier.fillMaxSize(), color = rootColor) {
        if (studio.locked) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                LockScreen(studio)
            }
            return@Surface
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
            when (val sc = studio.screen) {
                is Screen.Home -> HomeScreen(studio)
                is Screen.ClassDetail -> ClassScreen(studio, sc.classId)
                is Screen.Import -> ImportScreen(studio, sc.classId)
                is Screen.MultiImport -> MultiImportScreen(studio)
                is Screen.Sheet -> SheetScreen(studio, sc.classId)
                is Screen.Camera -> CameraScreen(studio, sc.classId, sc.no)
                is Screen.Review -> ReviewScreen(studio)
                is Screen.CaptureEditor -> CaptureEditorScreen(studio)
                is Screen.Settings -> SettingsScreen(vm, studio, showRecrop = false, onClose = { studio.back() })
                is Screen.Batch -> when (vm.route) {
                    Route.Gallery -> GalleryScreen(vm, onBack = { studio.back() })
                    Route.Editor -> EditorScreen(vm)
                    Route.Settings -> SettingsScreen(vm, studio, showRecrop = true, onClose = { vm.closeSettings() })
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
            )
        }
    }

    val warning = vm.nameWarning
    if (warning != null) {
        val sample = warning.bad.take(5).joinToString(", ") + if (warning.bad.size > 5) " …" else ""
        AlertDialog(
            onDismissRequest = { vm.dismissNameWarning() },
            title = { Text("Dosya adı uyarısı") },
            text = {
                Text(
                    "${warning.bad.size} dosyanın adı e-Okul kuralına uymuyor:\n$sample\n\n" +
                        "Kural: ${vm.settings.nameRegex}\n\nYine de kaydedilsin mi?"
                )
            },
            confirmButton = { TextButton(onClick = { vm.confirmNameWarning() }) { Text("Yine de kaydet") } },
            dismissButton = { TextButton(onClick = { vm.dismissNameWarning() }) { Text("Vazgeç") } }
        )
    }

    if (vm.failures.isNotEmpty()) {
        val fails = vm.failures
        val detail = fails.take(8).joinToString("\n") { (n, e) -> "• $n: ${e.take(70)}" } +
            if (fails.size > 8) "\n… ve ${fails.size - 8} tane daha" else ""
        AlertDialog(
            onDismissRequest = { vm.dismissFailures() },
            title = { Text("Kaydetme hataları") },
            text = { Text(detail) },
            confirmButton = { TextButton(onClick = { vm.dismissFailures() }) { Text("Tamam") } }
        )
    }

    studio.startupNote?.let { note ->
        AlertDialog(
            onDismissRequest = { studio.dismissStartupNote() },
            title = { Text("Bilgi") },
            text = { Text(note) },
            confirmButton = { TextButton(onClick = { studio.dismissStartupNote() }) { Text("Tamam") } }
        )
    }

    if (vm.confirmResetAll) {
        AlertDialog(
            onDismissRequest = { vm.confirmResetAll = false },
            title = { Text("Tüm kırpmaları sıfırla") },
            text = { Text("Elle yapılan düzenlemeler silinecek ve kırpmalar yeniden otomatik hesaplanacak. Devam edilsin mi?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.confirmResetAll = false
                    vm.resetAllCrops()
                }) { Text("Sıfırla") }
            },
            dismissButton = { TextButton(onClick = { vm.confirmResetAll = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun CaptureEditorScreen(studio: StudioViewModel) {
    val cap = studio.capture
    val st = cap?.let { c -> studio.classById(c.classId)?.students?.firstOrNull { it.no == c.no } }
    EditorContent(
        s = cap?.session,
        title = if (cap == null) "" else "${cap.no} · ${st?.name ?: ""}",
        settings = studio.settings,
        onCancel = { studio.cancelCaptureEditor() },
        onApply = { studio.applyCaptureEditor() },
        onResetFace = { studio.captureEditorResetToFace() },
        onPickFace = { studio.captureEditorPickFace(it) },
        onPrev = null,
        onNext = null,
        applyLabel = if (cap?.directEdit == true) "Uygula ve kaydet" else "Uygula"
    )
}
