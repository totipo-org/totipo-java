package org.totipo.storage.nio;

import org.totipo.*;
import org.totipo.backup.*;
import org.totipo.spi.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.totipo.backup.RestoreResult.Outcome.*;

@Timeout(40)
class NioRestoreTest {
    @TempDir Path directory;
    static byte[] fixture(String name) throws IOException {
        Path resources = Path.of(System.getProperty("totipo.test.snapshot")).getParent().getParent();
        return Files.readAllBytes(resources.resolve("backup/profile-1/" + name + ".zip"));
    }
    static BackupRestore plan(String name) throws IOException {
        return BackupArchives.prepareRestore(new ByteArrayInputStream(fixture(name)), BackupLimits.DEFAULT, "fixture password".toCharArray());
    }
    static Map<String, byte[]> entries(String name) throws IOException {
        var result = new TreeMap<String, byte[]>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(fixture(name)))) {
            ZipEntry e; while ((e = in.getNextEntry()) != null) result.put(e.getName(), in.readAllBytes());
        }
        result.remove("totipo-backup.txt"); return result;
    }
    static void exact(Path root, String name) throws IOException {
        for (var e : entries(name).entrySet()) assertArrayEquals(e.getValue(), Files.readAllBytes(root.resolve(e.getKey())), e.getKey());
        try (var paths = Files.walk(root)) {
            assertEquals(entries(name).size() + 1, paths.filter(Files::isRegularFile).count()); // plus allocation marker
        }
    }
    private RestoreReservation reserve(BackupRestore plan, String child, boolean coordinated) throws IOException {
        return coordinated ? NioStoreComposition.reserveRestore(directory, child, plan.summary().archiveSha256(), new NioDurability())
                : NioRestore.reserve(directory, child, plan.summary().archiveSha256());
    }
    @ParameterizedTest @CsvSource({"empty,false", "history,false", "missing-parent,false", "empty,true", "history,true", "missing-parent,true"})
    void goldenRestorationIsByteExactAndOpensWithOriginalPassword(String name, boolean coordinated) throws Exception {
        Files.write(directory.resolve("user-file"), new byte[]{4, 5, 6});
        Path root = directory.resolve("restored");
        try (var plan = plan(name)) {
            var reservation = reserve(plan, "restored", coordinated);
            assertEquals(COMPLETE, plan.install(reservation).outcome());
            assertEquals(COMPLETE, plan.retry().outcome()); // exact acknowledgement, no reauthoring
            exact(root, name);
        }
        assertArrayEquals(new byte[]{4, 5, 6}, Files.readAllBytes(directory.resolve("user-file")));
        try (var session = assertInstanceOf(OpenResult.Opened.class, NioTotipo.open(root, "fixture password".toCharArray())).session()) {
            assertEquals(Totipo.vaultId(entries(name).get("vault")), session.vaultId());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!(session.state().observation() instanceof ObservationProgress.Finished)) {
                if (System.nanoTime() > deadline) fail("Observation timeout"); Thread.onSpinWait();
            }
            if (name.equals("history")) {
                assertEquals(1, session.state().tokens().size());
                assertTrue(session.state().tokens().get(0).alternatives().size() > 1);
                assertTrue(session.state().tokens().get(0).alternatives().stream()
                        .anyMatch(a -> a.descriptor().status() == TokenStatus.TOMBSTONED));
            }
        }
    }
    @Test void everyExistingEntryIncludingEmptyDirectoryAndSymlinkIsRefusedWithoutChange() throws Exception {
        try (var plan = plan("empty")) {
            Files.createDirectory(directory.resolve("empty"));
            Files.write(directory.resolve("file"), new byte[]{9});
            Files.createSymbolicLink(directory.resolve("link"), directory.resolve("empty"));
            for (String child : List.of("empty", "file", "link")) {
                assertThrows(FileAlreadyExistsException.class, () -> reserve(plan, child, false));
            }
            assertTrue(Files.isSymbolicLink(directory.resolve("link")));
            assertArrayEquals(new byte[]{9}, Files.readAllBytes(directory.resolve("file")));
            try (var children = Files.list(directory.resolve("empty"))) { assertEquals(0, children.count()); }
            assertThrows(IllegalArgumentException.class, () -> reserve(plan, "../escape", false));
            assertThrows(IOException.class, () -> NioRestore.recover(directory.resolve("empty"), plan.summary().archiveSha256()));
        }
    }
    @Test void concurrentDirectoryReservationHasExactlyOneWinner() throws Exception {
        try (var plan = plan("empty")) {
            String digest = plan.summary().archiveSha256();
            var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
            Callable<Boolean> task = () -> { start.await(); try (var r = NioRestore.reserve(directory, "race", digest)) { return r.archiveSha256().equals(digest); }
                catch (FileAlreadyExistsException expected) { return false; } };
            try {
                var first = executor.submit(task); var second = executor.submit(task); start.countDown();
                assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
                assertFalse(Files.exists(directory.resolve("race/vault")));
            } finally { executor.shutdownNow(); }
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void bootstrapLostAcknowledgementRecoversByForceWithoutRewriting(boolean coordinated) throws Exception {
        Path root = directory.resolve("restore"); String digest;
        var durability = new NioDurability(); int[] installs = {0};
        try (var plan = plan("history")) {
            digest = plan.summary().archiveSha256(); reserve(plan, "restore", coordinated).close();
            var vaults = new NioVaultStorage.Operations(durability, coordinated ? NioCanonicalInstaller.COORDINATED_MOVE : NioCanonicalInstaller.HARD_LINK) {
                @Override void installIfAbsent(Path target, Path temp) throws IOException { super.installIfAbsent(target, temp); installs[0]++; }
                @Override void syncDirectory(Path p) throws IOException { throw new IOException("lost bootstrap acknowledgement"); }
            };
            var store = NioTotipoStore.open(root, new NioObjectStorage.Operations(durability), vaults, new NioTotipoStore.ScanOperations());
            var result = plan.install(NioRestore.wrap(root, digest, durability, store));
            assertEquals(UNCERTAIN, result.outcome()); assertEquals(RestoreResult.Bootstrap.UNCERTAIN, result.bootstrap());
            assertEquals(0, result.objects().installed()); assertEquals(1, installs[0]);
        }
        var time = Files.getLastModifiedTime(root.resolve("vault"));
        try (var recovered = plan("history")) {
            var reservation = coordinated ? NioStoreComposition.recoverRestore(root, digest, durability) : NioRestore.recover(root, digest);
            assertEquals(COMPLETE, recovered.install(reservation).outcome()); exact(root, "history");
        }
        assertEquals(time, Files.getLastModifiedTime(root.resolve("vault"))); assertEquals(1, installs[0]);
    }
    @ParameterizedTest @CsvSource({"1,0,false", "2,0,false", "3,0,false", "1,1,false", "2,1,false", "3,1,false", "1,2,false", "2,2,false", "3,2,false", "1,0,true", "2,0,true", "3,0,true", "1,1,true", "2,1,true", "3,1,true"})
    void realObjectFaultsAtFirstIntermediateAndFinalHaveTruthfulReceiptsAndExactRetry(int position, int mode, boolean coordinated) throws Exception {
        Path root = directory.resolve("restore"); var durability = new NioDurability();
        int[] index = {0}; boolean[] failing = {true};
        try (var plan = plan("history")) {
            String digest = plan.summary().archiveSha256(); reserve(plan, "restore", coordinated).close();
            var installer = coordinated ? NioCanonicalInstaller.COORDINATED_MOVE : NioCanonicalInstaller.HARD_LINK;
            var objects = new NioObjectStorage.Operations(durability, installer) {
                @Override void at(String point) throws IOException {
                    if (point.equals("snapshot")) index[0]++;
                    if (failing[0] && index[0] == position && point.equals(mode == 0 ? "before-link" : "after-link")) {
                        if (mode == 2) throw new IllegalStateException("acknowledgement lost");
                        throw new IOException("injected publication failure");
                    }
                }
            };
            var store = NioTotipoStore.open(root, objects, new NioVaultStorage.Operations(durability, installer), new NioTotipoStore.ScanOperations());
            var result = plan.install(NioRestore.wrap(root, digest, durability, store));
            assertEquals(mode == 0 ? PARTIAL : UNCERTAIN, result.outcome());
            assertEquals(position - 1, result.objects().installed());
            assertEquals(mode == 0 ? 0 : 1, result.objects().uncertain());
            assertEquals(3, result.inventory().size());
            assertEquals(position - 1, result.inventory().stream().filter(r -> r.knowledge() == RestoreResult.Knowledge.INSTALLED).count());
            assertThrows(UnsupportedOperationException.class, () -> result.inventory().clear());
            assertArrayEquals(entries("history").get("vault"), Files.readAllBytes(root.resolve("vault")));
            // Cancellation and unsuccessful re-entry cannot erase cumulative progress/uncertainty.
            Thread.currentThread().interrupt();
            try { var cancelled = plan.retry(); assertEquals(result.objects().installed(), cancelled.objects().installed());
                assertEquals(result.objects().uncertain(), cancelled.objects().uncertain()); }
            finally { Thread.interrupted(); }
            failing[0] = false;
            assertEquals(COMPLETE, plan.retry().outcome()); exact(root, "history");
        }
    }
    @Test void definiteBootstrapFailureLeavesNoCanonicalAndCanExplicitlyRetry() throws Exception {
        Path root = directory.resolve("restore"); var durability = new NioDurability(); boolean[] fail = {true};
        try (var plan = plan("empty")) {
            String digest = plan.summary().archiveSha256(); reserve(plan, "restore", false).close();
            var vaults = new NioVaultStorage.Operations(durability) {
                @Override void syncStage(java.nio.channels.FileChannel channel) throws IOException {
                    if (fail[0]) throw new IOException("pre-install failure"); super.syncStage(channel);
                }
            };
            var store = NioTotipoStore.open(root, new NioObjectStorage.Operations(durability), vaults, new NioTotipoStore.ScanOperations());
            assertEquals(REJECTED, plan.install(NioRestore.wrap(root, digest, durability, store)).outcome());
            assertFalse(Files.exists(root.resolve("vault"))); fail[0] = false;
            assertEquals(COMPLETE, plan.retry().outcome());
        }
    }
    @Test void interruptedAndCancelledJobsPreserveAllocationAndRequireExplicitRecovery() throws Exception {
        Path root = directory.resolve("restore"); String digest;
        try (var plan = plan("history")) {
            digest = plan.summary().archiveSha256(); var destination = reserve(plan, "restore", false);
            Thread.currentThread().interrupt();
            try { assertEquals(REJECTED, plan.install(destination).outcome()); }
            finally { Thread.interrupted(); }
            assertFalse(Files.exists(root.resolve("vault")));
            plan.cancel(); assertEquals(REJECTED, plan.retry().outcome());
        }
        assertThrows(FileAlreadyExistsException.class, () -> NioRestore.reserve(directory, "restore", digest));
        try (var recovered = plan("history")) { assertEquals(COMPLETE, recovered.install(NioRestore.recover(root, digest)).outcome()); }
    }
    @Test void recoveryRejectsOtherArchiveAndNeverReplacesBootstrapOrOccupiedObjects() throws Exception {
        Path root = directory.resolve("restore");
        try (var plan = plan("history")) {
            String digest = plan.summary().archiveSha256(); reserve(plan, "restore", false).close();
            assertThrows(IOException.class, () -> NioRestore.recover(root, "0".repeat(64)));
            Files.write(root.resolve("vault"), new byte[]{7, 8});
            assertEquals(REJECTED, plan.install(NioRestore.recover(root, digest)).outcome());
            assertArrayEquals(new byte[]{7, 8}, Files.readAllBytes(root.resolve("vault")));
        }
        Path another = directory.resolve("another");
        try (var plan = plan("history")) {
            var reservation = reserve(plan, "another", false);
            Files.createDirectory(another.resolve("objects-v1"));
            String name = entries("history").keySet().stream().filter(s -> s.startsWith("objects-v1/")).findFirst().orElseThrow();
            Files.write(another.resolve(name), new byte[]{9});
            assertEquals(PARTIAL, plan.install(reservation).outcome()); assertArrayEquals(new byte[]{9}, Files.readAllBytes(another.resolve(name)));
        }
    }
    @Test void unsupportedDirectoryDurabilityNeverReportsComplete() throws Exception {
        try (var plan = plan("empty")) {
            var reservation = NioRestore.reserve(directory, "restore", plan.summary().archiveSha256(), p -> { throw new IOException("unsupported"); });
            assertNotEquals(COMPLETE, plan.install(reservation).outcome()); assertFalse(Files.exists(directory.resolve("restore/vault")));
        }
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void cancellationAtBootstrapAndObjectBoundariesNeverHidesPartialInstallation(int after) throws Exception {
        Path root = directory.resolve("restore"); var durability = new NioDurability(); int[] count = {0};
        try (var plan = plan("history")) {
            String digest = plan.summary().archiveSha256(); reserve(plan, "restore", false).close();
            var vaults = new NioVaultStorage.Operations(durability) {
                @Override void syncDirectory(Path path) throws IOException {
                    super.syncDirectory(path); if (after == 0) Thread.currentThread().interrupt();
                }
            };
            var objects = new NioObjectStorage.Operations(durability) {
                @Override void at(String point) {
                    if (point.equals("after-link") && ++count[0] == after) Thread.currentThread().interrupt();
                }
            };
            var store = NioTotipoStore.open(root, objects, vaults, new NioTotipoStore.ScanOperations());
            try {
                var result = plan.install(NioRestore.wrap(root, digest, durability, store));
                // Interruption inside provider work may prevent its force acknowledgement.
                assertNotEquals(REJECTED, result.outcome());
                if (result.outcome() == COMPLETE) assertEquals(3, result.objects().installed());
                assertArrayEquals(entries("history").get("vault"), Files.readAllBytes(root.resolve("vault")));
            } finally { Thread.interrupted(); }
            assertEquals(COMPLETE, plan.retry().outcome());
        }
    }
    @Test void shutdownWaitsForEnteredPublicationAndPreservesUnregisteredPartialVault() throws Exception {
        Path root = directory.resolve("restore"); var durability = new NioDurability();
        var entered = new CountDownLatch(1); var proceed = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2); var plan = plan("history");
        String digest = plan.summary().archiveSha256(); reserve(plan, "restore", false).close();
        var objects = new NioObjectStorage.Operations(durability) {
            @Override void at(String point) throws IOException {
                if (point.equals("before-link")) {
                    entered.countDown();
                    try { if (!proceed.await(10, TimeUnit.SECONDS)) throw new IOException("timeout"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                }
            }
        };
        var store = NioTotipoStore.open(root, objects, new NioVaultStorage.Operations(durability), new NioTotipoStore.ScanOperations());
        var destination = NioRestore.wrap(root, digest, durability, store);
        try {
            var applying = executor.submit(() -> plan.install(destination));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            plan.cancel(); // lock-free shutdown admission boundary
            var closing = executor.submit(plan::close);
            assertFalse(closing.isDone()); proceed.countDown();
            var receipt = applying.get(10, TimeUnit.SECONDS);
            assertEquals(PARTIAL, receipt.outcome()); assertEquals(1, receipt.objects().installed());
            assertEquals(ImportResult.Reason.CANCELLED, receipt.objects().reason());
            closing.get(10, TimeUnit.SECONDS);
            assertThrows(IllegalStateException.class, destination::archiveSha256);
            assertTrue(Files.isRegularFile(root.resolve("vault")));
            try (var recovered = plan("history")) {
                assertEquals(COMPLETE, recovered.install(NioRestore.recover(root, digest)).outcome());
            }
        } finally { proceed.countDown(); executor.shutdownNow(); plan.close(); }
    }
    @Test void finalInventoryRereadRefusesCompletionAfterExternalObjectLoss() throws Exception {
        Path root = directory.resolve("restore"); var durability = new NioDurability(); int[] count = {0};
        try (var plan = plan("history")) {
            String digest = plan.summary().archiveSha256(); reserve(plan, "restore", false).close();
            var objects = new NioObjectStorage.Operations(durability) {
                @Override void at(String point) throws IOException {
                    if (point.equals("directory-sync") && ++count[0] == 3) {
                        String first = entries("history").keySet().stream().filter(s -> s.startsWith("objects-v1/")).findFirst().orElseThrow();
                        Files.delete(root.resolve(first)); // Simulated independent namespace mutation.
                    }
                }
            };
            var store = NioTotipoStore.open(root, objects, new NioVaultStorage.Operations(durability), new NioTotipoStore.ScanOperations());
            var result = plan.install(NioRestore.wrap(root, digest, durability, store));
            assertEquals(PARTIAL, result.outcome()); assertEquals(3, result.objects().installed());
            assertEquals(ImportResult.Reason.TARGET_UNAVAILABLE, result.objects().reason());
            assertEquals(COMPLETE, plan.retry().outcome());
        }
    }
    @Test void locationAcknowledgementLossIsUncertainUntilExplicitRetry() throws Exception {
        Path root = directory.resolve("restore"); int[] parentForces = {0}; boolean[] failing = {true};
        StorageDurability durability = path -> {
            new NioDurability().syncDirectory(path);
            if (path.equals(directory) && ++parentForces[0] == 2 && failing[0]) throw new IOException("lost location acknowledgement");
        };
        try (var plan = plan("history")) {
            var reservation = NioRestore.reserve(directory, "restore", plan.summary().archiveSha256(), durability);
            var result = plan.install(reservation);
            assertEquals(UNCERTAIN, result.outcome()); assertFalse(result.locationAcknowledged());
            assertEquals(RestoreResult.Bootstrap.ACKNOWLEDGED, result.bootstrap()); assertEquals(3, result.objects().installed());
            exact(root, "history"); failing[0] = false;
            assertEquals(COMPLETE, plan.retry().outcome());
        }
    }
    @Test void recoveryPreservesWrongKindsAndSymlinksAtCanonicalNames() throws Exception {
        for (boolean link : List.of(false, true)) {
            String child = link ? "link-vault" : "dir-vault"; Path root = directory.resolve(child);
            try (var plan = plan("empty")) {
                var reservation = reserve(plan, child, false);
                if (link) Files.createSymbolicLink(root.resolve("vault"), directory.resolve("sentinel"));
                else Files.createDirectory(root.resolve("vault"));
                assertEquals(REJECTED, plan.install(reservation).outcome());
                assertTrue(link ? Files.isSymbolicLink(root.resolve("vault")) : Files.isDirectory(root.resolve("vault")));
            }
        }
    }
    @Test void allocationBindingMismatchIsRejectedAndTornIntentIsNeverRepaired() throws Exception {
        try (var plan = plan("history"); var other = plan("empty")) {
            var reservation = reserve(other, "other", false);
            assertEquals(REJECTED, plan.install(reservation).outcome());
            assertFalse(Files.exists(directory.resolve("other/vault")));
        }
        try (var plan = plan("empty")) {
            var reservation = reserve(plan, "torn", false); reservation.close();
            Path marker = directory.resolve("torn/.totipo-restore-v1");
            Files.write(marker, new byte[]{1});
            assertThrows(IOException.class, () -> NioRestore.recover(directory.resolve("torn"), plan.summary().archiveSha256()));
            assertArrayEquals(new byte[]{1}, Files.readAllBytes(marker));
        }
    }
    @Test void objectLostAcknowledgementSurvivesShutdownAndExplicitRecovery() throws Exception {
        Path root = directory.resolve("restore"); var durability = new NioDurability(); String digest;
        try (var plan = plan("history")) {
            digest = plan.summary().archiveSha256(); reserve(plan, "restore", false).close();
            var objects = new NioObjectStorage.Operations(durability) {
                @Override void at(String point) throws IOException {
                    if (point.equals("after-link")) throw new IOException("lost first object acknowledgement");
                }
            };
            var store = NioTotipoStore.open(root, objects, new NioVaultStorage.Operations(durability), new NioTotipoStore.ScanOperations());
            assertEquals(UNCERTAIN, plan.install(NioRestore.wrap(root, digest, durability, store)).outcome());
        }
        try (var recovered = plan("history")) {
            var result = recovered.install(NioRestore.recover(root, digest));
            assertEquals(COMPLETE, result.outcome()); assertEquals(1, result.objects().retained());
            assertEquals(2, result.objects().installed()); exact(root, "history");
        }
    }
}
