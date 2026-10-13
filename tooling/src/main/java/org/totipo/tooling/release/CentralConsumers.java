package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.totipo.tooling.release.SourceFiles.require;

/** Trusted process boundary: only successful executed checks can produce finalization receipts. */
public final class CentralConsumers {
    private CentralConsumers() {}
    public record Command(Path root, List<String> arguments, Map<String, String> environment) {
        public Command { arguments = List.copyOf(arguments); environment = Map.copyOf(environment); }
    }
    public record Exit(int status) {}
    public interface Executor {
        /** Returns a new empty private Gradle home for this single invocation. */
        Path newHome() throws IOException;
        Exit execute(Command command) throws IOException;
        void removeHome(Path home) throws IOException;
    }
    public static final class Verified {
        private final ReleaseFinalizer.Prerequisites prerequisites;
        private Verified(ReleaseFinalizer.Prerequisites prerequisites) { this.prerequisites = prerequisites; }
        ReleaseFinalizer.Prerequisites prerequisites() { return prerequisites; }
    }
    public static Verified run(Path root, ReleaseInputs inputs, RemoteArtifactVerifier.Verified remote,
                               Executor executor) throws IOException {
        var source = new SourceFiles(root);
        require(source.text("VERSION").equals(inputs.version() + "\n") && remote.version().equals(inputs.version()), "Consumer version differs");
        ReleaseFinalizer.ConsumerEvidence module = null, pom = null;
        Path first = null;
        for (var mode : ReleaseFinalizer.ConsumerMode.values()) {
            Path home = executor.newHome();
            require(home != null && home.isAbsolute() && home.equals(home.normalize()) && !home.startsWith(root.toAbsolutePath().normalize())
                && !home.equals(first), "Independent external Gradle homes required");
            if (first == null) first = home;
            try {
                try (var directory = EvidenceFiles.parent(home.resolve("anchor"))) {
                    require(!directory.iterator().hasNext(), "Consumer Gradle home must start empty");
                }
                var args = new java.util.ArrayList<>(List.of("./gradlew", "-p", "publishing/consumer-smoke",
                    "--no-daemon", "--no-build-cache", "-PremoteCentral"));
                if (mode == ReleaseFinalizer.ConsumerMode.CENTRAL_POM) args.add("-PpomOnly");
                args.addAll(List.of("clean", "check"));
                var exit = executor.execute(new Command(root, args, Map.of("GRADLE_USER_HOME", home.toString(),
                    "GRADLE_OPTS", "-Dorg.gradle.configuration-cache=false -Dorg.gradle.daemon=false")));
                require(exit != null && exit.status() == 0, "Independent Central consumer failed");
                require(source.text("VERSION").equals(inputs.version() + "\n"), "Consumer version changed");
                var evidence = new ReleaseFinalizer.ConsumerEvidence(mode, inputs, remote.hashes());
                if (mode == ReleaseFinalizer.ConsumerMode.CENTRAL_MODULE_METADATA) module = evidence;
                else pom = evidence;
            } finally { executor.removeHome(home); }
        }
        return new Verified(new ReleaseFinalizer.Prerequisites(remote, module, pom));
    }
}
