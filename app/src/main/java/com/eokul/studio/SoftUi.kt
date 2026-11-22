package com.eokul.studio

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable

/**
 * Açık zeminli ekranlar için ortak pastel stil (ana ekranla aynı renkler): kart dolgusu ve ana düğme tek yerden gelir,
 * ekranlar renk seçmez.
 */
@Composable
fun softCardColors(): CardColors = CardDefaults.cardColors(containerColor = softColors().group)

/** Ana eylem düğmesi (Kaydet, İçe aktar…): pastel dolgu, koyu yazı. */
@Composable
fun softButtonColors(): ButtonColors = softColors().let {
    ButtonDefaults.buttonColors(containerColor = it.container, contentColor = it.onContainer)
}
