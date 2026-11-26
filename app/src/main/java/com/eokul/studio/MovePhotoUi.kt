package com.eokul.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * "Fotoğrafı başka öğrenciye taşı" penceresi. 1. adım: sınıf (varsayılan mevcut sınıf) ve aranabilir öğrenci listesi;
 * 2. adım: onay — hedef boşsa taşıma, doluysa "yer değiştir" (önerilen) veya "üzerine yaz" seçimi.
 */
@Composable
fun MovePhotoDialog(studio: StudioViewModel, classId: String, src: Student, onDismiss: () -> Unit) {
    val classes = studio.classes
    var cid by remember { mutableStateOf(classId) }
    var query by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<Pair<String, Student>?>(null) }
    val tgt = target
    if (tgt == null) {
        val cls = classes.firstOrNull { it.id == cid }
        val list = if (cls == null) emptyList() else PhotoMoveRules.candidates(cls, src.no, query, cid == classId)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Fotoğrafı kime taşıyalım?") },
            text = {
                Column {
                    Text(
                        "${src.no} · ${src.name} öğrencisinin fotoğrafı, orijinali ve kırpma bilgisiyle birlikte taşınır.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (classes.size > 1) {
                        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (c in classes) FilterChip(selected = c.id == cid, onClick = { cid = c.id }, label = { Text(c.name) })
                        }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text("Numara veya ad ara") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    if (list.isEmpty()) {
                        Text("Eşleşen öğrenci yok", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    } else {
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(list, key = { it.no }) { st ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { target = cid to st }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(st.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                        Text(st.no, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        if (st.captured) "fotoğrafı var" else "boş",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (st.captured) StatusColors.Warn else StatusColors.Done
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
        )
    } else {
        val (dcid, dst) = tgt
        val modes = PhotoMoveRules.modes(classes, PhotoMoveRules.Ref(dcid, dst.no))
        var mode by remember { mutableStateOf(modes.first()) }
        val dcls = classes.firstOrNull { it.id == dcid }
        val where = if (dcid != classId) " (${dcls?.name ?: "?"})" else ""
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Taşımayı onayla") },
            text = {
                Column {
                    Text("${src.no} · ${src.name}", fontWeight = FontWeight.Bold)
                    Text("↓", style = MaterialTheme.typography.titleMedium)
                    Text("${dst.no} · ${dst.name}$where", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(10.dp))
                    if (modes.size == 1) {
                        Text(
                            "Fotoğraf, orijinali ve kırpma bilgisi ${dst.name} adına taşınır; ${src.name} yeniden “bekliyor” olur.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text("${dst.name} öğrencisinin zaten fotoğrafı var. Ne yapılsın?", style = MaterialTheme.typography.bodyMedium)
                        for (m in modes) {
                            val label = if (m == PhotoMoveRules.Mode.SWAP) "Yer değiştir (önerilen)" else "Üzerine yaz"
                            val hint = if (m == PhotoMoveRules.Mode.SWAP) "İki öğrencinin fotoğrafı birbirine geçer; hiçbiri kaybolmaz."
                            else "${dst.name} öğrencisinin mevcut fotoğrafı silinir."
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = mode == m, role = Role.RadioButton, onClick = { mode = m }).padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = mode == m, onClick = null)
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(label, fontWeight = FontWeight.Medium)
                                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !studio.photoMoveBusy, onClick = {
                    studio.movePhoto(classId, src.no, dcid, dst.no, mode)
                    onDismiss()
                }) { Text(if (mode == PhotoMoveRules.Mode.SWAP) "Yer Değiştir" else if (mode == PhotoMoveRules.Mode.REPLACE) "Üzerine Yaz" else "Taşı") }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Geri") } }
        )
    }
}
