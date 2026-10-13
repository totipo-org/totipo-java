package org.totipo.tooling.release;

import java.nio.file.Path;
import java.util.Base64;

/** Test-only bridge for an independent Python oracle. Never included in the installed launcher. */
public final class ReleaseFixture {
    private ReleaseFixture() {}
    public static void main(String[] args) throws Exception {
        try {
            switch (args[0]) {
                case "version" -> SourceFiles.require(ReleaseInputs.validVersion(args[1]), "version");
                case "stable" -> ReleaseInputs.stable(args[1]);
                case "inputs" -> ReleaseInputs.from(System.getenv());
                case "notes" -> new SourceFiles(Path.of(args[1])).notes(args[2], Boolean.parseBoolean(args[3]), Boolean.parseBoolean(args[4]));
                case "integrity" -> new SourceFiles(Path.of(args[1])).integrity();
                case "prepare" -> {
                    var plan = PreparationPlan.inspect(new SourceFiles(Path.of(args[1])), new RecordedServices(Path.of(args[3])).git(), args[2], Boolean.parseBoolean(args[4]));
                    for (var e : plan.replacements().entrySet()) System.out.println(e.getKey() + "\t" + Base64.getEncoder().encodeToString(plan.bytes(e.getKey())));
                }
                case "identity" -> {
                    var services = new RecordedServices(Path.of(args[2]));
                    SourceIdentity.inspect(new SourceFiles(Path.of(args[1])), ReleaseInputs.from(System.getenv()), services.git(),
                            new RemoteReleaseState.GitHub(services.http(), System.getenv("GITHUB_REPOSITORY")), Boolean.parseBoolean(args[3]));
                }
                case "state", "history", "release" -> {
                    var services = new RecordedServices(Path.of(args[1]));
                    var github = new RemoteReleaseState.GitHub(services.http(), System.getenv("GITHUB_REPOSITORY"));
                    var inputs = ReleaseInputs.from(System.getenv());
                    switch (args[0]) {
                        case "history" -> System.out.println(github.prior(System.getenv("GITHUB_RUN_ID"), Integer.parseInt(System.getenv("GITHUB_RUN_ATTEMPT")), inputs.commit()));
                        case "release" -> System.out.println(github.release(inputs.version()).presence());
                        default -> {
                            var state = RemoteReleaseState.inspect(inputs.version(), services.http(), github, System.getenv("GITHUB_RUN_ID"),
                                    Integer.parseInt(System.getenv("GITHUB_RUN_ATTEMPT")), inputs.commit());
                            System.out.println(state.core() + " " + state.storage() + " " + state.priorAttempt() + " " + state.classification());
                        }
                    }
                }
                default -> throw new AssertionError("Unexpected fixture command");
            }
        } catch (java.io.IOException | RuntimeException e) {
            System.err.println("Fixture rejected: " + e.getMessage()); System.exit(1);
        }
    }
}
