package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Closed command vocabulary. Remote evidence is injected; no fetch is permitted. */
@FunctionalInterface
public interface GitQueries {
    enum Query { BRANCH, STATUS, HEAD, REMOTE_MAIN, LOCAL_TAG, LOCAL_TAG_COMMIT, LOCAL_TAG_TYPE, REMOTE_TAG }
    String query(Query query, String version) throws IOException;

    /** Only local read queries. Unexpected/remote commands fail rather than fall back. */
    static GitQueries local(Path root) {
        return (query, version) -> {
            List<String> args = switch (query) {
                case BRANCH -> List.of("branch", "--show-current");
                case STATUS -> List.of("status", "--porcelain", "--untracked-files=all");
                case HEAD -> List.of("rev-parse", "HEAD");
                case LOCAL_TAG -> List.of("tag", "--list", "v" + version);
                case LOCAL_TAG_TYPE -> List.of("cat-file", "-t", "refs/tags/v" + version);
                case LOCAL_TAG_COMMIT -> List.of("rev-parse", "refs/tags/v" + version + "^{commit}");
                case REMOTE_MAIN, REMOTE_TAG -> throw new IOException("Fresh remote Git evidence must be injected");
            };
            SourceFiles.require(ReleaseInputs.validVersion(version), "Invalid version for Git query");
            var command = new java.util.ArrayList<>(List.of("git", "--no-optional-locks", "-c", "core.fsmonitor=false", "-c", "core.untrackedCache=false", "-c", "core.hooksPath=/dev/null"));
            command.addAll(args);
            var builder = new ProcessBuilder(command).directory(root.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
            // Do not inherit user-supplied Git environment redirection or executable hooks.
            builder.environment().keySet().removeIf(k -> k.startsWith("GIT_"));
            builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
            builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
            var process = builder.start();
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var output = executor.submit(() -> process.getInputStream().readNBytes(1_048_577));
                try {
                    SourceFiles.require(process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS), "Git query deadline exceeded");
                    byte[] bytes = output.get(1, TimeUnit.SECONDS);
                    SourceFiles.require(process.exitValue() == 0 && bytes.length <= 1_048_576, "Git query failed or output limit exceeded");
                    return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString().strip();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); throw new IOException("Git query interrupted");
                } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                    throw new IOException("Git query failed");
                } finally { process.destroyForcibly(); }
            }
        };
    }
}
