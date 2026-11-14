package com.eokul.studio.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PIN özeti: PBKDF2-HMAC-SHA256 ("pbkdf2$tur$özet") ve eski SHA-256+tuz biçiminin doğrulanması. Saf Java, test edilebilir. */
public final class PinCrypto {
    public static final int ITERATIONS = 120_000;
    private static final String PREFIX = "pbkdf2$";

    private PinCrypto() {}

    public static String hash(String pin, String salt) {
        return pbkdf2(pin, salt, ITERATIONS);
    }

    /** Kayıtlı özet (eski ya da yeni biçim) ile PIN eşleşiyor mu. Sabit zamanlı karşılaştırır. */
    public static boolean verify(String pin, String salt, String stored) {
        if (pin == null || salt == null || stored == null) return false;
        if (stored.startsWith(PREFIX)) {
            String[] parts = stored.split("\\$");
            if (parts.length < 3) return false;
            int iters;
            try {
                iters = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (iters < 1 || iters > 5_000_000) return false;
            return equalsConstant(pbkdf2(pin, salt, iters), stored);
        }
        return equalsConstant(legacy(pin, salt), stored);
    }

    /** Eski biçimde (PBKDF2 olmayan) kaydedilmiş mi; doğru girişte yükseltilmelidir. */
    public static boolean isLegacy(String stored) {
        return stored != null && !stored.startsWith(PREFIX);
    }

    static String legacy(String pin, String salt) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((salt + pin).getBytes(StandardCharsets.UTF_8));
            return hex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String pbkdf2(String pin, String salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), iterations, 256);
            byte[] d = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return PREFIX + iterations + "$" + hex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean equalsConstant(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Ardışık yanlış deneme sayısına göre bekleme (sn): 3. hatadan sonra 5, 10, 15 … en çok 300. */
    public static int waitSecondsAfterFailures(int fails) {
        return fails >= 3 ? Math.min(300, 5 * (fails - 2)) : 0;
    }
}
