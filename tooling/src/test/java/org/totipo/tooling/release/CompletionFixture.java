package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import static org.totipo.tooling.release.ReleaseFinalizer.*;

/** Migration-only Python oracle bridge. Not installed, no live network or nonfixture mutations. */
public final class CompletionFixture {
    private CompletionFixture() {}
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[1]);
        switch (args[0]) {
            case "apply" -> {
                var props = properties(Path.of(args[3]));
                GitQueries git = (query, version) -> switch (query) {
                    case HEAD -> props.getProperty("head", FinalizationTest.SHA);
                    case BRANCH -> props.getProperty("branch", "main");
                    case STATUS -> props.getProperty("status", "");
                    default -> throw new AssertionError("Unexpected preparation query");
                };
                try { var reviewed = PreparationApplication.review(root, git, args[2]); PreparationApplication.apply(root, reviewed, git); System.out.println("PASS"); }
                catch (IOException | RuntimeException rejected) { System.out.println("FAIL"); }
            }
            case "remote" -> remote(root, Path.of(args[2]), Long.parseLong(args[3]));
            case "finalize" -> finalizeFixture(root, Path.of(args[2]));
            default -> throw new AssertionError("Unexpected migration command");
        }
    }
    static Properties properties(Path path) throws IOException {
        var props = new Properties(); try (var in = Files.newInputStream(path)) { props.load(in); } return props;
    }
    static void remote(Path root, Path script, long seconds) throws Exception {
        List<String> rows = Files.readAllLines(script), trace = new ArrayList<>(); int[] cursor = {0}; long[] nanos = {0};
        try {
            RemoteArtifactVerifier.verify(root.resolve("build/publication-manifest.json"), root.resolve("build/repository"), "0.2.0",
                    Files.readAllBytes(root.resolve("expected.json")), (uri, bound, timeout) -> {
                        if (cursor[0] >= rows.size()) throw new AssertionError("Unexpected extra fixture request");
                        String[] fields = rows.get(cursor[0]++).split("\t", -1);
                        if (!fields[0].equals(uri.toString())) throw new AssertionError("Unexpected fixture URL");
                        trace.add("REQUEST " + uri); nanos[0] += Duration.ofSeconds(Long.parseLong(fields[3])).toNanos();
                        int status = Integer.parseInt(fields[1]);
                        if (status == -1) throw new IOException("synthetic network failure");
                        byte[] body = Base64.getDecoder().decode(fields[2]);
                        if (body.length > bound) throw new IOException("resource bound");
                        String text = new String(body, java.nio.charset.StandardCharsets.US_ASCII);
                        return new RemoteArtifactVerifier.Download(status, SourceFiles.sha256(body), text.contains("-----BEGIN PGP SIGNATURE-----") && text.contains("-----END PGP SIGNATURE-----"));
                    }, () -> nanos[0], duration -> { trace.add("SLEEP " + duration.toSeconds()); nanos[0] += duration.toNanos(); }, Duration.ofSeconds(seconds));
            trace.add("PASS");
        } catch (RemoteArtifactVerifier.VerificationException rejected) { trace.add("FAIL " + rejected.failure() + " " + rejected.pending()); }
        catch (IOException | RuntimeException rejected) { trace.add("FAIL LOCAL"); }
        trace.forEach(System.out::println);
    }
    static Tag tag(String kind, boolean remote) {
        return switch (kind) {
            case "absent" -> null;
            case "valid" -> FinalizationTest.tag();
            case "wrong" -> new Tag(FinalizationTest.OBJECT, "f".repeat(40), "tag", FinalizationTest.tag().annotation());
            case "lightweight" -> new Tag(FinalizationTest.SHA, FinalizationTest.SHA, "commit", "");
            case "missing-peeled" -> new Tag(FinalizationTest.OBJECT, null, "tag", FinalizationTest.tag().annotation());
            case "conflict" -> new Tag(remote ? "c".repeat(40) : FinalizationTest.OBJECT, FinalizationTest.SHA, "tag", FinalizationTest.tag().annotation());
            case "annotation" -> new Tag(FinalizationTest.OBJECT, FinalizationTest.SHA, "tag", "unexpected\n");
            default -> throw new AssertionError("Unknown fixture tag");
        };
    }
    static void finalizeFixture(Path root, Path file) throws Exception {
        var p = properties(file);
        var expected = Expected.read(new SourceFiles(root), new ReleaseInputs("0.2.0", FinalizationTest.SHA));
        var pre = FinalizationTest.prerequisites(root);
        var record = p.containsKey("release") ? RemoteReleaseState.decodeRelease(Base64.getDecoder().decode(p.getProperty("release")), "0.2.0") : FinalizationTest.absent();
        var fake = new FinalizationTest.Fake(new Observed(p.getProperty("head", FinalizationTest.SHA), p.getProperty("main", FinalizationTest.SHA), p.getProperty("status", ""), FinalizationTest.convention(),
                tag(p.getProperty("local", "absent"), false), tag(p.getProperty("remote", "absent"), true), record));
        if (p.containsKey("fail")) fake.fail = p.getProperty("fail");
        fake.effectsBeforeFailure = Boolean.parseBoolean(p.getProperty("effects", "false"));
        for (int attempt = 0; attempt < Integer.parseInt(p.getProperty("attempts", "1")); attempt++) {
            int start = fake.calls.size();
            try { System.out.println(execute(expected, pre, fake).action()); }
            catch (IOException rejected) { System.out.println("UNCERTAIN"); }
            System.out.println(String.join(",", fake.calls.subList(start, fake.calls.size())));
            fake.fail = null;
        }
        System.out.println("BODY " + Base64.getEncoder().encodeToString(expected.body().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
