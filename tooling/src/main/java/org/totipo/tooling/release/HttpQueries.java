package org.totipo.tooling.release;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import static org.totipo.tooling.release.SourceFiles.require;

/** Anonymous canonical Central acquisition with bounded bodies, deadlines and no redirects. */
public final class HttpQueries implements RemoteReleaseState.Http, RemoteArtifactVerifier.Transport {
    public static final Duration DEADLINE = Duration.ofSeconds(20);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(DEADLINE)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    static int endpointLimit(URI uri) throws IOException {
        require("https".equals(uri.getScheme()) && uri.getPort() == -1 && uri.getUserInfo() == null
                && uri.getFragment() == null && uri.getRawPath().equals(uri.getPath()), "Unapproved inspection endpoint");
        String path = uri.getPath();
        if ("repo.maven.apache.org".equals(uri.getHost()) && uri.getQuery() == null) {
            var m = Pattern.compile("/maven2/org/totipo/(totipo-(?:core|storage-nio))/([^/]+)/\\1-\\2\\.pom").matcher(path);
            require(m.matches() && ReleaseInputs.validVersion(m.group(2)), "Unapproved Central coordinate endpoint");
            return 65_536;
        }
        throw new IOException("Unapproved inspection host");
    }
    @Override public RemoteReleaseState.Response get(URI uri) throws IOException {
        int limit = endpointLimit(uri);
        var request = HttpRequest.newBuilder(uri).timeout(DEADLINE).header("User-Agent", "totipo-java-release")
                .header("Accept", "application/json").GET().build();
        var future = client.sendAsync(request, info -> new BoundedBody(limit));
        try {
            var response = future.get(DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
            require(response.statusCode() == 200 || response.statusCode() == 404, "Unexpected inspection HTTP status: " + response.statusCode());
            return new RemoteReleaseState.Response(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException("Inspection request interrupted");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IOException("Inspection request failed or deadline exceeded");
        } finally { future.cancel(true); }
    }
    /** Canonical artifact acquisition, separate from coordinate-absence inspection. */
    static boolean artifactEndpoint(URI uri) throws IOException {
        require("https".equals(uri.getScheme()) && "repo.maven.apache.org".equals(uri.getHost())
                && uri.getPort() == -1 && uri.getUserInfo() == null && uri.getQuery() == null
                && uri.getFragment() == null && uri.getRawPath().equals(uri.getPath()), "Unapproved artifact endpoint");
        var match = Pattern.compile("/maven2/org/totipo/(totipo-(?:core|storage-nio))/([^/]+)/\\1-\\2(\\.jar|-sources\\.jar|-javadoc\\.jar|\\.pom|\\.module)(\\.asc)?").matcher(uri.getPath());
        require(match.matches() && ReleaseInputs.validVersion(match.group(2)), "Unapproved artifact path");
        return match.group(4) != null;
    }
    @Override public RemoteArtifactVerifier.Download get(URI uri, int maximumBytes, Duration timeout) throws IOException {
        boolean signature = artifactEndpoint(uri);
        require(maximumBytes == (signature ? RemoteArtifactVerifier.MAX_SIGNATURE : RemoteArtifactVerifier.MAX_ARTIFACT)
                && !timeout.isNegative() && !timeout.isZero() && timeout.compareTo(DEADLINE) <= 0, "Invalid acquisition limits");
        var body = new DigestBody(maximumBytes, signature);
        var request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        var future = client.sendAsync(request, info -> {
            body.headers(info.statusCode(), info.headers().firstValueAsLong("Content-Length"));
            return body;
        });
        try {
            var response = future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            require(response.statusCode() == 200 || response.statusCode() == 404, "Unexpected artifact HTTP status");
            var data = response.body();
            return new RemoteArtifactVerifier.Download(response.statusCode(), data.sha256(), data.signatureMarkers());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IOException("Artifact acquisition interrupted");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | RuntimeException error) {
            throw new IOException("Artifact acquisition failed or deadline exceeded");
        } finally { body.fail(); future.cancel(true); }
    }
    /** Incremental digest: artifact memory is O(1); only bounded signature bytes are retained. */
    static final class DigestBody implements HttpResponse.BodySubscriber<RemoteArtifactVerifier.Download> {
        private final int limit;
        private final java.security.MessageDigest digest;
        private final ByteArrayOutputStream signature;
        private final CompletableFuture<RemoteArtifactVerifier.Download> completion = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int count;
        private long expectedLength = -1;
        synchronized void headers(int status, java.util.OptionalLong length) {
            if (status != 200 && status != 404) { fail(); return; }
            if (length.isPresent()) {
                expectedLength = length.getAsLong();
                if (expectedLength < 0 || expectedLength > limit) fail();
            }
        }
        DigestBody(int limit, boolean signature) {
            this.limit = limit; this.signature = signature ? new ByteArrayOutputStream() : null;
            try { digest = java.security.MessageDigest.getInstance("SHA-256"); }
            catch (java.security.NoSuchAlgorithmException error) { throw new AssertionError(error); }
        }
        synchronized void fail() {
            if (subscription != null) subscription.cancel();
            completion.completeExceptionally(new IOException("Incomplete or oversized artifact response"));
        }
        @Override public CompletionStage<RemoteArtifactVerifier.Download> getBody() { return completion; }
        @Override public synchronized void onSubscribe(Flow.Subscription value) {
            if (subscription != null || completion.isDone()) { value.cancel(); return; }
            subscription = value; value.request(1);
        }
        @Override public synchronized void onNext(List<ByteBuffer> buffers) {
            if (completion.isDone()) return;
            for (var buffer : buffers) {
                int size = buffer.remaining();
                if (size > limit - count) { fail(); return; }
                count += size;
                if (signature != null) {
                    byte[] bytes = new byte[size]; buffer.get(bytes); digest.update(bytes); signature.writeBytes(bytes);
                } else digest.update(buffer);
            }
            subscription.request(1);
        }
        @Override public synchronized void onError(Throwable error) { fail(); }
        @Override public synchronized void onComplete() {
            if (expectedLength >= 0 && count != expectedLength) { fail(); return; }
            String text = signature == null ? "" : signature.toString(java.nio.charset.StandardCharsets.US_ASCII);
            completion.complete(new RemoteArtifactVerifier.Download(200, java.util.HexFormat.of().formatHex(digest.digest()),
                    text.contains("-----BEGIN PGP SIGNATURE-----") && text.contains("-----END PGP SIGNATURE-----")));
        }
    }
    /** Acquisition bound, not a truncating parser. Cancels transport immediately on overflow. */
    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> completion = new CompletableFuture<>();
        private Flow.Subscription subscription;
        BoundedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return completion; }
        @Override public void onSubscribe(Flow.Subscription value) {
            if (subscription != null) { value.cancel(); return; }
            subscription = value; value.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (completion.isDone()) return;
            for (var buffer : buffers) {
                if (buffer.remaining() > limit - output.size()) {
                    subscription.cancel(); completion.completeExceptionally(new IOException("HTTP response byte limit")); return;
                }
                byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); output.writeBytes(bytes);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { completion.completeExceptionally(new IOException("HTTP body acquisition failed")); }
        @Override public void onComplete() { completion.complete(output.toByteArray()); }
    }
}
