package com.eokul.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Klasör seçiciyi açan işlev. `pick(false)`: seçici "Belgeler"de açılır, seçilen klasörün içinde EOkul_Studio açılır.
 * `pick(true)`: kullanıcı istediği klasörü seçer ve o klasör olduğu gibi kayıt klasörü olur.
 */
@Composable
fun rememberRootPicker(studio: StudioViewModel): (Boolean) -> Unit {
    var direct by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) studio.setRootFolder(uri, direct)
    }
    return { d ->
        direct = d
        launcher.launch(if (d) null else studio.pickerHint)
    }
}

/**
 * İlk kurulum / yeniden kurulum ekranı (tam ekran). Tek yol: "İzin Ver ve Başla" → sistemin izin sayfası → dönüşte klasör
 * kendiliğinden kurulur. Android 10'da (veya izin verilmek istenmezse) küçük bir yedek yol olarak klasör seçici sunulur.
 * [pick] klasör seçiciyi açar; seçici bu ekranın dışında (her zaman bileşimde) durur, böylece sonuç kaybolmaz.
 */
@Composable
fun RootOnboarding(studio: StudioViewModel, pick: (Boolean) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val canRoot = studio.canUseRootMode
    val fresh = studio.rootState == RootRules.State.NOT_SET
    androidx.activity.compose.BackHandler { studio.rootSetupRequested = false }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.size(24.dp))
        Text("📁", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.size(12.dp))
        Text(
            if (fresh) "Kayıt klasörünü hazırlayalım" else "Kayıt klasörünü yeniden hazırlayalım",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.size(8.dp))
        val problem = RootRules.problem(studio.rootState)
        Text(
            if (!fresh && problem != null) "⚠ $problem" else
                "Fotoğraflar ve yedekler telefonunuzda tek bir ${RootRules.DEFAULT_SUB} klasöründe toplanır; uygulamayı silseniz bile kalır.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.size(24.dp))

        if (canRoot) {
            SetupStep(1, "“İzin Ver ve Başla”ya dokunun")
            SetupStep(2, "Açılan sayfada E-Okul Foto Studio için “Tüm dosyaları yönetme” anahtarını açın")
            SetupStep(3, "Geri dönün — klasör otomatik oluşturulur")
        } else {
            SetupStep(1, "“Klasör Seç”e dokunun")
            SetupStep(2, "Açılan ekranda “Bu klasörü kullan”a dokunun")
            SetupStep(3, "${RootRules.DEFAULT_SUB} klasörü otomatik oluşturulur")
        }
        Spacer(Modifier.size(20.dp))
        Text(
            "Klasör: " + if (canRoot) "Dahili depolama/${RootRules.DEFAULT_SUB}" else "seçtiğiniz yerin içinde ${RootRules.DEFAULT_SUB}",
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold
        )
        Text(
            "İnternet erişimi yoktur; hiçbir veri telefondan çıkmaz.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(24.dp))

        studio.rootSetupNote?.let {
            Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.size(12.dp))
        }

        if (studio.rootSetupBusy) {
            androidx.compose.material3.CircularProgressIndicator()
            Spacer(Modifier.size(8.dp))
            Text("İzin bekleniyor…", style = MaterialTheme.typography.bodySmall)
        } else {
            Button(
                onClick = {
                    if (canRoot) studio.setupRootDirectly { intent ->
                        try { ctx.startActivity(intent) } catch (_: Exception) { studio.toast("İzin sayfası açılamadı") }
                    } else pick(false)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (canRoot) "İzin Ver ve Başla" else "Klasör Seç", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.size(8.dp))
            if (canRoot) TextButton(onClick = { pick(false) }) { Text("İzin vermeden devam et (Belgeler klasörü)") }
            TextButton(onClick = { pick(true) }) { Text("Başka bir klasör seç…") }
            TextButton(onClick = { studio.rootSetupRequested = false }) { Text("Şimdi Değil") }
        }
    }
}

@Composable
private fun SetupStep(n: Int, text: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(softColors().container),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) { Text("$n", fontWeight = FontWeight.Bold, color = softColors().onContainer) }
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
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
            Button(onClick = { studio.openRootSetup() }) { Text(if (studio.rootState == RootRules.State.NOT_SET) "📂 Klasörü Kur" else "📂 Klasörü Yeniden Seç") }
        }
    }
}


/** Seçenek satırı: başlık + açıklama, dokununca [onClick]. */
@Composable
private fun ChoiceCard(title: String, text: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = softColors().container)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = softColors().onContainer)
            Text(text, style = MaterialTheme.typography.bodySmall, color = softColors().onContainer)
        }
    }
}

/**
 * "Klasörü Değiştir" pencereleri: (1) konum seçimi — kök dizin, Belgeler veya istenen herhangi bir klasör;
 * (2) seçilen klasörde bulunanların özeti ve "bu klasörü kullan / dosyaları kopyala / vazgeç" kararı.
 */
@Composable
fun RootChangeDialogs(studio: StudioViewModel, pick: (Boolean) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (studio.rootChooserOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { studio.rootChooserOpen = false },
            title = { Text("Kayıt klasörü nerede olsun?") },
            text = {
                Column {
                    if (studio.canUseRootMode) ChoiceCard(
                        "Telefonun kök dizini", "Dahili depolama/${RootRules.DEFAULT_SUB} — bir kez “Tüm dosyalara erişim” izni gerekir"
                    ) {
                        studio.setupRootDirectly { intent ->
                            try { ctx.startActivity(intent) } catch (_: Exception) { studio.toast("İzin sayfası açılamadı") }
                        }
                        studio.rootChooserOpen = false
                    }
                    ChoiceCard("Belgeler", "Seçtiğiniz klasörün içinde ${RootRules.DEFAULT_SUB} açılır") { pick(false) }
                    ChoiceCard(
                        "Başka klasör seç…",
                        "Daha önce kullandığınız veya istediğiniz herhangi bir klasör; fotoğraflar doğrudan o klasöre kaydedilir"
                    ) { pick(true) }
                    Text(
                        "Mevcut dosyalarınız silinmez; seçimden sonra ne yapılacağını siz belirlersiniz.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { studio.rootChooserOpen = false }) { Text("Kapat") } }
        )
    }
    studio.pendingRootChange?.let { ch ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { studio.cancelRootChange() },
            title = { Text("Bu klasör kullanılsın mı?") },
            text = {
                Column {
                    Text(ch.label, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(6.dp))
                    Text(RootRules.foundSummary(ch.found), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "“Bu Klasörü Kullan”: klasördeki dosyalar olduğu gibi kullanılır, orijinaller öğrencilere bağlanır.\n" +
                            "“Dosyaları Kopyala”: eski klasördeki orijinaller, yedekler ve eksik fotoğraflar buraya da kopyalanır. Eski dosyalar silinmez.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Button(onClick = { studio.confirmRootChange(copy = ch.found.isEmpty) }) {
                        Text(if (ch.found.isEmpty) "Kopyala ve Kullan" else "Bu Klasörü Kullan")
                    }
                    TextButton(onClick = { studio.confirmRootChange(copy = !ch.found.isEmpty) }) {
                        Text(if (ch.found.isEmpty) "Kopyalamadan Kullan" else "Dosyaları Kopyala")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { studio.cancelRootChange() }) { Text("Vazgeç") } }
        )
    }
}
