package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import static org.totipo.tooling.release.SourceFiles.require;

/** Shadow inspection only. Remote commands consume explicit recorded evidence, never live services. */
public final class ReleaseInspectionCli {
    private ReleaseInspectionCli() {}
    public static void run(String[] args, Map<String, String> env) throws IOException {
        require(args.length > 0, "Missing release inspection command");
        for (String arg : args) require(!arg.isEmpty(), "Empty release argument");
        switch (args[0]) {
            case "inputs" -> {
                require(args.length == 1, "inputs takes no arguments");
                var inputs = ReleaseInputs.from(env);
                System.out.println("Reviewed inputs: " + inputs.version() + " " + inputs.commit());
            }
            case "prepare-dry-run", "prepare-check" -> {
                require(args.length == 3, "Usage: release prepare-dry-run|prepare-check <root> <version>");
                var source = new SourceFiles(Path.of(args[1]));
                var plan = PreparationPlan.inspect(source, GitQueries.local(Path.of(args[1])), args[2], args[0].equals("prepare-check"));
                System.out.println("Read-only source inspection; qualification is separate:");
                plan.replacements().keySet().forEach(System.out::println);
            }
            case "inspect-identity" -> {
                require(args.length == 3, "Usage: release inspect-identity <root> <recorded-evidence-directory>");
                var services = new RecordedServices(Path.of(args[2]));
                var github = new RemoteReleaseState.GitHub(services.http(), ReleaseInputs.required(env, "GITHUB_REPOSITORY"));
                var identity = SourceIdentity.inspect(new SourceFiles(Path.of(args[1])), ReleaseInputs.from(env), services.git(), github, false);
                System.out.println("Recorded identity consistent: " + identity.head());
            }
            case "inspect-state" -> {
                require(args.length == 2, "Usage: release inspect-state <recorded-evidence-directory>");
                var services = new RecordedServices(Path.of(args[1]));
                var github = new RemoteReleaseState.GitHub(services.http(), ReleaseInputs.required(env, "GITHUB_REPOSITORY"));
                var inputs = ReleaseInputs.from(env);
                var state = RemoteReleaseState.inspect(inputs.version(), services.http(), github,
                        ReleaseInputs.required(env, "GITHUB_RUN_ID"), Integer.parseInt(ReleaseInputs.required(env, "GITHUB_RUN_ATTEMPT")), inputs.commit());
                System.out.println("Recorded remote facts: " + state.core() + " " + state.storage() + " " + state.priorAttempt() + " " + state.classification());
            }
            default -> throw new IOException("Unknown release inspection command");
        }
        System.out.println("Inspection does not authorize publication.");
    }
}
