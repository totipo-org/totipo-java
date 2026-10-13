package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.totipo.tooling.publication.AcceptedFixture;
import static org.totipo.tooling.release.ReleaseTest.*;
import static org.totipo.tooling.release.ReleaseFinalizer.*;
import static org.totipo.tooling.release.RemoteReleaseState.*;

/** Permanent strict fake adapters; every mutation and re-observation is counted. */
public final class FinalizationTest {
    private FinalizationTest() {}
    static final String SHA = "a".repeat(40), OBJECT = "b".repeat(40);
    static Tag tag() { return new Tag(OBJECT, SHA, "tag", "totipo-java 0.2.0\n"); }
    static Tag convention() { return new Tag("c".repeat(40), "d".repeat(40), "tag", "totipo-java 0.1.0\n"); }
    static ReleaseRecord absent() { return new ReleaseRecord(Presence.ABSENT, null, null, false, false, null); }
    static ReleaseRecord release(Expected expected) { return new ReleaseRecord(Presence.PRESENT, expected.tag(), expected.tag(), false, false, expected.body()); }
    static Observed state(Tag local, Tag remote, ReleaseRecord release) { return new Observed(SHA, SHA, "", convention(), local, remote, release); }
    static Prerequisites prerequisites(Path root) throws Exception {
        var remote = RemoteVerificationTest.verify(root, RemoteVerificationTest.fake(root), 2400);
        var inputs = new ReleaseInputs("0.2.0", SHA);
        return new Prerequisites(remote, new ConsumerEvidence(ConsumerMode.CENTRAL_MODULE_METADATA, inputs, remote.hashes()),
                new ConsumerEvidence(ConsumerMode.CENTRAL_POM, inputs, remote.hashes()));
    }
    static final class Fake implements Adapter {
        Observed state;
        final List<String> calls = new ArrayList<>();
        String fail; boolean effectsBeforeFailure, noop; int observations, apiFailureAt;
        Fake(Observed state) { this.state = state; }
        @Override public Observed observe(Expected expected) throws IOException {
            calls.add("observe"); if (++observations == apiFailureAt) throw new IOException("secret observation"); return state;
        }
        void mutate(String action, Observed next) throws IOException {
            calls.add(action);
            if ((!action.equals(fail) || effectsBeforeFailure) && !noop) state = next;
            if (action.equals(fail)) throw new IOException("synthetic credential-bearing diagnostics");
        }
        @Override public void createTag(Expected expected) throws IOException {
            if (state.local() != null || state.remote() != null) throw new AssertionError("Unexpected tag creation");
            mutate("tag", state(tag(), null, absent()));
        }
        @Override public void pushTag(Expected expected, Tag local) throws IOException {
            if (state.remote() != null || !tag().equals(local)) throw new AssertionError("Unexpected push");
            mutate("push", state(tag(), tag(), absent()));
        }
        @Override public void createRelease(Expected expected) throws IOException {
            if (state.remote() == null || state.release().presence() != Presence.ABSENT) throw new AssertionError("Unexpected release creation");
            mutate("release", state(state.local(), tag(), release(expected)));
        }
    }
    static void blocked(Expected expected, Prerequisites pre, Observed state) throws Exception {
        Fake fake = new Fake(state); ok(execute(expected, pre, fake).action() == Action.BLOCKED); ok(fake.calls.equals(List.of("observe")));
    }
    static void machine(Expected expected, Prerequisites pre) throws Exception {
        Fake fake = new Fake(state(null, null, absent())); ok(execute(expected, pre, fake).action() == Action.COMPLETE);
        ok(fake.calls.equals(List.of("observe", "tag", "observe", "push", "observe", "release", "observe")));
        fake = new Fake(state(tag(), null, absent())); ok(execute(expected, pre, fake).action() == Action.COMPLETE);
        ok(fake.calls.equals(List.of("observe", "push", "observe", "release", "observe")));
        fake = new Fake(state(null, tag(), absent())); ok(execute(expected, pre, fake).action() == Action.COMPLETE);
        ok(fake.calls.equals(List.of("observe", "release", "observe")));
        fake = new Fake(state(tag(), tag(), release(expected))); ok(execute(expected, pre, fake).action() == Action.COMPLETE); ok(fake.calls.equals(List.of("observe")));
        var record = release(expected);
        for (var bad : List.of(new ReleaseRecord(Presence.PRESENT, "v0.2.1", record.name(), false, false, record.body()),
                new ReleaseRecord(Presence.PRESENT, record.tag(), "other", false, false, record.body()),
                new ReleaseRecord(Presence.PRESENT, record.tag(), record.name(), true, false, record.body()),
                new ReleaseRecord(Presence.PRESENT, record.tag(), record.name(), false, true, record.body()),
                new ReleaseRecord(Presence.PRESENT, record.tag(), record.name(), false, false, "wrong"),
                new ReleaseRecord(Presence.PRESENT, record.tag(), record.name(), false, false, record.body() + "contradiction"))) blocked(expected, pre, state(tag(), tag(), bad));
        fake = new Fake(state(tag(), tag(), new ReleaseRecord(Presence.PRESENT, record.tag(), record.name(), false, false, record.body().replace("\n", "\r\n") + " \r\n")));
        ok(execute(expected, pre, fake).action() == Action.COMPLETE); ok(fake.calls.size() == 1);
        blocked(expected, pre, state(tag(), null, record));
        for (var bad : List.of(new Tag(SHA, SHA, "commit", ""), new Tag(OBJECT, "f".repeat(40), "tag", tag().annotation()),
                new Tag(OBJECT, null, "tag", tag().annotation()), new Tag(null, SHA, "tag", tag().annotation()),
                new Tag(OBJECT, SHA, "tag", "totipo-java 0.2.0\n-----BEGIN PGP SIGNATURE-----"),
                new Tag(OBJECT, SHA, "tag", "wrong annotation"))) {
            blocked(expected, pre, state(bad, null, absent())); blocked(expected, pre, state(null, bad, absent()));
        }
        blocked(expected, pre, state(new Tag("f".repeat(40), SHA, "tag", tag().annotation()), tag(), absent()));
        blocked(expected, pre, new Observed("f".repeat(40), SHA, "", convention(), null, null, absent()));
        blocked(expected, pre, new Observed(SHA, "f".repeat(40), "", convention(), null, null, absent()));
        blocked(expected, pre, new Observed(SHA, SHA, " M VERSION", convention(), null, null, absent()));
        blocked(expected, pre, new Observed(SHA, SHA, "", new Tag(OBJECT, SHA, "commit", ""), null, null, absent()));
        blocked(expected, pre, new Observed(SHA, SHA, "", null, null, null, absent()));
        blocked(expected, pre, state(null, null, null));
        blocked(expected, new Prerequisites(pre.remote(), pre.pom(), pre.module()), state(null, null, absent()));
        blocked(expected, new Prerequisites(pre.remote(), new ConsumerEvidence(ConsumerMode.CENTRAL_MODULE_METADATA, new ReleaseInputs("0.2.0", "f".repeat(40)), pre.remote().hashes()), pre.pom()), state(null, null, absent()));
        blocked(expected, new Prerequisites(pre.remote(), new ConsumerEvidence(ConsumerMode.CENTRAL_MODULE_METADATA, expected.inputs(), java.util.Map.of()), pre.pom()), state(null, null, absent()));
        for (String operation : List.of("tag", "push", "release")) for (boolean effects : List.of(false, true)) {
            fake = new Fake(state(null, null, absent())); fake.fail = operation; fake.effectsBeforeFailure = effects;
            Fake attempt = fake; reject(() -> execute(expected, pre, attempt));
            ok(fake.calls.getLast().equals(operation)); // No automatic second mutation or success from ambiguous outcome.
            int pushBefore = java.util.Collections.frequency(fake.calls, "push");
            int releaseBefore = java.util.Collections.frequency(fake.calls, "release");
            fake.fail = null; ok(execute(expected, pre, fake).action() == Action.COMPLETE);
            ok(java.util.Collections.frequency(fake.calls, "push") == (operation.equals("push") && !effects ? 2 : Math.max(1, pushBefore)));
            ok(java.util.Collections.frequency(fake.calls, "release") == (operation.equals("release") && !effects ? 2 : Math.max(1, releaseBefore)));
            int count = fake.calls.size(); ok(execute(expected, pre, fake).action() == Action.COMPLETE); ok(fake.calls.size() == count + 1);
        }
        fake = new Fake(state(null, null, absent())); fake.noop = true; Fake noop = fake; reject(() -> execute(expected, pre, noop)); ok(fake.calls.equals(List.of("observe", "tag", "observe")));
        fake = new Fake(state(tag(), null, absent())); fake.apiFailureAt = 2; Fake api = fake; reject(() -> execute(expected, pre, api));
        ok(fake.calls.equals(List.of("observe", "push", "observe")));
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("totipo-finalizer-fixture-");
        try {
            AcceptedFixture.create(root); ReleaseTest.fixture(root); Files.copy(Path.of(args[0]).resolve("SPEC_PIN.md"), root.resolve("SPEC_PIN.md"));
            var source = new SourceFiles(root); var expected = Expected.read(source, new ReleaseInputs("0.2.0", SHA));
            ok(expected.body().endsWith("- Portable corpus: 92/92 executed, none deferred.\n"));
            machine(expected, prerequisites(root));
            reject(() -> Expected.read(source, new ReleaseInputs("0.2.1", SHA)));
            Files.delete(root.resolve("review/V0_2_0_RELEASE_NOTES.md")); reject(() -> Expected.read(source, new ReleaseInputs("0.2.0", SHA)));
            System.out.println("FinalizationTest PASS");
        } finally { try (var paths = Files.walk(root)) { for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p); } }
    }
}
