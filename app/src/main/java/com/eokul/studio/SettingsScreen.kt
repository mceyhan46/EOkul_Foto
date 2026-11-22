package com.eokul.studio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: AppViewModel, studio: StudioViewModel, showRecrop: Boolean, onClose: () -> Unit) {
    BackHandler { if (!studio.isRestoring) onClose() }
    val form = remember { SettingsForm(vm.settings) }
    var pinOpen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackBtn(onClick = onClose)
            Text("Ayarlar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(8.dp))
        if (studio.isRestoring) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Yedek güvenli biçimde geri yükleniyor. İşlem tamamlanana kadar uygulamayı kapatmayın.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        GeneralSection(form, studio)
        CaptureSection(form)
        CropSection(form)
        OutputSection(form)
        DiagnosticsSection(form, studio)
        SecuritySection(form, vm, studio) { pinOpen = true }

        form.error?.let {
            Spacer(Modifier.size(10.dp))
            Text("⚠ $it", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.size(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    form.build(vm.settings)?.let {
                        vm.updateSettings(it)
                        onClose()
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Kaydet") }
            Button(
                onClick = {
                    form.build(vm.settings)?.let {
                        vm.updateSettings(it)
                        onClose()
                        vm.recropUnedited()
                    }
                },
                enabled = showRecrop && vm.items.isNotEmpty() && !vm.busy(),
                modifier = Modifier.weight(1.4f)
            ) { Text("Kaydet ve yeniden kırp") }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            "“Yeniden kırp” yalnızca elle düzenlemediğiniz fotoğrafları günceller; düzenledikleriniz korunur.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(20.dp))
        val appCtx = LocalContext.current
        val version = remember {
            try { appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        }
        Text(
            "E-Okul Foto Studio" + if (version.isNotEmpty()) " · Sürüm $version" else "",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Text(
            "Copyright © 2026 Hacimd. All rights reserved.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(24.dp))
    }

    if (pinOpen) PinDialog(studio, vm.settings.pinHash != null) { pinOpen = false }
}
