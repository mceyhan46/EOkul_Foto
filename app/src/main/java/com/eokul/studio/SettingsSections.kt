package com.eokul.studio

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun GeneralSection(form: SettingsForm, studio: StudioViewModel) {
    with(form) {
        SettingsCard("Genel", "Kayıt klasörü ve çekim akışı") {
        Text(
            "Kayıt klasörü: ${studio.rootName ?: "kurulmadı"}",
            style = MaterialTheme.typography.bodyMedium
        )
        RootRules.problem(studio.rootState)?.let {
            if (studio.rootState != RootRules.State.NOT_SET) Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.size(4.dp))
        OutlinedButton(onClick = {
            if (studio.rootState == RootRules.State.NOT_SET) studio.openRootSetup() else studio.rootChooserOpen = true
        }) { Text(if (studio.rootState == RootRules.State.NOT_SET) "Klasörü Kur" else "Klasörü Değiştir") }
        Text(
            "Yapı: Sınıf/12345-Ad Soyad.jpg · Sınıf/Orijinaller/12345-Ad Soyad.jpg. Klasörü değiştirirken istediğiniz konumu seçebilir, eski dosyaları isterseniz yeni klasöre kopyalayabilirsiniz.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = serial, onCheckedChange = { serial = it })
            Text("  Seri çekim: kayıttan sonra sıradaki öğrenciye geç", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = keepOrig, onCheckedChange = { keepOrig = it })
            Text("  Orijinal fotoğrafı sakla (en fazla 1600 px)", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = backupOrig, onCheckedChange = { backupOrig = it })
            Text("  Yedeğe orijinalleri de ekle", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Açıkken yedek dosyası daha büyük olur (öğrenci başına yaklaşık 0,3–0,9 MB) ancak başka bir klasöre/telefona geri yüklemede “Kırpmayı düzenle” çalışır.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        }
    }
}

@Composable
fun CaptureSection(form: SettingsForm) {
    with(form) {
        SettingsCard("Çekim", "Akıllı çekim, otomatik çekim ve hızlı onay") {
        val tolText = when {
            autoTol < 0.85f -> "sıkı"
            autoTol > 1.2f -> "gevşek"
            else -> "normal"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = smart, onCheckedChange = { smart = it })
            Text("  Akıllı çekim", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = hqCap, onCheckedChange = { hqCap = it })
            Text("  Yüksek kaliteli çekim", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Kameranın en iyi görüntü işlemesini (gürültü azaltma dahil) ve ~5 MP çözünürlüğü kullanır; çekim biraz yavaşlar. " +
                "Kapatırsanız hızlı mod (~2 MP) kullanılır. Kamera ekranı yeniden açılınca geçerli olur.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "Gözler kapalıysa, görüntü net değilse veya telefon sallanıyorsa çekimi bekletir; her çekimde 3 kare alıp en netini seçer (çekim yaklaşık 1 sn sürer).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = flipCap, onCheckedChange = { flipCap = it })
            Text("  Arka kamera: sağ-sol çevir", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = flipFront, onCheckedChange = { flipFront = it })
            Text("  Ön kamera: sağ-sol çevir", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Fotoğraf çekimden sonra ters (aynalı) görünüyorsa, o kamera için açın. Ön ve arka kamera ayrı ayrı ayarlanır. Yalnızca kamerayla çekilenleri etkiler, galeriden seçilenleri değil.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = autoAcc, onCheckedChange = { autoAcc = it })
            Text("  Hızlı onay: iyi çekimi otomatik kaydet", style = MaterialTheme.typography.bodyMedium)
        }
        if (autoAcc) {
            Text("Otomatik kayıt için en düşük kalite puanı: ${accScore.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(value = accScore, onValueChange = { accScore = it }, valueRange = 60f..95f)
            Text("Kırpılmış fotoğrafı görme süresi: ${"%.1f".format(accDelay / 1000f)} sn", style = MaterialTheme.typography.bodyMedium)
            Slider(value = accDelay, onValueChange = { accDelay = it }, valueRange = 700f..4000f)
        }
        Text(
            "Uyarısı olmayan ve puanı eşiği geçen fotoğraf, ayarlanan süre (varsayılan 1,8 sn) içinde onay beklemeden kaydedilir ve sıradaki öğrenciye geçilir (\"Durdur\" ile engellenebilir). Uyarılı veya düşük puanlı fotoğrafta onay ekranı açık kalır.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(8.dp))
        Text("Yüz konumu toleransı: $tolText (${(autoTol * 100).roundToInt()}%)", style = MaterialTheme.typography.bodyMedium)
        Slider(value = autoTol, onValueChange = { autoTol = it }, valueRange = 0.6f..1.6f)
        Text(
            "Sıkı: yüzün kılavuza çok iyi oturması gerekir. Gevşek: biraz sapmada bile çeker. Öğrenciler çok kıpır kıpırsa gevşetin.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(8.dp))
        Text("Sabit bekleme süresi: ${"%.1f".format(autoHold / 1000f)} sn", style = MaterialTheme.typography.bodyMedium)
        Slider(value = autoHold, onValueChange = { autoHold = it }, valueRange = 500f..3000f)
        Text(
            "Yüz hazır olduktan sonra çekimden önce beklenen süre (halka dolarken).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        }
    }
}

@Composable
fun CropSection(form: SettingsForm) {
    with(form) {
        var sideGaps by remember { mutableStateOf(false) }
        SettingsCard("Kırpma", "Baş konumu ve boyutu (${cur.photoW}×${cur.photoH})") {
        Text("Saç üstü boşluk: %${(topGap * 100).roundToInt()}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = topGap, onValueChange = { topGap = it }, valueRange = 0.01f..0.10f, steps = 8)
        Text(
            "Saçın tepesi ile fotoğrafın üst kenarı arasındaki sabit boşluk (kırpma yüksekliğinin oranı). " +
                "Tüm öğrencilerde aynı oranda uygulanır.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(12.dp))
        Text("Çene altı / göğüs boşluğu: %${(bottomGap * 100).roundToInt()}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = bottomGap, onValueChange = { bottomGap = it }, valueRange = 0.10f..0.50f, steps = 39)
        Text("Bu ayar artırıldığında kadraj aşağı doğru genişler; omuz ve göğüs bölgesine daha fazla alan bırakılır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { sideGaps = !sideGaps }) {
            Text(if (sideGaps) "▾ Yan boşlukları gizle" else "▸ Yan boşluklar (sol / sağ, gelişmiş)")
        }
        if (sideGaps) {
            Text("Sol boşluk: %${(leftGap * 100).roundToInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(value = leftGap, onValueChange = { leftGap = it }, valueRange = 0.05f..0.35f, steps = 29)
            Text("Sağ boşluk: %${(rightGap * 100).roundToInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(value = rightGap, onValueChange = { rightGap = it }, valueRange = 0.05f..0.35f, steps = 29)
            Text(
                "Sol ve sağ boşluk yüzün yanlarındaki asgari payı belirler (fotoğraf genişliğinin oranı). Büyütürseniz baş biraz küçülür; " +
                    "çekim kılavuzu da buna göre güncellenir. Görüntü sınırı izin vermiyorsa boşluklar otomatik daralır.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(6.dp))
        OutlinedButton(onClick = {
            topGap = def.topGap.toFloat()
            bottomGap = def.bottomGap.toFloat()
            leftGap = def.leftGap.toFloat()
            rightGap = def.rightGap.toFloat()
        }) { Text("Kırpma varsayılanları") }

        }
    }
}

@Composable
fun OutputSection(form: SettingsForm) {
    with(form) {
        var advanced by remember { mutableStateOf(false) }
        SettingsCard("Çıktı ve dışa aktarma", "JPEG kalitesi, dosya adı denetimi, renk profili") {
        Text("JPEG kalitesi: ${quality.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = quality, onValueChange = { quality = it }, valueRange = 70f..100f, steps = 29)
        Text(
            "JPEG kalitesi sıkıştırma düzeyidir (100 = en az kayıp, dosya biraz büyür). Renk alt örneklemesi (4:4:4) bundan bağımsızdır: uygulamada her zaman tam çözünürlükte yazılır. 300 DPI de her zaman yazılır.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(8.dp))
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "▾ Gelişmiş çıktı ayarlarını gizle" else "▸ Gelişmiş çıktı ayarları (boyut, klasör adı, dosya adı kuralı)") }
        if (advanced) {
        Spacer(Modifier.size(10.dp))
        Text("Fotoğraf standardı", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = photoW, onValueChange = { photoW = it },
                label = { Text("Genişlik (px)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = photoH, onValueChange = { photoH = it },
                label = { Text("Yükseklik (px)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = maxKb, onValueChange = { maxKb = it },
            label = { Text("Maksimum dosya boyutu (KB)") }, singleLine = true,
            supportingText = { Text("Dosya bu sınırı aşarsa JPEG kalitesi otomatik düşürülür (sınıra uyan en yüksek kalite seçilir).") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Standart (boyut ve KB sınırları) değişirse yalnızca bundan sonra çekilen veya yeniden kırpılan fotoğraflar yeni standartta üretilir; " +
                "eskiler olduğu gibi kalır. Eski boyutlu olanları “Veri denetimi” listeler.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(4.dp))
        TextButton(onClick = {
            photoW = def.photoW.toString(); photoH = def.photoH.toString()
            minKb = def.minKb.toString(); maxKb = def.maxKb.toString()
        }) { Text("e-Okul standardına dön (${def.photoW}×${def.photoH}, ${def.minKb}–${def.maxKb} KB)") }
        Spacer(Modifier.size(10.dp))
        OutlinedTextField(
            value = minKb, onValueChange = { minKb = it },
            label = { Text("Minimum dosya boyutu (KB)") }, singleLine = true,
            supportingText = { Text("Küçük dosyaları reddeden sistemler için: dosya bu boyuta kadar JPEG yorum bölümüyle doldurulur (görüntü ayrıntısı artmaz). Gerekmiyorsa 1 yapın.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(10.dp))
        OutlinedTextField(
            value = outDir, onValueChange = { outDir = it },
            label = { Text("Çıktı klasörü adı") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(10.dp))
        OutlinedTextField(
            value = regex, onValueChange = { regex = it },
            label = { Text("e-Okul dosya adı kuralı (regex)") }, singleLine = true,
            supportingText = { Text("Örn: ^\\d{5,11}$  (5–11 haneli sayı) — kaydetmeden önce denetlenir") },
            modifier = Modifier.fillMaxWidth()
        )
        }
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = embed, onCheckedChange = { embed = it })
            Text(
                "  🎨  Çıktıya sRGB renk profili göm (önerilir)",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        }
    }
}

@Composable
fun DiagnosticsSection(form: SettingsForm, studio: StudioViewModel) {
    with(form) {
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var diagOpen by remember { mutableStateOf(false) }
        val shareCtx = LocalContext.current
        val reportSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
            if (uri != null) studio.saveReportTo(uri)
        }
        SettingsCard("Gelişmiş: tanılama ve rapor", "Yalnızca bir sorun olduğunda gerekir") {
            TextButton(onClick = { diagOpen = !diagOpen }) {
                Text(if (diagOpen) "▾ Rapor araçlarını gizle" else "▸ Rapor araçlarını göster")
            }
            if (diagOpen) {
            Text(
                "Uygulama çekim, puanlama, kayıt ve hataları öğrenci adı olmadan cihazda tutar. Bir sorun olduğunda raporu alıp geliştiriciye iletin; hız ve hatalar buradan analiz edilir.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton("Raporu paylaş", onClick = {
                    scope.launch {
                        val t = studio.reportTextForShare()
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "E-Okul Foto Studio raporu")
                            .putExtra(Intent.EXTRA_TEXT, t)
                        try { shareCtx.startActivity(Intent.createChooser(send, "Raporu gönder")) } catch (_: Exception) { studio.toast("Paylaşım açılamadı; “Dosyaya kaydet” kullanın") }
                    }
                }, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
                AppButton("Dosyaya kaydet", onClick = { reportSaver.launch(studio.reportFileName()) }, style = BtnStyle.Secondary, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.size(6.dp))
            AppButton("Kaydı temizle", onClick = { studio.clearOpsLog() }, style = BtnStyle.Secondary, modifier = Modifier.fillMaxWidth())
            Text(
                "“Paylaş” raporu WhatsApp, e-posta vb. ile gönderir; “Dosyaya kaydet” konumu siz seçersiniz (örn. İndirilenler).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            }
        }
    }
}

@Composable
fun SecuritySection(form: SettingsForm, vm: AppViewModel, studio: StudioViewModel, onPin: () -> Unit) {
    with(form) {
        val startRestore = rememberRestoreFlow(studio)
        SettingsCard("Güvenlik ve yedek", "PIN kilidi ve yedekleme") {
        OutlinedButton(onClick = { onPin() }) {
            Text(if (vm.settings.pinHash != null) "🔒 PIN: açık (değiştir / kaldır)" else "🔓 PIN kilidi belirle")
        }
        Text(
            "PIN açıkken uygulama başlarken ve 30 sn’den uzun arka planda kalınca PIN sorulur. Son uygulamalar ekranında içerik gizlenir ve ekran görüntüsü alınamaz.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(12.dp))
        BackupProgressCard(studio)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("Yedekle", onClick = { studio.backup() }, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
            AppButton(
                "Geri yükle",
                onClick = startRestore,
                style = BtnStyle.Secondary, modifier = Modifier.weight(1f)
            )
        }
        val lastB = vm.settings.lastBackupMs
        Text(
            if (lastB == 0L) "Son yedek: hiç alınmadı"
            else "Son yedek: " + SimpleDateFormat("d MMM yyyy HH:mm", Locale.forLanguageTag("tr")).format(Date(lastB)),
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            "Yedek: sınıf listeleri ve küçük fotoğraflar kayıt klasörünün içindeki “Yedek” klasörüne Ydk_160726_0816.zip gibi (gün-ay-yıl_saat-dakika) yazılır ve yazıldıktan sonra doğrulanır. Orijinal fotoğraflar, “Yedeğe orijinalleri de ekle” açıksa yedeğe girer.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(10.dp))
        OutlinedTextField(
            value = backupPrefix, onValueChange = { backupPrefix = it.take(com.eokul.studio.core.BackupNames.MAX_PREFIX) },
            label = { Text("Yedek dosya adı öneki") }, singleLine = true,
            supportingText = {
                Text("Örnek: ${com.eokul.studio.core.BackupNames.sanitizePrefix(backupPrefix)}_160726_0816.zip · yalnızca harf, rakam, - ve _ ; “Kaydet” ile geçerli olur")
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(10.dp))
        Text("Yeni sürüme veya yeni telefona geçiş", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            "1. “Yedekle”ye basın; “Yedek alındı ve doğrulandı” mesajını bekleyin.\n" +
                "2. Yedek dosyası kayıt klasörünüzde durur; uygulamayı silmek onu silmez.\n" +
                "3. Eski uygulamayı kaldırıp yeni APK’yı kurun (imza değiştiyse güncelleme üstüne kurulmaz).\n" +
                "4. Açılışta aynı kayıt klasörünü yeniden seçin.\n" +
                "5. “Geri yükle” ile yedek zip dosyasını (Yedek klasöründe) seçin; içeriği önce gösterilir, geri yüklemeden önce mevcut verinin ayrı bir kopyası otomatik alınır.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
    }
}

/** Ayarlar bölümü: başlık, kısa açıklama ve içerik tek, sade kartta. */
@Composable
private fun SettingsCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = softCardColors()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = softColors().onGroup)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(10.dp))
            content()
        }
    }
}
