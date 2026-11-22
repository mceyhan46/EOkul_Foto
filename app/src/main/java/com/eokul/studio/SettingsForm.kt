package com.eokul.studio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/** Ayarlar ekranının düzenlenebilir değerleri (Kaydet'e basılana kadar [Settings]'e işlenmez) ve doğrulaması. */
class SettingsForm(val cur: Settings) {
    val def = Settings()

    var backupPrefix by mutableStateOf(cur.backupPrefix)
    var topGap by mutableStateOf(cur.topGap.toFloat())
    var bottomGap by mutableStateOf(cur.bottomGap.toFloat())
    var leftGap by mutableStateOf(cur.leftGap.toFloat())
    var rightGap by mutableStateOf(cur.rightGap.toFloat())
    var quality by mutableStateOf(cur.jpegQuality.toFloat())
    var minKb by mutableStateOf(cur.minKb.toString())
    var maxKb by mutableStateOf(cur.maxKb.toString())
    var photoW by mutableStateOf(cur.photoW.toString())
    var photoH by mutableStateOf(cur.photoH.toString())
    var hqCap by mutableStateOf(cur.hqCapture)
    var regex by mutableStateOf(cur.nameRegex)
    var outDir by mutableStateOf(cur.outputDir)
    var embed by mutableStateOf(cur.embedSrgb)
    var serial by mutableStateOf(cur.serialMode)
    var autoTol by mutableStateOf(cur.autoTol.toFloat())
    var autoHold by mutableStateOf(cur.autoHoldMs.toFloat())
    var smart by mutableStateOf(cur.smartShot)
    var flipCap by mutableStateOf(cur.flipCapture)
    var flipFront by mutableStateOf(cur.flipFront)
    var autoAcc by mutableStateOf(cur.autoAccept)
    var accScore by mutableStateOf(cur.autoAcceptScore.toFloat())
    var accDelay by mutableStateOf(cur.autoAcceptDelayMs.toFloat())
    var keepOrig by mutableStateOf(cur.keepOriginals)
    var backupOrig by mutableStateOf(cur.backupOriginals)
    var error by mutableStateOf<String?>(null)

    /** Değerleri doğrular; sorun varsa [error] doldurulur ve null döner, yoksa [base] üzerine uygulanmış ayarları verir. */
    fun build(base: Settings): Settings? {
        val kb = minKb.trim().toIntOrNull()
        val mkb = maxKb.trim().toIntOrNull()
        val pw = photoW.trim().toIntOrNull()
        val ph = photoH.trim().toIntOrNull()
        val dir = outDir.trim()
        val rx = regex.trim()
        val problem = SettingsRules.problem(kb, mkb, pw, ph, dir, rx)
        error = problem
        if (problem != null || kb == null || mkb == null || pw == null || ph == null) return null
        return base.copy(
            backupPrefix = com.eokul.studio.core.BackupNames.sanitizePrefix(backupPrefix),
            serialMode = serial,
            autoTol = (autoTol * 100.0).roundToInt() / 100.0,
            autoHoldMs = ((autoHold / 100f).roundToInt() * 100).coerceIn(400, 4000),
            keepOriginals = keepOrig,
            backupOriginals = backupOrig,
            smartShot = smart,
            flipCapture = flipCap,
            flipFront = flipFront,
            autoAccept = autoAcc,
            autoAcceptScore = accScore.roundToInt().coerceIn(60, 95),
            autoAcceptDelayMs = ((accDelay / 100f).roundToInt() * 100).coerceIn(700, 4000),
            topGap = (topGap * 100.0).roundToInt() / 100.0,
            bottomGap = (bottomGap * 100.0).roundToInt() / 100.0,
            leftGap = (leftGap * 100.0).roundToInt() / 100.0,
            rightGap = (rightGap * 100.0).roundToInt() / 100.0,
            jpegQuality = quality.roundToInt(),
            minKb = kb,
            maxKb = mkb,
            photoW = pw,
            photoH = ph,
            hqCapture = hqCap,
            embedSrgb = embed,
            nameRegex = rx,
            outputDir = dir
        )
    }
}
