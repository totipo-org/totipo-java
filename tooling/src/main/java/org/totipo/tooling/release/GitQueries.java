package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.totipo.tooling.release.SourceFiles.require;

/** Post-bootstrap HEAD, cleanliness and job-acquired main ancestry. No network or mutation. */
public final class GitQueries {
    public static final String REPOSITORY = "totipo-org/totipo-java";
    private final Path root, executable;
    public GitQueries(Path root, Path executable) throws IOException {
        new SourceFiles(root);
        require(executable.isAbsolute(), "Explicit Git executable required");
        this.root = root; this.executable = executable;
    }
    String run(String... args) throws IOException {
        var command = new java.util.ArrayList<>(List.of(executable.toString(), "--no-optional-locks",
            "-c", "core.fsmonitor=false", "-c", "core.untrackedCache=false", "-c", "core.hooksPath=/dev/null",
            "-c", "credential.helper=", "-c", "core.askPass=", "-c", "http.followRedirects=false"));
        command.addAll(List.of(args));
        var environment = new java.util.HashMap<>(Map.of("GIT_NO_REPLACE_OBJECTS", "1",
            "GIT_CONFIG_NOSYSTEM", "1", "GIT_CONFIG_GLOBAL", "/dev/null", "GIT_TERMINAL_PROMPT", "0",
            "GIT_ALLOW_PROTOCOL", "https"));
        String certificate = System.getenv("SSL_CERT_FILE");
        if (certificate != null) environment.put("GIT_SSL_CAINFO", certificate);
        return FinalizationProcesses.run(root, command, environment, new byte[0], Duration.ofSeconds(60));
    }
    public void source(ReleaseInputs inputs) throws IOException {
        require(run("rev-parse", "HEAD").strip().equals(inputs.commit()), "Checkout differs from reviewed commit");
        require(run("status", "--porcelain", "--untracked-files=all").isEmpty(), "Checkout must be clean");
        // Each fresh job acquired canonical main before executing this tooling.
        // Preserve that authenticated ancestry and post-build HEAD/clean checks.
        String main = run("rev-parse", "--verify", "refs/remotes/release/main").strip();
        require(main.matches("[0-9a-f]{40}"), "Missing/invalid job-acquired main identity");
        run("merge-base", "--is-ancestor", inputs.commit(), main);
    }
}
