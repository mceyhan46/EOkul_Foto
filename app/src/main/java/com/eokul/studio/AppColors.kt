package com.eokul.studio

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * UYGULAMANIN TÜM RENKLERİ BU DOSYADADIR. Başka dosyada Color(0x...) yazılmaz; renk değiştirmek için yalnızca burayı düzenleyin.
 *
 * Gruplar:
 *  1) LightScheme / DarkScheme : Material rolleri (arka plan, yüzey, birincil, ikincil, hata ...). Tüm standart bileşenler bunları kullanır.
 *  2) SoftColors (açık/koyu)   : Ana ekranın pastel yüzeyleri (özet kutusu, "Sınıf ekle" düğmesi, soft düğmeler). softColors() ile alınır.
 *  3) StatusColors             : Açık zeminli ekranlarda durum renkleri (tamam yeşili, uyarı turuncusu, düzenlendi mavisi).
 *  4) DarkUi                   : Koyu zeminli ekranlar (kamera, inceleme, düzenleyici, liste sayfası) için sabit renkler.
 *  5) ButtonColors             : AppButton'un koyu varyantı ve tehlike rengi.
 */

/**
 * Tasarım belirteçleri (açık/koyu). Vurgu rengi tek: mavi; yeşil/amber/kırmızı yalnızca durum bildirir.
 * Metin/zemin çiftleri 4,5:1 ve üzeri hedeflenir. Koyu temada saf siyah yerine lacivert zemin kullanılır.
 */
internal val LightScheme = lightColorScheme(
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

internal val DarkScheme = darkColorScheme(
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


/** 2) Pastel yüzeyler. Göz yormayan, aynı mavi ailesinden; metinleri koyu lacivert/açık mavi (4,5:1 üzeri). */
@Immutable
class SoftColors(
    /** Özet kutusu ve "Sınıf ekle" düğmesinin dolgusu. */
    val container: Color,
    /** Dolgu üzerindeki metin. */
    val onContainer: Color,
    /** Dolgu üzerindeki ikincil metin. */
    val onContainerMuted: Color,
    /** İlerleme çubuğu rengi ve boş kısmı (özet kutusunda). */
    val bar: Color,
    val barTrack: Color,
    /** Özet kutusunda "yedek önerilir" uyarı metni. */
    val warnText: Color,
    /** Menüdeki grup başlığı şeridinin dolgusu ve yazısı (başlık bloğundan bir ton açık). */
    val group: Color,
    val onGroup: Color
)

val LightSoft = SoftColors(
    container = Color(0xFFD3E2FA),
    onContainer = Color(0xFF0F2A66),
    onContainerMuted = Color(0xFF3A4F80),
    bar = Color(0xFF2F5FC4),
    barTrack = Color(0xFFF4F7FB),
    warnText = Color(0xFF8A3B0B),
    group = Color(0xFFE3ECFB),
    onGroup = Color(0xFF1B3A7A)
)

val DarkSoft = SoftColors(
    container = Color(0xFF2A3F6B),
    onContainer = Color(0xFFE3ECFF),
    onContainerMuted = Color(0xFFB4C4E6),
    bar = Color(0xFF8CAEFF),
    barTrack = Color(0xFF172033),
    warnText = Color(0xFFFFD27A),
    group = Color(0xFF222F4D),
    onGroup = Color(0xFFC9D8FA)
)

@Composable
fun softColors(): SoftColors = if (isSystemInDarkTheme()) DarkSoft else LightSoft

/** 3) Açık zeminli ekranlarda durum renkleri (hafif yumuşatılmış). */
object StatusColors {
    val Done = Color(0xFF4BA573)
    val Warn = Color(0xFFE59A2B)
    val Edited = Color(0xFF4F86E8)
    val Dim = Color(0xFF8A8F9C)
}

/** 4) Koyu zeminli ekranlar: kamera, inceleme, düzenleyici ve liste sayfası. */
object DarkUi {
    val Bg = Color(0xFF101014)
    val SheetBg = Color(0xFF0E1420)
    val Panel = Color(0xFF131A27)
    val Pill = Color(0xFF1B2232)
    val WarnPanel = Color(0xFF2A2415)
    val Chip = Color(0xFF232C3D)
    val Track = Color(0xFF232D3F)
    val Dim = Color(0xFFB0B6C8)
    val DimSheet = Color(0xFFA9B3C4)
    val Muted = Color(0xFF8A93A8)
    val Ok = Color(0xFF9ECE6A)
    val ScoreGood = Color(0xFF4CC38A)
    val Warn = Color(0xFFE0AF68)
    val WarnAlt = Color(0xFFE0A030)
    val Danger = Color(0xFFE5645F)
    val Accent = Color(0xFF7AA2F7)
    val Highlight = Color(0xFFFFD54F)
    val Brand = Color(0xFF2F5FC4)
    val Overlay = Color(0x22888888)
}

/** 5) AppButton: koyu varyant ve tehlike rengi. */
object ButtonColors {
    val Danger = Color(0xFFE5645F)
    val DarkSurface = Color(0xFF2A3042)
    val DarkOnSurface = Color(0xFFE8EBF4)
    val DarkDisabled = Color(0xFF59627A)
    val DarkDisabledBg = Color(0xFF1B1F2B)
}
