package com.eokul.studio.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PdfGridTest {
    private final PdfGrid.Spec spec = new PdfGrid.Spec();

    @Test public void sinifBasligi() {
        assertEquals("9. Sınıf / B Şubesi", PdfGrid.classTitle("9-B"));
        assertEquals("5. Sınıf / A Şubesi", PdfGrid.classTitle("5A"));
        assertEquals("12. Sınıf / C Şubesi", PdfGrid.classTitle("12 - c"));
        assertEquals("5. Sınıf / A Şubesi", PdfGrid.classTitle("5. Sınıf / A Şubesi"));
        assertEquals("9. Sınıf / İ Şubesi", PdfGrid.classTitle("9-i"));
        assertEquals("Hazırlık-A", PdfGrid.classTitle(" Hazırlık-A "));
        assertEquals("Anasınıfı", PdfGrid.classTitle("Anasınıfı"));
    }

    @Test public void yedıSutunVeSatirlar() {
        List<PdfGrid.Page> pages = PdfGrid.paginate(15, spec);
        assertEquals(1, pages.size());
        List<PdfGrid.Cell> c = pages.get(0).cells;
        assertEquals(15, c.size());
        for (int i = 0; i < 7; i++) assertEquals(c.get(0).y, c.get(i).y, 1e-9);        // ilk satır aynı hizada
        assertTrue(c.get(7).y > c.get(0).y);                                             // 8. fotoğraf ikinci satırda
        assertEquals(c.get(0).x, c.get(7).x, 1e-9);                                      // alt alta hizalı
        assertEquals(c.get(1).x, c.get(8).x, 1e-9);
        for (int i = 1; i < 7; i++) assertTrue(c.get(i).x > c.get(i - 1).x + c.get(i - 1).w - 1e-9);   // çakışma yok
    }

    @Test public void hicbirHucreSayfaDisinaTasmaz() {
        for (PdfGrid.Page pg : PdfGrid.paginate(200, spec))
            for (PdfGrid.Cell c : pg.cells) {
                assertTrue(c.x >= spec.margin - 1e-9 && c.x + c.w <= spec.pageW - spec.margin + 1e-9);
                assertTrue(c.y >= spec.margin + spec.titleH - 1e-9);
                assertTrue(c.captionY + spec.captionH <= spec.pageH - spec.margin + 1e-9);
            }
    }

    @Test public void sayfaSayisiVeDevam() {
        int per = spec.rowsPerPage() * 7;
        assertTrue(per >= 28);
        assertEquals(1, PdfGrid.paginate(per, spec).size());
        List<PdfGrid.Page> two = PdfGrid.paginate(per + 1, spec);
        assertEquals(2, two.size());
        assertFalse(two.get(0).continued);
        assertTrue(two.get(1).continued);
        assertEquals(1, two.get(1).cells.size());
        assertEquals(per, two.get(1).cells.get(0).index);
        assertEquals(1, PdfGrid.paginate(0, spec).size());   // boş sınıf: yine başlıklı tek sayfa
    }

    @Test public void numaraSirasi() {
        List<String> n = new ArrayList<>(Arrays.asList("1010", "98", "7", "A5", "12345", "007"));
        PdfGrid.sortByNumber(n, x -> x);
        assertEquals(Arrays.asList("007", "7", "98", "1010", "12345", "A5"), n);
    }

    @Test public void yaziSarma() {
        java.util.function.ToDoubleFunction<String> w = s -> s.length();   // 1 birim/karakter
        assertEquals(Arrays.asList("Ayşe Nur", "Yılmaz"), PdfGrid.wrap("Ayşe Nur Yılmaz", 9, 2, w));
        assertEquals(Arrays.asList("Ali"), PdfGrid.wrap("Ali", 9, 2, w));
        List<String> long3 = PdfGrid.wrap("Mehmet Ali Kemal Yıldırım Öztürk", 10, 2, w);
        assertEquals(2, long3.size());
        assertTrue(long3.get(1).endsWith("…"));
        assertTrue(long3.get(1).length() <= 10);
        List<String> tek = PdfGrid.wrap("Abcdefghijklmnopqrstuvwxyz", 8, 2, w);
        assertEquals(1, tek.size());
        assertTrue(tek.get(0).length() <= 8 && tek.get(0).endsWith("…"));
        assertTrue(PdfGrid.wrap("   ", 8, 2, w).isEmpty());
    }
}
