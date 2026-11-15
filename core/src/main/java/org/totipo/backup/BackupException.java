package org.totipo.backup;

import java.io.IOException;

/** A failed operation returns no successful capture/inspection. Stream I/O errors remain IOExceptions. */
public final class BackupException extends IOException {
    private static final long serialVersionUID = 1L;
    /** Coarse non-secret failure classification. */
    public enum Reason { CAPACITY, CANCELLED, SOURCE_UNAVAILABLE, INVALID_OBJECT, VAULT_CHANGED, MALFORMED, UNSUPPORTED, TARGET_CONFLICT }
    private final Reason reason;
    public BackupException(Reason reason) { super(reason.name()); this.reason = java.util.Objects.requireNonNull(reason); }
    public Reason reason() { return reason; }
}
