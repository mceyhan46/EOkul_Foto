package com.eokul.studio.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class PinCryptoTest {
    @Test public void hashVerifiesAndRejectsWrongPin() {
        String h = PinCrypto.hash("1234", "salt1");
        assertTrue(h.startsWith("pbkdf2$" + PinCrypto.ITERATIONS + "$"));
        assertTrue(PinCrypto.verify("1234", "salt1", h));
        assertFalse(PinCrypto.verify("1235", "salt1", h));
        assertFalse(PinCrypto.verify("1234", "salt2", h));
    }

    @Test public void legacyFormatStillVerifiesAndIsFlagged() {
        String legacy = PinCrypto.legacy("4321", "s");
        assertTrue(PinCrypto.isLegacy(legacy));
        assertTrue(PinCrypto.verify("4321", "s", legacy));
        assertFalse(PinCrypto.verify("0000", "s", legacy));
        assertFalse(PinCrypto.isLegacy(PinCrypto.hash("4321", "s")));
    }

    @Test public void malformedStoredValuesAreRejected() {
        assertFalse(PinCrypto.verify("1", "s", "pbkdf2$"));
        assertFalse(PinCrypto.verify("1", "s", "pbkdf2$abc$00"));
        assertFalse(PinCrypto.verify("1", "s", "pbkdf2$0$00"));
        assertFalse(PinCrypto.verify("1", "s", "pbkdf2$999999999$00"));
        assertFalse(PinCrypto.verify("1", null, "x"));
        assertFalse(PinCrypto.verify("1", "s", null));
    }

    @Test public void lockoutScheduleGrowsAndIsCapped() {
        assertEquals(0, PinCrypto.waitSecondsAfterFailures(0));
        assertEquals(0, PinCrypto.waitSecondsAfterFailures(2));
        assertEquals(5, PinCrypto.waitSecondsAfterFailures(3));
        assertEquals(10, PinCrypto.waitSecondsAfterFailures(4));
        assertEquals(300, PinCrypto.waitSecondsAfterFailures(500));
    }
}
