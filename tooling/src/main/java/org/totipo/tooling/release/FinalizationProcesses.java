package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.totipo.tooling.release.SourceFiles.require;

/** Bounded curl runtime. No retries, redirects, shell, credential argv, or tag update/delete operations. */
public final class FinalizationProcesses {
    private FinalizationProcesses() {}
    @FunctionalInterface public interface Api {
        RemoteReleaseState.Response request(String method, String path, Map<String, ?> body) throws IOException;
    }
    public static Api github(Path root, Path curl, String token) throws IOException {
        require(curl.isAbsolute(), "Explicit curl executable required");
        require(token != null && token.matches("[A-Za-z0-9_]{1,1024}"), "Missing or invalid GitHub runtime token");
        return (method, path, body) -> {
            boolean read = method.equals("GET") && (path.matches("/git/ref/tags/v[^/]+")
                || path.matches("/git/tags/[a-f0-9]{40}") || path.matches("/releases/tags/v[^/]+"));
            boolean create = method.equals("POST") && List.of("/git/tags", "/git/refs", "/releases").contains(path);
            require(read || create, "Unapproved GitHub operation");
            require(!path.contains("\r") && !path.contains("\n") && !path.contains("\"") && !path.contains("\\"), "Invalid API path");
            String data = body == null ? null : json(body);
            String config = "header = \"Authorization: Bearer " + token + "\"\n"
                + "header = \"Accept: application/vnd.github+json\"\nheader = \"X-GitHub-Api-Version: 2022-11-28\"\n";
            if (data != null) config += "header = \"Content-Type: application/json\"\ndata-binary = \""
                + data.replace("\\", "\\\\").replace("\"", "\\\"") + "\"\n";
            String certificate = System.getenv("SSL_CERT_FILE");
            Map<String, String> environment = certificate == null ? Map.of() : Map.of("CURL_CA_BUNDLE", certificate);
            String result = run(root, List.of(curl.toString(), "--disable", "--config", "-", "--silent", "--show-error",
                "--proto", "=https", "--retry", "0", "--connect-timeout", "20", "--max-time", "20",
                "--max-filesize", "4194304", "--request", method, "--write-out", "\n%{http_code}",
                "https://api.github.com/repos/" + GitQueries.REPOSITORY + path), environment,
                config.getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(25));
            int end = result.lastIndexOf('\n'); require(end >= 0, "Missing HTTP outcome; mutation may be uncertain");
            String status = result.substring(end + 1); require(status.matches("[1-5][0-9]{2}"), "Unknown HTTP outcome");
            return new RemoteReleaseState.Response(Integer.parseInt(status), result.substring(0, end).getBytes(StandardCharsets.UTF_8));
        };
    }
    static String json(Map<String, ?> data) throws IOException {
        var output = new java.io.ByteArrayOutputStream();
        try (var generator = new com.fasterxml.jackson.core.JsonFactory().createGenerator(output)) { emit(generator, data); }
        return output.toString(StandardCharsets.UTF_8);
    }
    private static void emit(com.fasterxml.jackson.core.JsonGenerator g, Object value) throws IOException {
        if (value instanceof Map<?, ?> map) {
            g.writeStartObject();
            for (var item : map.entrySet()) { g.writeFieldName((String) item.getKey()); emit(g, item.getValue()); }
            g.writeEndObject();
        } else if (value instanceof Boolean bool) g.writeBoolean(bool);
        else g.writeString((String) value);
    }
    static String run(Path root, List<String> command, Map<String, String> environment, byte[] stdin, Duration timeout) throws IOException {
        Process process = null;
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var builder = new ProcessBuilder(command).directory(root.toFile());
            builder.environment().clear(); builder.environment().putAll(environment);
            builder.environment().put("LC_ALL", "C.UTF-8");
            process = builder.start(); Process child = process;
            long end = System.nanoTime() + timeout.toNanos();
            var input = workers.submit(() -> { try (var stream = child.getOutputStream()) { stream.write(stdin); } return true; });
            var out = workers.submit(() -> bounded(child.getInputStream()));
            var err = workers.submit(() -> bounded(child.getErrorStream()));
            input.get(Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS);
            byte[] stdout = out.get(Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS);
            err.get(Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS);
            require(child.waitFor(Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS) && child.exitValue() == 0, "Process failed");
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(stdout)).toString();
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IOException("Release process failed or deadline exceeded; mutation outcome may be uncertain");
        } finally {
            if (process != null) {
                process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly();
                try { process.getInputStream().close(); process.getErrorStream().close(); process.getOutputStream().close(); }
                catch (IOException ignored) { /* Continue cleanup. */ }
            }
            workers.shutdownNow();
        }
    }
    private static byte[] bounded(java.io.InputStream input) throws IOException {
        try (input) { byte[] data = input.readNBytes(4_194_305); require(data.length <= 4_194_304, "Process output limit"); return data; }
    }
}
