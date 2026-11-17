package com.eokul.studio

import com.eokul.studio.core.RosterParser
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Dışa aktarma kuralları (Android'siz, test edilebilir): hangi fotoğraflar hangi ZIP girdi adıyla yazılır, yinelenen numaralar,
 * rapor.csv içeriği ve ZIP yazımı. Dosyayı nereye yazacağı (SAF) StudioViewModel'in işidir.
 */
object ExportService {

    /** ZIP'e girecek (girdi adı, kaynak dosya) çiftleri ve atlanan yinelenen numaralar. */
    class Plan(val items: List<Pair<String, File>>, val duplicates: List<String>)

    /**
     * [folders] true ise `Sınıf/12345-Ad Soyad.jpg` düzeni (numara yalnızca sınıf içinde tek olmalı);
     * false ise düz `12345.jpg` (numara tüm okulda tek olmalı; yinelenenler atlanır ve bildirilir).
     * [photo] bir öğrencinin uygulama içi 133x171 kopyasını verir; yoksa öğrenci atlanır.
     */
    fun plan(snap: List<SchoolClass>, folders: Boolean, photo: (SchoolClass, Student) -> File): Plan {
        val seen = HashSet<String>()
        val dups = ArrayList<String>()
        val items = ArrayList<Pair<String, File>>()
        for (cls in snap) for (st in cls.students) {
            if (!st.captured) continue
            val f = photo(cls, st)
            if (!f.exists()) continue
            val key = if (folders) cls.id + "|" + st.no else st.no
            if (!seen.add(key)) {
                dups.add("${st.no} (${cls.name})")
                continue
            }
            val entry = if (folders) RosterParser.sanitizeFileName(cls.name) + "/" + RosterParser.studentStem(st.no, st.name) + ".jpg"
            else st.no + ".jpg"
            items.add(entry to f)
        }
        return Plan(items, dups)
    }

    fun csvCell(v: String): String {
        val t = v.replace("\"", "\"\"")
        return if (t.contains(';') || t.contains('"') || t.contains('\n')) "\"$t\"" else t
    }

    /** Excel'in Türkçe karakterleri doğru açması için UTF-8 BOM + noktalı virgül. */
    fun reportCsv(snap: List<SchoolClass>): String {
        val sb = StringBuilder("﻿Sınıf;No;Ad Soyad;Durum;Kalite puanı;Tekrar çekilmeli\r\n")
        for (cls in snap) for (st in cls.students) {
            sb.append(csvCell(cls.name)).append(';').append(csvCell(st.no)).append(';')
                .append(csvCell(st.name)).append(';')
                .append(if (st.captured) "Çekildi" else "Bekliyor").append(';')
                .append(if (st.captured && st.score >= 0) st.score.toString() else "").append(';')
                .append(if (st.retake) "Evet" else "").append("\r\n")
        }
        return sb.toString()
    }

    /** ZIP'i yazar; [withReport] true ise ilk girdi `rapor.csv`. */
    fun writeZip(out: OutputStream, plan: Plan, snap: List<SchoolClass>, withReport: Boolean) {
        ZipOutputStream(out).use { zip ->
            if (withReport) {
                zip.putNextEntry(ZipEntry("rapor.csv"))
                zip.write(reportCsv(snap).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            for ((entry, f) in plan.items) {
                zip.putNextEntry(ZipEntry(entry))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun eokulZipName(className: String): String = RosterParser.sanitizeFileName(className) + "-eokul.zip"
}
