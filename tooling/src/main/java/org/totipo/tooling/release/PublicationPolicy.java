package org.totipo.tooling.release;

import java.io.IOException;
import java.util.Objects;
import static org.totipo.tooling.release.RemoteReleaseState.*;

/** Pure interpretation of complete observations. A candidate is never live upload authority. */
public final class PublicationPolicy {
    private PublicationPolicy() {}
    public enum Disposition { CANDIDATE_PUBLISH, VERIFY_EXISTING, BLOCKED }
    public enum Boundary { INITIAL, AFTER_APPROVAL, IMMEDIATELY_BEFORE_UPLOAD }
    public enum Reason { ABSENT_WITHOUT_PRIOR_UPLOAD, SAME_RUN_UPLOAD_VISIBLE,
                         EXISTING_WITHOUT_SAME_RUN_UPLOAD, PROPAGATION_UNCERTAIN, OBSERVATION_UNAVAILABLE }
    public record Decision(Disposition disposition, Reason reason) {
        public Decision {
            Objects.requireNonNull(disposition, "Missing disposition");
            Objects.requireNonNull(reason, "Missing reason");
            Disposition expected = switch (reason) {
                case ABSENT_WITHOUT_PRIOR_UPLOAD -> Disposition.CANDIDATE_PUBLISH;
                case SAME_RUN_UPLOAD_VISIBLE -> Disposition.VERIFY_EXISTING;
                default -> Disposition.BLOCKED;
            };
            if (disposition != expected) throw new IllegalArgumentException("Inconsistent policy decision");
        }
    }
    @FunctionalInterface public interface Observation { RemoteReleaseState read() throws IOException; }

    public static Decision interpret(RemoteReleaseState state) {
        Objects.requireNonNull(state, "Missing observations");
        boolean present = state.core() == Presence.PRESENT || state.storage() == Presence.PRESENT;
        boolean prior = state.priorAttempt() == PriorAttempt.POSSIBLE_UPLOAD;
        if (present) return prior
                ? new Decision(Disposition.VERIFY_EXISTING, Reason.SAME_RUN_UPLOAD_VISIBLE)
                : new Decision(Disposition.BLOCKED, Reason.EXISTING_WITHOUT_SAME_RUN_UPLOAD);
        return prior ? new Decision(Disposition.BLOCKED, Reason.PROPAGATION_UNCERTAIN)
                : new Decision(Disposition.CANDIDATE_PUBLISH, Reason.ABSENT_WITHOUT_PRIOR_UPLOAD);
    }
    /** Calls the observer anew at each boundary; accepts neither a previous decision nor an authorization token.
     * Future J4 must supply fresh live GitHub/Central observations and reviewed identity immediately before
     * upload. This API cannot prove freshness of a supplied observer and offers no atomic remote transaction.
     */
    public static Decision assess(Boundary boundary, Observation observation) {
        Objects.requireNonNull(boundary, "Missing boundary");
        Objects.requireNonNull(observation, "Missing observer");
        try {
            RemoteReleaseState state = observation.read();
            if (state != null) return interpret(state);
        } catch (IOException | RuntimeException unavailable) { /* No response or credential material in diagnostics. */ }
        return new Decision(Disposition.BLOCKED, Reason.OBSERVATION_UNAVAILABLE);
    }
}
