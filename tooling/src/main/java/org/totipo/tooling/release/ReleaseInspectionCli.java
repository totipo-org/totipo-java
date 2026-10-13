package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import static org.totipo.tooling.release.SourceFiles.require;

/** Focused operations; Actions owns qualification, signing, consumers and one Gradle publication invocation. */
public final class ReleaseInspectionCli {
    private ReleaseInspectionCli() {}
    public static void run(String[] args, Map<String, String> env) throws IOException {
        require(args.length > 0, "Missing release command");
        for (String arg : args) require(!arg.isEmpty(), "Empty release argument");
        switch (args[0]) {
            case "workflow-pins" -> {
                require(args.length == 2, "workflow-pins <root>");
                WorkflowPins.check(Path.of(args[1]));
            }
            case "test-results", "focused-test-results" -> {
                require(args.length == 2, "test-results|focused-test-results <root>");
                System.out.println(TestResults.account(Path.of(args[1]), args[0].equals("focused-test-results")).summary());
            }
            case "compare", "signed", "remote" -> {
                require(args.length == 4, "compare|signed|remote <root> <repository> <manifest>");
                Path root = Path.of(args[1]), staging = Path.of(args[2]), manifest = Path.of(args[3]);
                var inputs = ReleaseInputs.from(env);
                // Independently authenticate local staging semantics, not a caller hash list.
                var accepted = AcceptedInventory.verify(root, staging, inputs.version());
                AcceptedArtifacts.compare(manifest, staging, inputs.version(), accepted.bytes());
                switch (args[0]) {
                    case "compare" -> AcceptedArtifacts.compare(manifest, staging, inputs.version(),
                        ReleaseInputs.required(env, "EXPECTED_INVENTORY").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    case "signed" -> {
                        SourceIdentity.source(new SourceFiles(root), inputs);
                        AcceptedArtifacts.compare(manifest, staging, inputs.version(),
                            ReleaseInputs.required(env, "EXPECTED_INVENTORY").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        AcceptedArtifacts.signed(root, manifest, staging, inputs.version());
                    }
                    case "remote" -> RemoteArtifactVerifier.verify(manifest, staging, inputs.version(), accepted.bytes(),
                        new HttpQueries(), System::nanoTime, duration -> {
                            try { Thread.sleep(duration); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Verification interrupted"); }
                        }, java.time.Duration.ofMinutes(40));
                    default -> throw new AssertionError();
                }
            }
            case "inventory" -> {
                require(args.length == 3, "inventory <root> <repository>");
                System.out.println(AcceptedInventory.verify(Path.of(args[1]), Path.of(args[2]), ReleaseInputs.from(env).version()).json());
            }
            case "initial", "eligible", "tagged", "reserve", "finalize" -> {
                require(args.length == 2, "initial|eligible|tagged|reserve|finalize <root>");
                var inputs = ReleaseInputs.from(env);
                if (args[0].equals("initial")) inputs.initial(env);
                Path root = Path.of(args[1]); var source = new SourceFiles(root);
                SourceIdentity.source(source, inputs);
                new GitQueries(root, Path.of(ReleaseInputs.required(env, "RELEASE_GIT"))).source(inputs);
                var api = FinalizationProcesses.github(root, Path.of(ReleaseInputs.required(env, "RELEASE_CURL")),
                    ReleaseInputs.required(env, "GH_TOKEN"));
                switch (args[0]) {
                    case "initial", "eligible" -> { SourceIdentity.absent(api, inputs); RemoteReleaseState.absent(inputs.version(), new HttpQueries()); }
                    case "tagged" -> SourceIdentity.tag(api, inputs);
                    case "reserve" -> {
                        RemoteReleaseState.absent(inputs.version(), new HttpQueries());
                        SourceIdentity.reserve(api, inputs, ReleaseInputs.required(env, "GITHUB_RUN_ATTEMPT"));
                    }
                    case "finalize" -> ReleaseFinalizer.finish(ReleaseFinalizer.Expected.read(source, inputs), api);
                    default -> throw new AssertionError();
                }
            }
            default -> throw new IOException("Unknown release command");
        }
    }
}
