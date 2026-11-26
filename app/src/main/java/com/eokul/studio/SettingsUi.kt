package com.eokul.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp

/*
 * Ayarlar ekranının ortak bileşenleri. Tüm renkler tema ve pastel belirteçlerinden gelir (AppColors.kt):
 * beyaz kart + ince çerçeve + pastel simge dairesi; her ayar kendi satırında; sürgülerde değer rozeti ve uç etiketleri.
 */

/** Bölüm kartı: simgeli başlık, kısa açıklama, ince ayraç ve içerik. */
@Composable
fun SettingsCard(icon: ImageVector, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    val soft = softColors()
    Card(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(soft.container), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = soft.onContainer, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

/** Satırlar arası ince ayraç. */
@Composable
fun SettingDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

/** Açıklama / ipucu metni. */
@Composable
fun SettingNote(text: String, modifier: Modifier = Modifier) = Text(
    text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(top = 4.dp, bottom = 6.dp)
)

/** Anahtar satırı: başlık ve açıklama solda, anahtar sağda; satırın tamamı dokunulabilir. */
@Composable
fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

/**
 * Sürgü satırı: başlık solda, geçerli değer sağda pastel rozet; sürgünün dolu kısmı ana renk, boş kısmı belirgin pastel;
 * altında en düşük/en yüksek değer etiketleri ve açıklama.
 */
@Composable
fun SettingSlider(
    title: String, valueText: String, value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>,
    minLabel: String, maxLabel: String, description: String? = null, steps: Int = 0
) {
    val soft = softColors()
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                valueText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = soft.onContainer,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(soft.container).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = soft.container,
                activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                inactiveTickColor = soft.onContainerMuted.copy(alpha = 0.5f)
            )
        )
        Row(Modifier.fillMaxWidth()) {
            Text(minLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(maxLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (description != null) SettingNote(description)
    }
}

/** Açılır bölüm başlığı: dolgulu satır + ok simgesi (durumu açıkça gösterir). */
@Composable
fun SettingExpander(title: String, open: Boolean, onToggle: () -> Unit) {
    val soft = softColors()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(soft.group)
            .clickable(onClick = onToggle).defaultMinSize(minHeight = 48.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = soft.onGroup, modifier = Modifier.weight(1f))
        Icon(
            if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (open) "Gizle" else "Göster", tint = soft.onGroup
        )
    }
}

/** Vurgulu bilgi paneli (kayıt klasörü gibi öne çıkan değerler için). */
@Composable
fun SettingPanel(content: @Composable ColumnScope.() -> Unit) {
    val soft = softColors()
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(16.dp)).background(soft.container).padding(14.dp),
        content = content
    )
}

/** Satır içi düğme grubu için boşluk sabiti. */
val SettingButtonGap = Arrangement.spacedBy(8.dp)
