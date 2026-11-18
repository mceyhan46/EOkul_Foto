package com.eokul.studio

import com.eokul.studio.core.PinCrypto

/** PIN deneme sayacının kalıcı deposu (uygulamada SharedPreferences; testte bellek). */
interface LockStore {
    var fails: Int
    var until: Long
}

/**
 * PIN kilidi kuralları (Android'siz): yanlış deneme sınırı/bekleme ve arka plandan dönünce kilitleme kararı.
 * Sayaç kalıcıdır; uygulamayı kapatıp açmak beklemeyi sıfırlamaz.
 */
class PinLock(private val store: LockStore, private val clock: () -> Long = System::currentTimeMillis) {

    /** Kalan bekleme (sn); 0 = deneme yapılabilir. */
    fun waitSeconds(): Int {
        val left = store.until - clock()
        return if (left <= 0L) 0 else ((left + 999L) / 1000L).toInt()
    }

    /** Yanlış denemeyi kaydeder; toplam yanlış sayısını döndürür. */
    fun recordFailure(): Int {
        val n = store.fails + 1
        val wait = PinCrypto.waitSecondsAfterFailures(n)
        store.fails = n
        store.until = if (wait > 0) clock() + wait * 1000L else 0L
        return n
    }

    fun reset() {
        store.fails = 0
        store.until = 0L
    }

    companion object {
        const val GRACE_MS = 30_000L

        /** Uygulama [backgroundedAt]'ten beri arka plandaysa ve PIN varsa kilitlenmeli mi? */
        fun shouldLock(hasPin: Boolean, backgroundedAt: Long, now: Long): Boolean =
            hasPin && backgroundedAt != 0L && now - backgroundedAt > GRACE_MS
    }
}
