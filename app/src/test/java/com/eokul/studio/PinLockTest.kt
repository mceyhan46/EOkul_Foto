package com.eokul.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLockTest {
    private class Mem : LockStore { override var fails = 0; override var until = 0L }
    private var now = 1_000_000L
    private val store = Mem()
    private val lock = PinLock(store) { now }

    @Test fun ilkDenemelerdeBeklemeYok_sonrasindaArtar() {
        var lastWait = 0
        for (i in 1..8) {
            assertEquals(i, lock.recordFailure())
            val w = lock.waitSeconds()
            assertTrue("deneme $i: bekleme azalmamalı", w >= lastWait || w == 0)
            lastWait = w
        }
        assertTrue(lock.waitSeconds() > 0)
    }

    @Test fun beklemeZamanlaDoluncaBiter() {
        repeat(8) { lock.recordFailure() }
        val w = lock.waitSeconds()
        assertTrue(w > 0)
        now += w * 1000L
        assertEquals(0, lock.waitSeconds())
    }

    @Test fun kalanSureYukariYuvarlanir() {
        store.until = now + 1500
        assertEquals(2, lock.waitSeconds())
    }

    @Test fun dogruPinSayaciSifirlar() {
        repeat(8) { lock.recordFailure() }
        lock.reset()
        assertEquals(0, store.fails)
        assertEquals(0, lock.waitSeconds())
    }

    @Test fun sayacKalicidir_yeniNesneAyniDepoyuOkur() {
        repeat(8) { lock.recordFailure() }
        assertTrue(PinLock(store) { now }.waitSeconds() > 0)
    }

    @Test fun arkaPlandanDonuste_kilitKarari() {
        assertFalse(PinLock.shouldLock(false, 1, 100_000))
        assertFalse(PinLock.shouldLock(true, 0, 100_000))
        assertFalse(PinLock.shouldLock(true, 100_000, 130_000))   // tam 30 sn: kilitlenmez
        assertTrue(PinLock.shouldLock(true, 100_000, 130_001))
    }
}
