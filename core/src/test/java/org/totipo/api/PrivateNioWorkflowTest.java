package org.totipo.api;

import org.totipo.*;
import org.totipo.storage.nio.NioTotipoStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** End-to-end use of the additive factory through the unchanged application facade. */
class PrivateNioWorkflowTest {
    @TempDir Path root;

    @Test void createPublishRewrapAndReopenPrivateReplica() throws Exception {
        TokenId id;
        VaultFingerprint fingerprint;
        try (var session = assertInstanceOf(CreateVaultResult.Created.class,
                Totipo.create(NioTotipoStore.openPrivate(root), "old".toCharArray())).session()) {
            fingerprint = session.fingerprint();
            PublicApiTest.finished(session);
            try (var secret = NewSecret.copyOf(new byte[]{1, 2, 3}); var create = session.state().createToken()) {
                id = assertInstanceOf(SaveResult.Saved.class, create.issuer("private replica").secret(secret).save()).tokenId();
            }
            assertEquals(PasswordChangeResult.CHANGED, session.changePassword("old".toCharArray(), "new".toCharArray()));
        }
        try (var session = assertInstanceOf(OpenResult.Opened.class,
                Totipo.open(NioTotipoStore.openPrivate(root), "new".toCharArray())).session()) {
            assertEquals(fingerprint, session.fingerprint());
            assertEquals("private replica", PublicApiTest.finished(session).token(id).orElseThrow().alternatives().get(0).descriptor().issuer());
        }
        assertInstanceOf(CreateVaultResult.AlreadyExists.class,
                Totipo.create(NioTotipoStore.openPrivate(root), "other".toCharArray()));
    }
}
