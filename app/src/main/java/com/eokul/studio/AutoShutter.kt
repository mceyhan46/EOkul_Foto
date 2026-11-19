package com.eokul.studio

import com.eokul.studio.core.Score

/**
 * Otomatik çekim kararı (Android'siz, test edilebilir). Kamera ekranı her 100 ms'de [step] çağırır; sınıf yüzün hazır ve sabit olmasını,
 * ışık/netlik engelinden vazgeçmeyi, geri sayımı ve "silahlanma"yı (önceki öğrenci çerçevede kalınca hemen çekmemek için) yönetir.
 */
class AutoShutter(startMs: Long) {

    class Step(val eval: LiveEval?, val score: Int, val fire: Boolean)

    /** Yüz bir süre hazır değilse (çerçeve boşaldıysa) true olur; yalnızca o zaman otomatik çekim başlayabilir. */
    var armed = false
        private set
    /** Geri sayım ilerlemesi 0..1. */
    var progress = 0f
        private set

    private var readySince = 0L
    private var noReadySince = startMs
    private var lastCx = -1f
    private var lastCy = -1f
    private var sharpSince = 0L
    private var lightSince = 0L

    /** Öğrenci değişince: otomatik çekim yeniden silahlanmalı. */
    fun reset() {
        armed = false
        progress = 0f
    }

    /** Döngü yeniden başlarken (ayar/kamera değişimi) zamanlayıcılar sıfırlanır; [armed] ve [progress] korunur. */
    fun restartLoop(now: Long) {
        readySince = 0L
        noReadySince = now
        lastCx = -1f
        lastCy = -1f
        sharpSince = 0L
        lightSince = 0L
    }

    /**
     * [face] yoksa (veya bayatsa) null. [evaluate] (netlikten vazgeç, ışıktan vazgeç) ile yüzün hazır olup olmadığını döndürür.
     * [fire] true ise çekim yapılmalı (geri sayım bitti); sınıf kendini silahsızlandırır.
     */
    fun step(
        now: Long, face: LiveFace?, evaluate: (giveUpSharp: Boolean, giveUpLight: Boolean) -> LiveEval,
        autoOn: Boolean, capturing: Boolean, holdMs: Int
    ): Step {
        if (face == null) {
            sharpSince = 0L
            readySince = 0L
            progress = 0f
            lastCx = -1f
            lastCy = -1f
            if (noReadySince == 0L) noReadySince = now
            if (now - noReadySince >= ARM_AFTER_MS) armed = true
            return Step(null, -1, false)
        }
        var ev = evaluate(false, false)
        // Işık engeli yalnızca uyarıdır: 2 sn sonra vazgeçilir (ortam değiştirilemiyorsa çekim kilitlenmesin)
        var lightIgnored = false
        if (ev.lightOnly) {
            if (lightSince == 0L) lightSince = now
            if (now - lightSince > LIGHT_GIVE_UP_MS) {
                lightIgnored = true
                ev = evaluate(false, true)
            }
        } else {
            lightSince = 0L
        }
        // Yalnızca netlik engeliyse 3 sn sonra vazgeç (eşik uygun olmayan ortamda çekimi kilitlememek için)
        if (ev.sharpOnly) {
            if (sharpSince == 0L) sharpSince = now
            if (now - sharpSince > SHARP_GIVE_UP_MS) ev = evaluate(true, lightIgnored)
        } else {
            sharpSince = 0L
        }
        if (lightIgnored && ev.ready) ev = LiveEval(true, "Hazır — ışık ideal değil, sabit durun", ev.dev)
        val score = Score.total(
            ev.dev.toDouble(), face.sharp.toDouble(), Math.abs(face.roll).toDouble(), Math.abs(face.yaw).toDouble(),
            face.bright.toDouble(), face.contrast.toDouble(), face.eyeOpen.toDouble()
        )
        val still = lastCx < 0f || (Math.abs(face.cx - lastCx) < STILL_EPS && Math.abs(face.cy - lastCy) < STILL_EPS)
        lastCx = face.cx
        lastCy = face.cy
        if (!ev.ready || !still) {
            readySince = 0L
            progress = 0f
            if (!ev.ready) {
                if (noReadySince == 0L) noReadySince = now
                if (now - noReadySince >= ARM_AFTER_MS) armed = true
            }
            return Step(ev, score, false)
        }
        if (!autoOn || capturing) return Step(ev, score, false)
        if (!armed) {
            progress = 0f
            return Step(ev, score, false)
        }
        noReadySince = 0L
        if (readySince == 0L) readySince = now
        progress = ((now - readySince) / holdMs.toFloat()).coerceIn(0f, 1f)
        if (progress >= 1f) {
            armed = false
            readySince = 0L
            progress = 0f
            noReadySince = 0L
            return Step(ev, score, true)
        }
        return Step(ev, score, false)
    }

    companion object {
        const val ARM_AFTER_MS = 1000L
        const val LIGHT_GIVE_UP_MS = 2000L
        const val SHARP_GIVE_UP_MS = 3000L
        const val STILL_EPS = 0.03f
    }
}

/** EXIF yön değerini sağ-sol çevirir (kayıpsız çevirme için). 1=normal, 2=yatay ayna, … Bilinmeyen değer 2 olur. */
object ExifFlip {
    fun next(orientation: Int): Int = when (orientation) {
        1 -> 2; 2 -> 1; 3 -> 4; 4 -> 3; 5 -> 8; 8 -> 5; 6 -> 7; 7 -> 6
        else -> 2
    }
}

/** Canlı yüz analizinin saf hesapları. */
object LiveMath {
    /** Ön kamera aynasında x aralığını çevirir: (x1, x2) → (1-x2, 1-x1). */
    fun mirror(x1: Float, x2: Float): Pair<Float, Float> = (1f - x2) to (1f - x1)

    /** Göz çizgisinden yan eğiklik (derece, -90..90); gözler yakınsa veya yoksa ML Kit'in [headRoll] değeri. */
    fun roll(headRoll: Float, leX: Float?, leY: Float?, reX: Float?, reY: Float?): Float {
        if (leX == null || leY == null || reX == null || reY == null || Math.abs(reX - leX) <= 8f) return headRoll
        var ang = Math.toDegrees(Math.atan2((reY - leY).toDouble(), (reX - leX).toDouble())).toFloat()
        if (ang > 90f) ang -= 180f
        if (ang < -90f) ang += 180f
        return ang
    }

    /** İki gözün açıklık olasılığının küçüğü; bilinmiyorsa -1. */
    fun eyeOpen(left: Float?, right: Float?): Float = if (left != null && right != null) Math.min(left, right) else -1f
}

/** Işık durumu titremesin diye aynı sonuç art arda [need] ölçümde görülünce kabul edilir. */
class LightDebouncer(private val need: Int = 3) {
    private var cand = 0
    private var n = 0

    /** Yeni ölçümü işler; kabul edilmiş durum kodu varsa döndürür, yoksa null. */
    fun feed(code: Int): Int? {
        if (code == cand) n++ else { cand = code; n = 1 }
        return if (n >= need) cand else null
    }
}
