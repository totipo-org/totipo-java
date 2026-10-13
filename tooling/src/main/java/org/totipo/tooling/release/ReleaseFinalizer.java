package org.totipo.tooling.release;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import static org.totipo.tooling.release.SourceFiles.require;
import static org.totipo.tooling.release.RemoteReleaseState.*;

/** Reviewed identity, pure decisions, mutations and post-observations are separate boundaries.
 * No operational CLI. J4 must supply actual Central-only consumer and fresh source evidence.
 */
public final class ReleaseFinalizer {
    private ReleaseFinalizer() {}
    public static final class Expected {
        private final ReleaseInputs inputs;
        private final String body;
        private Expected(ReleaseInputs inputs, String body) { this.inputs = inputs; this.body = body; }
        public ReleaseInputs inputs() { return inputs; }
        public String body() { return body; }
        public String tag() { return "v" + inputs.version(); }
        public static Expected read(SourceFiles source, ReleaseInputs inputs) throws IOException {
            source.spec();
            require(source.text("VERSION").equals(inputs.version() + "\n"), "VERSION differs from reviewed identity");
            String notes = source.notes(inputs.version(), false, false);
            return new Expected(inputs, rstrip(notes) + "\n\n### Release provenance\n\n- Java source commit: `" + inputs.commit()
                    + "`\n- Totipo v1/r19 spec commit: `" + SourceFiles.SPEC_COMMIT
                    + "`\n- Portable corpus: 92/92 executed, none deferred.\n");
        }
    }
    // Python str.rstrip whitespace, including NBSP and U+001C..U+001F.
    static String rstrip(String value) {
        int end = value.length();
        while (end > 0) {
            int c = value.codePointBefore(end);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c) && !(c >= 0x1c && c <= 0x1f) && c != 0x85) break;
            end -= Character.charCount(c);
        }
        return value.substring(0, end);
    }
    public enum ConsumerMode { CENTRAL_MODULE_METADATA, CENTRAL_POM }
    /** Receipt from a successful independent consumer, not permission to execute it here. */
    public record ConsumerEvidence(ConsumerMode mode, ReleaseInputs inputs, Map<String, String> hashes) {
        public ConsumerEvidence { Objects.requireNonNull(mode); Objects.requireNonNull(inputs); hashes = Map.copyOf(hashes); }
    }
    public record Prerequisites(RemoteArtifactVerifier.Verified remote, ConsumerEvidence module, ConsumerEvidence pom) {
        public Prerequisites { Objects.requireNonNull(remote); Objects.requireNonNull(module); Objects.requireNonNull(pom); }
        void validate(Expected expected) throws IOException {
            require(remote.version().equals(expected.inputs().version()) && remote.hashes().size() == 10, "Wrong remote verification identity");
            require(module.mode() == ConsumerMode.CENTRAL_MODULE_METADATA && pom.mode() == ConsumerMode.CENTRAL_POM, "Both Central-only consumer modes required");
            for (var evidence : java.util.List.of(module, pom))
                require(evidence.inputs().equals(expected.inputs()) && evidence.hashes().equals(remote.hashes()), "Consumer identity/inventory differs");
        }
    }
    /** Null means absent; a present tag requires complete inspected object and peeled identity. */
    public record Tag(String object, String commit, String type, String annotation) {}
    public record Observed(String head, String remoteMain, String status, Tag convention,
                           Tag local, Tag remote, ReleaseRecord release) {}
    public enum Action { CREATE_ANNOTATED_TAG, PUSH_EXISTING_TAG, CREATE_GITHUB_RELEASE, COMPLETE, BLOCKED }
    public record Decision(Action action, String reason) {}
    public interface Adapter {
        Observed observe(Expected expected) throws IOException;
        void createTag(Expected expected) throws IOException;
        void pushTag(Expected expected, Tag local) throws IOException;
        void createRelease(Expected expected) throws IOException;
    }
    static boolean validTag(Tag tag, String commit, String annotation) {
        return tag != null && tag.object() != null && tag.object().matches("[0-9a-f]{40}")
                && tag.commit() != null && tag.commit().matches("[0-9a-f]{40}")
                && (commit == null || tag.commit().equals(commit)) && "tag".equals(tag.type())
                && tag.annotation() != null && tag.annotation().equals(annotation);
    }
    /** No I/O; contradictory or incomplete observations always block. */
    public static Decision plan(Expected expected, Prerequisites prerequisites, Observed state) {
        try {
            prerequisites.validate(expected);
            require(state != null && expected.inputs().commit().equals(state.head())
                    && expected.inputs().commit().equals(state.remoteMain()) && "".equals(state.status()), "Source identity changed or incomplete");
            require(validTag(state.convention(), null, "totipo-java 0.1.0\n"), "Established unsigned annotated-tag convention changed");
            String annotation = "totipo-java " + expected.inputs().version() + "\n";
            if (state.local() != null) require(validTag(state.local(), expected.inputs().commit(), annotation), "Invalid local annotated tag");
            if (state.remote() != null) require(validTag(state.remote(), expected.inputs().commit(), annotation), "Invalid remote annotated tag");
            if (state.local() != null && state.remote() != null)
                require(state.local().object().equals(state.remote().object()), "Conflicting local/remote tag objects");
            var release = state.release();
            require(release != null && release.presence() != null, "Release observation incomplete");
            if (release.presence() == Presence.PRESENT) {
                require(state.remote() != null && expected.tag().equals(release.tag()) && expected.tag().equals(release.name())
                        && !release.draft() && !release.prerelease() && release.body() != null
                        && rstrip(release.body().replace("\r\n", "\n")).equals(rstrip(expected.body())), "Inconsistent existing release");
                return new Decision(Action.COMPLETE, "Exact existing source tag and release");
            }
            require(release.tag() == null && release.name() == null && release.body() == null && !release.draft() && !release.prerelease(), "Contradictory absent release");
            if (state.remote() != null) return new Decision(Action.CREATE_GITHUB_RELEASE, "Reviewed remote tag exists");
            if (state.local() != null) return new Decision(Action.PUSH_EXISTING_TAG, "Reviewed local tag exists");
            return new Decision(Action.CREATE_ANNOTATED_TAG, "Reviewed tag absent");
        } catch (IOException | RuntimeException invalid) { return new Decision(Action.BLOCKED, "Incomplete or conflicting finalization evidence"); }
    }
    /** Each successful mutation must be independently observed before advancing. Uncertain outcomes stop.
     * A new invocation observes again; it never blindly repeats a mutation from stale state.
     */
    public static Decision execute(Expected expected, Prerequisites prerequisites, Adapter adapter) throws IOException {
        Observed state = observe(adapter, expected);
        for (int step = 0; step < 4; step++) {
            Decision decision = plan(expected, prerequisites, state);
            if (decision.action() == Action.BLOCKED || decision.action() == Action.COMPLETE) return decision;
            try {
                switch (decision.action()) {
                    case CREATE_ANNOTATED_TAG -> adapter.createTag(expected);
                    case PUSH_EXISTING_TAG -> adapter.pushTag(expected, state.local());
                    case CREATE_GITHUB_RELEASE -> adapter.createRelease(expected);
                    default -> throw new AssertionError("Unexpected finalization action");
                }
            } catch (IOException | RuntimeException uncertain) { throw new IOException("Finalization mutation uncertain; reobserve before retry"); }
            Observed next = observe(adapter, expected);
            Decision after = plan(expected, prerequisites, next);
            require(after.action() == Action.BLOCKED || after.action() == Action.COMPLETE
                    || after.action().ordinal() > decision.action().ordinal(), "Mutation did not establish required postcondition; stop and reobserve");
            state = next;
        }
        throw new IOException("Finalization action budget exhausted");
    }
    private static Observed observe(Adapter adapter, Expected expected) throws IOException {
        try { return adapter.observe(expected); }
        catch (IOException | RuntimeException unavailable) { throw new IOException("Finalization observation unavailable"); }
    }
}
