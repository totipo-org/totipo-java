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
            case "workflow-pins" -> {
                require(args.length == 2, "Usage: release workflow-pins <root>");
                WorkflowPins.check(Path.of(args[1]));
                System.out.println("Reviewed workflow action pins passed");
            }
            case "test-results", "focused-test-results" -> {
                require(args.length == 2, "Usage: release test-results|focused-test-results <root>");
                System.out.println(TestResults.account(Path.of(args[1]), args[0].equals("focused-test-results")).summary());
            }
            case "inventory" -> {
                require(args.length == 3, "Usage: release inventory <root> <repository>");
                var inputs = ReleaseInputs.from(env);
                System.out.println("inventory=" + AcceptedInventory.verify(Path.of(args[1]), Path.of(args[2]), inputs.version()).json());
            }
            case "compare" -> {
                require(args.length == 4, "Usage: release compare <manifest> <repository> <version>");
                AcceptedInventory.compare(Path.of(args[1]), Path.of(args[2]), args[3],
                    ReleaseInputs.required(env, "EXPECTED_INVENTORY").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.println("10/10 rebuilt artifact hashes match preflight");
            }
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
