package org.totipo.api;

import org.totipo.*;
import org.totipo.spi.*;
import org.totipo.testing.MemoryVault;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise canonical revalidation and provider certainty through only the cohesive layout SPI. */
class SpiApplicationTest {
    private static byte[] template;
    private static final char[] PASSWORD = "password".toCharArray();
    @BeforeAll static void template() {
        var memory = new MemoryVault();
        try (var session = memory.create()) { template = memory.bootstrap.clone(); assertNotNull(session.vaultId()); }
    }
    private static final class Store implements TotipoStore {
        byte[] vault = template.clone();
        final Map<ObjectName, byte[]> objects = new ConcurrentHashMap<>();
        final List<ObjectName> reads = Collections.synchronizedList(new ArrayList<>());
        int closes, installs, publications;
        String canonicalMode = "", installMode = "", publicationMode = "";
        boolean incomplete;
        BoundedRead forcedRead;
        BoundedRead forcedObjectRead;
        EntryKind scanKind = EntryKind.REGULAR;
        static BoundedRead read(byte[] bytes, int expected) {
            if (bytes == null) return new BoundedRead.Absent();
            if (bytes.length < expected) return new BoundedRead.Undersized(bytes.length);
            if (bytes.length > expected) return new BoundedRead.Oversized();
            return new BoundedRead.Present(bytes);
        }
        @Override public BoundedRead readVault(int expected) { return forcedRead == null ? read(vault, expected) : forcedRead; }
        @Override public ObjectScan scanObjects() {
            var entries = objects.entrySet().stream().map(e -> new ObjectEntry(e.getKey(), scanKind,
                    OptionalLong.of(999999))).toList(); // Deliberately stale metadata must not size the read.
            return incomplete ? new ObjectScan.Incomplete(entries, StoreFailure.UNAVAILABLE) : new ObjectScan.Complete(entries);
        }
        @Override public BoundedRead readObject(ObjectName name, int expected) {
            assertEquals(1024, expected); reads.add(name);
            return forcedObjectRead == null ? read(objects.get(name), expected) : forcedObjectRead;
        }
        @Override public ObjectWrite publishObject(ObjectName name, byte[] bytes) {
            publications++;
            if (publicationMode.equals("failed")) return new ObjectWrite.Failed(StoreFailure.UNAVAILABLE);
            var old = objects.putIfAbsent(name, bytes.clone());
            if (publicationMode.equals("throw")) throw new IllegalStateException("lost acknowledgement");
            if (publicationMode.equals("uncertain")) return new ObjectWrite.Uncertain(StoreFailure.UNAVAILABLE);
            if (old == null) return new ObjectWrite.Written();
            return Arrays.equals(old, bytes) ? new ObjectWrite.AlreadyPresentExact() : new ObjectWrite.ExistingDifferent();
        }
        @Override public VaultCreate createVault(byte[] bytes) {
            installs++;
            if (installMode.equals("failed")) return new VaultCreate.Failed(StoreFailure.UNAVAILABLE);
            if (installMode.equals("already") || vault != null) return new VaultCreate.AlreadyPresent();
            vault = bytes.clone();
            if (installMode.equals("throw")) throw new IllegalStateException("lost acknowledgement");
            if (installMode.equals("uncertain")) return new VaultCreate.Uncertain(StoreFailure.UNAVAILABLE);
            switch (canonicalMode) {
                case "changed" -> vault[0] ^= 1;
                case "short" -> vault = Arrays.copyOf(vault, 86);
                case "long" -> vault = Arrays.copyOf(vault, 88);
                case "absent" -> vault = null;
                case "unavailable" -> forcedRead = new BoundedRead.Unavailable(StoreFailure.UNAVAILABLE);
                case "wrong-kind" -> forcedRead = new BoundedRead.WrongKind(EntryKind.SYMLINK);
                case "throw" -> forcedRead = null;
                default -> { }
            }
            return new VaultCreate.Created();
        }
        @Override public void close() { closes++; }
    }
    private static VaultSession open(Store store) {
        return assertInstanceOf(OpenResult.Opened.class, Totipo.open(store, PASSWORD)).session();
    }

