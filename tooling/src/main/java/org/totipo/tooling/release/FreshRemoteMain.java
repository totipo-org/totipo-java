package org.totipo.tooling.release;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import static org.totipo.tooling.release.SourceFiles.require;

/** J4B read-only remote-main adapter design. No fetch or cached tracking-ref fallback.
 * Executor must enforce J3B.2's explicit Git executable, config allowlist, cleared environment,
 * bounded pipes and deadline. No concrete live executor is activated in J4A.
 */
public final class FreshRemoteMain {
    private FreshRemoteMain() {}
    @FunctionalInterface public interface Executor {
        String execute(List<String> arguments, Map<String, String> environment) throws IOException;
    }
    public static String read(String repository, Executor executor) throws IOException {
        require(repository != null && repository.matches("[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+")
            && !repository.endsWith("/.") && !repository.endsWith("/.."), "Invalid remote repository");
        String response = executor.execute(List.of("--no-optional-locks", "-c", "core.hooksPath=/dev/null",
            "-c", "credential.helper=", "-c", "core.askPass=", "-c", "http.followRedirects=false",
            "ls-remote", "--exit-code", "--refs", "--", "https://github.com/" + repository + ".git", "refs/heads/main"),
            Map.of("GIT_CONFIG_NOSYSTEM", "1", "GIT_CONFIG_GLOBAL", "/dev/null", "GIT_TERMINAL_PROMPT", "0",
                "GIT_NO_REPLACE_OBJECTS", "1", "GIT_ALLOW_PROTOCOL", "https"));
        require(response != null && response.matches("[0-9a-f]{40}\\trefs/heads/main\\n?"), "Fresh remote main unavailable or ambiguous");
        return response.substring(0, 40);
    }
}
