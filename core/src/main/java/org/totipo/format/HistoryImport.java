package org.totipo.format;

import org.totipo.backup.*;
import org.totipo.spi.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.totipo.backup.BackupException.Reason.*;

/** Authenticated encrypted replay. All methods run under the owning session provider gate. */
final class HistoryImport implements AutoCloseable {
    private final BackupZip.Archive archive;
    private final byte[] vault;
    final BackupSummary summary;
    private final byte[] knowledge; // 0 unacknowledged, 1 acknowledged, 2 uncertain
    private final boolean[] installed;
    private boolean mayHaveInstalled;
    private record Fact(TokenId token, List<ObjectId> parents) { }

    private HistoryImport(BackupZip.Archive archive, byte[] vault, int gaps) {
        this.archive = archive; this.vault = vault;
        var s = archive.summary;
        summary = new BackupSummary(s.vaultId(), s.objects(), gaps, true, s.archiveSha256());
        knowledge = new byte[s.objects()]; installed = new boolean[s.objects()];
    }
    static HistoryImport prepare(BackupZip.Archive archive, byte[] canonical, byte[] root,
                                 TotipoStore store, BooleanSupplier closing) throws IOException {
        byte[] vault = archive.payload(1);
        if (!Arrays.equals(vault, canonical)) throw new BackupException(VAULT_CHANGED);
        var facts = new HashMap<ObjectId, Fact>();
        for (int i = 0; i < archive.summary.objects(); i++) {
            BackupZip.cancelled();
            if (closing.getAsBoolean()) throw new org.totipo.SessionClosedException();
            var id = ObjectId.fromFilename(archive.name(i));
            var validation = TokenStoreReader.validate(id, archive.payload(i + 2), root);
            if (validation.token() == null) throw new BackupException(INVALID_OBJECT);
            var token = validation.token().token();
            try { facts.put(id, new Fact(token.tokenId(), token.parents())); }
            finally { token.value().credential().secret().clear(); }
        }
        int gaps = 0;
        for (var fact : facts.values()) for (var parent : fact.parents) {
            var target = facts.get(parent);
            if (target == null || !target.token.equals(fact.token)) gaps++;
        }
        var plan = new HistoryImport(archive, vault, gaps);
        var reason = plan.preflight(store, closing);
        if (reason == ImportResult.Reason.SESSION_CLOSING) throw new org.totipo.SessionClosedException();
        if (reason != ImportResult.Reason.NONE) throw new BackupException(switch (reason) {
            case CANCELLED -> CANCELLED;
            case SESSION_CLOSING -> CANCELLED;
            case VAULT_CHANGED -> VAULT_CHANGED;
            case TARGET_CONFLICT -> TARGET_CONFLICT;
            default -> SOURCE_UNAVAILABLE;
        });
        return plan;
    }
    private ImportResult.Reason binding(TotipoStore store) {
        try {
            var read = store.readVault(87);
            return read instanceof BoundedRead.Present present && Arrays.equals(vault, present.bytes())
                    ? ImportResult.Reason.NONE : ImportResult.Reason.VAULT_CHANGED;
        } catch (RuntimeException failure) { return ImportResult.Reason.VAULT_CHANGED; }
    }
    private static ImportResult.Reason cancelled(BooleanSupplier closing) {
        if (closing.getAsBoolean()) return ImportResult.Reason.SESSION_CLOSING;
        return Thread.currentThread().isInterrupted() ? ImportResult.Reason.CANCELLED : ImportResult.Reason.NONE;
    }
    private ImportResult.Reason preflight(TotipoStore store, BooleanSupplier closing) {
        var reason = cancelled(closing);
        if (reason != ImportResult.Reason.NONE) return reason;
        reason = binding(store);
        if (reason != ImportResult.Reason.NONE) return reason;
        for (int i = 0; i < knowledge.length; i++) {
            reason = cancelled(closing);
            if (reason != ImportResult.Reason.NONE) return reason;
            BoundedRead read;
            try { read = store.readObject(new ObjectName(archive.name(i)), 1024); }
            catch (RuntimeException failure) { return ImportResult.Reason.TARGET_UNAVAILABLE; }
            if (read instanceof BoundedRead.Absent) continue;
            if (read instanceof BoundedRead.Present present) {
                if (Arrays.equals(present.bytes(), archive.payload(i + 2))) continue;
                return ImportResult.Reason.TARGET_CONFLICT;
            }
            if (read instanceof BoundedRead.Undersized || read instanceof BoundedRead.Oversized)
                return ImportResult.Reason.TARGET_CONFLICT;
            return ImportResult.Reason.TARGET_UNAVAILABLE;
        }
        return binding(store);
    }
    ImportResult apply(TotipoStore store, BooleanSupplier closing) {
        var reason = preflight(store, closing);
        if (reason != ImportResult.Reason.NONE) return result(reason, false);
        for (int i = 0; i < knowledge.length; i++) {
            reason = cancelled(closing);
            if (reason != ImportResult.Reason.NONE) return result(reason, false);
            reason = binding(store); // Fresh binding immediately before every publication.
            if (reason != ImportResult.Reason.NONE) return result(reason, false);
            reason = cancelled(closing);
            if (reason != ImportResult.Reason.NONE) return result(reason, false);
            byte previous = knowledge[i];
            boolean previousMayHaveInstalled = mayHaveInstalled;
            mayHaveInstalled = true;
            knowledge[i] = 2; // Entering publication may lose acknowledgement, including unchecked failure.
            ObjectWrite write;
            try { write = store.publishObject(new ObjectName(archive.name(i)), archive.payload(i + 2)); }
            catch (RuntimeException failure) { return result(ImportResult.Reason.STORAGE, false); }
            if (write instanceof ObjectWrite.Written) { installed[i] = true; knowledge[i] = 1; }
            else if (write instanceof ObjectWrite.AlreadyPresentExact) {
                mayHaveInstalled = previousMayHaveInstalled; knowledge[i] = 1;
            } else if (write instanceof ObjectWrite.ExistingDifferent) {
                mayHaveInstalled = previousMayHaveInstalled;
                knowledge[i] = previous;
                return result(ImportResult.Reason.TARGET_CONFLICT, false);
            } else if (write instanceof ObjectWrite.Failed) {
                mayHaveInstalled = previousMayHaveInstalled;
                knowledge[i] = previous;
                return result(ImportResult.Reason.STORAGE, false);
            } else return result(ImportResult.Reason.STORAGE, false);
        }
        reason = binding(store);
        return result(reason, reason == ImportResult.Reason.NONE);
    }
    private ImportResult result(ImportResult.Reason reason, boolean complete) {
        int added = 0, retained = 0, uncertain = 0, remaining = 0;
        for (int i = 0; i < knowledge.length; i++) {
            if (knowledge[i] == 2) uncertain++;
            else if (knowledge[i] == 0) remaining++;
            else if (installed[i]) added++;
            else retained++;
        }
        var outcome = uncertain > 0 ? ImportResult.Outcome.UNCERTAIN
                : complete ? (mayHaveInstalled ? ImportResult.Outcome.COMPLETE : ImportResult.Outcome.NO_OP)
                : added + retained > 0 ? ImportResult.Outcome.PARTIAL : ImportResult.Outcome.FAILED;
        return new ImportResult(outcome, added, retained, uncertain, remaining, reason);
    }
    @Override public void close() { archive.close(); Arrays.fill(vault, (byte) 0); }
}
