package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import static org.totipo.tooling.release.SourceFiles.require;

/** Offline integration API, not an operational controller. Jobs and approvals remain Actions-owned.
 * Injected adapters are trusted execution boundaries, never caller booleans or persisted authority.
 * No implementation of signing/upload is installed, and Main cannot reach this coordinator.
 */
public final class ReleaseWorkflowCoordinator {
    private ReleaseWorkflowCoordinator() {}
    public enum Boundary { PREFLIGHT, AFTER_APPROVAL, BEFORE_UPLOAD, BEFORE_FINALIZATION }
    public enum Step { WRAPPER, GUARDRAILS, NORMAL_QUALIFICATION, OFFLINE_QUALIFICATION,
        LOCAL_POM_CONSUMER, FOCUSED_INTEGRITY, REBUILD, SIGN, GRADLE_UPLOAD }
    public record Job(Path root, ReleaseInputs inputs, String repository, String run, int attempt) {
        Path manifest() { return root.resolve("build/publication-manifest.json"); }
        Path staging() { return root.resolve("build/repository"); }
    }
    public record Observers(GitQueries git, RemoteReleaseState.Http http) {}
    @FunctionalInterface public interface FreshObservers { Observers acquire(Boundary boundary) throws IOException; }
    @FunctionalInterface public interface Execution { void execute(Step step) throws IOException; }
    public static final class PreflightEvidence {
        private final AcceptedInventory inventory;
        private final TestResults.Result normal, offline, focused;
        private PreflightEvidence(AcceptedInventory inventory, TestResults.Result normal,
                                  TestResults.Result offline, TestResults.Result focused) {
            this.inventory = inventory; this.normal = normal; this.offline = offline; this.focused = focused;
        }
        public AcceptedInventory inventory() { return inventory; }
        public TestResults.Result normal() { return normal; }
        public TestResults.Result offline() { return offline; }
        public TestResults.Result focused() { return focused; }
    }
    public enum PublicationBranch { SIMULATED_UPLOAD_COMPLETED, VERIFY_EXISTING }
    private static Observers identity(Job job, FreshObservers factory, Boundary boundary, boolean tags) throws IOException {
        Observers observers = factory.acquire(boundary);
        require(observers != null && observers.git() != null && observers.http() != null, "Fresh observers unavailable");
        SourceIdentity.inspect(new SourceFiles(job.root()), job.inputs(), observers.git(),
            new RemoteReleaseState.GitHub(observers.http(), job.repository()), tags);
        return observers;
    }
    private static PublicationPolicy.Disposition state(Job job, Observers observers) throws IOException {
        var facts = RemoteReleaseState.inspect(job.inputs().version(), observers.http(),
            new RemoteReleaseState.GitHub(observers.http(), job.repository()), job.run(), job.attempt(), job.inputs().commit());
        var decision = PublicationPolicy.interpret(facts);
        require(decision.disposition() != PublicationPolicy.Disposition.BLOCKED, "Remote state blocks workflow: " + decision.reason());
        return decision.disposition();
    }
    public static PreflightEvidence preflight(Job job, FreshObservers observers, Execution execution) throws IOException {
        var fresh = identity(job, observers, Boundary.PREFLIGHT, false);
        PreparationPlan.inspect(new SourceFiles(job.root()), fresh.git(), job.inputs().version(), true);
        state(job, fresh);
        execution.execute(Step.WRAPPER); execution.execute(Step.GUARDRAILS);
        execution.execute(Step.NORMAL_QUALIFICATION);
        var normal = TestResults.account(job.root(), false);
        var accepted = AcceptedInventory.verify(job.root(), job.staging(), job.inputs().version());
        execution.execute(Step.OFFLINE_QUALIFICATION);
        var offline = TestResults.account(job.root(), false);
        rebuilt(job, accepted.bytes());
        execution.execute(Step.LOCAL_POM_CONSUMER);
        execution.execute(Step.FOCUSED_INTEGRITY);
        var focused = TestResults.account(job.root(), true);
        new SourceFiles(job.root()).integrity();
        var handoff = AcceptedInventory.verify(job.root(), job.staging(), job.inputs().version());
        AcceptedInventory.compare(job.manifest(), job.staging(), job.inputs().version(), accepted.bytes());
        return new PreflightEvidence(handoff, normal, offline, focused);
    }
    private static void rebuilt(Job job, byte[] expected) throws IOException {
        // Semantic verification is independent of both the received hashes and current manifest.
        AcceptedInventory.verify(job.root(), job.staging(), job.inputs().version());
        AcceptedInventory.compare(job.manifest(), job.staging(), job.inputs().version(), expected);
    }
    /** A new job gets serialized expectations only. No preflight decision is accepted here. */
    public static PublicationBranch approved(Job job, byte[] expected, FreshObservers observers, Execution execution,
            Map<String, String> environment, SecretPreflight.PacketInspector inspector) throws IOException {
        SecretPreflight.check(environment, inspector);
        var fresh = identity(job, observers, Boundary.AFTER_APPROVAL, false);
        var branch = state(job, fresh);
        execution.execute(Step.WRAPPER); execution.execute(Step.REBUILD); rebuilt(job, expected);
        if (branch == PublicationPolicy.Disposition.VERIFY_EXISTING) return PublicationBranch.VERIFY_EXISTING;
        execution.execute(Step.SIGN);
        AcceptedArtifacts.signed(job.root(), job.manifest(), job.staging(), job.inputs().version());
        var immediate = identity(job, observers, Boundary.BEFORE_UPLOAD, false);
        require(state(job, immediate) == PublicationPolicy.Disposition.CANDIDATE_PUBLISH, "Upload-time state changed; stop and reobserve");
        // Deliberately adjacent: no returned token can be saved across a job or interruption.
        execution.execute(Step.GRADLE_UPLOAD);
        return PublicationBranch.SIMULATED_UPLOAD_COMPLETED;
    }
    public static CentralConsumers.Verified verify(Job job, byte[] expected, GitQueries sourceGit, Execution execution,
            RemoteArtifactVerifier.Transport transport, RemoteArtifactVerifier.Clock clock,
            RemoteArtifactVerifier.Sleeper sleeper, CentralConsumers.Executor consumers) throws IOException {
        require(new SourceFiles(job.root()).text("VERSION").equals(job.inputs().version() + "\n"), "Wrong verification version");
        require(sourceGit.query(GitQueries.Query.HEAD, job.inputs().version()).equals(job.inputs().commit()), "Wrong verification source");
        execution.execute(Step.WRAPPER); execution.execute(Step.REBUILD); rebuilt(job, expected);
        var remote = RemoteArtifactVerifier.verify(job.manifest(), job.staging(), job.inputs().version(), expected,
            transport, clock, sleeper, Duration.ofSeconds(2400));
        return CentralConsumers.run(job.root(), job.inputs(), remote, consumers);
    }
    public static ReleaseFinalizer.Decision finalize(Job job, CentralConsumers.Verified verified,
            FreshObservers observers, ReleaseFinalizer.Adapter adapter) throws IOException {
        identity(job, observers, Boundary.BEFORE_FINALIZATION, true);
        return ReleaseFinalizer.execute(ReleaseFinalizer.Expected.read(new SourceFiles(job.root()), job.inputs()),
            verified.prerequisites(), adapter);
    }
}