    @Test void successfulOpenTransfersOwnershipAndSessionClosesExactlyOnce() {
        var store = new Store();
        var session = open(store);
        assertEquals(0, store.closes);
        PublicApiTest.finished(session);
        session.close(); session.close();
        assertEquals(1, store.closes);
    }

    @Test void successfulCreateTransfersOwnership() {
        var store = new Store(); store.vault = null;
        var session = assertInstanceOf(CreateVaultResult.Created.class, Totipo.create(store, PASSWORD)).session();
        assertEquals(0, store.closes);
        assertEquals(1, store.installs);
        session.close(); session.close();
        assertEquals(1, store.closes);
    }

    @ParameterizedTest @ValueSource(strings = {"absent", "short", "long", "wrong-kind", "unavailable", "password"})
    void unsuccessfulOpenClosesTransferredStoreAndPreservesTaxonomy(String mode) {
        var store = new Store();
        store.forcedRead = switch (mode) {
            case "absent" -> new BoundedRead.Absent();
            case "short" -> new BoundedRead.Undersized(1);
            case "long" -> new BoundedRead.Oversized();
            case "wrong-kind" -> new BoundedRead.WrongKind(EntryKind.DIRECTORY);
            case "unavailable" -> new BoundedRead.Unavailable(StoreFailure.UNAVAILABLE);
            default -> null;
        };
        var result = Totipo.open(store, "wrong password".toCharArray());
        switch (mode) {
            case "absent" -> assertInstanceOf(OpenResult.Absent.class, result);
            case "short", "long" -> assertInstanceOf(OpenResult.InvalidVault.class, result);
            case "password" -> assertInstanceOf(OpenResult.AuthenticationFailed.class, result);
            default -> assertInstanceOf(OpenResult.Unavailable.class, result);
        }
        assertEquals(1, store.closes);
    }

    @Test void invalidPasswordStillClosesTransferredStore() {
        var store = new Store();
        assertThrows(NullPointerException.class, () -> Totipo.open(store, null));
        assertEquals(1, store.closes);
        var create = new Store(); create.vault = null;
        assertThrows(NullPointerException.class, () -> Totipo.create(create, null));
        assertEquals(1, create.closes);
    }

    @ParameterizedTest @ValueSource(strings = {"failed", "already", "uncertain", "throw"})
    void createMapsDefiniteAndAmbiguousInstallOutcomes(String mode) {
        var store = new Store(); store.vault = null; store.installMode = mode;
        var result = Totipo.create(store, PASSWORD);
        if (mode.equals("failed")) assertInstanceOf(CreateVaultResult.Failed.class, result);
        else if (mode.equals("already")) assertInstanceOf(CreateVaultResult.AlreadyExists.class, result);
        else assertInstanceOf(CreateVaultResult.Uncertain.class, result);
        assertEquals(1, store.installs); assertEquals(1, store.closes);
    }

    @ParameterizedTest @ValueSource(strings = {"uncertain", "throw"})
    void publicationUncertaintyIsMonotonicUntilPositiveExactRetry(String mode) {
        var store = new Store();
        try (var session = open(store); var secret = NewSecret.copyOf(new byte[]{1, 2, 3});
             var create = session.state().createToken()) {
            store.publicationMode = mode;
            var uncertain = assertInstanceOf(SaveResult.PublicationUncertain.class, create.secret(secret).save());
            assertEquals(1, store.objects.size());
            store.publicationMode = "failed";
            var again = assertInstanceOf(SaveResult.PublicationUncertain.class, uncertain.retry().retryPublication());
            store.publicationMode = "";
            assertInstanceOf(SaveResult.Saved.class, again.retry().retryPublication());
            assertEquals(1, store.objects.size()); assertEquals(3, store.publications);
        }
    }

