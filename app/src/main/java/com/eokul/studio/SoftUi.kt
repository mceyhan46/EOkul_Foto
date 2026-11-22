package com.eokul.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .background(soft.container)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            "Yedek alınıyor", fontWeight = FontWeight.Bold, color = soft.onContainer,
            style = MaterialTheme.typography.titleMedium
        )
        Text(p.label, color = soft.onContainerMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        val mod = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
        val f = p.fraction
        if (f == null) LinearProgressIndicator(modifier = mod, color = soft.bar, trackColor = soft.barTrack)
        else LinearProgressIndicator(progress = { f }, modifier = mod, color = soft.bar, trackColor = soft.barTrack)
        Text(
            "İşlem bitene kadar uygulamayı kapatmayın.", color = soft.onContainerMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
