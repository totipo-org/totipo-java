package org.totipo.internal;

import java.util.HexFormat;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HexTest {
    @Test void matchesJdkForAllBytesAndBoundaries() {
        var random = new Random(30);
        for (int size : new int[]{0, 1, 2, 31, 32, 87, 255, 256, 1024, 8193}) {
            byte[] bytes = new byte[size]; random.nextBytes(bytes);
            if (size == 256) for (int i = 0; i < size; i++) bytes[i] = (byte) i;
            String lower = HexFormat.of().formatHex(bytes);
            String upper = HexFormat.of().withUpperCase().formatHex(bytes);
            assertEquals(lower, Hex.format(bytes));
            assertEquals(upper, Hex.format(bytes).toUpperCase(Locale.ROOT));
            assertArrayEquals(bytes, Hex.parse(lower));
            assertArrayEquals(bytes, Hex.parse(upper));
            assertArrayEquals(HexFormat.of().parseHex(lower), Hex.parse(lower));
        }
        assertThrows(NullPointerException.class, () -> Hex.format(null));
        assertThrows(NullPointerException.class, () -> Hex.parse(null));
    }

    @Test void acceptsExactlyAsciiHexInEitherPosition() {
        for (int c = 0; c <= Character.MAX_VALUE; c++) {
            for (String text : new String[]{"0" + (char) c, (char) c + "0"}) {
                boolean ascii = c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
                if (ascii) assertArrayEquals(HexFormat.of().parseHex(text), Hex.parse(text));
                else {
                    assertThrows(NumberFormatException.class, () -> HexFormat.of().parseHex(text));
                    assertThrows(NumberFormatException.class, () -> Hex.parse(text));
                }
            }
        }
    }

    @Test void malformedAndOddInputsHaveTheSameExceptionTypes() {
        for (String text : new String[]{"0", "abc", "g", "０００", " 0", "0 ", "0x00", "aa:bb", "aa-bb", "aa\nbb", "\ud800\udc00"}) {
            var expected = assertThrows(IllegalArgumentException.class, () -> HexFormat.of().parseHex(text));
            var actual = assertThrows(IllegalArgumentException.class, () -> Hex.parse(text));
            assertEquals(expected.getClass(), actual.getClass(), text);
        }
    }
}
