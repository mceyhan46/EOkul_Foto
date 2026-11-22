package com.eokul.studio

import com.eokul.studio.RootRules.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootRulesTest {
    private val tree = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"

    @Test fun saklananDeger_eskiVeYeniBicim() {
        val old = RootRules.decode(tree)
        assertEquals(tree, old.base); assertNull(old.sub)
        val enc = RootRules.encode(tree, "EOkul_Studio")
        assertEquals("$tree#EOkul_Studio", enc)
        val r = RootRules.decode(enc)
        assertEquals(tree, r.base); assertEquals("EOkul_Studio", r.sub)
        assertEquals(tree, RootRules.encode(tree, null))
        assertEquals(tree, RootRules.encode(tree, ""))
        assertNull(RootRules.decode("$tree#").sub)
    }

    @Test fun altKlasorSecimi() {
        assertNull(RootRules.chooseSub("EOkul_Studio"))
        assertNull(RootRules.chooseSub("eokul_studio"))
        assertEquals("EOkul_Studio", RootRules.chooseSub("Documents"))
        assertEquals("EOkul_Studio", RootRules.chooseSub(null))
    }

    @Test fun durumKarari_oncelikSirasi() {
        assertEquals(State.NOT_SET, RootRules.status(false, true, true, true))
        assertEquals(State.PERMISSION_LOST, RootRules.status(true, false, true, true))
        assertEquals(State.MISSING, RootRules.status(true, true, false, true))
        assertEquals(State.READ_ONLY, RootRules.status(true, true, true, false))
        assertEquals(State.READY, RootRules.status(true, true, true, true))
    }

    @Test fun sorunMetni_yalnizcaReadyDegilken() {
        assertNull(RootRules.problem(State.READY))
        for (s in State.values()) if (s != State.READY) assertTrue(RootRules.problem(s)!!.isNotBlank())
    }

    @Test fun gosterilenYol() {
        assertEquals("Dahili depolama/Documents/EOkul_Studio", RootRules.label("primary:Documents", "EOkul_Studio"))
        assertEquals("Dahili depolama/Documents/EOkul_Studio", RootRules.label("primary:Documents/EOkul_Studio", null))
        assertEquals("Dahili depolama/Documents/EOkul_Studio", RootRules.label("primary:Documents/EOkul_Studio", "EOkul_Studio"))
        assertEquals("SD kart/Okul", RootRules.label("1A2B-3C4D:Okul", null))
        assertEquals("Dahili depolama", RootRules.label("primary:", null))
        assertEquals("EOkul_Studio", RootRules.label(null, "EOkul_Studio"))
        assertEquals("Kayıt klasörü", RootRules.label(null, null))
    }

    @Test fun eskiKayitKlasoruTanima() {
        assertTrue(RootRules.looksLikeStudioRoot(mapOf("Yedek" to emptyList(), "Foto" to emptyList()), "Yedek", "Orijinaller"))
        assertTrue(RootRules.looksLikeStudioRoot(mapOf("9-A" to listOf("Orijinaller")), "Yedek", "Orijinaller"))
        assertFalse(RootRules.looksLikeStudioRoot(mapOf("Resimler" to listOf("2024")), "Yedek", "Orijinaller"))
        assertFalse(RootRules.looksLikeStudioRoot(emptyMap(), "Yedek", "Orijinaller"))
    }

    @Test fun kokDizinKipi() {
        val v = "file:///storage/emulated/0/EOkul_Studio"
        val r = RootRules.decode(v)
        assertEquals(v, r.base); assertNull(r.sub)
        assertTrue(RootRules.isFileRoot(r.base))
        assertFalse(RootRules.isFileRoot(tree))
        assertEquals("Dahili depolama/EOkul_Studio", RootRules.fileLabel("/storage/emulated/0/EOkul_Studio"))
        assertEquals("Dahili depolama", RootRules.fileLabel("/storage/emulated/0"))
        assertEquals("/storage/1A2B-3C4D/Okul", RootRules.fileLabel("/storage/1A2B-3C4D/Okul"))
        assertEquals("Kayıt klasörü", RootRules.fileLabel(null))
    }
}
