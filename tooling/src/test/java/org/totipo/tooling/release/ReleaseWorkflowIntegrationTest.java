package org.totipo.tooling.release;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.totipo.tooling.publication.AcceptedFixture;
import static org.totipo.tooling.release.ReleaseWorkflowCoordinator.*;
import static org.totipo.tooling.release.TestResultsTest.ok;

/** Four disposable job roots. Strict, closed request/command queues; never live fallback. */
public final class ReleaseWorkflowIntegrationTest {
    private ReleaseWorkflowIntegrationTest() {}
    static final String SHA = "a".repeat(40), REPOSITORY = "totipo-org/totipo-java";
    static final ReleaseInputs INPUTS = new ReleaseInputs("0.2.0", SHA);
    static final List<String> CASES = List.of("publish", "verify-existing", "blocked-approval", "blocked-upload",
        "failed-upload-delayed", "partial-central", "changed-artifact", "changed-index", "changed-sidecar",
        "wrong-signing-input", "failed-signing", "incomplete-history", "later-page-failure", "http-timeout", "rate-limit",
        "remote-hash", "remote-signature-missing", "remote-signature-invalid", "module-fails", "pom-fails",
        "remote-tag", "release-fails", "uncertain-push", "complete", "wrong-body", "wrong-source", "invalid-output", "interrupted");
    record Request(String url, int status, String body) {}
    static final class Context implements FreshObservers, Execution {
        final Job job;
        final String scenario;
        final ArrayDeque<String> expected = new ArrayDeque<>();
        final List<String> calls = new ArrayList<>();
        int uploads, signs;
        final List<java.util.Collection<?>> pending = new ArrayList<>();
        Context(Path root, String scenario, int attempt) { this(root, scenario, attempt, INPUTS); }
        Context(Path root, String scenario, int attempt, ReleaseInputs inputs) { job = new Job(root, inputs, REPOSITORY, "123", attempt); this.scenario = scenario; }
        void expect(String... actions) { expected.addAll(List.of(actions)); }
        void call(String action) {
            calls.add(action);
            if (expected.isEmpty() || !expected.removeFirst().equals(action)) throw new AssertionError("Unexpected " + action + "; remaining=" + expected);
        }
        void blocked() { ok(expected.isEmpty()); }
        void complete() { blocked(); for (var queue : pending) ok(queue.isEmpty()); }
        @Override public Observers acquire(Boundary boundary) {
            call("observe:" + boundary);
            boolean drift = scenario.equals("wrong-source") || scenario.equals("blocked-approval") && boundary == Boundary.AFTER_APPROVAL;
            var queries = new ArrayDeque<>(List.of(GitQueries.Query.HEAD, GitQueries.Query.STATUS, GitQueries.Query.REMOTE_MAIN,
                GitQueries.Query.REMOTE_TAG, GitQueries.Query.LOCAL_TAG));
            var responses = new ArrayDeque<Request>();
            responses.add(new Request("https://api.github.com/repos/" + REPOSITORY + "/releases/tags/v0.2.0", 404, ""));
            if (boundary != Boundary.BEFORE_FINALIZATION) {
                boolean visible = List.of("verify-existing", "partial-central").contains(scenario)
                    || scenario.equals("blocked-upload") && boundary == Boundary.BEFORE_UPLOAD;
                boolean prior = visible || scenario.equals("failed-upload-delayed");
                for (String module : List.of("core", "storage-nio")) responses.add(new Request(RemoteReleaseState.CENTRAL + "org/totipo/totipo-" + module
                    + "/0.2.0/totipo-" + module + "-0.2.0.pom", visible && (module.equals("core") || !scenario.equals("partial-central")) ? 200 : 404, ""));
                if (job.attempt() == 2) {
                    String jobs = ReleaseTest.publish(prior ? "\"now\"" : "null", prior ? "\"failure\"" : "\"skipped\"");
                    String body = ReleaseTest.page(jobs);
                    if (scenario.equals("incomplete-history")) body = body.substring(0, body.length() - 1) + ",\"total_count\":2}";
                    if (scenario.equals("later-page-failure")) {
                        var filler = new ArrayList<String>(); filler.add(jobs);
                        for (int i = 1; i < 100; i++) filler.add(ReleaseTest.job("other" + i, SHA, ""));
                        body = ReleaseTest.page(String.join(",", filler));
                    }
                    responses.add(new Request(ReleaseTest.history(1, 1), 200, body));
                    if (scenario.equals("later-page-failure")) responses.add(new Request(ReleaseTest.history(1, 2), 503, ""));
                }
            }
            pending.add(queries); pending.add(responses);
            GitQueries git = (query, version) -> {
                if (queries.isEmpty() || queries.removeFirst() != query || !version.equals("0.2.0")) throw new AssertionError("Unexpected Git query " + query);
                return switch (query) {
                    case HEAD, REMOTE_MAIN -> drift ? "f".repeat(40) : SHA;
                    case STATUS, REMOTE_TAG, LOCAL_TAG -> "";
                    default -> throw new AssertionError("Unknown Git query");
                };
            };
            RemoteReleaseState.Http http = uri -> {
                if (responses.isEmpty()) throw new AssertionError("Unknown HTTP " + uri);
                Request next = responses.removeFirst();
                if (!next.url().equals(uri.toString())) throw new AssertionError("Out-of-order HTTP " + uri);
                if (boundary == Boundary.AFTER_APPROVAL && next.url().contains("maven2") && scenario.equals("http-timeout")) throw new IOException("fixture timeout");
                if (boundary == Boundary.AFTER_APPROVAL && next.url().contains("maven2") && scenario.equals("rate-limit")) return new RemoteReleaseState.Response(429, new byte[0]);
                return new RemoteReleaseState.Response(next.status(), ReleaseTest.bytes(next.body()));
            };
            return new Observers(git, http);
        }
        @Override public void execute(Step step) throws IOException {
            call(step.name());
            try {
                switch (step) {
                    case NORMAL_QUALIFICATION, OFFLINE_QUALIFICATION -> TestResultsTest.fixture(job.root(), false);
                    case FOCUSED_INTEGRITY -> Files.delete(TestResultsTest.core(job.root(), "corpus"));
                    case SIGN -> {
                        signs++;
                        if (scenario.equals("failed-signing")) throw new IOException("fixture failed signing");
                        AuthorizationTest.signatures(job.root());
                        if (scenario.equals("wrong-signing-input")) {
                            var a = AcceptedArtifacts.fresh(job.manifest(), job.staging(), "0.2.0").artifacts().getFirst();
                            Files.writeString(AuthorizationTest.input(job.root(), a), "wrong bytes");
                        }
                        if (scenario.equals("interrupted")) throw new IOException("interrupted phase");
                    }
                    case GRADLE_UPLOAD -> uploads++;
                    case REBUILD -> {
                        if (scenario.equals("changed-artifact")) {
                            var a = AcceptedArtifacts.fresh(job.manifest(), job.staging(), "0.2.0").artifacts().getFirst();
                            Files.writeString(job.staging().resolve(a.path()), "changed");
                        }
                        if (scenario.equals("changed-index")) Files.writeString(job.staging().resolve("org/totipo/totipo-core/maven-metadata.xml"), "changed");
                        if (scenario.equals("changed-sidecar")) Files.writeString(job.staging().resolve("org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.jar.sha256"), "0".repeat(64));
                    }
                    default -> { }
                }
            } catch (IOException e) { throw e; }
            catch (Exception e) { throw new IOException("Fixture execution failed", e); }
        }
    }
    static final class Consumer implements CentralConsumers.Executor {
        final Context context;
        final List<Path> homes = new ArrayList<>();
        int calls;
        Consumer(Context context) { this.context = context; }
        @Override public Path newHome() throws IOException {
            Path home = Files.createTempDirectory("totipo-central-home-"); homes.add(home); return home;
        }
        @Override public CentralConsumers.Exit execute(CentralConsumers.Command command) {
            boolean pom = calls++ == 1;
            context.call(pom ? "CENTRAL_POM" : "CENTRAL_MODULE");
            var expected = new ArrayList<>(List.of("./gradlew", "-p", "publishing/consumer-smoke", "--no-daemon", "--no-build-cache", "-PremoteCentral"));
            if (pom) expected.add("-PpomOnly"); expected.addAll(List.of("clean", "check"));
            ok(command.root().equals(context.job.root()) && command.arguments().equals(expected));
            ok(command.environment().size() == 2 && command.environment().get("GRADLE_USER_HOME").equals(homes.getLast().toString()));
            ok(homes.size() == 1 || !homes.getFirst().equals(homes.getLast()));
            return new CentralConsumers.Exit(context.scenario.equals(pom ? "pom-fails" : "module-fails") ? 1 : 0);
        }
        @Override public void removeHome(Path home) throws IOException { PreparationApplicationTest.remove(home); }
    }
    static void scenario(Path base, String name) throws Exception {
        Path directory = Files.createTempDirectory("totipo-workflow-" + name + "-");
        try {
            for (String job : List.of("preflight", "approved", "verify", "finalize")) ReleaseTest.copy(base, directory.resolve(job));
            boolean recovery = List.of("verify-existing", "partial-central", "failed-upload-delayed", "incomplete-history", "later-page-failure").contains(name);
            Context preflight = new Context(directory.resolve("preflight"), name.equals("wrong-source") ? name : "publish", 1);
            preflight.expect("observe:PREFLIGHT");
            if (name.equals("wrong-source")) {
                ReleaseTest.reject(() -> preflight(preflight.job, preflight, preflight)); preflight.blocked(); ok(preflight.uploads == 0); return;
            }
            preflight.expect("WRAPPER", "GUARDRAILS", "NORMAL_QUALIFICATION", "OFFLINE_QUALIFICATION", "LOCAL_POM_CONSUMER", "FOCUSED_INTEGRITY");
            var pre = preflight(preflight.job, preflight, preflight); preflight.complete();
            ok(pre.normal().total() == 7 && pre.offline().total() == 7 && pre.focused().total() == 4);
            Path output = directory.resolve("runner-output"); Files.createFile(output); GitHubOutputs.inputs(output, preflight.job.inputs()); GitHubOutputs.inventory(output, pre.inventory());
            var lines = Files.readAllLines(output);
            ok(lines.size() == 3 && lines.get(0).startsWith("version=") && lines.get(1).startsWith("commit=") && lines.get(2).startsWith("inventory="));
            var transported = new ReleaseInputs(lines.get(0).substring("version=".length()), lines.get(1).substring("commit=".length()));
            byte[] handoff = ReleaseTest.bytes(lines.get(2).substring("inventory=".length()));
            if (name.equals("invalid-output")) {
                String before = Files.readString(output);
                ReleaseTest.reject(() -> GitHubOutputs.inputs(output, new ReleaseInputs("0.2.0\nmode=publish", SHA)));
                ok(before.equals(Files.readString(output))); ok(preflight.uploads == 0); return;
            }
            Context approved = new Context(directory.resolve("approved"), name, recovery ? 2 : 1, transported);
            approved.expect("observe:AFTER_APPROVAL");
            boolean earlyBlock = List.of("blocked-approval", "failed-upload-delayed", "incomplete-history", "later-page-failure", "http-timeout", "rate-limit").contains(name);
            if (!earlyBlock) approved.expect("WRAPPER", "REBUILD");
            boolean rebuildBlock = List.of("changed-artifact", "changed-index", "changed-sidecar").contains(name);
            boolean verifyBranch = List.of("verify-existing", "partial-central").contains(name);
            boolean signBlock = List.of("wrong-signing-input", "failed-signing", "interrupted").contains(name);
            if (!earlyBlock && !rebuildBlock && !verifyBranch) approved.expect("SIGN");
            if (!earlyBlock && !rebuildBlock && !verifyBranch && !signBlock) approved.expect("observe:BEFORE_UPLOAD");
            if (!earlyBlock && !rebuildBlock && !verifyBranch && !signBlock && !name.equals("blocked-upload")) approved.expect("GRADLE_UPLOAD");
            if (earlyBlock || rebuildBlock || signBlock || name.equals("blocked-upload")) {
                ReleaseTest.reject(() -> approved(approved.job, handoff, approved, approved, AuthorizationTest.environment(), key -> SecretPreflight.Protection.UNPROTECTED));
                approved.blocked(); ok(approved.uploads == 0);
                if (name.equals("interrupted")) {
                    // New invocation must observe changed state; the old candidate cannot be resumed.
                    Context retry = new Context(approved.job.root(), "blocked-approval", 1); retry.expect("observe:AFTER_APPROVAL");
                    ReleaseTest.reject(() -> approved(retry.job, handoff, retry, retry, AuthorizationTest.environment(), key -> SecretPreflight.Protection.UNPROTECTED));
                    retry.blocked(); ok(retry.uploads == 0);
                }
                if (name.equals("failed-upload-delayed")) scenario(base, "partial-central");
                return;
            }
            var branch = approved(approved.job, handoff, approved, approved, AuthorizationTest.environment(), key -> SecretPreflight.Protection.UNPROTECTED);
            approved.complete(); ok(approved.uploads == (verifyBranch ? 0 : 1)); ok(approved.signs == (verifyBranch ? 0 : 1));
            ok(branch == (verifyBranch ? PublicationBranch.VERIFY_EXISTING : PublicationBranch.SIMULATED_UPLOAD_COMPLETED));
            Context verify = new Context(directory.resolve("verify"), name, recovery ? 2 : 1, transported);
            verify.expect("WRAPPER", "REBUILD");
            var http = RemoteVerificationTest.fake(verify.job.root());
            URI first = http.bodies.keySet().iterator().next(), signature = URI.create(first + ".asc");
            if (name.equals("remote-hash")) http.bodies.put(first, ReleaseTest.bytes("wrong"));
            if (name.equals("remote-signature-invalid")) http.bodies.put(signature, ReleaseTest.bytes("bad armor"));
            if (name.equals("remote-signature-missing")) http.absent.put(signature, 1000);
            Consumer consumers = new Consumer(verify);
            boolean remoteBlock = name.startsWith("remote-") && !name.equals("remote-tag");
            if (!remoteBlock) verify.expect("CENTRAL_MODULE");
            if (!remoteBlock && !name.equals("module-fails")) verify.expect("CENTRAL_POM");
            GitQueries local = (query, version) -> { if (query != GitQueries.Query.HEAD) throw new AssertionError(); return SHA; };
            if (remoteBlock || name.equals("module-fails") || name.equals("pom-fails")) {
                ReleaseTest.reject(() -> verify(verify.job, handoff, local, verify, http, http, http, consumers));
                verify.complete(); ok(consumers.calls == (remoteBlock ? 0 : name.equals("module-fails") ? 1 : 2)); return;
            }
            var verified = verify(verify.job, handoff, local, verify, http, http, http, consumers); verify.complete();
            ok(http.requests.equals(http.bodies.keySet().stream().map(URI::toString).toList()));
            Context finalize = new Context(directory.resolve("finalize"), name, recovery ? 2 : 1, transported); finalize.expect("observe:BEFORE_FINALIZATION");
            var expected = ReleaseFinalizer.Expected.read(new SourceFiles(finalize.job.root()), INPUTS);
            var initial = FinalizationTest.state(null, null, FinalizationTest.absent());
            if (name.equals("remote-tag")) initial = FinalizationTest.state(null, FinalizationTest.tag(), FinalizationTest.absent());
            if (name.equals("complete")) initial = FinalizationTest.state(FinalizationTest.tag(), FinalizationTest.tag(), FinalizationTest.release(expected));
            if (name.equals("wrong-body")) initial = FinalizationTest.state(FinalizationTest.tag(), FinalizationTest.tag(),
                new RemoteReleaseState.ReleaseRecord(RemoteReleaseState.Presence.PRESENT, expected.tag(), expected.tag(), false, false, "wrong"));
            var mutation = new FinalizationTest.Fake(initial);
            if (name.equals("release-fails")) mutation.fail = "release";
            if (name.equals("uncertain-push")) { mutation.fail = "push"; mutation.effectsBeforeFailure = true; }
            if (name.equals("release-fails") || name.equals("uncertain-push")) {
                ReleaseTest.reject(() -> ReleaseWorkflowCoordinator.finalize(finalize.job, verified, finalize, mutation));
                finalize.complete();
                ok(mutation.calls.equals(name.equals("release-fails") ? List.of("observe", "tag", "observe", "push", "observe", "release") : List.of("observe", "tag", "observe", "push")));
                finalize.expect("observe:BEFORE_FINALIZATION"); mutation.fail = null; mutation.calls.clear();
                ok(ReleaseWorkflowCoordinator.finalize(finalize.job, verified, finalize, mutation).action() == ReleaseFinalizer.Action.COMPLETE);
                finalize.complete(); ok(mutation.calls.equals(List.of("observe", "release", "observe"))); return;
            }
            var result = ReleaseWorkflowCoordinator.finalize(finalize.job, verified, finalize, mutation); finalize.complete();
            if (name.equals("wrong-body")) { ok(result.action() == ReleaseFinalizer.Action.BLOCKED); ok(mutation.calls.equals(List.of("observe"))); }
            else {
                ok(result.action() == ReleaseFinalizer.Action.COMPLETE);
                ok(mutation.calls.equals(name.equals("complete") ? List.of("observe") : name.equals("remote-tag")
                    ? List.of("observe", "release", "observe") : List.of("observe", "tag", "observe", "push", "observe", "release", "observe")));
            }
        } finally { PreparationApplicationTest.remove(directory); }
    }
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("totipo-workflow-base-");
        try {
            AcceptedFixture.create(base); ReleaseTest.fixture(base);
            Files.copy(Path.of(args[0]).resolve("SPEC_PIN.md"), base.resolve("SPEC_PIN.md")); ReleaseTest.writable(base.resolve("SPEC_PIN.md"));
            ReleaseTest.copy(Path.of(args[0]).resolve(SourceFiles.SNAPSHOT), base.resolve(SourceFiles.SNAPSHOT));
            for (String name : CASES) { scenario(base, name); System.out.println("Workflow case PASS: " + name); }
            System.out.println("ReleaseWorkflowIntegrationTest PASS: " + CASES.size() + " scenarios; strict job/Git/HTTP/mutation ordering");
        } finally { PreparationApplicationTest.remove(base); }
    }
}
