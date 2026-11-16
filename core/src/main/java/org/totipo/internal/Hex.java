package org.totipo.internal;

import java.util.Objects;

/** Internal encoding bridge; not an application API. Undelimited ASCII hexadecimal. */
public final class Hex {
    private static final String LOWER = "0123456789abcdef";
    private Hex() { }

    public static String format(byte[] bytes) {
        Objects.requireNonNull(bytes);
        char[] chars = new char[Math.multiplyExact(bytes.length, 2)];
        for (int i = 0; i < bytes.length; i++) {
            chars[2 * i] = LOWER.charAt((bytes[i] & 255) >>> 4);
            chars[2 * i + 1] = LOWER.charAt(bytes[i] & 15);
        }
        return new String(chars);
    }

    public static byte[] parse(String text) {
        Objects.requireNonNull(text);
        if ((text.length() & 1) != 0) throw new IllegalArgumentException("Odd hexadecimal length");
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((digit(text.charAt(2 * i)) << 4) | digit(text.charAt(2 * i + 1)));
        }
        return bytes;
    }

    private static int digit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        throw new NumberFormatException("Not an ASCII hexadecimal digit: " + (int) c);
    }
}
