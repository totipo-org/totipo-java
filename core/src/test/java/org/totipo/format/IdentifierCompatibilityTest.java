package org.totipo.format;

import org.totipo.VaultId;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentifierCompatibilityTest {
    @Test void fixedIdentifiersAndCanonicalValidationRemainUnchanged() {
        byte[] bytes = new byte[32]; for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        String hex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
        assertEquals(hex, new VaultId(bytes).hex());
        assertEquals("VaultId[" + hex + "]", new VaultId(bytes).toString());
        assertEquals(hex, new ObjectId(bytes).filename());
        assertArrayEquals(bytes, ObjectId.fromFilename(hex).bytes());
        assertEquals(hex, ApplicationSession.publicId(new TokenId(bytes)).hex());
        assertArrayEquals(bytes, ApplicationSession.internalId(new org.totipo.TokenId(hex)).bytes());
        for (String invalid : new String[]{hex.toUpperCase(java.util.Locale.ROOT), hex.substring(1),
                hex + "0", "g" + hex.substring(1), "\uff10" + hex.substring(1)}) {
            assertEquals("Invalid object filename", assertThrows(IllegalArgumentException.class,
                    () -> ObjectId.fromFilename(invalid)).getMessage());
            assertThrows(IllegalArgumentException.class, () -> new org.totipo.TokenId(invalid));
            assertThrows(IllegalArgumentException.class, () -> new org.totipo.RevisionId(invalid));
        }
        assertThrows(NullPointerException.class, () -> ObjectId.fromFilename(null));
    }

    @Test void identifierOrderingMatchesJdkUnsignedComparisonAtEveryBytePosition() {
        var random = new Random(30);
        for (int position = 0; position < 32; position++) {
            for (int value = 0; value < 256; value++) {
                byte[] left = new byte[32]; random.nextBytes(left);
                byte[] right = left.clone(); left[position] = (byte) value;
                right[position] = (byte) (255 - value);
                int expected = Integer.signum(Arrays.compareUnsigned(left, right));
                assertEquals(expected, Integer.signum(new TokenId(left).compareTo(new TokenId(right))));
                assertEquals(expected, Integer.signum(TokenGraph.OBJECT_ORDER.compare(new ObjectId(left), new ObjectId(right))));
                assertEquals(HexFormat.of().formatHex(left), new ObjectId(left).filename());
                assertEquals(0, new TokenId(left).compareTo(new TokenId(left)));
            }
        }
    }
}
