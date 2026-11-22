package com.eokul.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Klasör seçiciyi açan işlev: seçici "Belgeler"de açılır; seçilen klasör kurulur (içinde EOkul_Studio açılır). */
@Composable
fun rememberRootPicker(studio: StudioViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) studio.setRootFolder(uri)
    }
    return { launcher.launch(studio.pickerHint) }
}

/** Uygulama genelinde gösterilen ilk kurulum / yeniden kurulum penceresi. */
@Composable
fun RootSetupDialog(studio: StudioViewModel) {
    // Seçici, pencere kapansa bile bileşimde kalmalı: aksi hâlde "Devam"a basınca pencere kapanır, seçici kaydı silinir
    // ve seçim sonucu hiçbir yere ulaşmaz (klasör kurulmaz).
    val pick = rememberRootPicker(studio)
    if (!studio.rootSetupRequested) return
    val problem = RootRules.problem(studio.rootState)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val canRoot = studio.canUseRootMode
    AlertDialog(
        onDismissRequest = { studio.rootSetupRequested = false },
        title = { Text(if (studio.rootState == RootRules.State.NOT_SET) "Kayıt Klasörü Kurulumu" else "Kayıt Klasörünü Yeniden Seç") },
        text = {
            Column {
                if (problem != null && studio.rootState != RootRules.State.NOT_SET) {
                    Text("⚠ $problem", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(8.dp))
                }
                if (canRoot) {
                    Text(
                        "Önerilen: telefonun ana dizininde ${RootRules.DEFAULT_SUB} klasörü açılır. Bunun için bir kez " +
                            "“Tüm dosyalara erişim” izni vermeniz gerekir (açılan sayfada anahtarı açıp geri dönün)."
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "İzin vermek istemezseniz “Belgeler’de Kur”: klasör Belgeler içinde açılır, izin gerekmez.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Fotoğraflar seçeceğiniz yerin içinde ${RootRules.DEFAULT_SUB} klasörüne kaydedilir. Açılan ekranda “Bu klasörü kullan”a dokunun.")
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    "Uygulamayı silseniz bile dosyalarınız ve yedekleriniz kalır.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                if (canRoot) {
                    TextButton(onClick = {
                        studio.setupRootDirectly { intent -> try { ctx.startActivity(intent) } catch (_: Exception) { studio.toast("İzin sayfası açılamadı") } }
                    }) { Text("Kök Dizinde Kur") }
                }
                TextButton(onClick = { studio.rootSetupRequested = false; pick() }) { Text(if (canRoot) "Belgeler’de Kur" else "Devam") }
                TextButton(onClick = { studio.rootSetupRequested = false }) { Text("Sonra") }
            }
        }
    )
}

/** Ana ekranda kayıt klasörü kullanılamıyorsa gösterilen uyarı kartı (durum READY ise hiçbir şey çizmez). */
@Composable
fun RootStatusCard(studio: StudioViewModel) {
    val problem = RootRules.problem(studio.rootState) ?: return
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(problem, fontWeight = FontWeight.Bold)
            Text(
                if (studio.rootState == RootRules.State.NOT_SET)
                    "Fotoğraflar ${RootRules.DEFAULT_SUB} klasörüne kaydedilir; kurulum birkaç saniye sürer."
                else "Dosyalarınız silinmedi; klasörü yeniden seçerek devam edebilirsiniz.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.size(6.dp))
            Button(onClick = { studio.rootSetupRequested = true }) { Text(if (studio.rootState == RootRules.State.NOT_SET) "📂 Klasörü Kur" else "📂 Klasörü Yeniden Seç") }
        }
    }
}
