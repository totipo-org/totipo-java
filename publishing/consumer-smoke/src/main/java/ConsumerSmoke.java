import org.totipo.OpenResult;
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
import java.util.concurrent.Flow;

/** Compile/runtime client: read-only store opening, no vault mutation or implementation internals. */
public final class ConsumerSmoke {
    public static void main(String[] args) throws ClassNotFoundException, IOException {
        // Loading verifies that published runtime classes and runtime-only BC resolve.
        Class.forName(NioTotipo.class.getName());
        Class.forName(VaultSession.class.getName());
        Class.forName(VaultState.class.getName());
        Class.forName("org.bouncycastle.crypto.generators.Argon2BytesGenerator");
        Path root = Files.createTempDirectory("totipo-consumer-");
        try {
            try (NioTotipoStore shared = NioTotipoStore.open(root);
                 NioTotipoStore privateDefault = NioTotipoStore.openPrivate(root);
                 NioTotipoStore privateExplicit = NioTotipoStore.openPrivate(root, new NioDurability())) {
                // Both old and new factories must link and open without mutating the root.
                shared.scanObjects();
                privateDefault.scanObjects();
                privateExplicit.scanObjects();
            }
            try (var children = Files.list(root)) {
                if (children.findAny().isPresent()) throw new AssertionError("Opening mutated root");
            }
        } finally {
            Files.delete(root);
        }
        System.out.println("Shared/private published factories open read-only");
        System.out.println("Published API and runtime-only BC load successfully");
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
