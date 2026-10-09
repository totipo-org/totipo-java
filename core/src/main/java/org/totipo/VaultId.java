package org.totipo;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/** Non-secret SHA-256 identity of the exact canonical 87-byte VAULT representation.
 * A value alone proves neither authentication nor freshness. Use Totipo.vaultId before unlock. */
public final class VaultId {
    private final byte[] bytes;
    public VaultId(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length != 32) throw new IllegalArgumentException("VaultId must be 32 bytes");
        this.bytes = bytes.clone();
    }
    public byte[] bytes() { return bytes.clone(); }
    public String hex() { return HexFormat.of().formatHex(bytes); }
    @Override public boolean equals(Object other) { return other instanceof VaultId id && Arrays.equals(bytes, id.bytes); }
    @Override public int hashCode() { return Arrays.hashCode(bytes); }
    @Override public String toString() { return "VaultId[" + hex() + "]"; }
}
