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
        for (String path : new String[]{"README.md", "API_DESIGN.md"}) write(root, path,
            "<!-- prepared-release:start -->\nJava 0.2.0 is released. See RELEASE_CHECKLIST.md.\n<!-- prepared-release:end -->\n");
        write(root, "publishing/consumer-smoke/gradle.lockfile", "org.totipo:totipo-core:0.2.0=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath\n"
            + "org.totipo:totipo-storage-nio:0.2.0=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath\n");
        write(root, "review/V0_2_0_RELEASE_NOTES.md", "## v0.2.0\n\n### Highlights\nReviewed\n### Compatibility and scope\nReviewed\n### Validation\nReviewed\n");
    }
    public static void main(String[] args) throws Exception {
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
            copy(canonical.resolve(SourceFiles.SNAPSHOT), root.resolve(SourceFiles.SNAPSHOT));
            var source = new SourceFiles(root); SourceIdentity.source(source, inputs);
            for (String version : new String[]{"0.2.0", "0.2.0\r\n", "0.3.0\n", "0.2.0\n\n"}) {
                write(root, "VERSION", version); reject(() -> SourceIdentity.source(source, inputs));
            }
            write(root, "VERSION", "0.2.0\n");
            String metadata = source.text("README.md");
            write(root, "README.md", metadata.replace("0.2.0", "0.1.0")); reject(() -> SourceIdentity.source(source, inputs));
            write(root, "README.md", metadata);
            String notes = source.text(SourceFiles.notesPath("0.2.0"));
            for (String bad : new String[]{notes.replace("0.2.0", "0.1.0"), notes + "TODO:", notes.replace("### Validation", "### Other")}) {
                write(root, SourceFiles.notesPath("0.2.0"), bad); reject(() -> SourceIdentity.source(source, inputs));
            }
            write(root, SourceFiles.notesPath("0.2.0"), notes);
            String lock = source.text("publishing/consumer-smoke/gradle.lockfile");
            write(root, "publishing/consumer-smoke/gradle.lockfile", lock.replace("0.2.0", "0.1.0")); reject(() -> SourceIdentity.source(source, inputs));
            write(root, "publishing/consumer-smoke/gradle.lockfile", lock);
            Files.writeString(root.resolve("SPEC_PIN.md"), "wrong"); reject(source::integrity);
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
