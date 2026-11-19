package com.eokul.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoShutterTest {
    private fun face(cx: Float = 0.5f, cy: Float = 0.5f) =
        LiveFace(cx - 0.1f, cy - 0.1f, cx + 0.1f, cy + 0.1f, 0f, 0f, 0L, 1f, 0.1f, 120f, 40f, 0)

    private val ready = { _: Boolean, _: Boolean -> LiveEval(true, "Hazır", 0f) }
    private val notReady = { _: Boolean, _: Boolean -> LiveEval(false, "Hazır değil", 0f) }

    /** t ms'de bir adım; (fire?) */
    private fun AutoShutter.at(t: Long, f: LiveFace? = face(), ev: (Boolean, Boolean) -> LiveEval = ready, auto: Boolean = true, capturing: Boolean = false) =
        step(t, f, ev, auto, capturing, 900)

    @Test fun silahlanmadanOnceCekimYok_hazirDegilAnindaSilahlanir() {
        val s = AutoShutter(1)
        for (t in 100..3000L step 100) assertFalse(s.at(t).fire)     // hazır ama silahsız: hiç çekmez
        assertFalse(s.armed)
        s.at(3100, ev = notReady)       // hazır olmama başladı; başlangıçtan beri >1 sn geçtiği için silahlanır
        assertTrue(s.armed)
    }

    @Test fun yuzYokken1snSonraSilahlanir() {
        val s = AutoShutter(1)
        assertNull(s.at(500, f = null).eval)
        assertEquals(-1, s.at(500, f = null).score)
        assertFalse(s.armed)
        s.at(1001, f = null)
        assertTrue(s.armed)
    }

    @Test fun hazirVeSabit_geriSayimBitinceTekSeferCeker() {
        val s = AutoShutter(1)
        s.at(1001, f = null)                       // silahlan
        var fired = 0
        var t = 1100L
        var firstFire = 0L
        while (t < 4000) {
            val r = s.at(t)
            if (r.fire) { fired++; if (firstFire == 0L) firstFire = t }
            t += 100
        }
        assertEquals(1, fired)                      // çekimden sonra silahsız: tekrar çekmez
        assertEquals(1100 + 900L, firstFire)        // 900 ms bekleme
        assertFalse(s.armed)
        assertEquals(0f, s.progress, 0f)
    }

    @Test fun hareketedinceGeriSayimSifirlanir() {
        val s = AutoShutter(1)
        s.at(1001, f = null)
        s.at(1100); s.at(1500)
        assertTrue(s.progress > 0f)
        s.at(1600, f = face(0.7f, 0.5f))            // yüz kaydı
        assertEquals(0f, s.progress, 0f)
    }

    @Test fun otomatikKapaliVeyaCekimdeyken_cekmez() {
        val s = AutoShutter(1)
        s.at(1001, f = null)
        for (t in 1100..3000L step 100) {
            assertFalse(s.at(t, auto = false).fire)
            assertFalse(s.at(t, capturing = true).fire)
        }
    }

    @Test fun isikEngeli2snSonraVazgecilir() {
        val s = AutoShutter(1)
        s.at(1001, f = null)
        val calls = ArrayList<Pair<Boolean, Boolean>>()
        val ev = { gs: Boolean, gl: Boolean ->
            calls.add(gs to gl)
            if (gl) LiveEval(true, "Hazır", 0f) else LiveEval(false, "Işık", 0f, lightOnly = true)
        }
        assertFalse(s.at(1100, ev = ev).eval!!.ready)
        assertFalse(s.at(3000, ev = ev).eval!!.ready)           // 1900 ms: henüz değil
        val r = s.at(3200, ev = ev)                              // >2000 ms: vazgeç
        assertTrue(r.eval!!.ready)
        assertTrue(r.eval.hint.contains("ışık ideal değil"))
        assertTrue(calls.contains(false to true))
    }

    @Test fun netlikEngeli3snSonraVazgecilir() {
        val s = AutoShutter(1)
        val ev = { gs: Boolean, _: Boolean ->
            if (gs) LiveEval(true, "Hazır", 0f) else LiveEval(false, "Netlik", 0f, sharpOnly = true)
        }
        assertFalse(s.at(100, ev = ev).eval!!.ready)
        assertFalse(s.at(3100, ev = ev).eval!!.ready)           // tam 3000 ms: >3000 değil
        assertTrue(s.at(3200, ev = ev).eval!!.ready)
    }

    @Test fun ogrenciDegisince_yenidenSilahlanmaGerekir() {
        val s = AutoShutter(1)
        s.at(1001, f = null)
        assertTrue(s.armed)
        s.reset()
        assertFalse(s.armed)
        assertEquals(0f, s.progress, 0f)
    }

    @Test fun exifCevirme() {
        assertEquals(2, ExifFlip.next(1)); assertEquals(1, ExifFlip.next(2))
        assertEquals(7, ExifFlip.next(6)); assertEquals(6, ExifFlip.next(7))
        assertEquals(2, ExifFlip.next(0))
        for (o in 1..8) assertEquals(o, ExifFlip.next(ExifFlip.next(o)))   // iki kez çevirmek eski hâli verir
    }

    @Test fun canliHesaplar() {
        val m = LiveMath.mirror(0.2f, 0.5f)
        assertEquals(0.5f, m.first, 1e-6f); assertEquals(0.8f, m.second, 1e-6f)
        assertEquals(7f, LiveMath.roll(7f, null, null, null, null), 0f)          // landmark yok
        assertEquals(7f, LiveMath.roll(7f, 100f, 50f, 105f, 50f), 0f)             // gözler çok yakın
        assertEquals(0f, LiveMath.roll(7f, 100f, 50f, 200f, 50f), 1e-4f)          // düz göz çizgisi
        assertEquals(45f, LiveMath.roll(0f, 100f, 50f, 200f, 150f), 1e-3f)
        assertEquals(0.4f, LiveMath.eyeOpen(0.9f, 0.4f), 0f)
        assertEquals(-1f, LiveMath.eyeOpen(null, 0.4f), 0f)
    }

    @Test fun isikTitremesiFiltrelenir() {
        val d = LightDebouncer()
        assertNull(d.feed(1)); assertNull(d.feed(1))
        assertEquals(1, d.feed(1))
        assertNull(d.feed(2))                    // yeni aday: sayaç sıfırlanır
        assertNull(d.feed(2))
        assertEquals(2, d.feed(2))
    }
}
