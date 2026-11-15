package org.totipo.format;

import org.totipo.*;
import org.totipo.backup.*;
import org.totipo.spi.*;
import java.io.IOException;
import java.util.*;
import static org.totipo.backup.BackupException.Reason.*;

/** Authenticated capture under the caller's session provider gate. */
final class BackupSource {
    private BackupSource() { }
    private record Fact(TokenId token, List<ObjectId> parents) { }
    private static byte[] vault(TotipoStore store, VaultId expected) throws IOException {
        BackupZip.cancelled();
        var read = store.readVault(87);
        if (!(read instanceof BoundedRead.Present present)) throw new BackupException(SOURCE_UNAVAILABLE);
        byte[] bytes = present.bytes();
        try { if (!Totipo.vaultId(bytes).equals(expected)) throw new BackupException(VAULT_CHANGED); }
        catch (IllegalArgumentException invalid) { throw new BackupException(VAULT_CHANGED); }
        return bytes;
    }
    static BackupCapture capture(TotipoStore store, byte[] root, VaultId id, BackupLimits limits) throws IOException {
        Objects.requireNonNull(limits);
        byte[] vault = vault(store, id), spool = null;
        boolean transferred = false;
        try {
            BackupZip.cancelled();
            var scan = store.scanObjects(limits.observedEntries());
            if (scan.entries().size() > limits.observedEntries()) throw new BackupException(CAPACITY);
            if (scan instanceof ObjectScan.Incomplete incomplete)
                throw new BackupException(incomplete.reason() == StoreFailure.CAPACITY ? CAPACITY
                        : incomplete.reason() == StoreFailure.UNSUPPORTED ? UNSUPPORTED : SOURCE_UNAVAILABLE);
            var names = new ArrayList<String>();
            for (var entry : scan.entries()) {
                String name = entry.name().value();
                if (name.matches("[0-9a-f]{64}")) {
                    if (names.size() == limits.objects()) throw new BackupException(CAPACITY);
                    names.add(name);
                }
            }
            spool = new byte[names.size() * 1024];
            var facts = new HashMap<ObjectId, Fact>();
            for (int i = 0; i < names.size(); i++) {
                BackupZip.cancelled();
                var name = names.get(i);
                var read = store.readObject(new ObjectName(name), 1024);
                if (!(read instanceof BoundedRead.Present present)) throw new BackupException(SOURCE_UNAVAILABLE);
                byte[] bytes = present.bytes();
                var objectId = ObjectId.fromFilename(name);
                var validation = TokenStoreReader.validate(objectId, bytes, root);
                if (validation.token() == null) throw new BackupException(INVALID_OBJECT);
                var token = validation.token().token();
                try {
                    facts.put(objectId, new Fact(token.tokenId(), token.parents()));
                    System.arraycopy(bytes, 0, spool, i * 1024, 1024);
                } finally { token.value().credential().secret().clear(); }
            }
            int gaps = 0;
            for (var fact : facts.values()) {
                for (var parent : fact.parents) {
                    var target = facts.get(parent);
                    if (target == null || !target.token.equals(fact.token)) gaps++;
                }
            }
            if (!Arrays.equals(vault, vault(store, id))) throw new BackupException(VAULT_CHANGED);
            var frozen = new BackupZip.Frozen(vault, spool, names, gaps, limits);
            transferred = true;
            return frozen;
        } finally {
            if (!transferred) {
                Arrays.fill(vault, (byte) 0);
                if (spool != null) Arrays.fill(spool, (byte) 0);
            }
        }
    }
}
