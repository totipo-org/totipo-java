package org.totipo.format;

import org.totipo.backup.*;
import org.totipo.spi.*;
import java.io.*;
import java.util.*;
import static org.totipo.backup.RestoreResult.Bootstrap.*;

/** Internal bridge for detached preparation and reserved exact replay. No root survives prepare. */
public final class NewLocationRestore implements BackupRestore {
    private final HistoryImport replay;
    private final byte[] vault;
    private RestoreReservation destination;
    private RestoreResult.Bootstrap bootstrap = NOT_INSTALLED;
    private boolean locationUncertain;
    private volatile boolean stopping;
    private boolean closed;
    private NewLocationRestore(HistoryImport replay, byte[] vault) { this.replay = replay; this.vault = vault; }
    public static BackupRestore prepare(InputStream input, BackupLimits limits, char[] password) throws IOException {
        Objects.requireNonNull(password);
        var archive = BackupZip.read(input, limits);
        byte[] encoded = null, root = null, vault = null;
        boolean transferred = false;
        try {
            encoded = PasswordBytes.encode(password);
            if (encoded == null) throw new IllegalArgumentException("Invalid password input");
            vault = archive.payload(1);
            try (var unlocked = new VaultUnlocker().unlock(vault, encoded)) {
                if (unlocked.status() != VaultUnlockResult.Status.UNLOCKED)
                    throw new BackupException(BackupException.Reason.AUTHENTICATION_FAILED);
                root = unlocked.root();
                int gaps = HistoryImport.validateArchive(archive, root, () -> false);
                BackupZip.cancelled();
                var result = new NewLocationRestore(HistoryImport.replay(archive, vault.clone(), gaps), vault);
                transferred = true;
                return result;
            }
        } finally {
            VaultLifecycle.wipe(encoded); VaultLifecycle.wipe(root);
            if (!transferred) { archive.close(); VaultLifecycle.wipe(vault); }
        }
    }
    private void active() { if (closed) throw new IllegalStateException("Restore plan closed"); }
    @Override public synchronized BackupSummary summary() { active(); return replay.summary; }
    @Override public synchronized RestoreResult install(RestoreReservation reservation) {
        active(); Objects.requireNonNull(reservation);
        if (destination != null) throw new IllegalStateException("Destination already owned; use retry");
        destination = reservation;
        return apply();
    }
    @Override public synchronized RestoreResult retry() {
        active(); if (destination == null) throw new IllegalStateException("No destination");
        return apply();
    }
    private boolean cancelled() { return stopping || Thread.currentThread().isInterrupted(); }
    private RestoreResult apply() {
        ImportResult objects = replay.result(ImportResult.Reason.STORAGE, false);
        boolean location = false;
        try {
            if (!replay.summary.archiveSha256().equals(destination.archiveSha256())) return result(objects, false, false);
            if (cancelled()) return result(cancelledObjects(), false, false);
            locationUncertain = true;
            location = destination.acknowledgeLocation();
            locationUncertain = !location;
            if (!location) return result(objects, false, false);
            if (cancelled()) return result(cancelledObjects(), location, false);
            var previous = bootstrap;
            bootstrap = UNCERTAIN; // Entering provider work can lose acknowledgement.
            var write = destination.installVault(vault.clone());
            if (write instanceof ObjectWrite.Written || write instanceof ObjectWrite.AlreadyPresentExact) bootstrap = ACKNOWLEDGED;
            else {
                if (write instanceof ObjectWrite.Failed || write instanceof ObjectWrite.ExistingDifferent) bootstrap = previous;
                return result(objects, location, false);
            }
            objects = replay.apply(destination, this::cancelled);
            if (objects.reason() == ImportResult.Reason.SESSION_CLOSING)
                objects = replay.result(ImportResult.Reason.CANCELLED, false);
            boolean complete = (objects.outcome() == ImportResult.Outcome.COMPLETE || objects.outcome() == ImportResult.Outcome.NO_OP)
                    && replay.exactInventory(destination);
            if (!complete && objects.reason() == ImportResult.Reason.NONE)
                objects = replay.result(ImportResult.Reason.TARGET_UNAVAILABLE, false);
            if (complete) {
                locationUncertain = true;
                location = destination.acknowledgeLocation(); locationUncertain = !location;
                complete = location && replay.exactInventory(destination);
                if (!complete) objects = replay.result(ImportResult.Reason.STORAGE, false);
            }
            return result(objects, location, complete);
        } catch (RuntimeException failure) { return result(objects, location, false); }
    }
    private ImportResult cancelledObjects() {
        return replay.result(ImportResult.Reason.CANCELLED, false);
    }
    private RestoreResult result(ImportResult objects, boolean location, boolean complete) {
        var outcome = locationUncertain || bootstrap == UNCERTAIN || objects.uncertain() > 0 ? RestoreResult.Outcome.UNCERTAIN
                : complete ? RestoreResult.Outcome.COMPLETE
                : bootstrap == ACKNOWLEDGED ? RestoreResult.Outcome.PARTIAL : RestoreResult.Outcome.REJECTED;
        return new RestoreResult(outcome, bootstrap, location, objects, replay.receipts());
    }
    @Override public void cancel() { stopping = true; }
    @Override public void close() {
        stopping = true;
        synchronized (this) {
            if (closed) return;
            closed = true;
            try { replay.close(); Arrays.fill(vault, (byte) 0); }
            finally { VaultLifecycle.cleanup(destination); }
        }
    }
}
