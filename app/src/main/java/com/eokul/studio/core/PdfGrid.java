package com.eokul.studio.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sınıf fotoğraf listesi PDF'inin sayfa düzeni (birimsiz nokta/pt koordinatları) ve başlık/sıralama yardımcıları. Android'e bağlı değildir. */
public final class PdfGrid {
    private PdfGrid() {}

    /** Bir hücre: fotoğraf dikdörtgeni (x, y, genişlik, yükseklik) ve altındaki yazı alanının üst y'si. */
    public static final class Cell {
        public final int index;          // sınıf içindeki sıra (0'dan)
        public final double x, y, w, h;  // fotoğraf
        public final double captionY;    // yazı alanının üstü
        Cell(int index, double x, double y, double w, double h, double captionY) {
            this.index = index; this.x = x; this.y = y; this.w = w; this.h = h; this.captionY = captionY;
        }
    }

    public static final class Page {
        public final List<Cell> cells = new ArrayList<>();
        public final boolean continued;   // sınıfın 2. ve sonraki sayfası
        Page(boolean continued) { this.continued = continued; }
    }

    public static final class Spec {
        public double pageW = 595, pageH = 842, margin = 28, titleH = 46, gutter = 6, captionH = 30, rowGap = 8;
        public int cols = 7;
        /** Fotoğraf yüksekliği / genişliği (171/133). */
        public double ratio = 171.0 / 133.0;

        public double cellW() { return (pageW - 2 * margin - (cols - 1) * gutter) / cols; }
        public double photoH() { return cellW() * ratio; }
        public double rowH() { return photoH() + 4 + captionH + rowGap; }
        /** Sayfa başına satır sayısı (en az 1). */
        public int rowsPerPage() {
            double avail = pageH - 2 * margin - titleH;
            return Math.max(1, (int) Math.floor((avail + rowGap) / rowH()));
        }
    }

    /** [count] öğrenciyi sayfalara dağıtır: soldan sağa, yukarıdan aşağı, her satırda [Spec.cols] fotoğraf. count 0 ise tek boş sayfa döner. */
    public static List<Page> paginate(int count, Spec s) {
        List<Page> pages = new ArrayList<>();
        int perPage = s.rowsPerPage() * s.cols;
        int pageCount = Math.max(1, (count + perPage - 1) / perPage);
        double cw = s.cellW(), ph = s.photoH();
        for (int p = 0; p < pageCount; p++) {
            Page pg = new Page(p > 0);
            for (int k = 0; k < perPage; k++) {
                int idx = p * perPage + k;
                if (idx >= count) break;
                int row = k / s.cols, col = k % s.cols;
                double x = s.margin + col * (cw + s.gutter);
                double y = s.margin + s.titleH + row * s.rowH();
                pg.cells.add(new Cell(idx, x, y, cw, ph, y + ph + 4));
            }
            pages.add(pg);
        }
        return pages;
    }

    private static final Pattern GRADE = Pattern.compile(
        "^\\s*(\\d{1,2})\\s*[.\\-/ ]*\\s*(?:[sS][ıiIİ]n[ıiIİ]f[ıiIİ]?)?\\s*[.\\-/ ]*\\s*([A-Za-zÇĞİÖŞÜçğıöşü])\\s*(?:[şŞsS]ubesi)?\\s*$");

    /** "9-B", "9B", "9. Sınıf B" gibi adları "9. Sınıf / B Şubesi" biçimine çevirir; tanınmazsa adı olduğu gibi döndürür. */
    public static String classTitle(String name) {
        if (name == null) return "";
        Matcher m = GRADE.matcher(name);
        if (!m.matches()) return name.trim();
        String letter = m.group(2).toUpperCase(new java.util.Locale("tr"));
        return m.group(1) + ". Sınıf / " + letter + " Şubesi";
    }

    /** Numaraya göre sıralama: rakamlardan oluşanlar sayısal, diğerleri sonda metin olarak. */
    public static final Comparator<String> BY_NUMBER = (a, b) -> {
        boolean da = a.matches("\\d+"), db = b.matches("\\d+");
        if (da && db) {
            int c = new BigInteger(a).compareTo(new BigInteger(b));
            return c != 0 ? c : a.compareTo(b);
        }
        if (da != db) return da ? -1 : 1;
        return a.compareTo(b);
    };

    public static <T> void sortByNumber(List<T> list, java.util.function.Function<T, String> no) {
        Collections.sort(list, (x, y) -> BY_NUMBER.compare(no.apply(x), no.apply(y)));
    }

    /** Metni en çok [maxLines] satıra böler; sığmayan kısım "…" ile kesilir. [width] ölçüsü çağıranın metin genişliği işlevidir. */
    public static List<String> wrap(String text, double maxWidth, int maxLines, java.util.function.ToDoubleFunction<String> width) {
        List<String> all = new ArrayList<>();
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return all;
        StringBuilder cur = new StringBuilder();
        for (String w : t.split("\\s+")) {
            String cand = cur.length() == 0 ? w : cur + " " + w;
            if (width.applyAsDouble(cand) <= maxWidth) { cur = new StringBuilder(cand); continue; }
            if (cur.length() > 0) all.add(cur.toString());
            cur = new StringBuilder(w);
        }
        if (cur.length() > 0) all.add(cur.toString());
        if (all.size() > maxLines) {
            StringBuilder rest = new StringBuilder();
            for (int i = maxLines - 1; i < all.size(); i++) rest.append(rest.length() == 0 ? "" : " ").append(all.get(i));
            all = new ArrayList<>(all.subList(0, maxLines - 1));
            all.add(rest + "\u0000");   // kesilecek işareti
        }
        List<String> out = new ArrayList<>();
        for (String line : all) {
            boolean cut = line.endsWith("\u0000");
            String l = cut ? line.substring(0, line.length() - 1) : line;
            if (cut || width.applyAsDouble(l) > maxWidth) {
                while (l.length() > 1 && width.applyAsDouble(l + "\u2026") > maxWidth) l = l.substring(0, l.length() - 1);
                l = l + "\u2026";
            }
            out.add(l);
        }
        return out;
    }
}
