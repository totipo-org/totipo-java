package org.totipo.tooling.release;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.totipo.tooling.publication.PublicationManifest;
import static org.totipo.tooling.release.SourceFiles.require;

/** Exact bytes and armor presence only. Neither signer trust nor finalization authority. */
public final class RemoteArtifactVerifier {
    private RemoteArtifactVerifier() {}
    // J2B FilesSupport.MAX_FILE / J3B.1 EvidenceFiles signature bound.
    public static final int MAX_ARTIFACT = 64 * 1024 * 1024, MAX_SIGNATURE = 65_536;
    public static final Duration TIMEOUT = Duration.ofSeconds(2400), REQUEST_TIMEOUT = Duration.ofSeconds(20);
    @FunctionalInterface public interface Clock { long nanos(); }
    @FunctionalInterface public interface Sleeper { void sleep(Duration duration) throws IOException; }
    @FunctionalInterface public interface Transport {
        Download get(URI uri, int maximumBytes, Duration timeout) throws IOException;
    }
    /** Produced only after complete bounded acquisition by the trusted transport. */
    public record Download(int status, String sha256, boolean signatureMarkers) {}
    public enum Failure { PROPAGATION_TIMEOUT, PERMANENT_MISMATCH, INVALID_SIGNATURE, ACQUISITION_FAILED }
    public static final class VerificationException extends IOException {
        private static final long serialVersionUID = 1L;
        private final Failure failure;
        private final int pending;
        VerificationException(Failure failure, int pending) {
            super("Remote verification failed: " + failure + "; pending=" + pending);
            this.failure = failure; this.pending = pending;
        }
        public Failure failure() { return failure; }
        public int pending() { return pending; }
    }
    /** Cannot be constructed from an unchecked caller list or boolean. */
    public static final class Verified {
        private final String version;
        private final Map<String, String> hashes;
        private Verified(String version, Map<String, String> hashes) {
            this.version = version; this.hashes = Collections.unmodifiableMap(new LinkedHashMap<>(hashes));
        }
        public String version() { return version; }
        public Map<String, String> hashes() { return hashes; }
    }
    public static Verified verify(Path manifest, Path staging, String version, byte[] expectedInventory,
                                  Transport http, Clock clock, Sleeper sleeper, Duration timeout) throws IOException {
        require(timeout != null && !timeout.isZero() && !timeout.isNegative() && timeout.compareTo(TIMEOUT) <= 0, "Invalid propagation budget");
        var accepted = AcceptedArtifacts.fresh(manifest, staging, version);
        var expected = PublicationManifest.parse(expectedInventory, version);
        var hashes = hashes(accepted);
        require(hashes.equals(hashes(expected)), "Fresh artifact inventory differs from preflight");
        var pending = new LinkedHashMap<>(hashes);
        // Elapsed subtraction supports nanoTime wraparound. Clock must be monotonic.
        long start = clock.nanos(), previous = start, budget = timeout.toNanos();
        long delay = 10;
        while (!pending.isEmpty()) {
            for (var entry : new LinkedHashMap<>(pending).entrySet()) {
                previous = tick(clock, previous);
                Duration request = remaining(start, previous, budget, pending.size());
                Download artifact = acquire(http, URI.create(RemoteReleaseState.CENTRAL + entry.getKey()), MAX_ARTIFACT, request, pending.size(), clock, start, budget);
                previous = tick(clock, previous);
                if (artifact.status() == 200 && !entry.getValue().equals(artifact.sha256())) throw new VerificationException(Failure.PERMANENT_MISMATCH, pending.size());
                remaining(start, previous, budget, pending.size());
                if (artifact.status() == 404) continue;
                request = remaining(start, previous, budget, pending.size());
                Download signature = acquire(http, URI.create(RemoteReleaseState.CENTRAL + entry.getKey() + ".asc"), MAX_SIGNATURE, request, pending.size(), clock, start, budget);
                previous = tick(clock, previous);
                if (signature.status() == 200 && !signature.signatureMarkers()) throw new VerificationException(Failure.INVALID_SIGNATURE, pending.size());
                remaining(start, previous, budget, pending.size());
                if (signature.status() == 404) continue;
                pending.remove(entry.getKey());
            }
            if (!pending.isEmpty()) {
                previous = tick(clock, previous);
                long left = budget - (previous - start);
                if (left <= 0) throw new VerificationException(Failure.PROPAGATION_TIMEOUT, pending.size());
                sleeper.sleep(Duration.ofNanos(Math.min(Duration.ofSeconds(delay).toNanos(), left)));
                delay = Math.min(delay * 2, 60);
            }
        }
        return new Verified(version, hashes);
    }
    private static long tick(Clock clock, long previous) throws IOException {
        long now = clock.nanos(); require(now - previous >= 0, "Nonmonotonic clock"); return now;
    }
    private static Duration remaining(long start, long now, long budget, int pending) throws IOException {
        long left = budget - (now - start);
        if (left <= 0) throw new VerificationException(Failure.PROPAGATION_TIMEOUT, pending);
        return Duration.ofNanos(Math.min(left, REQUEST_TIMEOUT.toNanos()));
    }
    private static Download acquire(Transport http, URI uri, int bound, Duration timeout, int pending, Clock clock, long start, long budget) throws IOException {
        try {
            Download result = http.get(uri, bound, timeout);
            require(result != null && (result.status() == 200 || result.status() == 404), "Unexpected remote status");
            return result;
        } catch (IOException | RuntimeException error) {
            if (clock.nanos() - start >= budget) throw new VerificationException(Failure.PROPAGATION_TIMEOUT, pending);
            throw new VerificationException(Failure.ACQUISITION_FAILED, pending);
        }
    }
    static Map<String, String> hashes(PublicationManifest manifest) {
        var result = new LinkedHashMap<String, String>();
        for (var a : manifest.artifacts()) result.put(a.path(), a.sha256());
        return Collections.unmodifiableMap(result);
    }
}
