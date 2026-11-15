import org.totipo.OpenResult;
import org.totipo.Totipo;
import org.totipo.VaultId;
import org.totipo.spi.TotipoStore;
import org.totipo.spi.VaultCreate;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.totipo.ObjectCandidateValidation;
import org.totipo.RevisionId;
import org.totipo.VaultSession;
import org.totipo.VaultState;
import org.totipo.storage.nio.NioTotipo;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import org.totipo.storage.nio.NioTotipoStore;
import org.totipo.storage.nio.NioDurability;
import org.totipo.backup.*;
import java.util.concurrent.Flow;

/** Compile/runtime client: read-only store opening, no vault mutation or implementation internals. */
public final class ConsumerSmoke {
    public static void main(String[] args) throws ClassNotFoundException, IOException {
        // Loading verifies that published runtime classes and runtime-only BC resolve.
        Class.forName(NioTotipo.class.getName());
        Class.forName(VaultSession.class.getName());
        Class.forName(VaultState.class.getName());
        Class.forName("org.bouncycastle.crypto.generators.Argon2BytesGenerator");
        // Synthetic structural VAULT: this proves recognition, not authentication.
        byte[] canonical = new byte[87];
        System.arraycopy("TOTIPO-VLT".getBytes(StandardCharsets.US_ASCII), 0, canonical, 0, 10);
        canonical[10] = 1;
        VaultId id = Totipo.vaultId(canonical);
        VaultId copy = new VaultId(id.bytes());
        byte[] exported = id.bytes();
        exported[0] ^= 1;
        if (!id.equals(copy) || id.hashCode() != copy.hashCode()
                || id.hex().length() != 64 || !id.toString().equals("VaultId[" + id.hex() + "]")
                || Arrays.equals(exported, id.bytes())) throw new AssertionError("VaultId ownership/value behavior");
        canonical[86] = 1;
        if (id.equals(Totipo.vaultId(canonical))) throw new AssertionError("VAULT bytes must affect identity");
        Path root = Files.createTempDirectory("totipo-consumer-");
        try {
            try (NioTotipoStore shared = NioTotipoStore.open(root);
                 org.totipo.spi.TotipoStore coordinated = org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(root, new NioDurability())) {
                // Shared and coordinated factories must link and open without mutating the root.
                shared.scanObjects();
                coordinated.scanObjects();
                if (!(shared.scanObjects(1) instanceof org.totipo.spi.ObjectScan.Complete)
                        || !(coordinated.scanObjects(1) instanceof org.totipo.spi.ObjectScan.Complete))
                    throw new AssertionError("Bounded published provider API");
                if (!(NioTotipo.open(root, new char[0]) instanceof OpenResult.Absent))
                    throw new AssertionError("Ordinary facade should observe absent VAULT");
            }
            try (var children = Files.list(root)) {
                if (children.findAny().isPresent()) throw new AssertionError("Opening mutated root");
            }
        } finally {
            Files.delete(root);
        }
        System.out.println("Shared/coordinated published factories open read-only");
        System.out.println("Published API and runtime-only BC load successfully");
        try {
            BackupArchives.inspect(new java.io.ByteArrayInputStream(new byte[0]), BackupLimits.DEFAULT);
            throw new AssertionError("Empty bytes accepted as ZIP");
        } catch (BackupException expected) {
            if (expected.reason() != BackupException.Reason.MALFORMED) throw expected;
        }
    }
    public static VaultId vaultId(VaultSession session) {
        return session.vaultId();
    }
    public static BackupCapture capture(VaultSession session, BackupLimits limits) throws IOException {
        return session.captureBackup(limits);
    }
    public static BackupImport prepareImport(VaultSession session, java.io.InputStream input, BackupLimits limits) throws IOException {
        return session.prepareImport(input, limits);
    }
    public static ImportResult applyImport(BackupImport operation) {
        return operation.apply();
    }
    public static BackupRestore prepareRestore(java.io.InputStream input, char[] password) throws IOException {
        return BackupArchives.prepareRestore(input, BackupLimits.DEFAULT, password);
    }
    public static RestoreResult restoreNewChild(BackupRestore operation, Path parent, String child) throws IOException {
        return operation.install(org.totipo.storage.nio.NioRestore.reserve(parent, child, operation.summary().archiveSha256()));
    }
    public static RestoreResult recoverRestore(BackupRestore operation, Path root) throws IOException {
        return operation.install(org.totipo.storage.nio.NioRestore.recover(root, operation.summary().archiveSha256()));
    }
    public static BackupSummary inspect(java.io.InputStream input, BackupLimits limits) throws IOException {
        return BackupArchives.inspect(input, limits);
    }
    public static VaultCreate createVault(TotipoStore store, byte[] bytes) {
        return store.createVault(bytes);
    }
    public static OpenResult open(Path path, char[] password) {
        return NioTotipo.open(path, password);
    }
    public static ObjectCandidateValidation validate(VaultSession session, RevisionId id, byte[] bytes) {
        return session.validateObject(id, bytes);
    }
    public static VaultState state(VaultSession session) {
        return session.state();
    }
    public static Flow.Publisher<VaultState> states(VaultSession session) {
        return session.states();
    }
}
