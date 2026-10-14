package org.totipo.tooling.release;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Focused permanent source, input, JSON and canonical HTTP regressions. */
public final class ReleaseTest {
    private ReleaseTest() {}
    private static int assertions;
    @FunctionalInterface interface Checked { void run() throws Exception; }
    static void ok(boolean condition) { assertions++; if (!condition) throw new AssertionError("Assertion " + assertions); }
    static void reject(Checked action) throws Exception {
        assertions++;
        try { action.run(); } catch (IOException | IllegalArgumentException | UnsupportedOperationException e) { return; }
        throw new AssertionError("Expected rejection at " + assertions);
    }
    static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    static void write(Path root, String path, String text) throws IOException {
        Path file = root.resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file, text);
    }
    static void copy(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path p : paths.toList()) {
                Path target = destination.resolve(source.relativize(p));
                if (Files.isDirectory(p)) Files.createDirectories(target); else { Files.copy(p, target); writable(target); }
            }
        }
    }
    static void writable(Path path) throws IOException {
        Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
    }
    static void fixture(Path root) throws IOException {
        write(root, "VERSION", "0.2.0\n");
        write(root, "review/V0_2_0_RELEASE_NOTES.md", "## v0.2.0\n\n### Highlights\nReviewed\n### Compatibility and scope\nReviewed\n### Validation\nReviewed\n");
    }
    static void gitIdentity(Path executable) throws Exception {
        Path root = Files.createTempDirectory("source-git-test-");
        try {
            var git = new GitQueries(root, executable);
            git.run("init", "--initial-branch=main");
            git.run("-c", "user.name=Fixture", "-c", "user.email=fixture@invalid.example", "commit", "--allow-empty", "-m", "initial");
            String reviewed = git.run("rev-parse", "HEAD").strip();
            var inputs = new ReleaseInputs("0.2.0", reviewed);
            // A fresh-job acquisition ref must exist; the helper cannot silently use origin/main.
            reject(() -> git.source(inputs));
            git.run("update-ref", "refs/remotes/release/main", reviewed);
            git.run("config", "color.ui", "false"); // harmless local configuration is accepted
            git.source(inputs);
            reject(() -> git.source(new ReleaseInputs("0.2.0", "a".repeat(40))));
            write(root, "dirty", "untracked"); reject(() -> git.source(inputs)); Files.delete(root.resolve("dirty"));
            git.run("-c", "user.name=Fixture", "-c", "user.email=fixture@invalid.example", "commit", "--allow-empty", "-m", "next");
            String next = git.run("rev-parse", "HEAD").strip();
            // A stale acquired main that does not reach the reviewed commit fails.
            reject(() -> git.source(new ReleaseInputs("0.2.0", next)));
            git.run("update-ref", "refs/remotes/release/main", next);
            git.source(new ReleaseInputs("0.2.0", next));
            git.run("checkout", "--orphan", "untrusted");
            git.run("-c", "user.name=Fixture", "-c", "user.email=fixture@invalid.example", "commit", "--allow-empty", "-m", "unrelated");
            reject(() -> git.source(new ReleaseInputs("0.2.0", git.run("rev-parse", "HEAD").strip())));
        } finally { AuthorizationTest.remove(root); }
    }
    public static void main(String[] args) throws Exception {
        gitIdentity(Path.of(args[1]));
        String sha = "a".repeat(40);
        var inputs = new ReleaseInputs("0.2.0", sha);
        inputs.initial(Map.of("DISPATCH_REF", "refs/heads/main", "DISPATCH_SHA", sha));
        reject(() -> inputs.initial(Map.of("DISPATCH_REF", "refs/heads/main", "DISPATCH_SHA", "b".repeat(40))));
        reject(() -> inputs.initial(Map.of("DISPATCH_REF", "refs/tags/v0.2.0", "DISPATCH_SHA", sha)));
        for (String version : new String[]{"", "0.2.0\nx=y", "$(id)", "../x", "0.2.0-SnApShOt", "1".repeat(129)})
            reject(() -> new ReleaseInputs(version, sha));
        for (String commit : new String[]{"A".repeat(40), "a".repeat(39), sha + "\n", "--help"}) reject(() -> new ReleaseInputs("0.2.0", commit));
        Path root = Files.createTempDirectory("release-source-test-");
        try {
            fixture(root); Path canonical = Path.of(args[0]);
            Files.copy(canonical.resolve("SPEC_PIN.md"), root.resolve("SPEC_PIN.md")); writable(root.resolve("SPEC_PIN.md"));
            var source = new SourceFiles(root); SourceIdentity.source(source, inputs);
            for (String version : new String[]{"0.2.0", "0.2.0\r\n", "0.3.0\n", "0.2.0\n\n"}) {
                write(root, "VERSION", version); reject(() -> SourceIdentity.source(source, inputs));
            }
            write(root, "VERSION", "0.2.0\n");
            String notes = source.text(SourceFiles.notesPath("0.2.0"));
            for (String bad : new String[]{notes.replace("0.2.0", "0.1.0"), notes + "TODO:", "", "# v0.3.0\nCompatible with v0.2.0", "# v0.2.0\n", notes + "TBD"}) {
                write(root, SourceFiles.notesPath("0.2.0"), bad); reject(() -> SourceIdentity.source(source, inputs));
            }
            write(root, SourceFiles.notesPath("0.2.0"), notes);
            write(root, SourceFiles.notesPath("0.2.0"), "# Release v0.2.0\nReady\n");
            // Version identity is required; section names and heading syntax are not.
            SourceIdentity.source(source, inputs);
            Files.writeString(root.resolve("SPEC_PIN.md"), "wrong"); reject(source::spec);
            reject(() -> source.read("../VERSION", 100)); reject(() -> source.read("VERSION", 2));
            Files.createSymbolicLink(root.resolve("link"), root.resolve("VERSION")); reject(() -> source.read("link", 100));
        } finally { AuthorizationTest.remove(root); }
        RemoteReleaseState.absent("0.2.0", uri -> new RemoteReleaseState.Response(404, bytes("")));
        for (int status : new int[]{200, 301, 403, 429, 500}) reject(() -> RemoteReleaseState.absent("0.2.0", uri -> new RemoteReleaseState.Response(status, bytes(""))));
        reject(() -> RemoteReleaseState.absent("0.2.0", uri -> { throw new IOException("network"); }));
        for (String uri : new String[]{"http://repo.maven.apache.org/maven2/a", "https://evil.invalid/a", "https://api.github.com/repos/totipo-org/totipo-java/actions/runs/1/attempts/1/jobs", "https://api.github.com/repos/a/b/releases/tags/v0.2.0?x=y"})
            reject(() -> HttpQueries.endpointLimit(URI.create(uri)));
        for (String json : new String[]{"{} {}", "{\"a\":1,\"a\":2}", "{", "{\"a\":\"" + "x".repeat(262145) + "\"}"}) reject(() -> RemoteReleaseState.object(bytes(json)));
        reject(() -> RemoteReleaseState.sha(RemoteReleaseState.object(bytes("{\"sha\":" + "1".repeat(40) + "}")).get("sha")));
        System.out.println("Release source/input tests: " + assertions + " assertions passed");
    }
}
