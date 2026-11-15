package com.eokul.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/**
 * "Yedekten geri yükle" akışı (dosya seç → içeriği denetle → onay penceresi → geri yükle). Ayarlar ve ana menü aynı akışı kullanır.
 * Dönen işlev çağrılınca dosya seçici açılır; onay penceresi bu bileşenin çağrıldığı yerde çizilir.
 */
@Composable
fun rememberRestoreFlow(studio: StudioViewModel): () -> Unit {
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    var restoreInfo by remember { mutableStateOf<StudioViewModel.BackupInfo?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            // Önce içeriğine bakılır: geçerli bir yedek değilse hiçbir şey değişmeden uyarı verilir.
            val info = studio.inspectBackup(uri)
            if (info == null) studio.toast("Bu dosya geçerli bir yedek değil (roster.json ve sınıf listesi bulunamadı). Mevcut veriye dokunulmadı.")
            else { restoreInfo = info; confirmRestore = uri }
        }
    }
    val rs = confirmRestore
    if (rs != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Yedekten geri yüklensin mi?") },
            text = {
                val ri = restoreInfo
                val dateFmt = java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale.forLanguageTag("tr"))
                Text(
                    (if (ri != null)
                        "Yedekte: ${ri.classes} sınıf · ${ri.students} öğrenci (${ri.captured} fotoğraflı · ${ri.retake} tekrar işaretli)" +
                            (ri.createdMs?.let { " · " + dateFmt.format(java.util.Date(it)) } ?: "") +
                            (if (ri.appVersion.isNotBlank()) " · sürüm ${ri.appVersion}" else "") + "\n"
                    else "") +
                        "Şu an uygulamada: ${studio.classes.size} sınıf · ${studio.classes.sumOf { it.students.size }} öğrenci\n\n" +
                        "Uygulamadaki tüm sınıf listeleri ve küçük fotoğraflar yedekteki ile DEĞİŞTİRİLİR. " +
                        "Değiştirmeden önce mevcut verinin ayrı bir kopyası (EOkulStudio_GeriYuklemeOncesi_….zip) kayıt klasörüne otomatik alınır."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !studio.isRestoring,
                    onClick = {
                        confirmRestore = null
                        studio.restore(rs)
                    }
                ) { Text(if (studio.isRestoring) "Yükleniyor…" else "Geri yükle") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Vazgeç") } }
        )
    }

    return { restorePicker.launch(arrayOf("application/zip", "application/octet-stream")) }
}
