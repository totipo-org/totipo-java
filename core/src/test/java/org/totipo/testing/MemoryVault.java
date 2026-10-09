package org.totipo.testing;

import org.totipo.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Provider fault fixture; tests consuming it use only the application-facing API. */
public final class MemoryVault {
    public final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    public volatile byte[] bootstrap;
    public volatile boolean observationUnavailable, readUnavailable, failCreateBeforeMutation, failCreate;
    public volatile int publicationMode; // 0 acknowledged, 1 throws before write, 2 writes then throws
    public volatile int writes, scans, publications;
    public volatile RuntimeException fatalObservation;
    public volatile boolean publicationClosed, bootstrapClosed;
    public volatile int initialInstallAttempts;
    public volatile CountDownLatch entered, proceed, scanEntered, scanProceed;
    public final List<String> attemptedIds = new CopyOnWriteArrayList<>();
    public final List<byte[]> attemptedBytes = new CopyOnWriteArrayList<>();
    public VaultSession create() {
        return ((CreateVaultResult.Created) createResult("password".toCharArray())).session();
    }
    public CreateVaultResult createResult(char[] password) {
        return Totipo.create(new Store(), password);
    }
    public VaultSession open() { return ((OpenResult.Opened) openResult("password".toCharArray())).session(); }
    public OpenResult openResult(char[] password) {
        return Totipo.open(new Store(), password);
    }
    private final class Store implements org.totipo.spi.TotipoStore {
        @Override public org.totipo.spi.ObjectScan scanObjects() {
            scans++;
            try {
                if (scanEntered != null) { scanEntered.countDown(); await(scanProceed); }
            } catch (IOException e) { return new org.totipo.spi.ObjectScan.Incomplete(List.of(), org.totipo.spi.StoreFailure.UNAVAILABLE); }
            if (fatalObservation != null) throw fatalObservation;
            var entries = objects.entrySet().stream().map(e -> new org.totipo.spi.ObjectEntry(
                    new org.totipo.spi.ObjectName(e.getKey()), org.totipo.spi.EntryKind.REGULAR,
                    OptionalLong.of(e.getValue().length))).toList();
            return observationUnavailable ? new org.totipo.spi.ObjectScan.Incomplete(entries, org.totipo.spi.StoreFailure.UNAVAILABLE)
                    : new org.totipo.spi.ObjectScan.Complete(entries);
        }
        @Override public org.totipo.spi.BoundedRead readVault(int expected) {
            if (readUnavailable) return new org.totipo.spi.BoundedRead.Unavailable(org.totipo.spi.StoreFailure.UNAVAILABLE);
            return read(bootstrap, expected);
        }
        @Override public org.totipo.spi.BoundedRead readObject(org.totipo.spi.ObjectName name, int expected) {
            return read(objects.get(name.value()), expected);
        }
        @Override public org.totipo.spi.ObjectWrite publishObject(org.totipo.spi.ObjectName name, byte[] bytes) {
            publications++; attemptedIds.add(name.value()); attemptedBytes.add(bytes.clone());
            try { if (entered != null) { entered.countDown(); await(proceed); } }
            catch (IOException e) { return new org.totipo.spi.ObjectWrite.Failed(org.totipo.spi.StoreFailure.UNAVAILABLE); }
            if (publicationMode == 1) return new org.totipo.spi.ObjectWrite.Failed(org.totipo.spi.StoreFailure.UNAVAILABLE);
            var previous = objects.putIfAbsent(name.value(), bytes.clone());
            if (previous == null) writes++;
            else if (!Arrays.equals(previous, bytes)) return new org.totipo.spi.ObjectWrite.ExistingDifferent();
            if (publicationMode == 2) return new org.totipo.spi.ObjectWrite.Uncertain(org.totipo.spi.StoreFailure.UNAVAILABLE);
            return previous == null ? new org.totipo.spi.ObjectWrite.Written() : new org.totipo.spi.ObjectWrite.AlreadyPresentExact();
        }
        @Override public org.totipo.spi.VaultCreate createVault(byte[] bytes) {
            if (failCreateBeforeMutation) return new org.totipo.spi.VaultCreate.Failed(org.totipo.spi.StoreFailure.UNAVAILABLE);
            initialInstallAttempts++;
            if (bootstrap != null) return new org.totipo.spi.VaultCreate.AlreadyPresent();
            bootstrap = bytes.clone();
            return failCreate ? new org.totipo.spi.VaultCreate.Uncertain(org.totipo.spi.StoreFailure.UNAVAILABLE)
                    : new org.totipo.spi.VaultCreate.Created();
        }
        @Override public void close() { publicationClosed = true; bootstrapClosed = true; }
    }
    private static org.totipo.spi.BoundedRead read(byte[] bytes, int expected) {
        if (bytes == null) return new org.totipo.spi.BoundedRead.Absent();
        if (bytes.length < expected) return new org.totipo.spi.BoundedRead.Undersized(bytes.length);
        if (bytes.length > expected) return new org.totipo.spi.BoundedRead.Oversized();
        return new org.totipo.spi.BoundedRead.Present(bytes);
    }
    private static void await(CountDownLatch latch) throws IOException {
        if (latch == null) return;
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IOException("Fixture timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
    }
}
