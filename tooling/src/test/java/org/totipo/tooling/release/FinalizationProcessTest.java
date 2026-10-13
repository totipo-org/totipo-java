package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.totipo.tooling.release.ReleaseTest.*;

/** Actual process deadline, overflow, redaction and stdin credential boundaries. */
public final class FinalizationProcessTest {
    private FinalizationProcessTest() {}
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("release-process-test-");
        String executable = Path.of(System.getProperty("java.home"), "bin/java").toString();
        String cp = java.util.Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
            .map(path -> Path.of(path).toAbsolutePath().toString()).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        try {
            Path curl = root.resolve("fake-curl");
            Files.writeString(curl, "#!" + args[0] + "\nexec '" + executable.replace("'", "'\\''")
                + "' -cp '" + cp.replace("'", "'\\''") + "' " + FakeFinalizationCli.class.getName() + " ok \"$@\"\n");
            ok(curl.toFile().setExecutable(true));
            var actualAdapter = FinalizationProcesses.github(root, curl, "synthetic_token");
            ok(actualAdapter.request("GET", "/releases/tags/v0.2.0", null).status() == 404);
            ok(actualAdapter.request("POST", "/releases", Map.of("body", "reviewed\nnotes\"\\text")).status() == 404);
            for (String mode : List.of("ok", "timeout", "overflow", "fail")) {
                var command = List.of(executable, "-cp", cp, FakeFinalizationCli.class.getName(), mode);
                if (mode.equals("ok")) ok(FinalizationProcesses.run(root, command, Map.of(), bytes("Authorization: Bearer synthetic_token"), Duration.ofSeconds(5)).endsWith("404"));
                else try {
                    FinalizationProcesses.run(root, command, Map.of(), bytes("Authorization: Bearer synthetic_token"), Duration.ofSeconds(mode.equals("timeout") ? 1 : 5));
                    throw new AssertionError("Unsafe process result accepted");
                } catch (IOException error) {
                    ok(!error.getMessage().contains("synthetic_token") && error.getCause() == null && error.getSuppressed().length == 0);
                }
            }
            reject(() -> FinalizationProcesses.github(root, Path.of("curl"), "synthetic_token"));
            reject(() -> FinalizationProcesses.github(root, Path.of("/bin/curl"), "token\nheader"));
            var api = FinalizationProcesses.github(root, Path.of("/bin/curl"), "synthetic_token");
            for (String method : List.of("PATCH", "DELETE", "PUT")) reject(() -> api.request(method, "/git/refs", null));
        } finally { AuthorizationTest.remove(root); }
        System.out.println("Process isolation tests PASS");
    }
}
