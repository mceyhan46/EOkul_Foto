package com.eokul.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(studio: StudioViewModel) {
    val rootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) studio.setRootFolder(uri)
    }
    var newClassOpen by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var alsoFilesClass by remember { mutableStateOf(false) }

    val st = homeStats(studio.classes)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Üst başlık: düz zemin, durum çubuğunun arkasına uzanır
            Row(
                Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "E-Okul Foto Studio",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Fotoğraf paneli", style = MaterialTheme.typography.titleLarge)
                }
                var toolsMenu by remember { mutableStateOf(false) }
                Box {
                    MoreBtn(onClick = { toolsMenu = true })
                    DropdownMenu(expanded = toolsMenu, onDismissRequest = { toolsMenu = false }) {
                        DropdownMenuItem(text = { Text("📋 Çoklu sınıf listesi (Excel)") }, onClick = {
                            toolsMenu = false
                            studio.navigate(Screen.MultiImport)
                        })
                        DropdownMenuItem(text = { Text("📁 Tüm sınıflar ZIP (klasörlü + rapor)") }, enabled = studio.classes.isNotEmpty(), onClick = {
                            toolsMenu = false
                            studio.exportAllFoldersZip()
                        })
                        DropdownMenuItem(text = { Text("🛠 Eksik klasör dosyalarını onar") }, enabled = studio.classes.isNotEmpty(), onClick = {
                            toolsMenu = false
                            studio.repairFolderFiles()
                        })
                        DropdownMenuItem(text = { Text("🩺 Veri denetimi") }, enabled = studio.classes.isNotEmpty(), onClick = {
                            toolsMenu = false
                            studio.auditData()
                        })
                        DropdownMenuItem(text = { Text("✂ Toplu kırpma (hazır fotoğraflar)") }, onClick = {
                            toolsMenu = false
                            studio.navigate(Screen.Batch)
                        })
                    }
                }
                SettingsBtn(onClick = { studio.navigate(Screen.Settings) })
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (studio.settings.rootUri == null || !studio.hasRootAccess()) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    if (studio.settings.rootUri == null) "Kayıt klasörü seçilmedi" else "Kayıt klasörü izni kaybolmuş",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    "Fotoğraflar seçeceğiniz klasörün altında sınıf klasörlerine kaydedilir.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(Modifier.size(10.dp))
                                AppButton("Klasör seç", onClick = { rootPicker.launch(null) }, style = BtnStyle.Primary)
                            }
                        }
                    }
                }

                if (studio.classes.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Henüz sınıf yok", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "Sınıf ekleyin, ardından e-Okul veya Excel’den kopyaladığınız numara + ad soyad listesini yapıştırın.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.size(16.dp))
                            AppButton("İlk sınıfı ekle", onClick = { newClassOpen = true }, style = BtnStyle.Primary, icon = Icons.Filled.Add)
                        }
                    }
                } else {
                    item { HeroCard(st, onContinue = { st.next?.let { studio.startSerial(it.id) } }) }
                    item { KpiRow(st, studio.settings.lastBackupMs) }
                    item {
                        AttentionSection(
                            st, studio.settings.lastBackupMs,
                            onReview = { st.flaggedClass?.let { studio.navigate(Screen.ClassDetail(it.id)) } },
                            onAudit = { studio.auditData() },
                            onBackup = { studio.backup() }
                        )
                    }
                    item {
                        Text(
                            "Sınıflar", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(studio.classes, key = { it.id }) { c ->
                        ClassCard(
                            c,
                            onOpen = { studio.navigate(Screen.ClassDetail(c.id)) },
                            onRename = { renameId = c.id },
                            onDelete = { deleteId = c.id }
                        )
                    }
                }
            }
            Text(
                "Copyright © 2026 Hacimd. All rights reserved.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        if (studio.classes.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { newClassOpen = true },
                // Alttaki telif yazısının (≈28 dp) üstünde kalsın
                modifier = Modifier.align(Alignment.BottomEnd).padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 44.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Sınıf ekle")
            }
        }
    }

    studio.auditReport?.let { rep ->
        AlertDialog(
            onDismissRequest = { studio.dismissAudit() },
            title = { Text("Veri denetimi") },
            text = { Text(rep, modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) },
            confirmButton = { TextButton(onClick = { studio.dismissAudit() }) { Text("Tamam") } }
        )
    }

    if (newClassOpen) {
        NameDialog(
            title = "Yeni sınıf",
            label = "Sınıf adı (örn. 9-A)",
            initial = "",
            confirmText = "Devam",
            onDismiss = { newClassOpen = false },
            onConfirm = { name ->
                val id = studio.addClass(name)
                if (id == null) {
                    "Ad boş veya bu adla bir sınıf var"
                } else {
                    newClassOpen = false
                    studio.navigate(Screen.Import(id))
                    null
                }
            }
        )
    }
    val rn = renameId?.let { studio.classById(it) }
    if (rn != null) {
        NameDialog(
            title = "Sınıfı yeniden adlandır",
            label = "Sınıf adı",
            initial = rn.name,
            confirmText = "Kaydet",
            onDismiss = { renameId = null },
            onConfirm = { name ->
                if (studio.renameClass(rn.id, name)) {
                    renameId = null
                    null
                } else {
                    "Geçersiz veya kullanılan ad"
                }
            }
        )
    }
    val del = deleteId?.let { studio.classById(it) }
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Sınıf silinsin mi?") },
            text = {
                Column {
                    Text("“${del.name}” ve ${del.students.size} öğrencilik listesi uygulamadan silinir.")
                    Row(
                        Modifier.fillMaxWidth().clickable { alsoFilesClass = !alsoFilesClass }.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(checked = alsoFilesClass, onCheckedChange = { alsoFilesClass = it })
                        Text(
                            "Kayıt klasöründeki fotoğraf dosyalarını (Orijinaller dahil) da sil",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (alsoFilesClass) Text(
                        "Bu işlem geri alınamaz.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    studio.deleteClass(del.id, alsoFilesClass)
                    deleteId = null
                    alsoFilesClass = false
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Vazgeç") } }
        )
    }
}

private class HomeStats(
    val total: Int,
    val done: Int,
    val retake: Int,
    /** Çekilmiş ama kalite uyarılı (düşük puan ya da tekrar işaretli) fotoğraf sayısı. */
    val flagged: Int,
    val avg: Int?,
    /** Birden çok sınıfta bulunan öğrenci numarası sayısı. */
    val dups: Int,
    val next: SchoolClass?,
    val flaggedClass: SchoolClass?
) {
    val missing: Int get() = total - done
}

private fun homeStats(classes: List<SchoolClass>): HomeStats {
    var total = 0
    var done = 0
    var retake = 0
    var flagged = 0
    var sum = 0
    var n = 0
    var flaggedClass: SchoolClass? = null
    for (c in classes) {
        for (s in c.students) {
            total++
            if (s.captured) {
                done++
                if (s.score >= 0) {
                    sum += s.score
                    n++
                }
            }
            if (s.retake) retake++
            if (s.captured && (s.retake || s.score in 0 until 70)) {
                flagged++
                if (flaggedClass == null) flaggedClass = c
            }
        }
    }
    val dups = classes.flatMap { c -> c.students.map { it.no }.distinct() }
        .groupingBy { it }.eachCount().count { it.value > 1 }
    return HomeStats(
        total, done, retake, flagged, if (n > 0) sum / n else null, dups,
        classes.firstOrNull { c -> c.students.any { !it.captured } }, flaggedClass
    )
}

private const val WEEK_MS = 7L * 24 * 3600 * 1000

private fun backupStale(st: HomeStats, last: Long): Boolean =
    st.done > 0 && (last == 0L || System.currentTimeMillis() - last > WEEK_MS)

@Composable
private fun HeroCard(st: HomeStats, onContinue: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val frac = if (st.total == 0) 0f else st.done.toFloat() / st.total
    AppCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = 12.dp.toPx()
                        val topLeft = Offset(sw / 2, sw / 2)
                        val arc = Size(size.width - sw, size.height - sw)
                        drawArc(
                            color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            topLeft = topLeft, size = arc, style = Stroke(width = sw)
                        )
                        if (frac > 0f) drawArc(
                            color = primary, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                            topLeft = topLeft, size = arc, style = Stroke(width = sw, cap = StrokeCap.Round)
                        )
                    }
                    Text("%" + (frac * 100).toInt(), style = MaterialTheme.typography.titleLarge)
                }
                Column {
                    Text("Çekilen öğrenci", style = MaterialTheme.typography.bodySmall, color = muted)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)) { append(st.done.toString()) }
                            withStyle(SpanStyle(fontSize = 18.sp, color = muted)) { append(" / " + st.total) }
                        }
                    )
                    Text(
                        if (st.missing == 0) "Hepsi tamam" else "${st.missing} öğrenci kaldı",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            val next = st.next
            AppButton(
                text = if (next != null) "Çekime devam et · ${next.name}" else "Tüm çekimler tamam",
                onClick = onContinue,
                style = BtnStyle.Primary,
                icon = UiIcons.Camera,
                enabled = next != null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun KpiTile(label: String, value: String, note: String, noteColor: Color, modifier: Modifier) {
    AppCard(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
            Text(note, style = MaterialTheme.typography.labelMedium, color = noteColor, maxLines = 1)
        }
    }
}

@Composable
private fun KpiRow(st: HomeStats, lastBackup: Long) {
    val ok = okColor()
    val warn = warnColor()
    val bad = MaterialTheme.colorScheme.error
    val avg = st.avg
    val (avgNote, avgColor) = when {
        avg == null -> "Henüz yok" to MaterialTheme.colorScheme.onSurfaceVariant
        avg >= 85 -> "İyi" to ok
        avg >= 70 -> "Orta" to warn
        else -> "Düşük" to bad
    }
    val stale = backupStale(st, lastBackup)
    val backupValue = when {
        lastBackup == 0L -> "Hiç"
        else -> {
            val days = ((System.currentTimeMillis() - lastBackup) / (24L * 3600 * 1000)).toInt()
            if (days <= 0) "Bugün" else "$days g"
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiTile("Ort. kalite", avg?.toString() ?: "—", avgNote, avgColor, Modifier.weight(1f))
        KpiTile(
            "Uyarılı", st.flagged.toString(),
            if (st.flagged > 0) "İncele" else "Temiz", if (st.flagged > 0) warn else ok, Modifier.weight(1f)
        )
        KpiTile("Son yedek", backupValue, if (stale) "Yenile" else "Güncel", if (stale) warn else ok, Modifier.weight(1f))
    }
}

@Composable
private fun AttentionRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    text: String,
    action: String?,
    onClick: (() -> Unit)?
) {
    AppCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (action != null) Text(
                action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AttentionSection(
    st: HomeStats,
    lastBackup: Long,
    onReview: () -> Unit,
    onAudit: () -> Unit,
    onBackup: () -> Unit
) {
    val warn = warnColor()
    val warnBg = MaterialTheme.colorScheme.tertiaryContainer
    val info = MaterialTheme.colorScheme.primary
    val infoBg = MaterialTheme.colorScheme.primaryContainer
    val stale = backupStale(st, lastBackup)
    val any = st.flagged > 0 || st.dups > 0 || stale
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dikkat gerektirenler", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        if (!any) {
            AttentionRow(Icons.Filled.Check, infoBg, info, "Her şey yolunda", null, null)
        }
        if (st.flagged > 0) {
            AttentionRow(Icons.Filled.Warning, warnBg, warn, "${st.flagged} fotoğraf kalite uyarılı", "İncele", onReview)
        }
        if (st.dups > 0) {
            AttentionRow(Icons.Filled.Info, infoBg, info, "${st.dups} numara birden çok sınıfta var", "Denetle", onAudit)
        }
        if (stale) {
            AttentionRow(
                Icons.Filled.Refresh, warnBg, warn,
                if (lastBackup == 0L) "Henüz yedek alınmadı" else "Son yedek 7 günden eski", "Yedekle", onBackup
            )
        }
    }
}

@Composable
private fun ClassCard(
    c: SchoolClass,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val total = c.students.size
    val done = c.capturedCount
    val missing = total - done
    val retake = c.students.count { it.retake }
    val complete = total > 0 && missing == 0 && retake == 0
    val ok = okColor()
    val warn = warnColor()
    val barColor = when {
        complete -> ok
        retake > 0 -> warn
        else -> MaterialTheme.colorScheme.primary
    }
    val frac = if (total == 0) 0f else done.toFloat() / total
    val sub = when {
        total == 0 -> "Liste boş"
        complete -> "✓ Tamamlandı · $total öğrenci"
        else -> "$done / $total çekildi · $missing eksik" + if (retake > 0) " · $retake tekrar" else ""
    }
    AppCard(onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 14.dp, end = 0.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    sub, style = MaterialTheme.typography.bodySmall,
                    color = if (complete) ok else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(barColor))
                }
            }
            Box {
                MoreBtn(onClick = { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Yeniden adlandır") }, onClick = {
                        menu = false
                        onRename()
                    })
                    DropdownMenuItem(text = { Text("Sınıfı sil") }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        }
    }
}

/** Tek satırlık ad girişi diyaloğu. [onConfirm] hata metni döndürürse gösterilir, null ise kapatma çağıran tarafa aittir. */
@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> String?
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null },
                    label = { Text(label) }, singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { error = onConfirm(text) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
