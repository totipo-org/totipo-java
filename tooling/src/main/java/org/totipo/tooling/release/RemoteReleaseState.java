package org.totipo.tooling.release;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.totipo.tooling.StrictJson;
import static org.totipo.tooling.release.SourceFiles.require;

/** Bounded release reads and Central absence veto. No workflow history or upload recovery. */
public final class RemoteReleaseState {
    private RemoteReleaseState() {}
    public enum Presence { PRESENT, ABSENT }
    public record Response(int status, byte[] body) {
        public Response { body = body.clone(); }
        @Override public byte[] body() { return body.clone(); }
    }
    @FunctionalInterface public interface Http { Response get(URI uri) throws IOException; }
    public record ReleaseRecord(Presence presence, String tag, String name, boolean draft, boolean prerelease, String body) {}
    public static final StrictJson.Profile GITHUB = new StrictJson.Profile(4_194_304, 32, 262_144, 128, 128, 500_000);
    public static final String CENTRAL = "https://repo.maven.apache.org/maven2/";
    public static void absent(String version, Http http) throws IOException {
        require(ReleaseInputs.validVersion(version), "Invalid release version");
        for (String module : List.of("core", "storage-nio")) {
            String artifact = "totipo-" + module;
            Response r = http.get(URI.create(CENTRAL + "org/totipo/" + artifact + "/" + version + "/" + artifact + "-" + version + ".pom"));
            require(r != null && r.status() == 404 && r.body().length <= 65_536, "Central coordinate exists or absence is uncertain");
        }
    }
    // Schema decoding uses the existing strict Jackson stream infrastructure, including unknown fields.
    static JsonParser parser(byte[] bytes) throws IOException {
        java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
        require(!(bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf), "JSON BOM rejected");
        for (byte b : bytes) require(b != 0, "Raw NUL in JSON");
        return StrictJson.open(bytes, GITHUB);
    }
    static void expect(JsonParser p, JsonToken token) throws IOException { require(p.currentToken() == token, "Malformed GitHub JSON field"); }
    static String string(JsonParser p) throws IOException { expect(p, JsonToken.VALUE_STRING); return p.getText(); }
    static String nullable(JsonParser p) throws IOException { return p.currentToken() == JsonToken.VALUE_NULL ? null : string(p); }
    static boolean bool(JsonParser p) throws IOException {
        require(p.currentToken() == JsonToken.VALUE_TRUE || p.currentToken() == JsonToken.VALUE_FALSE, "Expected boolean GitHub field");
        return p.currentToken() == JsonToken.VALUE_TRUE;
    }
    static void skip(JsonParser p) throws IOException {
        // Force lazy string validation even in irrelevant API metadata. Never use skipChildren().
        if (p.currentToken() == JsonToken.START_ARRAY || p.currentToken() == JsonToken.START_OBJECT) {
            JsonToken end = p.currentToken() == JsonToken.START_ARRAY ? JsonToken.END_ARRAY : JsonToken.END_OBJECT;
            while (p.nextToken() != end) { require(p.currentToken() != null, "Truncated GitHub JSON"); skip(p); }
        } else if (p.currentToken() == JsonToken.VALUE_STRING) p.getText();
        else require(p.currentToken() != null, "Truncated GitHub JSON");
    }
    static String field(JsonParser p) throws IOException {
        expect(p, JsonToken.FIELD_NAME); String name = p.currentName(); p.nextToken(); return name;
    }
    public static ReleaseRecord decodeRelease(byte[] bytes, String version) throws IOException {
        try (var p = parser(bytes)) {
            p.nextToken(); expect(p, JsonToken.START_OBJECT);
            String tag = null, name = null, body = null; Boolean draft = null, prerelease = null;
            while (p.nextToken() != JsonToken.END_OBJECT) switch (field(p)) {
                case "tag_name" -> tag = string(p);
                case "name" -> name = string(p);
                case "body" -> body = string(p);
                case "draft" -> draft = bool(p);
                case "prerelease" -> prerelease = bool(p);
                default -> skip(p);
            }
            require(p.nextToken() == null, "Trailing GitHub JSON");
            require(tag != null && tag.equals("v" + version) && name != null && body != null && draft != null && prerelease != null,
                    "Missing/inconsistent GitHub release fields");
            return new ReleaseRecord(Presence.PRESENT, tag, name, draft, prerelease, body);
        } catch (IOException | RuntimeException error) {
            // Jackson diagnostics may include source tokens. Never expose response contents.
            throw new IOException("Malformed or out-of-bounds GitHub release response");
        }
    }

    static Map<String, Object> object(byte[] bytes) throws IOException {
        try (var p = parser(bytes)) {
            p.nextToken(); Object value = value(p);
            require(p.nextToken() == null && value instanceof Map, "Expected complete GitHub object");
            return member(Map.of("root", value), "root");
        } catch (IOException | RuntimeException error) { throw new IOException("Malformed bounded GitHub object"); }
    }
    private static Object value(JsonParser p) throws IOException {
        if (p.currentToken() == JsonToken.START_OBJECT) {
            var result = new java.util.LinkedHashMap<String, Object>();
            while (p.nextToken() != JsonToken.END_OBJECT) { String name = field(p); result.put(name, value(p)); }
            return result;
        }
        if (p.currentToken() == JsonToken.START_ARRAY) {
            var result = new java.util.ArrayList<Object>();
            while (p.nextToken() != JsonToken.END_ARRAY) { require(p.currentToken() != null, "Truncated object"); result.add(value(p)); }
            return result;
        }
        if (p.currentToken() == JsonToken.VALUE_STRING) return p.getText();
        if (p.currentToken() == JsonToken.VALUE_NULL) return null;
        if (p.currentToken() == JsonToken.VALUE_TRUE || p.currentToken() == JsonToken.VALUE_FALSE) return bool(p);
        require(p.currentToken() != null && p.currentToken().isNumeric(), "Malformed object value");
        return p.getNumberValue();
    }
    static Map<String, Object> member(Map<String, Object> object, String name) throws IOException {
        require(object.get(name) instanceof Map, "Missing GitHub object field");
        var result = new java.util.LinkedHashMap<String, Object>();
        for (var entry : ((Map<?, ?>) object.get(name)).entrySet()) {
            require(entry.getKey() instanceof String, "Invalid object key");
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }
    static String sha(Object value) throws IOException {
        require(value instanceof String && ((String) value).matches("[0-9a-f]{40}"), "Invalid object SHA");
        return (String) value;
    }
}
