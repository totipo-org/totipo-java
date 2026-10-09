package org.totipo.api;

import org.totipo.*;
import org.totipo.storage.nio.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableNioWorkflowTest {
    @TempDir Path root;
    @Test void coordinatedCreateSaveAndReopenPreserveCanonicalVault() throws Exception {
        byte[] canonical;
        VaultId id;
        try (var session = assertInstanceOf(CreateVaultResult.Created.class,
                Totipo.create(NioStoreComposition.coordinatedDelegate(root, new NioDurability()), "p".toCharArray())).session()) {
            canonical = Files.readAllBytes(root.resolve("vault")); id = session.vaultId();
            try (var secret = NewSecret.copyOf(new byte[]{1,2,3}); var create = session.state().createToken()) {
                assertInstanceOf(SaveResult.Saved.class, create.secret(secret).save());
            }
            assertArrayEquals(canonical, Files.readAllBytes(root.resolve("vault")));
        }
        try (var session = assertInstanceOf(OpenResult.Opened.class,
                Totipo.open(NioStoreComposition.coordinatedDelegate(root, new NioDurability()), "p".toCharArray())).session()) {
            assertEquals(id, session.vaultId()); assertEquals(1, PublicApiTest.finished(session).tokens().size());
        }
        assertInstanceOf(CreateVaultResult.AlreadyExists.class, NioTotipo.create(root, "other".toCharArray()));
        assertArrayEquals(canonical, Files.readAllBytes(root.resolve("vault")));
    }
    @Test void ordinaryFacadeExplainsObservedObjectDataAndPreservesIt() throws Exception {
        Path object = Files.write(Files.createDirectory(root.resolve("objects-v1")).resolve("ab".repeat(32)), new byte[]{7});
        var failed = assertInstanceOf(CreateVaultResult.Failed.class, NioTotipo.create(root, "p".toCharArray()));
        assertEquals(CreateVaultResult.FailureReason.OBJECT_DATA_OBSERVED, failed.reason());
        assertFalse(Files.exists(root.resolve("vault"))); assertArrayEquals(new byte[]{7}, Files.readAllBytes(object));
        try (var files = Files.list(root)) { assertEquals(1, files.count()); }
    }
}
