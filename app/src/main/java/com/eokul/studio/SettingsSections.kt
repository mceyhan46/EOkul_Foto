package com.eokul.studio

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eokul.studio.core.BackupNames
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun GeneralSection(form: SettingsForm, studio: StudioViewModel) {
    with(form) {
        SettingsCard(Icons.Filled.Settings, "Genel", "Kayıt klasörü ve çekim akışı") {
            val notSet = studio.rootState == RootRules.State.NOT_SET
            val problem = if (notSet) null else RootRules.problem(studio.rootState)
            SettingPanel {
                val soft = softColors()
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Filled.Place, contentDescription = null, tint = soft.onContainer)
                    Spacer(Modifier.size(8.dp))
                    Text("Kayıt klasörü", style = MaterialTheme.typography.labelLarge, color = soft.onContainerMuted)
                }
                Text(
                    studio.rootName ?: "Kurulmadı", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = soft.onContainer, modifier = Modifier.padding(top = 4.dp)
                )
                if (problem != null) {
                    Text("⚠ $problem", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                }
                Text(
                    "Sınıf/12345-Ad Soyad.jpg\nSınıf/Orijinaller/12345-Ad Soyad.jpg",
                    style = MaterialTheme.typography.bodySmall, color = soft.onContainerMuted, modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.size(12.dp))
                AppButton(
                    if (notSet) "Klasörü Kur" else "Klasörü Değiştir",
                    onClick = { if (notSet) studio.openRootSetup() else studio.rootChooserOpen = true },
                    style = BtnStyle.Primary, modifier = Modifier.fillMaxWidth()
                )
            }
            SettingNote("Klasörü değiştirirken istediğiniz konumu seçebilir, eski dosyaları isterseniz yeni klasöre kopyalayabilirsiniz.")
            SettingDivider()
            SettingSwitch("Seri çekim", serial, { serial = it }, "Kayıttan sonra sıradaki öğrenciye geçer")
            SettingDivider()
            SettingSwitch("Orijinal fotoğrafı sakla", keepOrig, { keepOrig = it }, "En fazla 1600 px, “Orijinaller” klasörüne; “Kırpmayı düzenle” bunu kullanır")
            SettingDivider()
            SettingSwitch(
                "Yedeğe orijinalleri de ekle", backupOrig, { backupOrig = it },
                "Yedek dosyası büyür (öğrenci başına ≈0,3–0,9 MB) ama başka telefona geri yüklemede “Kırpmayı düzenle” çalışır"
            )
        }
    }
}

@Composable
fun CaptureSection(form: SettingsForm) {
    with(form) {
        SettingsCard(Icons.Filled.Face, "Çekim", "Akıllı çekim, otomatik çekim ve hızlı onay") {
            val tolText = when {
                autoTol < 0.85f -> "sıkı"
                autoTol > 1.2f -> "gevşek"
                else -> "normal"
            }
            SettingSwitch(
                "Akıllı çekim", smart, { smart = it },
                "Gözler kapalıysa, görüntü net değilse veya telefon sallanıyorsa bekletir; 3 kare alıp en iyisini seçer (≈1 sn)"
            )
            SettingDivider()
            SettingSwitch(
                "Yüksek kaliteli çekim", hqCap, { hqCap = it },
                "En iyi görüntü işleme ve ~5 MP; çekim biraz yavaşlar. Kapalıysa hızlı mod (~2 MP). Kamera yeniden açılınca geçerli olur"
            )
            SettingDivider()
            SettingSwitch("Arka kamera: sağ-sol çevir", flipCap, { flipCap = it }, "Fotoğraf çekimden sonra ters (aynalı) görünüyorsa açın")
            SettingDivider()
            SettingSwitch("Ön kamera: sağ-sol çevir", flipFront, { flipFront = it }, "Yalnızca kamerayla çekilenleri etkiler, galeriden seçilenleri değil")
            SettingDivider()
            SettingSwitch("Hızlı onay", autoAcc, { autoAcc = it }, "Uyarısı olmayan ve puanı eşiği geçen fotoğraf onay beklemeden kaydedilir; “Durdur” ile engellenebilir")
            if (autoAcc) {
                SettingSlider(
                    "En düşük kalite puanı", "${accScore.roundToInt()}", accScore, { accScore = it }, 60f..95f, "60", "95",
                    "Bu puanın altındaki fotoğrafta onay ekranı açık kalır"
                )
                SettingSlider(
                    "Kırpılmış fotoğrafı görme süresi", "${"%.1f".format(accDelay / 1000f)} sn", accDelay, { accDelay = it }, 700f..4000f,
                    "0,7 sn", "4 sn"
                )
            }
            SettingDivider()
            SettingSlider(
                "Yüz konumu toleransı", "$tolText · ${(autoTol * 100).roundToInt()}%", autoTol, { autoTol = it }, 0.6f..1.6f, "Sıkı", "Gevşek",
                "Sıkı: yüz kılavuza çok iyi oturmalı. Gevşek: biraz sapmada bile çeker. Öğrenciler kıpırdıyorsa gevşetin."
            )
            SettingSlider(
                "Sabit bekleme süresi", "${"%.1f".format(autoHold / 1000f)} sn", autoHold, { autoHold = it }, 500f..3000f, "0,5 sn", "3 sn",
                "Yüz hazır olduktan sonra çekimden önce beklenen süre (halka dolarken)."
            )
        }
    }
}

