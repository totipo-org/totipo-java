package org.totipo.format;

import java.util.Arrays;

/** Logical TOKEN identity, independent of the keyed identity of an object. */
final class TokenId {
    private final byte[] bytes;

    TokenId(byte[] bytes) {
        if (bytes.length != 32) throw new IllegalArgumentException("TOKEN_ID must be 32 bytes");
        this.bytes = bytes.clone();
    }

    byte[] bytes() { return bytes.clone(); }

    int compareTo(TokenId other) {
        for (int i = 0; i < bytes.length; i++) {
            int difference = (bytes[i] & 255) - (other.bytes[i] & 255);
            if (difference != 0) return difference;
        }
        return 0;
    }

    @Override public boolean equals(Object other) {
        return other instanceof TokenId id && Arrays.equals(bytes, id.bytes);
    }
    @Override public int hashCode() { return Arrays.hashCode(bytes); }
}
