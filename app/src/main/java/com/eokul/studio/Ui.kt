package com.eokul.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Ortak kart: beyaz yüzey, ince çerçeve, 20 dp köşe, gölge yok. [onClick] verilirse tüm kart dokunulabilir. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = if (onClick != null) modifier.fillMaxWidth().clickable { onClick() } else modifier.fillMaxWidth()
    Card(
        modifier = m,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

/** "Başarılı" durum rengi (yeşil): açık temada 6,4:1, koyu temada yüksek kontrast. Her zaman metinle birlikte kullanılır. */
@Composable
fun okColor(): Color = if (isSystemInDarkTheme()) Color(0xFF6EE7A0) else Color(0xFF166534)

/** "Uyarı" durum rengi (amber): temanın tertiary rengi. */
@Composable
fun warnColor(): Color = MaterialTheme.colorScheme.tertiary

private fun vec(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

object UiIcons {
    val Camera: ImageVector by lazy {
        vec(
            "Camera",
            "M12 8.8a3.2 3.2 0 1 0 0 6.4a3.2 3.2 0 1 0 0-6.4zM9 2L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z"
        )
    }
}
