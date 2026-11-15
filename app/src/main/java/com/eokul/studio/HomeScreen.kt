package com.eokul.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height

@Composable
fun HomeScreen(studio: StudioViewModel) {
    val rootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) studio.setRootFolder(uri)
    }
    var newClassOpen by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var alsoFilesClass by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
                Row(
                    Modifier.windowInsetsPadding(WindowInsets.statusBars)
                        .fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(
                            "E-Okul Foto Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    var toolsMenu by remember { mutableStateOf(false) }
                    Box {
                        MoreBtn(onClick = { toolsMenu = true }, tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
                    SettingsBtn(onClick = { studio.navigate(Screen.Settings) }, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            if (studio.settings.rootUri == null || !studio.hasRootAccess()) {
                Card(
                    Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (studio.settings.rootUri == null) "Kayıt klasörü seçilmedi" else "Kayıt klasörü izni kaybolmuş",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Fotoğraflar seçeceğiniz klasörün altında sınıf klasörlerine kaydedilir.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.size(6.dp))
                        Button(onClick = { rootPicker.launch(null) }) { Text("📂 Klasör seç") }
                    }
                }
            }

            if (studio.classes.isNotEmpty()) SummaryCard(studio)

            if (studio.classes.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Henüz sınıf yok", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Sınıf ekleyin, ardından e-Okul veya Excel’den kopyaladığınız numara + ad soyad listesini yapıştırın.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = { newClassOpen = true }, colors = ButtonDefaults.buttonColors(containerColor = softColors().container, contentColor = softColors().onContainer)) { Text("＋ İlk sınıfı ekle") }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp, bottom = 112.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 44.dp),
                containerColor = softColors().container,
                contentColor = softColors().onContainer,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp)
            ) { Text("＋ Sınıf ekle", fontWeight = FontWeight.SemiBold) }
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

/** İnce özet şeridi: kutu yok; çekilen/toplam, yüzde, ilerleme çizgisi ve tek satır bilgi. */
@Composable
private fun SummaryCard(studio: StudioViewModel) {
    val classes = studio.classes
    val total = classes.sumOf { it.students.size }
    val done = classes.sumOf { it.capturedCount }
    val missing = total - done
    val retake = classes.sumOf { c -> c.students.count { it.retake } }
    val last = studio.settings.lastBackupMs
    val backupText = if (last == 0L) "Yedek yok"
    else "Yedek " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.forLanguageTag("tr")).format(java.util.Date(last))
    val stale = done > 0 && (last == 0L || System.currentTimeMillis() - last > 7L * 24 * 3600 * 1000)
    val frac = if (total == 0) 0f else done.toFloat() / total
    val soft = softColors()
    val fg = soft.onContainer
    // Pastel dolgulu blok: sınıf kartlarından (beyaz) ayrı, genel bir özet olduğu hemen anlaşılır.
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .background(soft.container)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)) { append("$done") }
                    withStyle(SpanStyle(color = soft.onContainerMuted)) { append(" / $total çekildi") }
                },
                color = fg,
                modifier = Modifier.weight(1f)
            )
            Text("%${(frac * 100).toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
        }
        Spacer(Modifier.size(8.dp))
        LinearProgressIndicator(
            progress = { frac },
            color = soft.bar,
            trackColor = soft.barTrack,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "${classes.size} sınıf · $missing eksik" + (if (retake > 0) " · $retake tekrar" else "") +
                " · $backupText" + (if (stale) " (yedek önerilir)" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = if (stale) soft.warnText else soft.onContainerMuted
        )
    }
}

private val DoneGreen = StatusColors.Done
private val WarnOrange = StatusColors.Warn

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
    val barColor = when {
        complete -> DoneGreen
        retake > 0 -> WarnOrange
        else -> MaterialTheme.colorScheme.primary
    }
    val sub = when {
        total == 0 -> "Liste boş"
        complete -> "$total öğrenci · tamamlandı"
        else -> "$done/$total çekildi · $missing eksik" + if (retake > 0) " · $retake tekrar" else ""
    }

    Card(Modifier.fillMaxWidth().clickable { onOpen() }) {
        Column(Modifier.padding(start = 14.dp, end = 0.dp, top = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        sub, style = MaterialTheme.typography.bodySmall,
                        color = if (complete) DoneGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                color = barColor,
                modifier = Modifier.fillMaxWidth().padding(end = 14.dp).height(6.dp).clip(CircleShape)
            )
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
