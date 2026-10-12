package org.totipo.tooling.publication;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.totipo.tooling.StrictJson;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import static org.totipo.tooling.publication.FilesSupport.*;

/** Independently checks serialized producer outputs, compiled bytes and checked-out sources. */
public final class PublicationVerifier {
    private static final Set<String> HASHES = Set.of("md5", "sha1", "sha256", "sha512");
    private static final String URL = "https://github.com/totipo-org/totipo-java";
    private static final String POM_NS = "http://maven.apache.org/POM/4.0.0";
    private PublicationVerifier() {}
    public static PublicationManifest verify(Path root, Path repository, String version) throws IOException {
        String before = PublicationRepository.fingerprint(repository);
        Set<String> inventory = inventory(repository), expectedRepo = new HashSet<>();
        for (String module : PublicationManifest.MODULES) {
            try {
                String artifact = "totipo-" + module, prefix = artifact + "-" + version;
                String directory = "org/totipo/" + artifact + "/" + version + "/";
                Set<String> names = new HashSet<>();
                for (var a : PublicationManifest.expected(version)) if (a.module().equals(module)) {
                    names.add(Path.of(a.path()).getFileName().toString());
                    require(inventory.contains(a.path()), "Missing artifact: " + a.path());
                    expectedRepo.add(a.path());
                }
                for (String path : inventory) if (path.startsWith(directory) && !expectedRepo.contains(path)) {
                    String name = path.substring(directory.length()); int dot = name.lastIndexOf('.');
                    require(dot > 0 && names.contains(name.substring(0, dot)) && HASHES.contains(name.substring(dot + 1)),
                            "Unexpected publication file: " + path);
                    checksum(repository.resolve(path), repository.resolve(directory + name.substring(0, dot)), name.substring(dot + 1));
                    expectedRepo.add(path);
                }
                String index = "org/totipo/" + artifact + "/maven-metadata.xml";
                Element xml = xml(read(repository.resolve(index), 65_536));
                require(text(xml, "", "groupId").equals("org.totipo") && text(xml, "", "artifactId").equals(artifact), "Bad repository index");
                Element versions = child(child(xml, "", "versioning"), "", "versions");
                var indexed = children(versions, "", "version");
                require(indexed.size() == 1 && leadingText(indexed.getFirst()).equals(version), "Unexpected staged versions");
                expectedRepo.add(index);
                for (String algorithm : HASHES) if (inventory.contains(index + "." + algorithm)) {
                    checksum(repository.resolve(index + "." + algorithm), repository.resolve(index), algorithm);
                    expectedRepo.add(index + "." + algorithm);
                }
                Path dir = repository.resolve(directory);
                pom(read(dir.resolve(prefix + ".pom"), 65_536), module, artifact, version);
                metadata(read(dir.resolve(prefix + ".module"), StrictJson.MODULE.bytes()), dir, module, artifact, prefix, version, names);
                compareArchive(dir.resolve(prefix + ".jar"), root.resolve(module + "/build/classes/java/main"), ".class", true);
                compareArchive(dir.resolve(prefix + "-sources.jar"), root.resolve(module + "/src/main/java"), ".java", false);
                compareArchive(dir.resolve(prefix + "-javadoc.jar"), root.resolve(module + "/build/docs/javadoc"), "", false);
            } catch (IOException e) {
                throw new IOException(module + ": " + e.getMessage(), e);
            }
        }
        require(inventory.equals(expectedRepo), "Unexpected repository paths");
        var manifest = PublicationManifest.accepted(repository, version);
        require(before.equals(manifest.repositorySha256()), "Repository changed during semantic verification");
        PublicationRepository.checkStaging(manifest, repository);
        return manifest;
    }
    private static void checksum(Path sidecar, Path file, String algorithm) throws IOException {
        require(pythonStrip(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(read(sidecar, 1024))).toString()).equals(digest(read(file), algorithm)), "Bad checksum: " + sidecar);
    }
    private static String pythonStrip(String s) {
        int start = 0, end = s.length();
        while (start < end && whitespace(s.charAt(start))) start++;
        while (end > start && whitespace(s.charAt(end - 1))) end--;
        return s.substring(start, end);
    }
    private static boolean whitespace(char c) {
        return (c >= 9 && c <= 13) || (c >= 28 && c <= 32) || c == 0x85 || c == 0xa0
                || c == 0x1680 || (c >= 0x2000 && c <= 0x200a) || c == 0x2028 || c == 0x2029
                || c == 0x202f || c == 0x205f || c == 0x3000;
    }
    static Element xml(byte[] bytes) throws IOException {
        try {
            var f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setXIncludeAware(false); f.setExpandEntityReferences(false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = f.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("External XML entity rejected"); });
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
            });
            return builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
        } catch (javax.xml.parsers.ParserConfigurationException | SAXException e) { throw new IOException("Unsafe/malformed XML", e); }
    }
    private static List<Element> children(Element parent, String ns, String name) {
        List<Element> result = new ArrayList<>();
        for (var n = parent.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element e && name.equals(e.getLocalName()) && ns.equals(e.getNamespaceURI() == null ? "" : e.getNamespaceURI())) result.add(e);
        return result;
    }
    private static Element child(Element parent, String ns, String name) throws IOException {
        var items = children(parent, ns, name); require(!items.isEmpty(), "Missing XML element: " + name); return items.getFirst();
    }
    private static String text(Element parent, String ns, String path) throws IOException {
        for (String part : path.split("/")) parent = child(parent, ns, part);
        return leadingText(parent);
    }
    private static String leadingText(Element element) {
        StringBuilder result = new StringBuilder();
        for (var node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element) break;
            if (node.getNodeType() == org.w3c.dom.Node.TEXT_NODE || node.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE)
                result.append(node.getNodeValue());
        }
        return result.toString();
    }
    static void pom(byte[] bytes, String module, String artifact, String version) throws IOException {
        Element pom = xml(bytes);
        Map<String, String> fields = new HashMap<>(Map.of("groupId", "org.totipo", "artifactId", artifact, "version", version,
                "name", module.equals("core") ? "Totipo Java Core" : "Totipo Java NIO Storage", "url", URL,
                "licenses/license/name", "Apache License 2.0", "licenses/license/url", "https://www.apache.org/licenses/LICENSE-2.0.txt"));
        fields.putAll(Map.of("developers/developer/id", "ingon", "developers/developer/name", "Nikolay Petrov",
                "developers/developer/url", "https://github.com/ingon/", "scm/url", URL, "scm/connection", "scm:git:" + URL + ".git",
                "scm/developerConnection", "scm:git:ssh://git@github.com/totipo-org/totipo-java.git"));
        for (var e : fields.entrySet()) require(text(pom, POM_NS, e.getKey()).equals(e.getValue()), artifact + ": wrong " + e.getKey());
        require(!text(pom, POM_NS, "description").isEmpty(), "Missing description");
        List<Element> deps = children(child(pom, POM_NS, "dependencies"), POM_NS, "dependency");
        require(deps.size() == 1, "Wrong POM dependency count");
        var target = target(module, version);
        String[] keys = {"groupId", "artifactId", "version", "scope"};
        for (int i = 0; i < keys.length; i++) require(text(deps.getFirst(), POM_NS, keys[i]).equals(target.get(i)), "Wrong POM dependency " + keys[i]);
        require(pom.getElementsByTagNameNS(POM_NS, "optional").getLength() == 0 && pom.getElementsByTagNameNS(POM_NS, "classifier").getLength() == 0,
                "Unexpected optional/classifier dependency");
    }
    private static List<String> target(String module, String version) {
        return module.equals("core") ? List.of("org.bouncycastle", "bcprov-jdk18on", "1.86", "runtime") : List.of("org.totipo", "totipo-core", version, "compile");
    }
    static void metadata(byte[] bytes, Path directory, String module, String artifact, String prefix, String version, Set<String> names) throws IOException {
        var m = Json.object(Json.parse(bytes, StrictJson.MODULE));
        require(Map.of("group", "org.totipo", "module", artifact, "version", version, "attributes", Map.of("org.gradle.status", "release")).equals(m.get("component")), "Wrong module component");
        require("1.1".equals(m.get("formatVersion")), "Unexpected metadata format");
        String serialized = Json.dumps(m, false);
        require(!serialized.contains(":core") && !serialized.contains(":storage-nio"), "Project path leakage");
        List<?> variants = Json.array(m.get("variants"));
        require(variants.size() == 4, "Expected four variants");
        Set<String> seen = new HashSet<>();
        Map<String, String> suffixes = Map.of("apiElements", ".jar", "runtimeElements", ".jar", "sourcesElements", "-sources.jar", "javadocElements", "-javadoc.jar");
        for (Object item : variants) {
            var v = Json.object(item); String name = Json.string(v.get("name"));
            require(suffixes.containsKey(name) && seen.add(name), "Unexpected/duplicate variant");
            for (String prohibited : List.of("capabilities", "dependencyConstraints", "available-at")) {
                Object x = v.get(prohibited);
                require(x == null || x.equals(List.of()) || x.equals(Map.of()), "Unexpected variant metadata: " + prohibited);
            }
            var t = target(module, version);
            List<?> wanted = name.equals("runtimeElements") || (module.equals("storage-nio") && name.equals("apiElements"))
                    ? List.of(Map.of("group", t.get(0), "module", t.get(1), "version", Map.of("requires", t.get(2)))) : List.of();
            require(wanted.equals(v.getOrDefault("dependencies", List.of())), "Wrong module dependencies");
            if (name.equals("apiElements") || name.equals("runtimeElements"))
                require(BigInteger.valueOf(17).equals(Json.object(v.get("attributes")).get("org.gradle.jvm.version")), "Wrong Java target");
            List<?> files = Json.array(v.get("files")); require(files.size() == 1, "Wrong variant file count");
            var file = Json.object(files.getFirst()); String filename = prefix + suffixes.get(name);
            require(filename.equals(file.get("name")) && filename.equals(file.get("url")) && names.contains(filename), "Wrong module artifact");
            byte[] data = read(directory.resolve(filename));
            require(BigInteger.valueOf(data.length).equals(file.get("size")), "Wrong module size");
            require(file.containsKey("sha256"), "Missing module SHA-256");
            for (String algorithm : HASHES) if (file.containsKey(algorithm))
                require(digest(data, algorithm).equals(file.get(algorithm)), "Wrong module checksum");
        }
    }
    static Map<String, byte[]> archive(Path path) throws IOException {
        // Snapshot before ZipFile opens it; ZipFile requires a complete central directory.
        Path snapshot = Files.createTempFile("totipo-publication-", ".zip");
        try {
            Files.write(snapshot, read(path));
            try (ZipFile zip = new ZipFile(snapshot.toFile(), StandardCharsets.UTF_8)) {
                var entries = zip.entries(); Set<String> names = new HashSet<>(); Map<String, byte[]> result = new HashMap<>();
                long total = 0;
                while (entries.hasMoreElements()) {
                    var e = entries.nextElement(); String name = e.getName();
                    require(names.size() < 10_000 && names.add(name), "Duplicate/excessive ZIP entries: " + path);
                    require(!name.isEmpty() && !name.startsWith("/") && !name.contains("\\") && !name.contains(":"), "Invalid ZIP name");
                    String relative = e.isDirectory() ? name.substring(0, name.length() - 1) : name;
                    for (String part : relative.split("/", -1)) require(!part.isEmpty() && !part.equals(".") && !part.equals("..") && part.chars().noneMatch(c -> c < 32), "Invalid ZIP entry: " + name);
                    require(e.getSize() >= 0 && e.getSize() <= 16 * 1024 * 1024, "ZIP entry size limit");
                    byte[] data;
                    try (var in = zip.getInputStream(e)) { data = in.readNBytes(16 * 1024 * 1024 + 1); }
                    total += data.length;
                    require(data.length == e.getSize() && total <= 128L * 1024 * 1024, "ZIP decompression limit/size mismatch");
                    CRC32 crc = new CRC32(); crc.update(data); require(crc.getValue() == e.getCrc(), "Bad ZIP CRC");
                    if (e.isDirectory()) require(data.length == 0, "Nonempty ZIP directory"); else result.put(name, data);
                }
                // Validate local headers/data as well as the central directory. ZipFile alone
                // does not reject a substituted local entry name the way Python does.
                Set<String> localNames = new HashSet<>();
                try (var local = new java.util.zip.ZipInputStream(Files.newInputStream(snapshot), StandardCharsets.UTF_8)) {
                    java.util.zip.ZipEntry entry;
                    while ((entry = local.getNextEntry()) != null) {
                        require(localNames.add(entry.getName()) && names.contains(entry.getName()), "ZIP local/central name mismatch");
                        byte[] data = local.readNBytes(16 * 1024 * 1024 + 1);
                        require(data.length <= 16 * 1024 * 1024, "ZIP local decompression limit");
                        require(entry.isDirectory() ? data.length == 0 : Arrays.equals(data, result.get(entry.getName())), "ZIP local/central byte mismatch");
                        local.closeEntry();
                    }
                }
                require(localNames.equals(names), "ZIP local/central inventory mismatch");
                return result;
            }
        } finally { Files.deleteIfExists(snapshot); }
    }
    private static void compareArchive(Path jar, Path expectedRoot, String suffix, boolean classes) throws IOException {
        Map<String, byte[]> actual = archive(jar), expected = new HashMap<>();
        long total = 0;
        for (String name : inventory(expectedRoot)) if (name.endsWith(suffix)) {
            byte[] data = read(expectedRoot.resolve(name)); total += data.length;
            require(total <= 128L * 1024 * 1024, "Expected content limit"); expected.put(name, data);
            if (classes) require(data.length >= 8 && java.nio.ByteBuffer.wrap(data).getInt() == 0xCAFEBABE
                    && data[4] == 0 && data[5] == 0 && data[6] == 0 && data[7] == 61, "Not Java 17: " + name);
        }
        require(!expected.isEmpty(), "No expected content: " + expectedRoot);
        if (suffix.isEmpty()) require(expected.containsKey("index.html"), "Missing Javadoc index");
        expected.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        require(actual.keySet().equals(expected.keySet()), "Wrong archive membership: " + jar);
        for (var e : expected.entrySet()) require(Arrays.equals(e.getValue(), actual.get(e.getKey())), "Wrong archive bytes: " + jar + "!" + e.getKey());
    }
    public static byte[] consumerMetadata(PublicationManifest manifest, byte[] template) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(template)).toString();
        require(text.indexOf("   </components>") >= 0 && text.indexOf("   </components>") == text.lastIndexOf("   </components>"), "Ambiguous consumer template");
        List<String> components = new ArrayList<>();
        for (String module : PublicationManifest.MODULES) {
            List<String> artifacts = new ArrayList<>();
            for (var a : manifest.artifacts()) if (a.module().equals(module) && Set.of("binary", "pom", "module").contains(a.role()))
                artifacts.add("         <artifact name=\"" + Path.of(a.path()).getFileName() + "\"><sha256 value=\"" + a.sha256() + "\" origin=\"Verified local staging output\"/></artifact>");
            components.add("      <component group=\"org.totipo\" name=\"totipo-" + module + "\" version=\"" + manifest.version() + "\">\n" + String.join("\n", artifacts) + "\n      </component>");
        }
        return text.replace("   </components>", String.join("\n", components) + "\n   </components>").getBytes(StandardCharsets.UTF_8);
    }
}
