package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.totipo.tooling.publication.PublicationTest;
import org.totipo.tooling.publication.PublicationManifest;

/** Permanent offline, credential-free regression runner; no Python or GPG installation needed. */
public final class AuthorizationTest {
    private static int assertions;
    private static final String VERSION = "0.2.0";
    private static final byte[] SIGNATURE = bytes("-----BEGIN PGP SIGNATURE-----\nfixture\n-----END PGP SIGNATURE-----\n");
    private AuthorizationTest() {}
    @FunctionalInterface interface Action { void run() throws Exception; }
    static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    static void check(boolean ok) { if (!ok) throw new AssertionError("Assertion failed"); assertions++; }
    static void reject(Action action) throws Exception {
        try { action.run(); } catch (IOException | IllegalArgumentException e) { assertions++; return; }
        throw new AssertionError("Unsafe evidence accepted");
    }
    static Path manifest(Path root) { return root.resolve("build/publication-manifest.json"); }
    static Path staging(Path root) { return root.resolve("build/repository"); }
    static PublicationManifest accepted(Path root) throws Exception {
        return AcceptedArtifacts.fresh(manifest(root), staging(root), VERSION);
    }
    static void write(Path file, byte[] bytes) throws IOException { Files.createDirectories(file.getParent()); Files.write(file, bytes); }
    static Path input(Path root, PublicationManifest.Artifact a) {
        return root.resolve(a.module() + "/build/signing/" + AcceptedArtifacts.signingFilename(a));
    }
    static void signatures(Path root) throws Exception {
        for (var a : accepted(root).artifacts()) {
            Path input = input(root, a);
            write(input, Files.readAllBytes(staging(root).resolve(a.path())));
            write(Path.of(input + ".asc"), SIGNATURE);
        }
    }
    static void signed(Path root) throws Exception { AcceptedArtifacts.signed(root, manifest(root), staging(root), VERSION); }
    static void remove(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
    static void inventory(Path root) throws Exception {
        byte[] original = Files.readAllBytes(manifest(root));
        check(AcceptedArtifacts.inventory(manifest(root), staging(root), VERSION).size() == 10);
        AcceptedArtifacts.compare(manifest(root), staging(root), VERSION, original); assertions++;
        // Field and artifact ordering do not define identity.
        var a = accepted(root);
        // Cross-build index timestamps/fingerprint may differ; ten canonical artifact hashes must agree.
        AcceptedArtifacts.compare(manifest(root), staging(root), VERSION,
                new PublicationManifest(VERSION, "0".repeat(64), a.artifacts()).bytes()); assertions++;
        var reversed = new java.util.ArrayList<>(a.artifacts()); java.util.Collections.reverse(reversed);
        AcceptedArtifacts.compare(manifest(root), staging(root), VERSION, new PublicationManifest(VERSION, a.repositorySha256(), reversed).bytes()); assertions++;
        String json = new String(original, StandardCharsets.UTF_8);
        String reordered = "{\"schema\":1," + json.substring(1).replace("  \"schema\": 1,\n", "");
        AcceptedArtifacts.compare(manifest(root), staging(root), VERSION, bytes(reordered)); assertions++;
        for (String bad : new String[]{json + "{}", json + "!", "{", json.replaceFirst("\\{", "{\"schema\":1,"),
                json.replace("\"role\": \"binary\"", "\"role\": \"pom\""), json.replace("org.totipo", "other.group"),
                json.replace("0.2.0", "0.2.1"), json.replace(a.artifacts().getFirst().sha256(), "0".repeat(64))})
            reject(() -> AcceptedArtifacts.compare(manifest(root), staging(root), VERSION, bytes(bad)));
        for (int size : new int[]{9, 11}) {
            var list = new java.util.ArrayList<>(a.artifacts());
            if (size == 9) list.removeLast(); else list.add(list.getFirst());
            reject(() -> AcceptedArtifacts.compare(manifest(root), staging(root), VERSION, new PublicationManifest(VERSION, a.repositorySha256(), list).bytes()));
        }
        // Fresh-byte and whole-repository checks are distinct authorities.
        Path artifact = staging(root).resolve(a.artifacts().getFirst().path());
        byte[] old = Files.readAllBytes(artifact); Files.write(artifact, bytes("changed"));
        reject(() -> AcceptedArtifacts.inventory(manifest(root), staging(root), VERSION)); Files.write(artifact, old);
        Path index = staging(root).resolve("org/totipo/totipo-core/maven-metadata.xml");
        old = Files.readAllBytes(index); Files.write(index, bytes("changed"));
        reject(() -> AcceptedArtifacts.inventory(manifest(root), staging(root), VERSION)); Files.write(index, old);
        Path sidecar = Path.of(artifact + ".sha256"); Files.write(sidecar, bytes("0".repeat(64)));
        reject(() -> AcceptedArtifacts.inventory(manifest(root), staging(root), VERSION)); Files.delete(sidecar);
        var wrong = a.artifacts().stream().map(x -> new PublicationManifest.Artifact(x.module(), x.group(), x.artifact(), x.version(), x.role(), x.suffix(), x.path(), "0".repeat(64))).toList();
        reject(() -> AcceptedArtifacts.compare(manifest(root), staging(root), VERSION, new PublicationManifest(VERSION, a.repositorySha256(), wrong).bytes()));
        Files.write(manifest(root), new PublicationManifest(VERSION, a.repositorySha256(), wrong).bytes());
        reject(() -> AcceptedArtifacts.inventory(manifest(root), staging(root), VERSION)); Files.write(manifest(root), original);
    }
    static void signing(Path root) throws Exception {
        signatures(root); signed(root); assertions++;
        var accepted = accepted(root);
        for (var a : accepted.artifacts()) {
            Path input = input(root, a), signature = Path.of(input + ".asc");
            Files.delete(signature); reject(() -> signed(root)); Files.write(signature, SIGNATURE);
            Path duplicate = root.resolve(a.module() + "/build/duplicate/" + signature.getFileName());
            write(duplicate, SIGNATURE); reject(() -> signed(root)); Files.delete(duplicate);
            byte[] unsigned = Files.readAllBytes(input); Files.write(input, bytes("wrong"));
            reject(() -> signed(root)); Files.write(input, unsigned);
            for (byte[] bad : new byte[][]{bytes(""), bytes("-----BEGIN PGP SIGNATURE-----"), bytes("-----END PGP SIGNATURE-----")}) {
                Files.write(signature, bad); reject(() -> signed(root));
            }
            Files.write(signature, SIGNATURE);
            Files.delete(input); Files.createDirectory(input); reject(() -> signed(root)); Files.delete(input); Files.write(input, unsigned);
            Files.delete(signature); Files.createDirectory(signature); reject(() -> signed(root)); Files.delete(signature); Files.write(signature, SIGNATURE);
            Path escaped = root.resolve("outside-input"); Files.write(escaped, unsigned);
            Files.delete(input); Files.createSymbolicLink(input, escaped); reject(() -> signed(root)); Files.delete(input); Files.write(input, unsigned); Files.delete(escaped);
            Path wrongModule = root.resolve((a.module().equals("core") ? "storage-nio" : "core") + "/build/wrong/" + signature.getFileName());
            Files.move(signature, createParent(wrongModule)); reject(() -> signed(root)); Files.move(wrongModule, signature);
            if (a.role().equals("pom") || a.role().equals("module")) {
                Path wrongName = signature.resolveSibling(Path.of(a.path()).getFileName() + ".asc");
                Files.move(signature, wrongName); reject(() -> signed(root)); Files.move(wrongName, signature);
            }
            Files.write(signature, new byte[65_537]); reject(() -> signed(root)); Files.write(signature, SIGNATURE);
        }
        var first = accepted.artifacts().getFirst();
        Path staged = staging(root).resolve(first.path()); byte[] old = Files.readAllBytes(staged);
        Files.write(staged, bytes("changed")); reject(() -> signed(root)); Files.write(staged, old);
        Path link = root.resolve("core/build/escape"); Files.createSymbolicLink(link, staging(root)); reject(() -> signed(root)); Files.delete(link);
        Path deep = root.resolve("core/build"); for (int i = 0; i < 34; i++) deep = deep.resolve("deep");
        Files.createDirectories(deep); reject(() -> signed(root)); remove(root.resolve("core/build/deep"));
        Path oversizedInput = input(root, first);
        byte[] reviewedInput = Files.readAllBytes(oversizedInput);
        try (var channel = java.nio.channels.FileChannel.open(oversizedInput, java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(64L * 1024 * 1024); channel.write(java.nio.ByteBuffer.wrap(new byte[]{1}));
        }
        reject(() -> signed(root)); Files.write(oversizedInput, reviewedInput);
        Path excessive = root.resolve("core/build/excessive"); Files.createDirectories(excessive);
        for (int i = 0; i < 20_001; i++) Files.createFile(excessive.resolve("entry-" + i));
        reject(() -> signed(root)); remove(excessive);
        signed(root); assertions++;
    }
    static Path createParent(Path path) throws IOException { Files.createDirectories(path.getParent()); return path; }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("authorization-test-");
        try { PublicationTest.acceptedFixture(root); inventory(root); signing(root); }
        finally { remove(root); }
        System.out.println("Signing/inventory tests: " + assertions + " assertions passed");
    }
}
