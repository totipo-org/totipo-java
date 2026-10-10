package org.totipo;

import org.totipo.format.VaultLifecycle;
import org.totipo.spi.TotipoStore;

/** Store-facing entry point for provider integrations. Normal NIO applications use NioTotipo.
 * This library entry point does not claim v1 application conformance.
 * Interactive callers implement creation warnings/confirmation and truthful presentation.
 * Creation vetoes observed plausible object names before generating secrets.
 * These names are contextual evidence, without authentication or exhaustive enumeration.
 * Both calls take ownership of the store, including on failure or invalid password input.
 * A successful session owns it until close; callers must no longer use or close it. */
public final class Totipo {
    private Totipo() {}
    /** Structural VAULT identity without password authentication or Argon2. */
    public static VaultId vaultId(byte[] canonicalVault) { return VaultLifecycle.vaultId(canonicalVault); }
    /** Opens read-only; may block for KDF, configured-store I/O and coordination.
     * @see VaultSession#close()
     */
    public static OpenResult open(TotipoStore store, char[] password) { return VaultLifecycle.openSession(store, password); }
    /** Creates through the configured store; may block for KDF, I/O and coordination.
     * @see VaultSession#close()
     */
    public static CreateVaultResult create(TotipoStore store, char[] password) { return VaultLifecycle.createSession(store, password); }
}
