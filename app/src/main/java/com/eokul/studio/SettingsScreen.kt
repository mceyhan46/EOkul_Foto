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
import androidx.compose.material3.Surface
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

    Column(Modifier.fillMaxSize()) {
        // Üst çubuk: geri + başlık
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BackBtn(onClick = onClose)
            Text("Ayarlar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
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
            Spacer(Modifier.size(16.dp))
        }

        // Sabit alt çubuk: Kaydet her zaman görünür; hata varsa hemen üstünde gösterilir
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                form.error?.let {
                    Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        "Kaydet", style = BtnStyle.Primary, modifier = Modifier.weight(1f),
                        onClick = {
                            form.build(vm.settings)?.let {
                                vm.updateSettings(it)
                                onClose()
                            }
                        }
                    )
                    AppButton(
                        "Kaydet ve yeniden kırp", style = BtnStyle.Secondary, modifier = Modifier.weight(1.4f),
                        enabled = showRecrop && vm.items.isNotEmpty() && !vm.busy(),
                        onClick = {
                            form.build(vm.settings)?.let {
                                vm.updateSettings(it)
                                onClose()
                                vm.recropUnedited()
                            }
                        }
                    )
                }
                Text(
                    "“Yeniden kırp” yalnızca elle düzenlemediğiniz fotoğrafları günceller.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    if (pinOpen) PinDialog(studio, vm.settings.pinHash != null) { pinOpen = false }
}