    @Test void coreFiltersProtocolNamesAndUsesIncompleteObservationsWithoutTrustingMetadata() {
        var initial = new Store();
        try (var session = open(initial); var secret = NewSecret.copyOf(new byte[]{1, 2, 3});
             var create = session.state().createToken()) {
            assertInstanceOf(SaveResult.Saved.class, create.secret(secret).save());
            PublicApiTest.refresh(session);
        }
        var store = new Store(); store.objects.putAll(initial.objects);
        var canonical = store.objects.keySet().iterator().next();
        var junk = new ObjectName("junk");
        var upper = new ObjectName(canonical.value().toUpperCase(Locale.ROOT));
        store.objects.put(junk, new byte[]{4}); store.objects.put(upper, store.objects.get(canonical));
        store.reads.clear(); store.incomplete = true;
        try (var session = open(store)) {
            var state = PublicApiTest.finished(session);
            assertEquals(1, state.tokens().size());
            assertTrue(store.reads.contains(canonical));
            assertFalse(store.reads.contains(junk)); assertFalse(store.reads.contains(upper));
            assertFalse(state.diagnostics().isEmpty());
        }
        assertEquals(1, store.closes);
    }

    @ParameterizedTest @ValueSource(strings = {"changed", "short", "long", "absent", "wrong-kind", "unavailable"})
    void canonicalRevalidationFailureCannotReportCreated(String mode) {
        var store = new Store(); store.vault = null; store.canonicalMode = mode;
        assertInstanceOf(CreateVaultResult.Uncertain.class, Totipo.create(store, PASSWORD));
        assertEquals(1, store.installs); assertEquals(1, store.closes);
    }

    @ParameterizedTest @EnumSource(EntryKind.class)
    void observedCandidateVetoesCreationEvenWhenScanIsIncomplete(EntryKind kind) {
        var store = new Store(); store.vault = null; store.incomplete = true; store.scanKind = kind;
        store.objects.put(new ObjectName("a".repeat(64)), new byte[]{7});
        var result = assertInstanceOf(CreateVaultResult.Failed.class, Totipo.create(store, PASSWORD));
        assertEquals(CreateVaultResult.FailureReason.OBJECT_DATA_OBSERVED, result.reason());
        assertEquals(0, store.installs); assertNull(store.vault);
        assertArrayEquals(new byte[]{7}, store.objects.values().iterator().next());
    }

    @ParameterizedTest @EnumSource(EntryKind.class)
    void everyCanonicalScanKindGetsFreshReadAndWrongKindEvidence(EntryKind kind) {
        var store = new Store();
        var canonical = new ObjectName("ab".repeat(32));
        store.objects.put(canonical, new byte[1024]);
        store.objects.put(new ObjectName("junk"), new byte[0]);
        store.objects.put(new ObjectName(".tmp"), new byte[0]);
        store.objects.put(new ObjectName("a".repeat(63)), new byte[0]);
        store.scanKind = kind;
        store.forcedObjectRead = new BoundedRead.WrongKind(kind == EntryKind.REGULAR ? EntryKind.SYMLINK : kind);
        try (var session = open(store)) {
            var state = PublicApiTest.finished(session);
            assertEquals(List.of(canonical), store.reads);
            assertTrue(state.tokens().isEmpty());
            assertEquals(List.of(new VaultDiagnostic("UNAVAILABLE")), state.diagnostics());
        }
    }

    @ParameterizedTest @EnumSource(value = EntryKind.class, names = {"SYMLINK", "DIRECTORY", "OTHER", "UNKNOWN"})
    void nonRegularScanKindCanBecomeValidAtFreshRead(EntryKind kind) {
        var initial = new Store();
        try (var session = open(initial); var secret = NewSecret.copyOf(new byte[]{1, 2, 3});
             var create = session.state().createToken()) {
            assertInstanceOf(SaveResult.Saved.class, create.secret(secret).save());
        }
        var store = new Store(); store.objects.putAll(initial.objects); store.scanKind = kind;
        store.objects.put(new ObjectName("junk"), new byte[0]);
        try (var session = open(store)) {
            var state = PublicApiTest.finished(session);
            assertEquals(1, state.tokens().size());
            assertTrue(state.diagnostics().isEmpty());
            assertEquals(List.copyOf(initial.objects.keySet()), store.reads);
        }
    }
}
