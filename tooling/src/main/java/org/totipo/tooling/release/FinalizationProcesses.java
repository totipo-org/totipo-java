package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.totipo.tooling.release.SourceFiles.require;
import static org.totipo.tooling.release.ReleaseFinalizer.*;

/** Side-effect-capable runtime adapter, deliberately unreachable from Main. Observations are
 * separately injected and must include inspected remote objects, not ls-remote presence alone.
 * Explicit executables, trusted temporary parent and frozen source root are runtime trust inputs.
 */
public final class FinalizationProcesses implements Adapter {
    @FunctionalInterface public interface Observer { Observed read(Expected expected) throws IOException; }
    private final Path root, git, gh, temporaryParent;
    private final String repository, token, pushUrl;
    private final Duration timeout;
    private final Observer observer;
    public FinalizationProcesses(Path root, Path git, Path gh, Path temporaryParent, String repository,
                                 String token, Duration timeout, Observer observer) throws IOException {
        this(root, git, gh, temporaryParent, repository, token, timeout, observer, null);
    }
    // Fixture-only local bare remote; no general caller-selected live origin API.
    FinalizationProcesses(Path root, Path git, Path gh, Path temporaryParent, String repository,
                          String token, Duration timeout, Observer observer, Path fixtureRemote) throws IOException {
        require(git.isAbsolute() && gh.isAbsolute() && timeout.compareTo(Duration.ofMinutes(1)) <= 0
                && !timeout.isNegative() && !timeout.isZero(), "Invalid finalization runtime configuration");
        require(repository != null && repository.matches("[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+")
                && !repository.endsWith("/.") && !repository.endsWith("/.."), "Invalid finalization repository");
        require(token != null && !token.isEmpty() && token.chars().noneMatch(Character::isISOControl), "Missing or invalid runtime token");
        this.root = root.toAbsolutePath().normalize(); this.git = git; this.gh = gh;
        this.temporaryParent = temporaryParent; this.repository = repository; this.token = token;
        this.timeout = timeout; this.observer = observer;
        new SourceFiles(this.root);
        require(Files.isDirectory(this.root.resolve(".git"), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(this.root.resolve(".git")), "Standalone local Git directory required");
        pushUrl = fixtureRemote == null ? "https://github.com/" + repository + ".git" : fixtureRemote.toAbsolutePath().toString();
        if (fixtureRemote != null) require(Files.isDirectory(fixtureRemote) && !Files.isSymbolicLink(fixtureRemote), "Local fixture remote required");
    }
    @Override public Observed observe(Expected expected) throws IOException { return observer.read(expected); }
    /** Refuse repository config that can redirect commands, credentials, helpers or transports. */
    private void configuration() throws IOException {
        String config = runGit(List.of("config", "--local", "--null", "--list"), Map.of());
        for (String record : config.split("\0", -1)) {
            if (record.isEmpty()) continue;
            int split = record.indexOf('\n'); require(split > 0, "Malformed local Git configuration");
            String key = record.substring(0, split), value = record.substring(split + 1);
            boolean accepted = switch (key) {
                case "core.repositoryformatversion" -> value.equals("0");
                case "core.filemode", "core.logallrefupdates" -> value.equals("true") || value.equals("false");
                case "core.bare" -> value.equals("false");
                case "remote.origin.url" -> value.equals(pushUrl);
                case "remote.origin.fetch" -> value.equals("+refs/heads/*:refs/remotes/origin/*");
                default -> false;
            };
            require(accepted, "Unreviewed local Git configuration; no mutation performed");
        }
    }
    @Override public void createTag(Expected expected) throws IOException {
        configuration();
        runGit(List.of("tag", "--no-sign", "-a", expected.tag(), expected.inputs().commit(), "-m", "totipo-java " + expected.inputs().version()), Map.of());
    }
    @Override public void pushTag(Expected expected, Tag local) throws IOException {
        configuration();
        require(validTag(local, expected.inputs().commit(), "totipo-java " + expected.inputs().version() + "\n")
                && local.equals(inspectTag(expected.tag())), "Local tag changed before push");
        Map<String, String> authentication = pushUrl.startsWith("https://github.com/") ? Map.of(
                "GIT_CONFIG_COUNT", "1", "GIT_CONFIG_KEY_0", "http.https://github.com/" + repository + ".git.extraheader",
                "GIT_CONFIG_VALUE_0", "AUTHORIZATION: basic " + Base64.getEncoder().encodeToString(("x-access-token:" + token).getBytes(StandardCharsets.UTF_8))) : Map.of();
        // Push the inspected object itself: a concurrent local ref change cannot substitute bytes.
        runGit(List.of("push", "--no-verify", "--", pushUrl, local.object() + ":refs/tags/" + expected.tag()), authentication);
    }
    public Tag inspectTag(String tag) throws IOException {
        require(tag != null && tag.startsWith("v") && ReleaseInputs.validVersion(tag.substring(1)), "Invalid inspected tag name");
        configuration();
        String ref = "refs/tags/" + tag;
        String object = runGit(List.of("rev-parse", "--verify", ref), Map.of()).strip();
        require(object.matches("[0-9a-f]{40}"), "Malformed tag object identity");
        String type = runGit(List.of("cat-file", "-t", object), Map.of()).strip();
        require(type.equals("tag"), "Incomplete or lightweight tag inspection");
        String raw = runGit(List.of("cat-file", "-p", object), Map.of());
        String[] parts = raw.split("\n\n", 2);
        require(parts.length == 2, "Malformed tag object");
        String[] lines = parts[0].split("\n");
        require(lines.length == 4 && lines[0].matches("object [0-9a-f]{40}") && lines[1].equals("type commit")
                && lines[2].equals("tag " + tag) && lines[3].matches("tagger [^\r\n]+ [0-9]+ [+-][0-9]{4}"), "Malformed tag headers");
        String commit = runGit(List.of("rev-parse", "--verify", ref + "^{commit}"), Map.of()).strip();
        require(commit.equals(lines[0].substring(7)) && !parts[1].contains("-----BEGIN PGP SIGNATURE-----"), "Contradictory or signed tag object");
        return new Tag(object, commit, type, parts[1]);
    }
    @Override public void createRelease(Expected expected) throws IOException {
        Path directory = null;
        try {
            directory = Files.createTempDirectory(temporaryParent, "totipo-release-body-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            Path notes = directory.resolve("body.md");
            Files.write(notes, expected.body().getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE_NEW);
            Files.setPosixFilePermissions(notes, PosixFilePermissions.fromString("rw-------"));
            run(List.of(gh.toString(), "release", "create", expected.tag(), "--repo", repository, "--verify-tag", "--title", expected.tag(), "--notes-file", notes.toString()),
                    Map.of("GH_TOKEN", token, "GH_HOST", "github.com", "GH_CONFIG_DIR", directory.toString(), "GH_PROMPT_DISABLED", "1", "HOME", directory.toString()));
        } finally {
            if (directory != null) {
                try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                } catch (IOException cleanup) { throw new IOException("Release body cleanup failed"); }
            }
        }
    }
    private String runGit(List<String> args, Map<String, String> environment) throws IOException {
        var command = new ArrayList<>(List.of(git.toString(), "--no-optional-locks", "-c", "core.fsmonitor=false", "-c", "core.untrackedCache=false",
                "-c", "core.hooksPath=/dev/null", "-c", "tag.gpgSign=false", "-c", "user.name=github-actions[bot]",
                "-c", "user.email=41898282+github-actions[bot]@users.noreply.github.com", "-c", "credential.helper=", "-c", "core.askPass=", "-c", "http.followRedirects=false"));
        command.addAll(args);
        var env = new java.util.HashMap<>(environment);
        env.put("GIT_NO_REPLACE_OBJECTS", "1");
        env.put("GIT_CONFIG_NOSYSTEM", "1"); env.put("GIT_CONFIG_GLOBAL", "/dev/null"); env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GIT_ALLOW_PROTOCOL", pushUrl.startsWith("https://") ? "https" : "file");
        return run(command, env);
    }
    private String run(List<String> command, Map<String, String> environment) throws IOException {
        Process process = null;
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var builder = new ProcessBuilder(command).directory(root.toFile());
            builder.environment().clear(); builder.environment().putAll(environment);
            builder.environment().put("LC_ALL", "C.UTF-8");
            // Only the reviewed Git executable directory, for gh's git child if needed.
            builder.environment().put("PATH", git.getParent().toString());
            process = builder.start(); process.getOutputStream().close(); Process child = process;
            long end = System.nanoTime() + timeout.toNanos();
            Future<byte[]> out = workers.submit(() -> bounded(child.getInputStream()));
            Future<byte[]> err = workers.submit(() -> bounded(child.getErrorStream()));
            byte[] stdout = out.get(Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS);
            err.get(Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS);
            require(child.waitFor(Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS) && child.exitValue() == 0, "Process failed");
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(stdout)).toString();
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            // No causes, argv, captured diagnostics or encoded headers can escape.
            throw new IOException("Finalization process failed or deadline exceeded");
        } finally {
            if (process != null) {
                process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly();
                try { process.waitFor(1, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                try { process.getInputStream().close(); process.getErrorStream().close(); process.getOutputStream().close(); } catch (IOException ignored) { /* Continue cleanup. */ }
            }
            workers.shutdownNow();
        }
    }
    private static byte[] bounded(java.io.InputStream input) throws IOException {
        try (input) { byte[] data = input.readNBytes(1_048_577); require(data.length <= 1_048_576, "Process output limit"); return data; }
    }
}
