package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.totipo.tooling.release.SourceFiles.require;

/** Fixed canonical acquisition and local identity reads. No tag mutation. */
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
        String config = run("config", "--local", "--null", "--list");
        for (String entry : config.split("\0")) {
            int split = entry.indexOf('\n'); require(split > 0, "Malformed Git configuration");
            String key = entry.substring(0, split), value = entry.substring(split + 1);
            boolean safe = switch (key) {
                case "core.repositoryformatversion" -> value.equals("0");
                case "core.filemode", "core.logallrefupdates" -> value.equals("true") || value.equals("false");
                case "core.bare" -> value.equals("false");
                case "remote.origin.url" -> value.equals("https://github.com/" + REPOSITORY) || value.equals("https://github.com/" + REPOSITORY + ".git");
                case "remote.origin.fetch" -> value.equals("+refs/heads/*:refs/remotes/origin/*");
                default -> false;
            };
            require(safe, "Unreviewed Git configuration");
        }
        require(run("rev-parse", "HEAD").strip().equals(inputs.commit()), "Checkout differs from reviewed commit");
        require(run("status", "--porcelain", "--untracked-files=all").isEmpty(), "Checkout must be clean");
        run("fetch", "--no-tags", "--no-recurse-submodules", "--", "https://github.com/" + REPOSITORY + ".git",
            "+refs/heads/main:refs/remotes/release/main");
        String main = run("rev-parse", "--verify", "refs/remotes/release/main").strip();
        require(main.matches("[0-9a-f]{40}"), "Invalid fresh main identity");
        run("merge-base", "--is-ancestor", inputs.commit(), main);
    }
}
