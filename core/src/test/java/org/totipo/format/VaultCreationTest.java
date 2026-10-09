package org.totipo.format;

import org.totipo.*;
import org.totipo.spi.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class VaultCreationTest {
    private static final Argon2idKdf FAST = (password, salt) -> CryptoSupport.sha256(password, salt);
    private static final char[] PASSWORD = {'p'};
    private static final class Store extends TestStore {
        byte[] vault;
        int reads, writes, closes;
        java.util.function.UnaryOperator<BoundedRead> canonical = r -> r;
        ObjectScan scan = new ObjectScan.Complete(List.of());
        @Override public ObjectScan scanObjects() { return scan; }
        @Override public BoundedRead readVault(int expected) {
            reads++;
            var read = read(vault, expected);
            return writes == 0 ? read : canonical.apply(read);
        }
        @Override public VaultCreate createVault(byte[] bytes) { writes++; vault = bytes.clone(); return new VaultCreate.Created(); }
        @Override public void close() { closes++; }
    }
    private static VaultLifecycle lifecycle(Argon2idKdf kdf, List<byte[]> draws) {
        return new VaultLifecycle(new VaultBootstrapWriter(kdf), new VaultUnlocker(kdf), bytes -> {
            draws.add(bytes); Arrays.fill(bytes, (byte) draws.size());
        });
    }
    @Test void successNeedsThreeKdfCallsFreshReadAndWipesEveryEntropyBuffer() {
        var calls = new AtomicInteger();
        var draws = new ArrayList<byte[]>();
        var lifecycle = lifecycle((p, s) -> { calls.incrementAndGet(); return FAST.derive(p, s); }, draws);
        var store = new Store();
        var created = assertInstanceOf(CreateVaultResult.Created.class, lifecycle.create(store, PASSWORD));
        assertEquals(3, calls.get()); // Encode, local validation, fresh canonical authentication.
        assertEquals(2, store.reads); assertEquals(1, store.writes); assertEquals(0, store.closes);
        assertEquals(List.of(32, 16, 12), draws.stream().map(a -> a.length).toList());
        draws.forEach(a -> assertArrayEquals(new byte[a.length], a));
        assertEquals(Totipo.vaultId(store.vault), created.session().vaultId());
        created.session().close(); assertEquals(1, store.closes);
    }
    @ParameterizedTest @ValueSource(strings = {"short", "long", "missing", "wrong-kind", "unavailable", "changed", "exception"})
    void failedPostPublicationVerificationIsUncertainAndNeverRepairs(String mode) {
        var store = new Store();
        store.canonical = read -> switch (mode) {
            case "short" -> new BoundedRead.Present(new byte[86]); // Dishonest provider contract.
            case "long" -> new BoundedRead.Present(new byte[88]);
            case "missing" -> new BoundedRead.Absent();
            case "wrong-kind" -> new BoundedRead.WrongKind(EntryKind.DIRECTORY);
            case "unavailable" -> new BoundedRead.Unavailable(StoreFailure.UNAVAILABLE);
            case "changed" -> { byte[] b = ((BoundedRead.Present) read).bytes(); b[86] ^= 1; yield new BoundedRead.Present(b); }
            default -> throw new IllegalStateException("Injected post-create read failure");
        };
        assertInstanceOf(CreateVaultResult.Uncertain.class, lifecycle(FAST, new ArrayList<>()).create(store, PASSWORD));
        assertEquals(2, store.reads); assertEquals(1, store.writes); assertEquals(1, store.closes);
        assertEquals(87, store.vault.length);
    }
    @Test void exactBytesMustStillBeAuthenticatedAfterPublication() {
        var calls = new AtomicInteger();
        var draws = new ArrayList<byte[]>();
        var lifecycle = lifecycle((p, s) -> calls.incrementAndGet() == 3 ? new byte[32] : FAST.derive(p, s), draws);
        var store = new Store();
        assertInstanceOf(CreateVaultResult.Uncertain.class, lifecycle.create(store, PASSWORD));
        assertEquals(3, calls.get()); assertEquals(1, store.writes); assertEquals(87, store.vault.length);
        draws.forEach(a -> assertArrayEquals(new byte[a.length], a));
    }
    @ParameterizedTest @ValueSource(strings = {"A", "a", "a.tmp", "short", "upper", "nested"})
    void nonCandidatesDoNotInventAnOrphanVeto(String mode) {
        String name = switch (mode) {
            case "upper" -> "A".repeat(64);
            case "short" -> "a".repeat(63);
            case "nested" -> "nested/" + "a".repeat(64);
            default -> mode;
        };
        var store = new Store();
        store.scan = new ObjectScan.Incomplete(List.of(new ObjectEntry(new ObjectName(name), EntryKind.REGULAR,
                OptionalLong.empty())), StoreFailure.UNAVAILABLE);
        var created = assertInstanceOf(CreateVaultResult.Created.class, lifecycle(FAST, new ArrayList<>()).create(store, PASSWORD));
        created.session().close();
    }
    @Test void candidateVetoHappensBeforeAnyEntropyOrKdfAndPreservesObservedBytes() {
        var store = new Store();
        store.scan = new ObjectScan.Incomplete(List.of(new ObjectEntry(new ObjectName("ab".repeat(32)),
                EntryKind.REGULAR, OptionalLong.of(3))), StoreFailure.UNAVAILABLE);
        var lifecycle = new VaultLifecycle(new VaultBootstrapWriter((p,s) -> fail("KDF")),
                new VaultUnlocker((p,s) -> fail("KDF")), b -> fail("Entropy"));
        var failed = assertInstanceOf(CreateVaultResult.Failed.class, lifecycle.create(store, PASSWORD));
        assertEquals(CreateVaultResult.FailureReason.OBJECT_DATA_OBSERVED, failed.reason());
        assertEquals(0, store.writes); assertNull(store.vault);
    }
}
