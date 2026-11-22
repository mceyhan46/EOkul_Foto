package com.eokul.studio

/** Ayarlar formunun doğrulama kuralları (saf; ilk sorunun Türkçe açıklamasını verir, sorun yoksa null). */
object SettingsRules {
    fun problem(kb: Int?, mkb: Int?, pw: Int?, ph: Int?, dir: String, rx: String): String? = when {
        kb == null || kb !in 1..500 -> "Minimum boyut 1–500 KB arasında olmalı"
        mkb == null || mkb !in 5..5000 -> "Maksimum boyut 5–5000 KB arasında olmalı"
        mkb < kb -> "Maksimum boyut, minimum boyuttan küçük olamaz"
        pw == null || pw !in 40..1200 -> "Genişlik 40–1200 piksel arasında olmalı"
        ph == null || ph !in 40..1600 -> "Yükseklik 40–1600 piksel arasında olmalı"
        dir.isEmpty() -> "Çıktı klasörü adı boş olamaz"
        dir.any { it in "\\/:*?\"<>|" } || dir == "." || dir == ".." -> "Çıktı klasörü adı geçersiz karakter içeriyor"
        else -> try {
            Regex(rx)
            null
        } catch (e: Exception) {
            "Dosya adı kuralı (regex) geçersiz"
        }
    }
}
