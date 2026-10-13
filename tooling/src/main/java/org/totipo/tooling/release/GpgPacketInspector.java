package org.totipo.tooling.release;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.totipo.tooling.release.SourceFiles.require;

/** Narrow fixed-argument GPG adapter. Only packet metadata is read; key bytes go solely to stdin.
 * The explicit absolute executable is a runtime trust input, never chosen from an environment PATH.
 * Use outside derivations with real credentials; qualification uses a deterministic fake executable.
 */
public final class GpgPacketInspector implements SecretPreflight.PacketInspector {
    public static final int MAX_KEY = 1024 * 1024, MAX_OUTPUT = 1024 * 1024;
    private final Path executable, temporaryParent;
    private final Duration timeout;
    public GpgPacketInspector(Path executable, Path temporaryParent, Duration timeout) {
        if (!executable.isAbsolute() || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(1)) > 0)
            throw new IllegalArgumentException("Invalid packet inspection runtime configuration");
        this.executable = executable; this.temporaryParent = temporaryParent; this.timeout = timeout;
    }
    @Override public SecretPreflight.Protection inspect(String key) throws IOException {
        require(key != null && key.length() <= MAX_KEY, "Private key input limit");
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        require(bytes.length <= MAX_KEY, "Private key input limit");
        Path home = null;
        Process process = null;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            home = Files.createTempDirectory(temporaryParent, "totipo-key-inspection-");
            var builder = new ProcessBuilder(executable.toString(), "--no-options", "--batch", "--no-autostart",
                    "--homedir", home.toString(), "--pinentry-mode", "error", "--list-packets");
            // Do not inherit credentials, GPG configuration overrides, or dynamic loader overrides.
            builder.environment().clear();
            builder.environment().put("LC_ALL", "C");
            process = builder.start();
            Process child = process;
            long deadline = System.nanoTime() + timeout.toNanos();
            Future<byte[]> stdout = executor.submit(() -> bounded(child.getInputStream()));
            Future<byte[]> stderr = executor.submit(() -> bounded(child.getErrorStream()));
            Future<Void> stdin = executor.submit(() -> {
                try (var out = child.getOutputStream()) { out.write(bytes); }
                return null;
            });
            // All three pipes operate concurrently, including a blocked stdin writer.
            await(stdin, deadline);
            byte[] packets = await(stdout, deadline);
            byte[] diagnostics = await(stderr, deadline);
            require(child.waitFor(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS), "Packet inspection deadline");
            require(child.exitValue() == 0, "Packet inspection process failed");
            try { return metadata(packets); }
            finally { Arrays.fill(packets, (byte) 0); Arrays.fill(diagnostics, (byte) 0); }
        } catch (IOException | RuntimeException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            // Deliberately discard causes: they can contain packet text, argv or provider diagnostics.
            throw new IOException("Private key packet inspection failed");
        } finally {
            if (process != null) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                try { process.waitFor(1, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                try { process.getOutputStream().close(); process.getInputStream().close(); process.getErrorStream().close(); }
                catch (IOException ignored) { /* Cleanup continues. */ }
            }
            executor.shutdownNow();
            Arrays.fill(bytes, (byte) 0);
            if (home != null) {
                try (var paths = Files.walk(home)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                } catch (IOException failure) { throw new IOException("Private key inspection cleanup failed"); }
            }
        }
    }
    private static byte[] bounded(InputStream stream) throws IOException {
        try (stream) {
            byte[] data = stream.readNBytes(MAX_OUTPUT + 1);
            require(data.length <= MAX_OUTPUT, "Packet inspection output limit");
            return data;
        }
    }
    private static <T> T await(Future<T> future, long deadline) throws IOException, InterruptedException {
        try { return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
        catch (ExecutionException | TimeoutException failure) { throw new IOException("Packet inspection unavailable"); }
    }
    /** GPG text framing only, not a new OpenPGP parser. Reject unframed/invalid output conservatively. */
    static SecretPreflight.Protection metadata(byte[] bytes) throws IOException {
        require(bytes.length <= MAX_OUTPUT, "Packet inspection output limit");
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().replace("\r\n", "\n").replace('\r', '\n');
        boolean secret = false;
        for (String line : text.split("\n", -1)) {
            for (int i = 0; i < line.length(); i++)
                require(line.charAt(i) == '\t' || !Character.isISOControl(line.charAt(i)), "Malformed packet metadata");
            if (line.isEmpty()) continue;
            require(line.startsWith("# ") || line.startsWith(":") || line.startsWith("\t") || line.startsWith(" "),
                    "Unexpected packet metadata output");
            if (line.equals(":secret key packet:")) secret = true;
        }
        require(secret, "Not a readable private key packet");
        return text.contains("protected") || text.contains("protect algo:")
                ? SecretPreflight.Protection.PROTECTED : SecretPreflight.Protection.UNPROTECTED;
    }
}