@Composable
fun CropSection(form: SettingsForm) {
    with(form) {
        var sideGaps by remember { mutableStateOf(false) }
        SettingsCard(Icons.Filled.Create, "Kırpma", "Baş konumu ve boyutu (${cur.photoW}×${cur.photoH})") {
            SettingSlider(
                "Saç üstü boşluk", "%${(topGap * 100).roundToInt()}", topGap, { topGap = it }, 0.01f..0.10f, "%1", "%10",
                "Saçın tepesi ile üst kenar arasındaki sabit boşluk (kırpma yüksekliğinin oranı); tüm öğrencilerde aynı uygulanır.", steps = 8
            )
            SettingSlider(
                "Çene altı / göğüs boşluğu", "%${(bottomGap * 100).roundToInt()}", bottomGap, { bottomGap = it }, 0.10f..0.50f, "%10", "%50",
                "Artırırsanız kadraj aşağı genişler; omuz ve göğüse daha fazla alan kalır.", steps = 39
            )
            SettingExpander("Yan boşluklar (sol / sağ, gelişmiş)", sideGaps) { sideGaps = !sideGaps }
            if (sideGaps) {
                SettingSlider("Sol boşluk", "%${(leftGap * 100).roundToInt()}", leftGap, { leftGap = it }, 0.05f..0.35f, "%5", "%35", steps = 29)
                SettingSlider(
                    "Sağ boşluk", "%${(rightGap * 100).roundToInt()}", rightGap, { rightGap = it }, 0.05f..0.35f, "%5", "%35",
                    "Yüzün yanlarındaki asgari pay (genişliğin oranı). Büyütürseniz baş küçülür; çekim kılavuzu da güncellenir.", steps = 29
                )
            }
            Spacer(Modifier.size(6.dp))
            AppButton("Kırpma varsayılanları", onClick = {
                topGap = def.topGap.toFloat()
                bottomGap = def.bottomGap.toFloat()
                leftGap = def.leftGap.toFloat()
                rightGap = def.rightGap.toFloat()
            }, style = BtnStyle.Secondary, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun OutputSection(form: SettingsForm) {
    with(form) {
        var advanced by remember { mutableStateOf(false) }
        SettingsCard(Icons.Filled.Share, "Çıktı ve dışa aktarma", "JPEG kalitesi, dosya adı denetimi, renk profili") {
            SettingSlider(
                "JPEG kalitesi", "${quality.roundToInt()}", quality, { quality = it }, 70f..100f, "70", "100",
                "100 = en az kayıp, dosya biraz büyür. Renk örneklemesi (4:4:4) ve 300 DPI her zaman yazılır.", steps = 29
            )
            SettingDivider()
            SettingSwitch("sRGB renk profili göm", embed, { embed = it }, "Önerilir")
            SettingExpander("Gelişmiş çıktı ayarları (boyut, klasör adı, dosya adı kuralı)", advanced) { advanced = !advanced }
            if (advanced) {
                Text("Fotoğraf standardı", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                Row(horizontalArrangement = SettingButtonGap, modifier = Modifier.fillMaxWidth()) {
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
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = minKb, onValueChange = { minKb = it },
                    label = { Text("Minimum dosya boyutu (KB)") }, singleLine = true,
                    supportingText = { Text("Küçük dosyaları reddeden sistemler için: dosya bu boyuta kadar JPEG yorum bölümüyle doldurulur. Gerekmiyorsa 1 yapın.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = outDir, onValueChange = { outDir = it },
                    label = { Text("Çıktı klasörü adı") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = regex, onValueChange = { regex = it },
                    label = { Text("e-Okul dosya adı kuralı (regex)") }, singleLine = true,
                    supportingText = { Text("Örn: ^\\d{5,11}$  (5–11 haneli sayı) — kaydetmeden önce denetlenir") },
                    modifier = Modifier.fillMaxWidth()
                )
                SettingNote(
                    "Boyut ve KB sınırları değişirse yalnızca bundan sonra çekilen veya yeniden kırpılan fotoğraflar yeni standartta üretilir; " +
                        "eskileri “Veri denetimi” listeler."
                )
                AppButton("e-Okul standardına dön (${def.photoW}×${def.photoH}, ${def.minKb}–${def.maxKb} KB)", onClick = {
                    photoW = def.photoW.toString(); photoH = def.photoH.toString()
                    minKb = def.minKb.toString(); maxKb = def.maxKb.toString()
                }, style = BtnStyle.Secondary, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun DiagnosticsSection(form: SettingsForm, studio: StudioViewModel) {
    with(form) {
        val scope = rememberCoroutineScope()
        var diagOpen by remember { mutableStateOf(false) }
        val shareCtx = LocalContext.current
        val reportSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
            if (uri != null) studio.saveReportTo(uri)
        }
        SettingsCard(Icons.Filled.Build, "Tanılama ve rapor", "Yalnızca bir sorun olduğunda gerekir") {
            SettingExpander("Rapor araçları", diagOpen) { diagOpen = !diagOpen }
            if (diagOpen) {
                SettingNote("Uygulama çekim, puanlama, kayıt ve hataları öğrenci adı olmadan cihazda tutar. Bir sorun olduğunda raporu alıp geliştiriciye iletin.")
                Row(horizontalArrangement = SettingButtonGap) {
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
                Spacer(Modifier.size(8.dp))
                AppButton("Kaydı temizle", onClick = { studio.clearOpsLog() }, style = BtnStyle.Secondary, modifier = Modifier.fillMaxWidth())
                SettingNote("“Paylaş” raporu WhatsApp, e-posta vb. ile gönderir; “Dosyaya kaydet” konumu siz seçersiniz (örn. İndirilenler).")
            }
        }
    }
}

@Composable
fun SecuritySection(form: SettingsForm, vm: AppViewModel, studio: StudioViewModel, onPin: () -> Unit) {
    with(form) {
        val startRestore = rememberRestoreFlow(studio)
        SettingsCard(Icons.Filled.Lock, "Güvenlik ve yedek", "PIN kilidi ve yedekleme") {
            Spacer(Modifier.size(10.dp))
            AppButton(
                if (vm.settings.pinHash != null) "PIN: açık (değiştir / kaldır)" else "PIN kilidi belirle",
                onClick = { onPin() }, style = BtnStyle.Secondary, modifier = Modifier.fillMaxWidth()
            )
            SettingNote("PIN açıkken uygulama başlarken ve 30 sn’den uzun arka planda kalınca PIN sorulur. Son uygulamalar ekranında içerik gizlenir, ekran görüntüsü alınamaz.")
            SettingDivider()
            Spacer(Modifier.size(10.dp))
            BackupProgressCard(studio)
            Row(horizontalArrangement = SettingButtonGap) {
                AppButton("Yedekle", onClick = { studio.backup() }, style = BtnStyle.Primary, modifier = Modifier.weight(1f))
                AppButton("Geri yükle", onClick = startRestore, style = BtnStyle.Secondary, modifier = Modifier.weight(1f))
            }
            val lastB = vm.settings.lastBackupMs
            Text(
                if (lastB == 0L) "Son yedek: hiç alınmadı"
                else "Son yedek: " + SimpleDateFormat("d MMM yyyy HH:mm", Locale.forLanguageTag("tr")).format(Date(lastB)),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp)
            )
            SettingNote(
                "Sınıf listeleri ve küçük fotoğraflar kayıt klasöründeki “Yedek” klasörüne ${BackupNames.sanitizePrefix(backupPrefix)}_160726_0816.zip gibi yazılır ve doğrulanır. " +
                    "Orijinaller, “Yedeğe orijinalleri de ekle” açıksa yedeğe girer."
            )
            OutlinedTextField(
                value = backupPrefix, onValueChange = { backupPrefix = it.take(BackupNames.MAX_PREFIX) },
                label = { Text("Yedek dosya adı öneki") }, singleLine = true,
                supportingText = { Text("Yalnızca harf, rakam, - ve _ ; “Kaydet” ile geçerli olur") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(12.dp))
            var guide by remember { mutableStateOf(false) }
            SettingExpander("Yeni sürüme veya yeni telefona geçiş", guide) { guide = !guide }
            if (guide) {
                SettingNote(
                    "1. “Yedekle”ye basın; “Yedek alındı ve doğrulandı” mesajını bekleyin.\n" +
                        "2. Yedek dosyası kayıt klasörünüzde durur; uygulamayı silmek onu silmez.\n" +
                        "3. Eski uygulamayı kaldırıp yeni APK’yı kurun (imza değiştiyse güncelleme üstüne kurulmaz).\n" +
                        "4. Açılışta aynı kayıt klasörünü yeniden seçin.\n" +
                        "5. “Geri yükle” ile yedek zip dosyasını seçin; içeriği önce gösterilir, geri yüklemeden önce mevcut verinin kopyası otomatik alınır."
                )
            }
        }
    }
}
