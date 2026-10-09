package org.totipo.api;

import org.totipo.*;
import org.totipo.conformance.VectorCaseLoader;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VaultIdTest {
    @Test void exactKnownAnswersAreAvailableBeforeUnlockAndMutationChangesIdentity() throws Exception {
        for (var v : VectorCaseLoader.bootstrapCases()) {
            var b = v.data().field("bootstrap");
            byte[] record = b.field("record_hex").hex();
            var id = Totipo.vaultId(record);
            assertEquals(b.field("vault_id_hex").string(), id.hex());
            assertEquals(id, new VaultId(id.bytes())); assertEquals(id.hashCode(), new VaultId(id.bytes()).hashCode());
            byte[] owned = id.bytes(); owned[0] ^= 1; assertNotEquals(id, new VaultId(owned));
            record[86] ^= 1;
            assertNotEquals(id, Totipo.vaultId(record)); // Structural identity makes no authentication claim.
            if (b.has("changed_vault_id_hex")) assertEquals(b.field("changed_vault_id_hex").string(), Totipo.vaultId(record).hex());
            assertEquals("VaultId[" + id.hex() + "]", id.toString());
        }
    }
    @Test void rejectsNonCanonicalRepresentationsAndOwnsValueBytes() throws Exception {
        byte[] record = VectorCaseLoader.bootstrapCases().get(0).data().field("bootstrap").field("record_hex").hex();
        for (int size : new int[]{0, 86, 88, 100000})
            assertThrows(IllegalArgumentException.class, () -> Totipo.vaultId(Arrays.copyOf(record, size)));
        for (int offset : new int[]{0, 9, 10}) {
            byte[] changed = record.clone(); changed[offset] ^= 1;
            assertThrows(IllegalArgumentException.class, () -> Totipo.vaultId(changed));
        }
        byte[] bytes = new byte[32]; var id = new VaultId(bytes); bytes[0] = 1;
        assertArrayEquals(new byte[32], id.bytes());
        assertThrows(IllegalArgumentException.class, () -> new VaultId(new byte[31]));
        assertThrows(NullPointerException.class, () -> Totipo.vaultId(null));
    }
    @Test void retiredLifecycleOperationsAreAbsent() {
        assertTrue(Arrays.stream(VaultSession.class.getMethods()).noneMatch(m -> m.getName().equals("changePassword") || m.getName().equals("fingerprint")));
        for (String type : List.of("org.totipo.PasswordChangeResult", "org.totipo.VaultFingerprint", "org.totipo.spi.VaultReplace",
                "org.totipo.spi.PreparedVault", "org.totipo.format.ApplicationVaults", "org.totipo.format.StoreAdapter"))
            assertThrows(ClassNotFoundException.class, () -> Class.forName(type));
    }
}
