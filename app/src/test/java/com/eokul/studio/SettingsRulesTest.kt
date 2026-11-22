package com.eokul.studio

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRulesTest {
    private fun p(kb: Int? = 5, mkb: Int? = 100, pw: Int? = 133, ph: Int? = 171, dir: String = "Cikti", rx: String = "^\\d{5,11}$") =
        SettingsRules.problem(kb, mkb, pw, ph, dir, rx)

    @Test fun gecerliDegerlerSorunsuz() = assertNull(p())

    @Test fun boyutSiniri() {
        assertTrue(p(kb = 0)!!.contains("Minimum"))
        assertTrue(p(kb = null)!!.contains("Minimum"))
        assertTrue(p(mkb = 4)!!.contains("Maksimum"))
        assertTrue(p(kb = 50, mkb = 20)!!.contains("küçük olamaz"))
    }

    @Test fun olcuSiniri() {
        assertTrue(p(pw = 39)!!.contains("Genişlik"))
        assertTrue(p(ph = 1601)!!.contains("Yükseklik"))
    }

    @Test fun klasorAdi() {
        assertTrue(p(dir = "")!!.contains("boş"))
        assertNotNull(p(dir = "a/b"))
        assertNotNull(p(dir = ".."))
    }

    @Test fun gecersizRegex() = assertTrue(p(rx = "(")!!.contains("regex"))
}
