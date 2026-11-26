package com.eokul.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Tek öğrenci için "dosyadan fotoğraf ekle/değiştir" onayı (fotoğrafı olan öğrencide eskinin yerine geçeceğini söyler). */
@Composable
fun ReplaceFromFileDialog(st: Student, onPick: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fotoğraf değiştirilsin mi?") },
        text = { Text("${st.no} · ${st.name} öğrencisinin mevcut fotoğrafı ve orijinali, seçeceğiniz dosyayla değiştirilecek.") },
        confirmButton = { TextButton(onClick = onPick) { Text("Dosya Seç") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

/**
 * Toplu ekleme önizlemesi: dosya adındaki numaraya göre eşleşen öğrenciler işaretli gelir; fotoğrafı olanlar, çakışanlar
 * ve eşleşmeyen dosyalar açıkça belirtilir. Onaylanınca seçilenler sırayla inceleme ekranından geçirilip kaydedilir.
 */
@Composable
fun BatchImportDialog(plan: PhotoImportRules.Plan, onConfirm: (List<Pair<String, PhotoImportRules.File>>) -> Unit, onDismiss: () -> Unit) {
    val sel = remember(plan) { mutableStateListOf<Boolean>().also { l -> plan.matches.forEach { l.add(it.selected) } } }
    val chosen = plan.matches.filterIndexed { i, _ -> sel[i] }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fotoğrafları eşleştir") },
        text = {
            Column {
                Text(
                    "${plan.matches.size} dosya öğrencilerle eşleşti" + if (plan.unmatched.isNotEmpty()) ", ${plan.unmatched.size} dosya eşleşmedi." else ".",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Eşleştirme dosya adındaki numaraya göre yapılır (örn. 12345.jpg veya 12345-Ad Soyad.jpg). İşaretli olanlar sırayla kırpma ekranından geçip kaydedilir.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    itemsIndexed(plan.matches) { i, m ->
                        Row(Modifier.fillMaxWidth().clickable { sel[i] = !sel[i] }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = sel[i], onCheckedChange = { sel[i] = it })
                            Column(Modifier.weight(1f)) {
                                Text("${m.no} · ${m.studentName}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(m.file.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                m.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = StatusColors.Warn) }
                            }
                        }
                        HorizontalDivider()
                    }
                    if (plan.unmatched.isNotEmpty()) {
                        item {
                            Spacer(Modifier.size(10.dp))
                            Text("Eşleşmeyen dosyalar", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Text(
                                plan.unmatched.take(8).joinToString("\n") { it.name } + if (plan.unmatched.size > 8) "\n… +${plan.unmatched.size - 8}" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Numarası listede yok veya dosya adından anlaşılamıyor. Bu öğrencilere tek tek “Dosyadan fotoğraf ekle” ile ekleyebilirsiniz.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = { onConfirm(PhotoImportRules.queue(chosen).map { it }) }) {
                Text("Seçilenleri Ekle (${PhotoImportRules.queue(chosen).size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
