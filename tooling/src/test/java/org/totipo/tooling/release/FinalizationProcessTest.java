package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.totipo.tooling.release.ReleaseTest.*;
import static org.totipo.tooling.release.ReleaseFinalizer.*;

/** Actual Git operations ONLY in disposable repositories, pushing ONLY to a local bare fixture.
 * GitHub CLI is always a strict synthetic executable. */
public final class FinalizationProcessTest {
    private FinalizationProcessTest() {}
    static String git(Path executable, Path root, String... args) throws Exception {
        var command = new ArrayList<>(List.of(executable.toString())); command.addAll(List.of(args));
        var pb = new ProcessBuilder(command).directory(root.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.environment().keySet().removeIf(k -> k.startsWith("GIT_")); pb.environment().put("GIT_CONFIG_NOSYSTEM", "1"); pb.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        var process = pb.start(); byte[] bytes = process.getInputStream().readNBytes(1_048_577);
        ok(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0); return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).strip();
    }
    static String quote(String s) { return "'" + s.replace("'", "'\"'\"'") + "'"; }
    static Path launcher(Path directory, String shell, String mode, Path body) throws Exception {
        Path path = directory.resolve("fake-" + mode);
        Files.writeString(path, "#!" + shell + "\nexec " + quote(Path.of(System.getProperty("java.home"), "bin/java").toString()) + " -cp "
                + quote(java.util.Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator)).map(p -> Path.of(p).toAbsolutePath().normalize().toString()).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator))) + " org.totipo.tooling.release.FakeFinalizationCli " + quote(mode) + " " + quote(body.toString()) + " \"$@\"\n");
        Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")); return path;
    }
    public static void main(String[] args) throws Exception {
        Path integrity = Path.of(args[0]), executable = Path.of(args[1]); String shell = args[2];
        Path temp = Files.createTempDirectory("totipo-git-fixture-");
        try {
            Path root = temp.resolve("source"), bare = temp.resolve("remote.git"), notesTemp = temp.resolve("notes");
            Files.createDirectories(root); Files.createDirectories(bare); Files.createDirectories(notesTemp);
            ReleaseTest.fixture(root); Files.copy(integrity.resolve("SPEC_PIN.md"), root.resolve("SPEC_PIN.md"));
            git(executable, root, "init", "--initial-branch=main"); git(executable, bare, "init", "--bare");
            git(executable, root, "add", "."); git(executable, root, "-c", "user.name=fixture", "-c", "user.email=fixture@example.invalid", "commit", "--no-gpg-sign", "-m", "fixture");
            String head = git(executable, root, "rev-parse", "HEAD");
            Expected expected = Expected.read(new SourceFiles(root), new ReleaseInputs("0.2.0", head));
            Path body = temp.resolve("expected-body"); Files.writeString(body, expected.body());
            var runtime = new FinalizationProcesses(root, executable, launcher(temp, shell, "gh", body), notesTemp,
                    "totipo-org/totipo-java", "synthetic-token", Duration.ofSeconds(5), e -> { throw new AssertionError("No live observations"); }, bare);
            String configuration = Files.readString(root.resolve(".git/config"));
            runtime.createTag(expected); Tag local = runtime.inspectTag(expected.tag());
            ok(local.commit().equals(head) && local.annotation().equals("totipo-java 0.2.0\n") && local.type().equals("tag"));
            ok(git(executable, root, "cat-file", "-p", local.object()).contains("tagger github-actions[bot] <41898282+github-actions[bot]@users.noreply.github.com>"));
            runtime.pushTag(expected, local);
            ok(git(executable, bare, "rev-parse", "refs/tags/v0.2.0").equals(local.object()));
            ok(git(executable, bare, "rev-parse", "refs/tags/v0.2.0^{commit}").equals(head));
            runtime.createRelease(expected); try (var files = Files.list(notesTemp)) { ok(files.findAny().isEmpty()); }
            ok(Files.readString(root.resolve(".git/config")).equals(configuration));
            reject(() -> runtime.createTag(expected)); // Existing tag never overwritten.
            git(executable, root, "tag", "v0.2.1", head); reject(() -> runtime.inspectTag("v0.2.1"));
            reject(() -> runtime.pushTag(expected, new Tag("f".repeat(40), head, "tag", local.annotation())));
            for (String mode : List.of("failure", "timeout", "overflow", "malformed")) {
                var failed = new FinalizationProcesses(root, executable, launcher(temp, shell, mode, body), notesTemp,
                        "totipo-org/totipo-java", "synthetic-token", Duration.ofMillis(mode.equals("timeout") ? 300 : 5000), e -> null, bare);
                try { failed.createRelease(expected); throw new AssertionError("Expected failed CLI"); }
                catch (IOException error) { ok(!error.toString().contains("synthetic-token") && !error.toString().contains("AUTHORIZATION") && error.getCause() == null); }
                try (var files = Files.list(notesTemp)) { ok(files.findAny().isEmpty()); }
            }
            Path gitEvidence = temp.resolve("fake-git.properties");
            Files.writeString(gitEvidence, "object=" + local.object() + "\ncommit=" + head + "\n");
            var credentialGit = new FinalizationProcesses(root, launcher(temp, shell, "git", gitEvidence), launcher(temp, shell, "gh", body), notesTemp,
                    "totipo-org/totipo-java", "synthetic-token", Duration.ofSeconds(5), e -> null);
            credentialGit.pushTag(expected, local); // Fake executable asserts exact scoped ephemeral header; no network.
            var failedGit = new FinalizationProcesses(root, launcher(temp, shell, "failure", body), launcher(temp, shell, "gh", body), notesTemp,
                    "totipo-org/totipo-java", "synthetic-token", Duration.ofSeconds(5), e -> null, bare);
            reject(() -> failedGit.createTag(expected));
            for (String mode : List.of("timeout", "overflow", "malformed")) {
                var failed = new FinalizationProcesses(root, launcher(temp, shell, mode, body), launcher(temp, shell, "gh", body), notesTemp,
                        "totipo-org/totipo-java", "synthetic-token", Duration.ofMillis(mode.equals("timeout") ? 300 : 5000), e -> null, bare);
                reject(() -> failed.createTag(expected));
            }
            reject(() -> runtime.inspectTag("v0.2.2"));
            git(executable, root, "config", "alias.push", "!touch forbidden"); reject(() -> runtime.pushTag(expected, local));
            ok(!Files.exists(root.resolve("forbidden")));
            System.out.println("FinalizationProcessTest PASS (fixture Git and fake gh only)");
        } finally { PreparationApplicationTest.remove(temp); }
    }
}
