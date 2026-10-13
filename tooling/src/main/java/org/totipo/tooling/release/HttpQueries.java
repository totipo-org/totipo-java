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

/** Optional anonymous read-only adapter. Never used by the J3A CLI or qualification tests. */
public final class HttpQueries implements RemoteReleaseState.Http {
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
        require("api.github.com".equals(uri.getHost()), "Unapproved inspection host");
        var release = Pattern.compile("/repos/([A-Za-z0-9_-]+)/([A-Za-z0-9_.-]+)/releases/tags/v([^/]+)").matcher(path);
        if (release.matches()) {
            require(!SetNames.invalidRepositoryName(release.group(2)) && ReleaseInputs.validVersion(release.group(3)) && uri.getQuery() == null, "Invalid release endpoint");
        } else {
            var history = Pattern.compile("/repos/([A-Za-z0-9_-]+)/([A-Za-z0-9_.-]+)/actions/runs/[1-9][0-9]{0,19}/attempts/([1-9][0-9]{0,2})/jobs").matcher(path);
            require(history.matches() && !SetNames.invalidRepositoryName(history.group(2))
                    && Integer.parseInt(history.group(3)) <= RemoteReleaseState.MAX_ATTEMPTS
                    && uri.getQuery() != null && uri.getQuery().matches("per_page=100&page=[1-9][0-9]{0,2}"), "Invalid history endpoint");
            require(Integer.parseInt(uri.getQuery().substring("per_page=100&page=".length())) <= RemoteReleaseState.MAX_PAGES, "History page budget");
        }
        return RemoteReleaseState.GITHUB.bytes();
    }
    private static final class SetNames {
        private SetNames() {}
        static boolean invalidRepositoryName(String value) { return value.equals(".") || value.equals(".."); }
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
