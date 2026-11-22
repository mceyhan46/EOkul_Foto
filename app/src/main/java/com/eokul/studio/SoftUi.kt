package com.eokul.studio

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

/**
 * Açık zeminli ekranlar için ortak pastel stil (ana ekranla aynı renkler): kart dolgusu ve ana düğme tek yerden gelir,
 * ekranlar renk seçmez.
 */
@Composable
fun softCardColors(): CardColors = CardDefaults.cardColors(containerColor = softColors().group)

/** Ana eylem düğmesi (Kaydet, İçe aktar…): pastel dolgu, koyu yazı. */
@Composable
fun softButtonColors(): ButtonColors = softColors().let {
    ButtonDefaults.buttonColors(containerColor = it.container, contentColor = it.onContainer)
}

/** Yedek alınırken ana ekranda görünen ilerleme kartı: aşama, yüzde ve "kapatmayın" notu. */
@Composable
fun BackupProgressCard(studio: StudioViewModel) {
    val p = studio.backupProgress ?: return
    val soft = softColors()
    androidx.compose.foundation.layout.Column(
        androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(androidx.compose.material3.MaterialTheme.shapes.large)
            .background(soft.container)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        androidx.compose.material3.Text(
            "Yedek alınıyor", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = soft.onContainer,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
        )
        androidx.compose.material3.Text(p.label, color = soft.onContainerMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
        val mod = androidx.compose.ui.Modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
        val f = p.fraction
        if (f == null) androidx.compose.material3.LinearProgressIndicator(modifier = mod, color = soft.bar, trackColor = soft.barTrack)
        else androidx.compose.material3.LinearProgressIndicator(progress = { f }, modifier = mod, color = soft.bar, trackColor = soft.barTrack)
        androidx.compose.material3.Text(
            "İşlem bitene kadar uygulamayı kapatmayın.", color = soft.onContainerMuted,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = androidx.compose.ui.Modifier.padding(top = 6.dp)
        )
    }
}
