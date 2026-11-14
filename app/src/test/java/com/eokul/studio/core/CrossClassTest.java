package com.eokul.studio.core;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class CrossClassTest {
    private Map<String, Set<String>> data() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("a", new HashSet<>(Arrays.asList("1", "2", "3")));
        m.put("b", new HashSet<>(Arrays.asList("3", "4")));
        m.put("c", new HashSet<>(Arrays.asList("3", "5")));
        return m;
    }

    @Test public void findsOtherClassesInOrderAndExcludesOwn() {
        assertEquals(Arrays.asList("b", "c"), CrossClass.classesWith("3", data(), "a"));
        assertEquals(Arrays.asList("a", "c"), CrossClass.classesWith("3", data(), "b"));
        assertTrue(CrossClass.classesWith("1", data(), "a").isEmpty());
        assertEquals(Arrays.asList("a"), CrossClass.classesWith("1", data(), null));
        assertTrue(CrossClass.classesWith("9", data(), "a").isEmpty());
    }

    @Test public void countsSharedNumbersOncePerNumber() {
        assertEquals(2, CrossClass.countShared(Arrays.asList("3", "4", "3", "9"), data(), "a")); // 3 ve 4
        assertEquals(0, CrossClass.countShared(Arrays.asList("1", "2"), data(), "a"));
        assertEquals(0, CrossClass.countShared(Collections.<String>emptyList(), data(), "a"));
    }
}
