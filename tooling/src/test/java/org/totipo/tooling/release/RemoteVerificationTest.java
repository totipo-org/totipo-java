package org.totipo.tooling.release;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.totipo.tooling.publication.PublicationTest;
import static org.totipo.tooling.release.ReleaseTest.*;
import static org.totipo.tooling.release.RemoteArtifactVerifier.*;

/** Permanent offline polling/acquisition tests. Fake requests have no live fallback; no sleeps. */
public final class RemoteVerificationTest {
    private RemoteVerificationTest() {}
    static final byte[] ARMOR = bytes("-----BEGIN PGP SIGNATURE-----\nfixture\n-----END PGP SIGNATURE-----");
    static final class Fake implements Transport, Clock, Sleeper {
        long time;
        final Map<URI, byte[]> bodies = new LinkedHashMap<>();
        final Map<URI, Integer> absent = new LinkedHashMap<>();
        final List<String> requests = new ArrayList<>();
        final List<Long> sleeps = new ArrayList<>();
        URI broken; int status = 200; long acquisition; boolean backwards;
        @Override public long nanos() { return backwards ? --time : time; }
        @Override public void sleep(Duration duration) { sleeps.add(duration.toSeconds()); time += duration.toNanos(); }
        @Override public Download get(URI uri, int bound, Duration timeout) throws IOException {
            if (!bodies.containsKey(uri)) throw new AssertionError("Unexpected offline URL: " + uri);
            requests.add(uri.toString());
            ok(bound == (uri.toString().endsWith(".asc") ? MAX_SIGNATURE : MAX_ARTIFACT));
            ok(!timeout.isZero() && timeout.compareTo(REQUEST_TIMEOUT) <= 0);
            time += acquisition;
            if (uri.equals(broken)) {
                if (status == -1) throw new IOException("synthetic network or TLS failure");
                if (status != 200) return new Download(status, null, false);
            }
            int left = absent.getOrDefault(uri, 0);
            if (left > 0) { absent.put(uri, left - 1); return new Download(404, null, false); }
            byte[] body = bodies.get(uri);
            if (body.length > bound) throw new IOException("byte bound");
            String text = new String(body, java.nio.charset.StandardCharsets.US_ASCII);
            return new Download(200, SourceFiles.sha256(body), text.contains("-----BEGIN PGP SIGNATURE-----") && text.contains("-----END PGP SIGNATURE-----"));
        }
    }
    static Fake fake(Path root) throws Exception {
        var manifest = AcceptedArtifacts.fresh(root.resolve("build/publication-manifest.json"), root.resolve("build/repository"), "0.2.0");
        Fake fake = new Fake();
        for (var a : manifest.artifacts()) {
            URI uri = URI.create(RemoteReleaseState.CENTRAL + a.path());
            fake.bodies.put(uri, Files.readAllBytes(root.resolve("build/repository").resolve(a.path())));
            fake.bodies.put(URI.create(uri + ".asc"), ARMOR.clone());
        }
        return fake;
    }
    static Verified verify(Path root, Fake fake, long seconds) throws Exception {
        return verify(root, fake, seconds, Files.readAllBytes(root.resolve("build/publication-manifest.json")));
    }
    static Verified verify(Path root, Fake fake, long seconds, byte[] expected) throws Exception {
        return RemoteArtifactVerifier.verify(root.resolve("build/publication-manifest.json"), root.resolve("build/repository"), "0.2.0", expected,
                fake, fake, fake, Duration.ofSeconds(seconds));
    }
    static void failure(Path root, Fake fake, long seconds, Failure failure) throws Exception {
        try { verify(root, fake, seconds); throw new AssertionError("Expected " + failure); }
        catch (VerificationException e) { ok(e.failure() == failure); ok(e.pending() > 0); }
    }
    static void polling(Path root) throws Exception {
        Fake fake = fake(root); var verified = verify(root, fake, 2400);
        ok(verified.hashes().size() == 10 && verified.version().equals("0.2.0")); ok(fake.sleeps.isEmpty());
        List<String> all = fake.bodies.keySet().stream().map(URI::toString).toList(); ok(fake.requests.equals(all));
        URI first = fake.bodies.keySet().iterator().next(), signature = URI.create(first + ".asc");
        fake = fake(root); fake.absent.put(first, 1); verify(root, fake, 2400);
        var expected = new ArrayList<>(all); expected.remove(1); expected.add(first.toString()); expected.add(signature.toString());
        ok(fake.requests.equals(expected)); ok(fake.sleeps.equals(List.of(10L)));
        fake = fake(root); fake.absent.put(signature, 1); verify(root, fake, 2400);
        expected = new ArrayList<>(all); expected.add(first.toString()); expected.add(signature.toString());
        ok(fake.requests.equals(expected)); ok(fake.sleeps.equals(List.of(10L)));
        fake = fake(root); List<URI> urls = new ArrayList<>(fake.bodies.keySet());
        fake.absent.put(first, 4); fake.absent.put(urls.get(4), 2); fake.absent.put(urls.get(9), 3);
        verify(root, fake, 2400); ok(fake.sleeps.equals(List.of(10L, 20L, 40L, 60L)));
        expected = new ArrayList<>();
        for (int poll = 0; poll < 5; poll++) for (int i = 0; i < urls.size(); i += 2) {
            int ready = i == 0 ? 4 : i == 4 ? 2 : i == 8 ? 3 : 0;
            if (poll > ready) continue;
            expected.add(urls.get(i).toString());
            if ((i == 0 && poll < 4) || (i == 4 && poll < 2)) continue;
            expected.add(urls.get(i + 1).toString());
        }
        ok(fake.requests.equals(expected));
        fake = fake(root); fake.absent.put(signature, 100); failure(root, fake, 25, Failure.PROPAGATION_TIMEOUT);
        ok(fake.sleeps.equals(List.of(10L, 15L))); ok(fake.requests.size() == 22);
        fake = fake(root); fake.acquisition = Duration.ofSeconds(2).toNanos(); failure(root, fake, 1, Failure.PROPAGATION_TIMEOUT); ok(fake.requests.size() == 1);
        fake = fake(root); fake.acquisition = Duration.ofSeconds(2).toNanos(); fake.bodies.put(first, bytes("wrong")); failure(root, fake, 1, Failure.PERMANENT_MISMATCH);
        fake = fake(root); fake.absent.put(first, 1); fake.bodies.put(first, bytes("wrong")); failure(root, fake, 2400, Failure.PERMANENT_MISMATCH);
        ok(fake.sleeps.equals(List.of(10L))); ok(fake.requests.size() == 20);
        fake = fake(root); fake.bodies.put(urls.get(4), bytes("wrong")); failure(root, fake, 2400, Failure.PERMANENT_MISMATCH); ok(fake.requests.size() == 5);
        fake = fake(root); fake.bodies.put(signature, bytes("-----BEGIN PGP SIGNATURE-----")); failure(root, fake, 2400, Failure.INVALID_SIGNATURE); ok(fake.requests.size() == 2);
        for (int status : new int[]{-1, 301, 302, 401, 403, 429, 500, 503}) {
            fake = fake(root); fake.broken = urls.get(4); fake.status = status; failure(root, fake, 2400, Failure.ACQUISITION_FAILED); ok(fake.requests.size() == 5 && fake.sleeps.isEmpty());
        }
        fake = fake(root); fake.bodies.put(signature, new byte[MAX_SIGNATURE + 1]); failure(root, fake, 2400, Failure.ACQUISITION_FAILED);
        fake = fake(root); fake.backwards = true; Fake nonmonotonic = fake; reject(() -> verify(root, nonmonotonic, 2400)); ok(fake.requests.isEmpty());
        fake = fake(root); Fake unchecked = fake; reject(() -> verify(root, unchecked, 2400, bytes("{}"))); ok(fake.requests.isEmpty());
        byte[] accepted = Files.readAllBytes(root.resolve("build/publication-manifest.json"));
        String differentIndex = new String(accepted, java.nio.charset.StandardCharsets.UTF_8).replaceFirst("\"repositorySha256\": \"[a-f0-9]+\"", "\"repositorySha256\": \"" + "f".repeat(64) + "\"");
        verify(root, fake(root), 2400, bytes(differentIndex));
        String changed = new String(accepted, java.nio.charset.StandardCharsets.UTF_8).replaceFirst("\"sha256\": \"[a-f0-9]+\"", "\"sha256\": \"" + "f".repeat(64) + "\"");
        fake = fake(root); Fake mismatch = fake; reject(() -> verify(root, mismatch, 2400, bytes(changed))); ok(fake.requests.isEmpty());
        Path index = root.resolve("build/repository/index-extra"); Files.writeString(index, "changed");
        fake = unchecked; reject(() -> verify(root, unchecked, 2400)); ok(unchecked.requests.isEmpty()); Files.delete(index);
    }
    static final class Subscription implements Flow.Subscription {
        int cancelled, requests;
        @Override public void request(long n) { requests++; }
        @Override public void cancel() { cancelled++; }
    }
    static void acquisition() throws Exception {
        for (boolean signature : List.of(false, true)) {
            var body = new HttpQueries.DigestBody(64, signature); var sub = new Subscription(); body.onSubscribe(sub);
            body.onNext(List.of(ByteBuffer.wrap(new byte[40]), ByteBuffer.wrap(new byte[25])));
            ok(sub.cancelled == 1); rejectFuture(body);
        }
        var body = new HttpQueries.DigestBody(MAX_ARTIFACT, false); var sub = new Subscription(); body.onSubscribe(sub);
        byte[] block = new byte[1024 * 1024];
        for (int i = 0; i < 64; i++) body.onNext(List.of(ByteBuffer.wrap(block)));
        ok(sub.cancelled == 0); body.onNext(List.of(ByteBuffer.wrap(new byte[1]))); ok(sub.cancelled == 1);
        body = new HttpQueries.DigestBody(MAX_SIGNATURE, true); sub = new Subscription(); body.onSubscribe(sub);
        for (byte b : ARMOR) body.onNext(List.of(ByteBuffer.wrap(new byte[]{b})));
        body.onComplete(); var result = body.getBody().toCompletableFuture().get(); ok(result.signatureMarkers() && result.sha256().equals(SourceFiles.sha256(ARMOR)));
        var truncated = new HttpQueries.DigestBody(100, false); sub = new Subscription(); truncated.onSubscribe(sub); truncated.onError(new IOException("truncated"));
        rejectFuture(truncated); ok(sub.cancelled == 1);
        var advertisedOversize = new HttpQueries.DigestBody(100, false); advertisedOversize.fail(); sub = new Subscription(); advertisedOversize.onSubscribe(sub);
        ok(sub.cancelled == 1); rejectFuture(advertisedOversize);
        for (long declared : new long[]{-1, 1, 1000}) {
            var misleading = new HttpQueries.DigestBody(100, false); var subscription = new Subscription();
            misleading.headers(200, java.util.OptionalLong.of(declared)); misleading.onSubscribe(subscription);
            misleading.onNext(List.of(ByteBuffer.wrap(new byte[101]))); misleading.onComplete();
            rejectFuture(misleading); ok(subscription.cancelled > 0);
        }
        var shortBody = new HttpQueries.DigestBody(100, false); var subscription = new Subscription();
        shortBody.headers(200, java.util.OptionalLong.of(50)); shortBody.onSubscribe(subscription);
        shortBody.onNext(List.of(ByteBuffer.wrap(new byte[49]))); shortBody.onComplete(); rejectFuture(shortBody); ok(subscription.cancelled == 1);
        for (int status : new int[]{301, 401, 403, 429, 500}) {
            var rejected = new HttpQueries.DigestBody(100, false); rejected.headers(status, java.util.OptionalLong.empty());
            subscription = new Subscription(); rejected.onSubscribe(subscription); rejectFuture(rejected); ok(subscription.cancelled == 1);
        }
        for (String url : List.of("http://repo.maven.apache.org/maven2/x", "https://other.test/maven2/x", "https://repo.maven.apache.org:443/maven2/x", "https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.jar?x=1", "https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.jar%2f")) reject(() -> HttpQueries.artifactEndpoint(URI.create(url)));
        ok(!HttpQueries.artifactEndpoint(URI.create(RemoteReleaseState.CENTRAL + "org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.jar")));
    }
    static void rejectFuture(HttpQueries.DigestBody body) throws Exception {
        try { body.getBody().toCompletableFuture().get(); throw new AssertionError("Expected body failure"); }
        catch (java.util.concurrent.ExecutionException expected) { ok(true); }
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("totipo-remote-fixture-");
        try { PublicationTest.acceptedFixture(root); polling(root); acquisition(); System.out.println("RemoteVerificationTest PASS"); }
        finally { try (var paths = Files.walk(root)) { for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p); } }
    }
}
