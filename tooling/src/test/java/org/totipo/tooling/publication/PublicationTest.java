package org.totipo.tooling.publication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independent synthetic boundary fixtures; no Python, Gradle, production classes or JUnit. */
public final class PublicationTest {
    private static final String VERSION = "0.2.0";
    private static final String URL = "https://github.com/totipo-org/totipo-java";
    private static int assertions;
    private PublicationTest() {}
    @FunctionalInterface interface Action { void run() throws Exception; }
    @FunctionalInterface interface Mutation { void run(Path root) throws Exception; }
    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private static void write(Path p, byte[] b) throws IOException { Files.createDirectories(p.getParent()); Files.write(p, b); }
    private static void write(Path p, String s) throws IOException { write(p, bytes(s)); }
    private static String hash(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
    private static Path repo(Path root) { return root.resolve("build/repository"); }
    private static Path artifact(Path root, String suffix) { return repo(root).resolve("org/totipo/totipo-core/0.2.0/totipo-core-0.2.0" + suffix); }
    private static void reject(Action action) throws Exception {
        boolean failed = false;
        try { action.run(); } catch (IOException | IllegalArgumentException e) { failed = true; }
        if (!failed) throw new AssertionError("Negative accepted"); assertions++;
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Assertion failed"); assertions++; }
    public static void acceptedFixture(Path root) throws Exception {
        fixture(root);
        var accepted = PublicationVerifier.verify(root.resolve("build/repository"), "0.2.0");
        Files.write(root.resolve("build/publication-manifest.json"), accepted.bytes());
    }
    static Path fixture(Path root) throws Exception {
        write(root.resolve("VERSION"), VERSION + "\n");
        write(root.resolve("publishing/consumer-smoke/verification-metadata-template.xml"), "<verification-metadata>\n   <components>\n   </components>\n</verification-metadata>\n");
        for (String module : List.of("core", "storage-nio")) {
            String artifact = "totipo-" + module, prefix = artifact + "-" + VERSION;
            Path dir = repo(root).resolve("org/totipo/" + artifact + "/" + VERSION);
            // Opaque bytes deliberately need no ZIP structure or producer output trees.
            for (String suffix : List.of(".jar", "-sources.jar", "-javadoc.jar"))
                write(dir.resolve(prefix + suffix), module + suffix + " opaque artifact bytes");
            String group = module.equals("core") ? "org.bouncycastle" : "org.totipo";
            String dependency = module.equals("core") ? "bcprov-jdk18on" : "totipo-core";
            String depVersion = module.equals("core") ? "1.86" : VERSION;
            String scope = module.equals("core") ? "runtime" : "compile";
            write(dir.resolve(prefix + ".pom"), "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><groupId>org.totipo</groupId><artifactId>" + artifact
                    + "</artifactId><version>0.2.0</version><name>" + (module.equals("core") ? "Totipo Java Core" : "Totipo Java NIO Storage")
                    + "</name><description>fixture</description><url>" + URL + "</url><licenses><license><name>Apache License 2.0</name><url>https://www.apache.org/licenses/LICENSE-2.0.txt</url></license></licenses>"
                    + "<developers><developer><id>ingon</id><name>Nikolay Petrov</name><url>https://github.com/ingon/</url></developer></developers>"
                    + "<scm><url>" + URL + "</url><connection>scm:git:" + URL + ".git</connection><developerConnection>scm:git:ssh://git@github.com/totipo-org/totipo-java.git</developerConnection></scm>"
                    + "<dependencies><dependency><groupId>" + group + "</groupId><artifactId>" + dependency + "</artifactId><version>" + depVersion + "</version><scope>" + scope + "</scope></dependency></dependencies></project>");
            List<String> variants = new ArrayList<>();
            for (String variant : List.of("apiElements", "runtimeElements", "sourcesElements", "javadocElements")) {
                String suffix = variant.equals("sourcesElements") ? "-sources.jar" : variant.equals("javadocElements") ? "-javadoc.jar" : ".jar";
                byte[] data = Files.readAllBytes(dir.resolve(prefix + suffix));
                String deps = variant.equals("runtimeElements") || (module.equals("storage-nio") && variant.equals("apiElements"))
                        ? ",\"dependencies\":[{\"group\":\"" + group + "\",\"module\":\"" + dependency + "\",\"version\":{\"requires\":\"" + depVersion + "\"}}]" : "";
                String attributes = variant.equals("sourcesElements") || variant.equals("javadocElements")
                        ? "\"org.gradle.category\":\"documentation\",\"org.gradle.usage\":\"java-runtime\",\"org.gradle.docstype\":\"" + (variant.equals("sourcesElements") ? "sources" : "javadoc") + "\""
                        : "\"org.gradle.category\":\"library\",\"org.gradle.usage\":\"" + (variant.equals("apiElements") ? "java-api" : "java-runtime") + "\",\"org.gradle.jvm.version\":17";
                variants.add("{\"name\":\"" + variant + "\",\"attributes\":{" + attributes + "}" + deps
                        + ",\"files\":[{\"name\":\"" + prefix + suffix + "\",\"url\":\"" + prefix + suffix + "\",\"size\":" + data.length + ",\"sha256\":\"" + hash(data) + "\"}]}");
            }
            write(dir.resolve(prefix + ".module"), "{\"formatVersion\":\"1.1\",\"component\":{\"group\":\"org.totipo\",\"module\":\"" + artifact + "\",\"version\":\"0.2.0\",\"attributes\":{\"org.gradle.status\":\"release\"}},\"variants\":[" + String.join(",", variants) + "]}");
            write(dir.getParent().resolve("maven-metadata.xml"), "<metadata><groupId>org.totipo</groupId><artifactId>" + artifact + "</artifactId><versioning><versions><version>0.2.0</version></versions></versioning></metadata>");
        }
        return root;
    }
    private static void replace(Path file, String from, String to) throws IOException {
        String original = Files.readString(file);
        if (!original.contains(from)) throw new AssertionError("Fixture replacement missing: " + from);
        write(file, original.replace(from, to));
    }
    private static void semantic(String name, boolean javaAccept, Mutation mutation) throws Exception {
        Path root = Files.createTempDirectory("publication-fixture-" + name + "-");
        fixture(root); mutation.run(root);
        Action verify = () -> PublicationVerifier.verify(repo(root), VERSION);
        if (javaAccept) { verify.run(); assertions++; } else reject(verify);
        remove(root);
    }
    private static void pomCase(String name, String from, String to) throws Exception {
        semantic(name, false, r -> replace(artifact(r, ".pom"), from, to));
    }
    private static void moduleCase(String name, String from, String to) throws Exception {
        semantic(name, false, r -> replace(artifact(r, ".module"), from, to));
    }
    private static void remove(Path root) throws IOException {
        try (var paths = Files.walk(root)) { for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("Optional fixture export directory");
        semantic("valid", true, r -> {});
        semantic("missing-artifact", false, r -> Files.delete(artifact(r, ".pom")));
        semantic("extra-artifact", false, r -> write(artifact(r, ".other"), "extra"));
        semantic("bad-sidecar", false, r -> write(artifact(r, ".jar.sha256"), "0".repeat(64)));
        semantic("bad-index-checksum", false, r -> write(artifact(r, ".jar").getParent().getParent().resolve("maven-metadata.xml.sha256"), "0".repeat(64)));
        semantic("missing-index-version", false, r -> replace(artifact(r, ".jar").getParent().getParent().resolve("maven-metadata.xml"), "<version>0.2.0</version>", ""));
        semantic("extra-index-version", false, r -> replace(artifact(r, ".jar").getParent().getParent().resolve("maven-metadata.xml"), "</versions>", "<version>0.3.0</version></versions>"));
        semantic("extra-repository-file", false, r -> write(repo(r).resolve("unexpected"), "extra"));
        pomCase("pom-group", "<groupId>org.totipo", "<groupId>wrong");
        pomCase("pom-artifact", "<artifactId>totipo-core", "<artifactId>wrong");
        pomCase("pom-version", "<version>0.2.0", "<version>0.3.0");
        pomCase("pom-dependency", "bcprov-jdk18on", "other");
        pomCase("pom-scope", "<scope>runtime", "<scope>compile");
        pomCase("pom-extra-dependency", "</dependencies>", "<dependency/></dependencies>");
        pomCase("pom-optional", "</dependency>", "<optional>true</optional></dependency>");
        semantic("pom-explicit-defaults", true, r -> replace(artifact(r, ".pom"), "</dependency>", "<optional>false</optional><type>jar</type></dependency>"));
        pomCase("pom-dependency-type", "</dependency>", "<type>pom</type></dependency>");
        pomCase("pom-exclusion", "</dependency>", "<exclusions><exclusion/></exclusions></dependency>");
        pomCase("pom-classifier", "</dependency>", "<classifier>other</classifier></dependency>");
        semantic("pom-descriptive-metadata", true, r -> replace(artifact(r, ".pom"), "Apache License 2.0", "Other license"));
        pomCase("pom-missing-scm", "<connection>scm:git:" + URL + ".git</connection>", "");
        pomCase("pom-duplicate-coordinate", "<groupId>org.totipo</groupId>", "<groupId>org.totipo</groupId><groupId>wrong</groupId>");
        pomCase("pom-malformed", "</project>", "");
        semantic("pom-external-entity", false, r -> {
            replace(artifact(r, ".pom"), "<project", "<!DOCTYPE project [<!ENTITY evil SYSTEM 'file:///nonexistent-totipo-j2b'>]><project");
            replace(artifact(r, ".pom"), "<description>fixture", "<description>&evil;");
        });
        semantic("pom-network-entity", false, r -> {
            replace(artifact(r, ".pom"), "<project", "<!DOCTYPE project [<!ENTITY evil SYSTEM 'http://127.0.0.1:1/untrusted'>]><project");
            replace(artifact(r, ".pom"), "<description>fixture", "<description>&evil;");
        });
        semantic("pom-nested-field", false, r -> replace(artifact(r, ".pom"), "<groupId>org.totipo</groupId>", "<groupId><nested>org.totipo</nested></groupId>"));
        semantic("pom-internal-dtd-tightening", false, r -> replace(artifact(r, ".pom"), "<project", "<!DOCTYPE project [<!ENTITY benign 'safe'>]><project"));
        moduleCase("module-component", "\"group\":\"org.totipo\"", "\"group\":\"wrong\"");
        moduleCase("module-format", "\"1.1\"", "\"1.2\"");
        semantic("module-variant-names", true, r -> replace(artifact(r, ".module"), "javadocElements", "other"));
        moduleCase("module-docstype", "\"org.gradle.docstype\":\"javadoc\"", "\"org.gradle.docstype\":\"other\"");
        moduleCase("module-dependency", "bcprov-jdk18on", "other");
        moduleCase("module-size", "\"size\":", "\"size\":9");
        moduleCase("module-java-bool", "\"org.gradle.jvm.version\":17", "\"org.gradle.jvm.version\":true");
        moduleCase("module-java", "\"org.gradle.jvm.version\":17", "\"org.gradle.jvm.version\":25");
        moduleCase("module-float-size", "\"size\":", "\"size\":0.1e");
        moduleCase("module-hash", "\"sha256\":\"", "\"sha256\":\"0");
        moduleCase("module-capability", "\"name\":\"apiElements\"", "\"capabilities\":[{}],\"name\":\"apiElements\"");
        moduleCase("module-constraint", "\"name\":\"apiElements\"", "\"dependencyConstraints\":[{}],\"name\":\"apiElements\"");
        moduleCase("module-available-at", "\"name\":\"apiElements\"", "\"available-at\":{\"url\":\"other\"},\"name\":\"apiElements\"");
        semantic("module-unrelated-extension", true, r -> replace(artifact(r, ".module"), "\"formatVersion\":", "\"extension\":\":core\",\"formatVersion\":"));
        semantic("module-duplicate-tightening", false, r -> replace(artifact(r, ".module"), "\"formatVersion\":", "\"formatVersion\":\"bad\",\"formatVersion\":"));
        semantic("module-utf16", false, r -> write(artifact(r, ".module"), Files.readString(artifact(r, ".module")).getBytes(StandardCharsets.UTF_16)));
        semantic("module-bom", false, r -> write(artifact(r, ".module"), "\ufeff" + Files.readString(artifact(r, ".module"))));
        semantic("module-invalid-utf8", false, r -> write(artifact(r, ".module"), new byte[] {(byte) 0xff}));
        semantic("module-truncated", false, r -> write(artifact(r, ".module"), "{"));
        semantic("module-oversized-tightening", false, r -> write(artifact(r, ".module"), Files.readString(artifact(r, ".module")) + " ".repeat(65_536)));
        semantic("valid-sidecars", true, r -> {
            byte[] binary = Files.readAllBytes(artifact(r, ".jar"));
            write(artifact(r, ".jar.sha256"), "\u00a0" + hash(binary) + "\u0085\n");
        });
        semantic("module-integral-java-float-tightening", false, r -> replace(artifact(r, ".module"), "\"org.gradle.jvm.version\":17", "\"org.gradle.jvm.version\":17.0"));
        semantic("module-integral-size-float-tightening", false, r -> write(artifact(r, ".module"), Files.readString(artifact(r, ".module")).replaceAll("(\"size\":\\d+)", "$1.0")));
        semantic("module-nan-tightening", false, r -> replace(artifact(r, ".module"), "\"formatVersion\":", "\"unused\":NaN,\"formatVersion\":"));
        semantic("module-capability-type-tightening", false, r -> replace(artifact(r, ".module"), "\"name\":\"apiElements\"", "\"capabilities\":false,\"name\":\"apiElements\""));
        semantic("module-extra-variant-tightening", false, r -> {
            var m = Json.object(Json.parse(Files.readAllBytes(artifact(r, ".module")), org.totipo.tooling.StrictJson.MODULE));
            List<Object> variants = new ArrayList<>(Json.array(m.get("variants"))); variants.add(variants.getFirst());
            m.put("variants", variants); write(artifact(r, ".module"), Json.dumps(m, false));
        });
        semantic("module-missing-checksum-tightening", false, r -> {
            var m = Json.object(Json.parse(Files.readAllBytes(artifact(r, ".module")), org.totipo.tooling.StrictJson.MODULE));
            for (Object variant : Json.array(m.get("variants"))) {
                @SuppressWarnings("unchecked") Map<String, Object> v = (Map<String, Object>) variant;
                var file = Json.object(Json.array(v.get("files")).getFirst()); file.remove("sha256"); v.put("files", List.of(file));
            }
            write(artifact(r, ".module"), Json.dumps(m, false));
        });
        manifestTests(); jsonCompatibility(); profileTests();
        System.out.println("Publication tests passed: " + assertions + " assertions");
    }
    private static void manifestTests() throws Exception {
        Path root = fixture(Files.createTempDirectory("publication-manifest-test-"));
        Path output = Files.createTempDirectory("publication-output-test-");
        try {
            var accepted = PublicationVerifier.verify(repo(root), VERSION);
            String text = new String(accepted.bytes(), StandardCharsets.UTF_8);
            var parsed = PublicationManifest.parse(bytes(text), VERSION);
            check(parsed.equals(accepted));
            for (String invalid : List.of(text.replace("\"schema\": 1", "\"schema\": true"),
                    text.replace("\"schema\": 1", "\"schema\": 1.0"), text.replace("\"schema\": 1", "\"schema\": 2"),
                    text.replace("\"schema\": 1", "\"schema\": 1, \"schema\": 1"), text.replace("0.2.0", "0.3.0"),
                    text.replace("\"role\": \"binary\"", "\"role\": \"bad\""), text.replace("\"group\": \"org.totipo\"", "\"group\": \"wrong\""),
                    text.replace("\"suffix\": \".jar\"", "\"suffix\": \".pom\""), text.replace("\"path\": \"org/totipo/", "\"path\": \"../"),
                    text.replace("\"schema\": 1", "\"extra\": 0, \"schema\": 1"),
                    text.replace(accepted.artifacts().getFirst().sha256(), "A".repeat(64)), "{", text + " {}", text + " ".repeat(32_768)))
                reject(() -> PublicationManifest.parse(bytes(invalid), VERSION));
            var model = Json.object(Json.parse(bytes(text), org.totipo.tooling.StrictJson.MANIFEST));
            List<?> records = Json.array(model.get("artifacts"));
            for (List<?> invalid : List.of(records.subList(0, 9), List.of(), java.util.Collections.nCopies(10, records.getFirst()), java.util.Collections.nCopies(11, records.getFirst()))) {
                var m = new LinkedHashMap<>(model); m.put("artifacts", invalid);
                reject(() -> PublicationManifest.parse(bytes(Json.dumps(m, false)), VERSION));
            }
            String a = accepted.artifacts().get(0).sha256(), b = accepted.artifacts().get(1).sha256();
            var swapped = PublicationManifest.parse(bytes(text.replace(a, "SWAP").replace(b, a).replace("SWAP", b)), VERSION);
            reject(() -> PublicationRepository.checkStaging(swapped, repo(root)));
            PublicationRepository.checkStaging(parsed, repo(root)); assertions++;
            Path destination = output.resolve("copy");
            PublicationRepository.copy(parsed, repo(root), destination); assertions++;
            reject(() -> PublicationRepository.copy(parsed, repo(root), destination));
            try (var files = Files.walk(destination)) { check(files.filter(Files::isRegularFile).count() == 10); }
            write(destination.resolve(parsed.artifacts().getFirst().path()), "corrupt");
            reject(() -> PublicationRepository.checkArtifacts(parsed, destination));
            Path extra = output.resolve("extra"); write(extra.resolve("unexpected"), "extra");
            reject(() -> PublicationRepository.copy(parsed, repo(root), extra));
            Path symlink = output.resolve("link"); Files.createSymbolicLink(symlink, extra);
            reject(() -> PublicationRepository.copy(parsed, repo(root), symlink));
            Path intermediate = output.resolve("intermediate"); Files.createDirectories(intermediate);
            Files.createSymbolicLink(intermediate.resolve("org"), extra);
            reject(() -> PublicationRepository.copy(parsed, repo(root), intermediate));
            for (String path : List.of("org/totipo/totipo-core/maven-metadata.xml", "extra", "index.sha256", parsed.artifacts().getFirst().path())) {
                Path file = repo(root).resolve(path); byte[] prior = Files.exists(file) ? Files.readAllBytes(file) : null;
                write(file, "mutation"); reject(() -> PublicationRepository.checkStaging(parsed, repo(root)));
                if (prior == null) Files.delete(file); else write(file, prior);
            }
            Path manifest = output.resolve("accepted.json"), metadata = output.resolve("consumer.xml");
            reject(() -> PublicationCli.writeEvidence(accepted, bytes("partial consumer output"), manifest, metadata,
                    () -> { throw new IOException("Injected re-observation failure after metadata write"); }));
            check(!Files.exists(manifest) && !Files.exists(metadata));
            try (var files = Files.list(output)) { check(files.noneMatch(p -> p.getFileName().toString().startsWith(".publication-"))); }
            String[] cli = {"verify", root.toString(), repo(root).toString(), manifest.toString(), metadata.toString()};
            PublicationCli.run(cli); check(java.util.Arrays.equals(accepted.bytes(), Files.readAllBytes(manifest)));
            // Stale evidence must disappear after semantic failure.
            write(artifact(root, ".pom"), "bad"); reject(() -> PublicationCli.run(cli));
            check(!Files.exists(manifest) && !Files.exists(metadata));
            fixture(root);
            PublicationCli.run(cli); write(root.resolve("VERSION"), "bad\n");
            reject(() -> PublicationCli.run(cli)); check(!Files.exists(manifest) && !Files.exists(metadata));
            // Output failure after valid semantic checks must leave no accepted manifest.
            fixture(root); Files.createDirectory(metadata);
            reject(() -> PublicationCli.run(cli)); check(!Files.exists(manifest)); Files.delete(metadata);
            Path integratedManifest = root.resolve("build/publication-manifest.json");
            Path integratedMetadata = root.resolve("publishing/consumer-smoke/gradle/verification-metadata.xml");
            check(!Files.exists(integratedMetadata.getParent()));
            PublicationCli.run(new String[]{"verify", root.toString(), repo(root).toString(),
                integratedManifest.toString(), integratedMetadata.toString()});
            check(java.util.Arrays.equals(accepted.bytes(), Files.readAllBytes(integratedManifest)));
            check(Files.isRegularFile(integratedMetadata));
            reject(() -> PublicationCli.run(new String[]{"verify", root.toString(), repo(root).toString(),
                root.resolve("unapproved.json").toString(), integratedMetadata.toString()}));
            Files.createSymbolicLink(artifact(root, ".pom.sha256"), output.resolve("outside"));
            reject(() -> PublicationVerifier.verify(repo(root), VERSION));
            Files.delete(artifact(root, ".pom.sha256"));
            byte[] consumer = PublicationVerifier.consumerMetadata(accepted, bytes("   </components>\n"));
            String c = new String(consumer, StandardCharsets.UTF_8);
            check(c.contains("origin=\"Verified local staging output\""));
            check(c.split("<artifact ", -1).length == 7);
            check(!c.contains("sources.jar") && !c.contains("javadoc.jar"));
            reject(() -> PublicationVerifier.consumerMetadata(accepted, bytes("   </components>   </components>")));
        } finally { remove(root); remove(output); }
    }
    private static void profileTests() throws Exception {
        for (var profile : List.of(org.totipo.tooling.StrictJson.MODULE, org.totipo.tooling.StrictJson.MANIFEST)) {
            for (String invalid : List.of("[".repeat(profile.depth() + 1) + "0" + "]".repeat(profile.depth() + 1),
                    "\"" + "x".repeat(profile.string() + 1) + "\"", "{\"" + "x".repeat(profile.name() + 1) + "\":0}",
                    "1".repeat(profile.number() + 1), "[" + "0,".repeat(profile.tokens()) + "0]", "{\"a\":1,\"a\":2}", "{} {}", "[", "{\"a\":NaN}"))
                reject(() -> Json.parse(bytes(invalid), profile));
        }
    }
    private static void jsonCompatibility() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("z/\\\"\n", "é😀\u007f\b\f\r\t"); values.put("\ue000", "BMP"); values.put("😀", "astral");
        String expected = "{\"z/\\\\\\\"\\n\": \"\\u00e9\\ud83d\\ude00\\u007f\\b\\f\\r\\t\", \"\\ue000\": \"BMP\", \"\\ud83d\\ude00\": \"astral\"}";
        check(Json.dumps(values, false).equals(expected));
        check(Json.dumps(Map.of(), false).equals("{}"));
        Path empty = Files.createTempDirectory("publication-empty-");
        try { check(PublicationRepository.fingerprint(empty).equals("44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a")); }
        finally { remove(empty); }
        Path fingerprintRepo = Files.createTempDirectory("publication-unicode-");
        try {
            for (String name : List.of("a/é", "z\\quote\"", "😀", "\ue000", "control\nname")) write(fingerprintRepo.resolve(name), "fixture bytes");
            check(PublicationRepository.fingerprint(fingerprintRepo).equals("179293eb76c895b3c42ead499aaee22a8cf8f6cf2af73ea9e5f490b58a650963"));
        } finally { remove(fingerprintRepo); }
    }
}
