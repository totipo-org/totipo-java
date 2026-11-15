package org.totipo.format;

import org.totipo.*;
import org.totipo.backup.*;
import org.totipo.spi.*;
import org.totipo.storage.nio.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.totipo.backup.ImportResult.Outcome.*;

@Timeout(30)
class HistoryImportTest {
    @TempDir Path directory;
    private static byte[] fixture(String name) throws IOException {
        try (var input = HistoryImportTest.class.getResourceAsStream("/backup/profile-1/" + name + ".zip")) {
            return Objects.requireNonNull(input).readAllBytes();
        }
    }
    private static Map<String, byte[]> entries(byte[] zip) throws IOException {
        var result = new TreeMap<String, byte[]>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) result.put(entry.getName(), input.readAllBytes());
        }
        return result;
    }
    private static final class Store implements TotipoStore {
        volatile byte[] vault;
        final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        volatile BiFunction<ObjectName, byte[], ObjectWrite> publication;
        volatile Runnable onVault = () -> { };
        volatile String unreadable, wrongKind;
        int calls;
        Store() throws IOException { vault = entries(fixture("empty")).get("vault"); }
        @Override public BoundedRead readVault(int expected) { onVault.run(); return new BoundedRead.Present(vault); }
        @Override public BoundedRead readObject(ObjectName name, int expected) {
            if (name.value().equals(unreadable)) return new BoundedRead.Unavailable(StoreFailure.UNAVAILABLE);
            if (name.value().equals(wrongKind)) return new BoundedRead.WrongKind(EntryKind.DIRECTORY);
            byte[] b = objects.get(name.value());
            if (b == null) return new BoundedRead.Absent();
            if (b.length < expected) return new BoundedRead.Undersized(b.length);
            if (b.length > expected) return new BoundedRead.Oversized();
            return new BoundedRead.Present(b);
        }
        @Override public ObjectScan scanObjects() {
            return new ObjectScan.Complete(objects.keySet().stream().map(n ->
                    new ObjectEntry(new ObjectName(n), EntryKind.REGULAR, OptionalLong.empty())).toList());
        }
        @Override public ObjectWrite publishObject(ObjectName name, byte[] bytes) {
            calls++;
            return publication == null ? install(name, bytes) : publication.apply(name, bytes);
        }
        ObjectWrite install(ObjectName name, byte[] bytes) {
            byte[] previous = objects.putIfAbsent(name.value(), bytes.clone());
            return previous == null ? new ObjectWrite.Written() : Arrays.equals(previous, bytes)
                    ? new ObjectWrite.AlreadyPresentExact() : new ObjectWrite.ExistingDifferent();
        }
        @Override public VaultCreate createVault(byte[] bytes) { throw new AssertionError("Import never creates VAULT"); }
        @Override public void close() { }
    }
    private static VaultSession open(Store store) {
        var session = assertInstanceOf(OpenResult.Opened.class, Totipo.open(store, "fixture password".toCharArray())).session();
        finished(session); return session;
    }
    private static void finished(VaultSession session) {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!(session.state().observation() instanceof ObservationProgress.Finished)) {
            if (System.nanoTime() > until) fail("Observation timeout");
            Thread.onSpinWait();
        }
    }
    private static void refresh(VaultSession session) throws InterruptedException {
        var old = session.state(); session.requestRefresh();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (session.state() == old) {
            if (System.nanoTime() > until) fail("Refresh timeout");
            Thread.sleep(1);
        }
        finished(session);
    }
    private static BackupImport prepare(VaultSession session, byte[] zip) throws IOException {
        return session.prepareImport(new ByteArrayInputStream(zip), BackupLimits.DEFAULT);
    }
    private static TreeMap<String, byte[]> history() throws IOException {
        var objects = new TreeMap<String, byte[]>();
        entries(fixture("history")).forEach((n, b) -> { if (n.startsWith("objects-v1/")) objects.put(n.substring(11), b); });
        return objects;
    }
    private static byte[] archive(Store store, Map<String, byte[]> objects, int gaps) throws IOException {
        var sorted = new TreeMap<>(objects);
        byte[] spool = new byte[1024 * objects.size()]; int offset = 0;
        for (var b : sorted.values()) { System.arraycopy(b, 0, spool, offset, 1024); offset += 1024; }
        try (var capture = new BackupZip.Frozen(store.vault.clone(), spool, new ArrayList<>(sorted.keySet()), gaps, BackupLimits.DEFAULT)) {
            var out = new ByteArrayOutputStream(); capture.writeTo(out); return out.toByteArray();
        }
    }
    private static void same(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        expected.forEach((n, b) -> assertArrayEquals(b, actual.get(n), n));
    }
    @ParameterizedTest @ValueSource(strings = {"empty", "history", "missing-parent"})
    void goldenImportAndRepeatedImportAreExactAndObservationIsSeparate(String name) throws Exception {
        var store = new Store(); byte[] bootstrap = store.vault.clone();
        try (var session = open(store); var plan = prepare(session, fixture(name))) {
            var before = session.state();
            assertEquals(name.equals("missing-parent") ? 1 : 0, plan.summary().unresolvedReferences());
            assertTrue(plan.summary().sourceAuthenticated()); assertEquals(0, store.calls);
            var result = plan.apply();
            assertEquals(name.equals("empty") ? NO_OP : COMPLETE, result.outcome());
            assertEquals(plan.summary().objects(), result.installed());
            assertSame(before, session.state()); // No observation is promised or secretly performed by apply.
            assertEquals(result, plan.apply()); // Cumulative unique-object accounting.
            try (var repeated = prepare(session, fixture(name))) {
                var noop = repeated.apply(); assertEquals(NO_OP, noop.outcome());
                assertEquals(plan.summary().objects(), noop.retained());
            }
            entries(fixture(name)).forEach((n, b) -> { if (n.startsWith("objects-v1/")) assertArrayEquals(b, store.objects.get(n.substring(11))); });
            assertArrayEquals(bootstrap, store.vault);
            refresh(session);
            if (name.equals("history")) {
                var token = session.state().tokens().get(0);
                assertTrue(token.hasConflict());
                assertTrue(token.alternatives().stream().anyMatch(a -> a.descriptor().status() == TokenStatus.TOMBSTONED));
                // Existing ordinary merge still operates on the observed conflict.
                try (var merge = session.state().merge(token.id())) {
                    assertInstanceOf(SaveResult.Saved.class, merge.keep(token.alternatives().get(0)).save());
                }
            }
        }
    }
    @Test void missingAncestorsOlderHistoryAndNewBranchesKeepNewerDescendants() throws Exception {
        var store = new Store(); var all = history();
        var childEntries = entries(fixture("missing-parent"));
        String child = childEntries.keySet().stream().filter(n -> n.startsWith("objects-v1/")).findFirst().orElseThrow().substring(11);
        store.objects.put(child, all.get(child));
        try (var session = open(store)) {
            var old = session.state().tokens().get(0).heads().get(0);
            try (var update = session.state().update(old)) {
                assertInstanceOf(SaveResult.Saved.class, update.issuer("new descendant").save());
            }
            refresh(session);
            var preexisting = new TreeMap<>(store.objects);
            var newer = session.state().tokens().get(0).heads().get(0).revision();
            try (var plan = prepare(session, fixture("history"))) { assertEquals(COMPLETE, plan.apply().outcome()); }
            same(preexisting, store.objects); refresh(session);
            var token = session.state().tokens().get(0);
            assertTrue(token.heads().stream().anyMatch(h -> h.revision().equals(newer)));
            assertEquals(2, token.heads().size()); // New descendant plus imported concurrent tombstone.
            assertTrue(token.unresolvedReferences().isEmpty()); assertTrue(token.hasConflict());
        }
    }
    @Test void equalValuedDistinctHeadsAndMetadataSurviveWithoutNewAssertions() throws Exception {
        var store = new Store(); store.objects.putAll(history());
        try (var session = open(store)) {
            var head = session.state().tokens().get(0).heads().get(0);
            for (long time : List.of(1L, 2L)) try (var update = session.state().update(head)) {
                assertInstanceOf(SaveResult.Saved.class, update.issuer("equal").status(TokenStatus.ACTIVE)
                        .metadata(new ClientMetadata(Optional.of("import test"), Optional.of(time))).save());
            }
            var expected = new TreeMap<>(store.objects);
            byte[] zip = archive(store, expected, 0); store.objects.clear();
            try (var plan = prepare(session, zip)) { assertEquals(COMPLETE, plan.apply().outcome()); }
            same(expected, store.objects); assertEquals(expected.size(), store.objects.size());
            refresh(session);
            var equal = session.state().tokens().get(0).alternatives().stream()
                    .filter(a -> a.descriptor().issuer().equals("equal")).findFirst().orElseThrow();
            assertEquals(2, equal.heads().size());
            assertEquals(Set.of(1L, 2L), new HashSet<>(equal.heads().stream().map(h -> h.metadata().clientTimeBits().orElseThrow()).toList()));
        }
    }
    @Test void archiveDiagnosticsAreRecomputedAndMissingOrWrongTokenParentsAreAllowed() throws Exception {
        var store = new Store(); var all = history();
        byte[] root = new byte[32]; Arrays.fill(root, (byte) 0x11);
        var secret = new SecurityBytes(new byte[]{1, 2, 3}, 3);
        try {
            var value = new TokenValue(1, "other token", "", new TokenValue.Credential(1, 6, 30, secret));
            var object = V1EnvelopeWriter.seal(root, TokenWriter.write(new TokenObject(new TokenId(new byte[32]),
                    List.of(ObjectId.fromFilename(all.firstKey())), value, new TokenMetadata(Optional.empty(), Optional.empty()))));
            all.put(object.id().filename(), object.bytes());
            // Forged producer claim is structurally legal but cannot override computed diagnostics.
            byte[] zip = archive(store, all, 0);
            assertEquals(0, BackupArchives.inspect(new ByteArrayInputStream(zip), BackupLimits.DEFAULT).unresolvedReferences());
            try (var session = open(store); var plan = prepare(session, zip)) {
                assertEquals(1, plan.summary().unresolvedReferences()); assertEquals(COMPLETE, plan.apply().outcome());
            }
        } finally { secret.clear(); Arrays.fill(root, (byte) 0); }
    }
    @ParameterizedTest @ValueSource(strings = {"different", "short", "long", "unreadable", "wrong-kind"})
    void everyKnownTargetConflictRejectsWholeBatchBeforeWrites(String kind) throws Exception {
        var store = new Store(); var all = history(); String target = all.lastKey();
        switch (kind) {
            case "different" -> store.objects.put(target, new byte[1024]);
            case "short" -> store.objects.put(target, new byte[1023]);
            case "long" -> store.objects.put(target, new byte[1025]);
            case "unreadable" -> store.unreadable = target;
            case "wrong-kind" -> store.wrongKind = target;
            default -> fail();
        }
        var before = new TreeMap<>(store.objects);
        try (var session = open(store)) {
            assertThrows(BackupException.class, () -> prepare(session, fixture("history")));
            assertEquals(0, store.calls); same(before, store.objects); assertEquals(before.size(), store.objects.size());
        }
    }
    @Test void completeValidationPrecedesWritesAndExactBootstrapIsRequired() throws Exception {
        var store = new Store(); var objects = history(); objects.put(objects.lastKey(), new byte[1024]);
        try (var session = open(store)) {
            assertEquals(BackupException.Reason.INVALID_OBJECT, assertThrows(BackupException.class,
                    () -> prepare(session, archive(store, objects, 0))).reason());
            byte[] original = store.vault; store.vault = original.clone(); store.vault[86] ^= 1;
            // Same session root, structurally canonical alternate bootstrap is still refused.
            assertEquals(BackupException.Reason.VAULT_CHANGED, assertThrows(BackupException.class,
                    () -> prepare(session, archive(store, Map.of(), 0))).reason());
            store.vault = original;
            byte[] malformed = fixture("history"); malformed[0] = 0;
            assertThrows(BackupException.class, () -> prepare(session, malformed));
            assertEquals(0, store.calls); assertTrue(store.objects.isEmpty());
        }
    }
    @Test void differentAuthenticatedBootstrapEvenWithSameRecoveredRootIsRejected() throws Exception {
        var store = new Store(); byte[] root = new byte[32]; Arrays.fill(root, (byte) 0x11);
        byte[] salt = new byte[16]; Arrays.fill(salt, (byte) 0x24);
        byte[] alternate = new VaultBootstrapWriter().encode("fixture password".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                root, salt, new byte[12]);
        try (var session = open(store)) {
            byte[] original = store.vault; store.vault = alternate;
            byte[] zip = archive(store, history(), 0); store.vault = original;
            try (var unlocked = new VaultUnlocker().unlock(alternate, "fixture password".getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                assertArrayEquals(root, unlocked.root());
            }
            assertEquals(BackupException.Reason.VAULT_CHANGED, assertThrows(BackupException.class,
                    () -> prepare(session, zip)).reason());
            assertEquals(0, store.calls);
        } finally { Arrays.fill(root, (byte) 0); }
    }
    @Test void authenticatedBadGrammarWrongKeyedIdentityAndTamperingRejectWholeArchive() throws Exception {
        var store = new Store(); byte[] root = new byte[32]; Arrays.fill(root, (byte) 0x11);
        var invalidGrammar = V1EnvelopeWriter.seal(root, new byte[]{0});
        var all = history(); byte[] tampered = all.firstEntry().getValue().clone(); tampered[1023] ^= 1;
        try (var session = open(store)) {
            for (var invalid : List.of(Map.of(invalidGrammar.id().filename(), invalidGrammar.bytes()),
                    Map.of("0".repeat(64), all.firstEntry().getValue()), Map.of(all.firstKey(), tampered))) {
                assertEquals(BackupException.Reason.INVALID_OBJECT, assertThrows(BackupException.class,
                        () -> prepare(session, archive(store, invalid, 0))).reason());
                assertEquals(0, store.calls); assertTrue(store.objects.isEmpty());
            }
        } finally { Arrays.fill(root, (byte) 0); }
    }
    @Test void applyRepeatsFullPreflightAndBindingChecksBeforeAnyPublication() throws Exception {
        var store = new Store();
        try (var session = open(store); var plan = prepare(session, fixture("history"))) {
            store.objects.put(history().lastKey(), new byte[1024]);
            assertEquals(ImportResult.Reason.TARGET_CONFLICT, plan.apply().reason()); assertEquals(0, store.calls);
            store.objects.clear(); store.unreadable = history().lastKey();
            assertEquals(ImportResult.Reason.TARGET_UNAVAILABLE, plan.apply().reason()); assertEquals(0, store.calls);
            store.unreadable = null; store.wrongKind = history().lastKey();
            assertEquals(ImportResult.Reason.TARGET_UNAVAILABLE, plan.apply().reason()); assertEquals(0, store.calls);
            store.wrongKind = null; store.vault[86] ^= 1;
            assertEquals(ImportResult.Reason.VAULT_CHANGED, plan.apply().reason()); assertEquals(0, store.calls);
            store.vault[86] ^= 1; assertEquals(COMPLETE, plan.apply().outcome());
        }
    }
    @Test void preparationHandlesBudgetsUnsupportedInputCancellationShortReadsAndHandleDisposal() throws Exception {
        var store = new Store(); byte[] zip = fixture("history");
        try (var session = open(store)) {
            for (var limits : List.of(new BackupLimits(0, 8192, 6 * 1024 * 1024), new BackupLimits(4096, 8192, 512))) {
                assertEquals(BackupException.Reason.CAPACITY, assertThrows(BackupException.class,
                        () -> session.prepareImport(new ByteArrayInputStream(zip), limits)).reason());
            }
            byte[] unsupported = zip.clone(); unsupported[8] = 8; // Compression is outside the frozen profile.
            assertEquals(BackupException.Reason.UNSUPPORTED, assertThrows(BackupException.class,
                    () -> prepare(session, unsupported)).reason());
            Thread.currentThread().interrupt();
            try { assertEquals(BackupException.Reason.CANCELLED, assertThrows(BackupException.class,
                    () -> prepare(session, zip)).reason()); }
            finally { assertTrue(Thread.interrupted()); }
            assertThrows(IOException.class, () -> session.prepareImport(new InputStream() {
                @Override public int read() throws IOException { throw new IOException("input failure"); }
            }, BackupLimits.DEFAULT));
            var input = new ByteArrayInputStream(zip) {
                boolean zero;
                @Override public synchronized int read(byte[] b, int off, int len) {
                    zero = !zero; return zero ? 0 : super.read(b, off, Math.min(len, 7));
                }
                @Override public void close() { fail("Borrowed stream closed"); }
            };
            var plan = session.prepareImport(input, BackupLimits.DEFAULT);
            assertEquals(0, store.calls); plan.close(); plan.close();
            assertThrows(IllegalStateException.class, plan::apply); assertThrows(IllegalStateException.class, plan::summary);
            assertTrue(store.objects.isEmpty());
        }
    }
    @ParameterizedTest @CsvSource({"1,0", "2,0", "3,0", "1,1", "2,1", "3,1", "1,2", "2,2", "3,2"})
    void definiteFailureAndLostAcknowledgementAtEveryPositionRetainPrefixAndRetry(int position, int mode) throws Exception {
        var store = new Store(); byte[] zip = fixture("history");
        boolean lost = mode != 0;
        store.publication = (name, bytes) -> {
            if (store.calls != position) return store.install(name, bytes);
            if (lost) {
                store.install(name, bytes);
                if (mode == 2) throw new IllegalStateException("Lost acknowledgement");
                return new ObjectWrite.Uncertain(StoreFailure.UNAVAILABLE);
            }
            return new ObjectWrite.Failed(StoreFailure.UNAVAILABLE);
        };
        try (var session = open(store); var plan = prepare(session, zip)) {
            var result = plan.apply();
            assertEquals(lost ? UNCERTAIN : position == 1 ? FAILED : PARTIAL, result.outcome());
            assertEquals(position - 1, result.installed()); assertEquals(lost ? 1 : 0, result.uncertain());
            assertEquals(3 - position + (lost ? 0 : 1), result.remaining());
            assertEquals(position - 1 + (lost ? 1 : 0), store.objects.size());
            var prefix = new TreeMap<>(store.objects);
            if (lost) {
                Thread.currentThread().interrupt();
                try { assertEquals(UNCERTAIN, plan.apply().outcome()); }
                finally { Thread.interrupted(); }
                store.publication = (n, b) -> new ObjectWrite.Failed(StoreFailure.UNAVAILABLE);
                assertEquals(UNCERTAIN, plan.apply().outcome()); // Preflight reads never clear uncertainty.
            }
            Arrays.fill(zip, (byte) 0); // Retrying cannot consult the mutable ingress again.
            store.publication = null;
            var completed = plan.apply(); assertEquals(COMPLETE, completed.outcome());
            assertEquals(0, completed.uncertain()); assertEquals(0, completed.remaining());
            same(prefix, store.objects); same(history(), store.objects); assertEquals(3, store.objects.size());
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void concurrentPublisherSameBytesAcknowledgesAndDifferentBytesNeverOverwrites(boolean different) throws Exception {
        var store = new Store();
        store.publication = (name, bytes) -> {
            if (store.calls == 2) store.objects.put(name.value(), different ? new byte[1024] : bytes.clone());
            return store.install(name, bytes);
        };
        try (var session = open(store); var plan = prepare(session, fixture("history"))) {
            var result = plan.apply();
            assertEquals(different ? PARTIAL : COMPLETE, result.outcome());
            assertEquals(different ? ImportResult.Reason.TARGET_CONFLICT : ImportResult.Reason.NONE, result.reason());
            assertEquals(different ? 2 : 3, store.objects.size());
            if (different) {
                assertArrayEquals(new byte[1024], store.objects.get(history().keySet().stream().skip(1).findFirst().orElseThrow()));
                assertEquals(PARTIAL, plan.apply().outcome()); // Preflight refuses, never repairs occupied evidence.
            } else assertEquals(1, result.retained());
        }
    }
    @Test void publicationUncheckedFailureIsUncertainAndIdentityChangesStopAtPublicationAndCompletion() throws Exception {
        var store = new Store(); byte[] original = store.vault.clone();
        try (var session = open(store); var plan = prepare(session, fixture("history"))) {
            store.publication = (n, b) -> { store.install(n, b); throw new IllegalStateException("ack lost"); };
            assertEquals(UNCERTAIN, plan.apply().outcome());
            store.publication = null; store.vault[86] ^= 1;
            assertEquals(UNCERTAIN, plan.apply().outcome()); store.vault = original;
            assertEquals(COMPLETE, plan.apply().outcome());
        }
        for (int position : List.of(1, 3)) {
            var changed = new Store();
            try (var session = open(changed); var plan = prepare(session, fixture("history"))) {
                changed.publication = (n, b) -> {
                    var result = changed.install(n, b);
                    if (changed.calls == position) changed.vault[86] ^= 1;
                    return result;
                };
                var result = plan.apply(); assertEquals(PARTIAL, result.outcome());
                assertEquals(ImportResult.Reason.VAULT_CHANGED, result.reason()); assertEquals(position, changed.calls);
            }
        }
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void cooperativeCancellationNeverHidesAcknowledgedPublication(int after) throws Exception {
        var store = new Store();
        try (var session = open(store); var plan = prepare(session, fixture("history"))) {
            store.publication = (n, b) -> { var result = store.install(n, b); if (store.calls == after) Thread.currentThread().interrupt(); return result; };
            if (after == 0) Thread.currentThread().interrupt();
            try {
                var result = plan.apply(); assertEquals(after == 0 ? FAILED : after == 3 ? COMPLETE : PARTIAL, result.outcome());
                assertEquals(after, result.installed()); assertTrue(Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
            store.publication = null; assertEquals(COMPLETE, plan.apply().outcome());
        }
    }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    @Test void closeWaitsForAdmittedPublicationAndStopsRemainingObjects() throws Exception {
        var store = new Store(); var session = open(store); var plan = prepare(session, fixture("history"));
        var entered = new CountDownLatch(1); var proceed = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            store.publication = (n, b) -> { entered.countDown(); await(proceed); return store.install(n, b); };
            var applying = executor.submit(plan::apply); await(entered);
            var closing = executor.submit(session::close);
            // Wait for close to reject new work, without waiting for its provider cleanup.
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (true) {
                try { session.state().createToken().close(); }
                catch (SessionClosedException expected) { break; }
                if (System.nanoTime() > until) fail("Close never started");
            }
            assertFalse(closing.isDone()); proceed.countDown();
            var result = applying.get(10, TimeUnit.SECONDS);
            assertEquals(PARTIAL, result.outcome()); assertEquals(1, result.installed());
            assertEquals(ImportResult.Reason.SESSION_CLOSING, result.reason()); closing.get(10, TimeUnit.SECONDS);
            assertThrows(SessionClosedException.class, plan::apply); assertThrows(IllegalStateException.class, plan::summary);
            assertEquals(1, store.objects.size());
        } finally { proceed.countDown(); executor.shutdownNow(); session.close(); plan.close(); }
    }
    @Test void blockedIngressHoldsNoSessionOrProviderGateAndBorrowedStreamStaysOpen() throws Exception {
        var store = new Store(); var session = open(store);
        var entered = new CountDownLatch(1); var proceed = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            byte[] zip = fixture("history");
            var input = new ByteArrayInputStream(zip) {
                @Override public synchronized int read(byte[] b, int offset, int length) {
                    entered.countDown(); await(proceed); return super.read(b, offset, Math.min(length, 7));
                }
                @Override public void close() { fail("Borrowed ingress was closed"); }
            };
            var preparing = executor.submit(() -> prepareStream(session, input)); await(entered);
            executor.submit(session::close).get(5, TimeUnit.SECONDS); // Would deadlock if ingress held gate.
            proceed.countDown();
            assertInstanceOf(SessionClosedException.class, assertThrows(ExecutionException.class,
                    () -> preparing.get(10, TimeUnit.SECONDS)).getCause());
            assertEquals(0, store.calls);
        } finally { proceed.countDown(); executor.shutdownNow(); session.close(); }
    }
    private static BackupImport prepareStream(VaultSession session, InputStream input) throws IOException {
        return session.prepareImport(input, BackupLimits.DEFAULT);
    }
    @Test void nioImportPreservesEveryPreexistingByteAndRefusesWrongKind() throws Exception {
        var store = new Store(); Files.write(directory.resolve("vault"), store.vault);
        var objects = Files.createDirectory(directory.resolve("objects-v1"));
        Files.write(objects.resolve("unrelated"), new byte[]{9, 8, 7});
        String invalidUnrelatedId = "f".repeat(64);
        Files.write(objects.resolve(invalidUnrelatedId), new byte[]{6, 5, 4});
        var all = history(); Files.write(objects.resolve(all.firstKey()), all.get(all.firstKey()));
        try (var session = assertInstanceOf(OpenResult.Opened.class, NioTotipo.open(directory, "fixture password".toCharArray())).session()) {
            finished(session);
            Path blocked = Files.createDirectory(objects.resolve(all.lastKey()));
            assertThrows(BackupException.class, () -> prepare(session, fixture("history")));
            try (var children = Files.list(objects)) { assertEquals(4, children.count()); }
            Files.delete(blocked);
            Files.createSymbolicLink(blocked, directory.resolve("vault"));
            assertThrows(BackupException.class, () -> prepare(session, fixture("history")));
            assertTrue(Files.isSymbolicLink(blocked));
            assertArrayEquals(store.vault, Files.readAllBytes(directory.resolve("vault")));
            Files.delete(blocked);
            try (var plan = prepare(session, fixture("history"))) {
                var result = plan.apply(); assertEquals(COMPLETE, result.outcome()); assertEquals(1, result.retained());
            }
            assertArrayEquals(store.vault, Files.readAllBytes(directory.resolve("vault")));
            assertArrayEquals(new byte[]{9, 8, 7}, Files.readAllBytes(objects.resolve("unrelated")));
            assertArrayEquals(new byte[]{6, 5, 4}, Files.readAllBytes(objects.resolve(invalidUnrelatedId)));
            for (var e : all.entrySet()) assertArrayEquals(e.getValue(), Files.readAllBytes(objects.resolve(e.getKey())));
        }
    }
    @Test void coordinatedDelegateUsesExistingWholeRootDomainWithoutSessionUnderBridge() throws Exception {
        var store = new Store(); Files.write(directory.resolve("vault"), store.vault);
        var rootGate = new java.util.concurrent.locks.ReentrantLock();
        TotipoStore delegate = NioStoreComposition.coordinatedDelegate(directory, new NioDurability());
        TotipoStore wrapper = new TotipoStore() {
            private <T> T call(java.util.function.Supplier<T> action) {
                rootGate.lock(); try { return action.get(); } finally { rootGate.unlock(); }
            }
            @Override public BoundedRead readVault(int n) { return call(() -> delegate.readVault(n)); }
            @Override public BoundedRead readObject(ObjectName name, int n) { return call(() -> delegate.readObject(name, n)); }
            @Override public ObjectScan scanObjects() { return call(delegate::scanObjects); }
            @Override public ObjectWrite publishObject(ObjectName name, byte[] bytes) { return call(() -> delegate.publishObject(name, bytes)); }
            @Override public VaultCreate createVault(byte[] bytes) { return call(() -> delegate.createVault(bytes)); }
            @Override public void close() { call(() -> { delegate.close(); return true; }); }
        };
        try (var session = assertInstanceOf(OpenResult.Opened.class, Totipo.open(wrapper, "fixture password".toCharArray())).session()) {
            finished(session);
            try (var plan = prepare(session, fixture("history"))) {
                assertFalse(rootGate.isHeldByCurrentThread());
                var executor = Executors.newSingleThreadExecutor();
                try {
                    rootGate.lock(); // Simulate a bridge already active before session apply is admitted.
                    Future<ImportResult> applying;
                    try { applying = executor.submit(plan::apply); }
                    finally { rootGate.unlock(); } // No session-under-bridge call or wait.
                    assertEquals(COMPLETE, applying.get(10, TimeUnit.SECONDS).outcome());
                } finally { executor.shutdownNow(); }
                refresh(session); assertEquals(1, session.state().tokens().size());
                for (var e : history().entrySet()) assertArrayEquals(e.getValue(), Files.readAllBytes(directory.resolve("objects-v1").resolve(e.getKey())));
            }
        }
    }
}
