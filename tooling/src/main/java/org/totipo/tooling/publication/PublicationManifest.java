package org.totipo.tooling.publication;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.totipo.tooling.StrictJson;
import static org.totipo.tooling.publication.FilesSupport.*;

/** Existing schema 1: identity is derived from the reviewed contract, never filenames. */
public record PublicationManifest(String version, String repositorySha256, List<Artifact> artifacts) {
    private static final Set<String> ARTIFACT_FIELDS = Set.of("module", "group", "artifact", "version", "role", "suffix", "path", "sha256");
    static final List<String> MODULES = List.of("core", "storage-nio");
    enum Role {
        binary(".jar"), sources("-sources.jar"), javadoc("-javadoc.jar"), pom(".pom"), module(".module");
        final String suffix;
        Role(String suffix) { this.suffix = suffix; }
    }
    public record Artifact(String module, String group, String artifact, String version,
                           String role, String suffix, String path, String sha256) {
        Map<String, Object> json() {
            return Map.of("module", module, "group", group, "artifact", artifact, "version", version,
                    "role", role, "suffix", suffix, "path", path, "sha256", sha256);
        }
    }
    public PublicationManifest { artifacts = List.copyOf(artifacts); }
    static List<Artifact> expected(String version) throws IOException {
        require(version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?"), "Invalid manifest version");
        List<Artifact> result = new ArrayList<>();
        for (String module : MODULES) for (Role role : Role.values()) {
            String artifact = "totipo-" + module;
            result.add(new Artifact(module, "org.totipo", artifact, version, role.name(), role.suffix,
                    "org/totipo/" + artifact + "/" + version + "/" + artifact + "-" + version + role.suffix, ""));
        }
        return result;
    }
    public static PublicationManifest parse(byte[] bytes, String version) throws IOException {
        var data = Json.object(Json.parse(bytes, StrictJson.MANIFEST));
        require(data.keySet().equals(Set.of("schema", "version", "repositorySha256", "artifacts")), "Invalid manifest fields");
        require(BigInteger.ONE.equals(data.get("schema")) && version.equals(data.get("version")), "Wrong manifest schema/version");
        List<?> records = Json.array(data.get("artifacts"));
        require(records.size() == 10, "Expected 10 unsigned artifacts");
        Map<String, Artifact> expected = new LinkedHashMap<>();
        for (var a : expected(version)) expected.put(a.path(), a);
        List<Artifact> accepted = new ArrayList<>();
        for (Object record : records) {
            var m = Json.object(record);
            require(m.keySet().equals(ARTIFACT_FIELDS), "Invalid artifact fields");
            String path = Json.string(m.get("path"));
            Artifact a = expected.remove(path);
            require(a != null, "Duplicate/invalid artifact path: " + path);
            for (var e : a.json().entrySet()) if (!e.getKey().equals("sha256"))
                require(e.getValue().equals(m.get(e.getKey())), "Wrong artifact module/path/role: " + path);
            String hash = Json.string(m.get("sha256")); validHash(hash);
            accepted.add(new Artifact(a.module(), a.group(), a.artifact(), version, a.role(), a.suffix(), path, hash));
        }
        require(expected.isEmpty(), "Missing/extra artifact");
        String fingerprint = Json.string(data.get("repositorySha256")); validHash(fingerprint);
        return new PublicationManifest(version, fingerprint, accepted);
    }
    private static void validHash(String value) throws IOException {
        require(value.matches("[0-9a-f]{64}"), "Invalid SHA-256");
    }
    // Package-private: production CLI reaches this only after complete semantic verification.
    static PublicationManifest accepted(Path repository, String version) throws IOException {
        List<Artifact> result = new ArrayList<>();
        for (Artifact a : expected(version)) result.add(new Artifact(a.module(), a.group(), a.artifact(), version,
                a.role(), a.suffix(), a.path(), digest(read(repository.resolve(a.path())), "SHA-256")));
        return new PublicationManifest(version, PublicationRepository.fingerprint(repository), result);
    }
    public byte[] bytes() throws IOException {
        return (Json.dumps(Map.of("schema", 1, "version", version, "repositorySha256", repositorySha256,
                "artifacts", artifacts.stream().map(Artifact::json).toList()), true) + "\n").getBytes(StandardCharsets.UTF_8);
    }
    /** Python json.dumps(sort_keys=True, separators=(",", ":")); same schema and record order. */
    public String compact() throws IOException {
        String json = new String(bytes(), StandardCharsets.UTF_8);
        StringBuilder output = new StringBuilder();
        boolean quoted = false, escaped = false;
        for (char c : json.toCharArray()) {
            if (quoted || !Character.isWhitespace(c)) output.append(c);
            if (escaped) escaped = false;
            else if (quoted && c == '\\') escaped = true;
            else if (c == '"') quoted = !quoted;
        }
        return output.toString();
    }
}
