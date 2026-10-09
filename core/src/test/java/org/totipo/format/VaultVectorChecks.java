package org.totipo.format;

import org.totipo.*;
import org.totipo.spi.*;
import org.totipo.conformance.VectorCaseLoader;
import org.totipo.storage.nio.NioTotipoStore;
import java.nio.file.Files;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Execute each exact r19 workflow, retaining ambiguity rather than declaring absence. */
final class VaultVectorChecks {
    private VaultVectorChecks() { }
    static void check(VectorCaseLoader.Case vector) throws Exception {
        assertEquals("PASS", vector.expected());
        assertEquals("workflow", vector.data().field("operation").string());
        var f = vector.data().field("workflow");
        assertTrue(f.field("complete").bool());
        if (f.field("action").string().equals("publish")) {
            var directory = Files.createTempDirectory("totipo-vault-invariant-");
            try {
                byte[] canonical = f.field("vault_hex").hex();
                Files.write(directory.resolve("vault"), canonical);
                byte[] intended = f.field("intended_hex").hex();
                var crypto = VectorCaseLoader.cryptoCases().stream().filter(v -> v.data().has("crypto")
                        && Arrays.equals(v.data().field("crypto").field("object_hex").hex(), intended)).findFirst().orElseThrow();
                var name = new ObjectName(crypto.data().field("crypto").field("object_id").string());
                if (f.field("kind").string().equals("regular")) {
                    Files.createDirectory(directory.resolve("objects-v1"));
                    Files.write(directory.resolve("objects-v1").resolve(name.value()), f.field("existing_hex").hex());
                }
                try (var store = NioTotipoStore.open(directory)) {
                    assertEquals(f.field("result").string(), StorageVectorChecks.verdict(store.publishObject(name, intended)));
                }
                assertArrayEquals(canonical, Files.readAllBytes(directory.resolve("vault")));
            } finally { StorageVectorChecks.deleteTree(directory); }
            return;
        }
        assertEquals("create", f.field("action").string());
        byte[] intended = f.field("intended_hex").hex();
        assertEquals(87, intended.length);
        var ascii = VectorCaseLoader.bootstrapCases().stream().filter(v -> v.id().equals("v1.bootstrap.ascii.001")).findFirst().orElseThrow();
        byte[] password = ascii.data().field("bootstrap").field("password_hex").hex();
        char[] chars = new String(password, java.nio.charset.StandardCharsets.UTF_8).toCharArray();
        byte[] root = ascii.data().field("root_hex").hex();
        byte[] salt = ascii.data().field("bootstrap").field("salt_hex").hex();
        byte[] nonce = ascii.data().field("bootstrap").field("nonce_hex").hex();
        for (boolean residue : f.field("durable").bool() ? List.of(true) : List.of(false, true)) {
            var store = new TestStore() {
                byte[] vault = f.field("kind").string().equals("absent") ? null : f.field("existing_hex").hex();
                int reads, creates, draws;
                @Override public BoundedRead readVault(int expected) {
                    reads++;
                    if (f.field("kind").string().equals("symlink")) return new BoundedRead.WrongKind(EntryKind.SYMLINK);
                    if (creates > 0 && !f.field("readable").bool()) return new BoundedRead.Unavailable(StoreFailure.UNAVAILABLE);
                    return read(vault, expected);
                }
                @Override public ObjectScan scanObjects() {
                    return new ObjectScan.Incomplete(f.field("orphan_objects").bool() ? List.of(new ObjectEntry(
                            new ObjectName("ab".repeat(32)), EntryKind.REGULAR, OptionalLong.empty())) : List.of(), StoreFailure.UNAVAILABLE);
                }
                @Override public VaultCreate createVault(byte[] bytes) {
                    creates++; assertArrayEquals(intended, bytes);
                    if (residue) vault = bytes.clone();
                    return f.field("durable").bool() ? new VaultCreate.Created() : new VaultCreate.Uncertain(StoreFailure.UNAVAILABLE);
                }
                void entropy(byte[] bytes) {
                    byte[] source = switch (draws++) { case 0 -> root; case 1 -> salt; case 2 -> nonce; default -> throw new AssertionError(); };
                    System.arraycopy(source, 0, bytes, 0, bytes.length);
                }
            };
            var lifecycle = new VaultLifecycle(new VaultBootstrapWriter(), new VaultUnlocker(), store::entropy);
            byte[] before = store.vault == null ? null : store.vault.clone();
            var result = lifecycle.create(store, chars);
            assertEquals(f.field("result").string(), result instanceof CreateVaultResult.Created ? "CREATED" : "FAILED", vector.context());
            boolean blocked = !f.field("kind").string().equals("absent") || f.field("orphan_objects").bool();
            assertEquals(blocked ? 0 : 3, store.draws);
            assertEquals(blocked ? 0 : 1, store.creates);
            if (blocked) assertArrayEquals(before, store.vault);
            if (result instanceof CreateVaultResult.Created created) {
                assertEquals(Totipo.vaultId(intended), created.session().vaultId());
                created.session().close();
                assertEquals(2, store.reads);
            }
        }
    }
}
