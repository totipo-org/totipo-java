package org.totipo.tooling.publication;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.totipo.tooling.StrictJson;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import static org.totipo.tooling.publication.FilesSupport.*;

/** Checks essential Maven metadata and the complete bytes of ten opaque artifacts. */
public final class PublicationVerifier {
    private static final Set<String> HASHES = Set.of("md5", "sha1", "sha256", "sha512");
    private static final String POM_NS = "http://maven.apache.org/POM/4.0.0";
    private PublicationVerifier() {}
    public static PublicationManifest verify(Path repository, String version) throws IOException {
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
                require(indexed.size() == 1 && text(versions, "", "version").equals(version), "Unexpected staged versions");
                expectedRepo.add(index);
                for (String algorithm : HASHES) if (inventory.contains(index + "." + algorithm)) {
                    checksum(repository.resolve(index + "." + algorithm), repository.resolve(index), algorithm);
                    expectedRepo.add(index + "." + algorithm);
                }
                Path dir = repository.resolve(directory);
                pom(read(dir.resolve(prefix + ".pom"), 65_536), module, artifact, version);
                metadata(read(dir.resolve(prefix + ".module"), StrictJson.MODULE.bytes()), dir, module, artifact, prefix, version, names);
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
        var items = children(parent, ns, name); require(items.size() == 1, "Missing/duplicate XML element: " + name); return items.getFirst();
    }
    private static String text(Element parent, String ns, String path) throws IOException {
        for (String part : path.split("/")) parent = child(parent, ns, part);
        for (var n = parent.getFirstChild(); n != null; n = n.getNextSibling())
            require(!(n instanceof Element), "Nested XML value: " + path);
        return parent.getTextContent().strip();
    }
    static void pom(byte[] bytes, String module, String artifact, String version) throws IOException {
        Element pom = xml(bytes);
        require("project".equals(pom.getLocalName()) && POM_NS.equals(pom.getNamespaceURI()), "Wrong POM root");
        for (var field : Map.of("groupId", "org.totipo", "artifactId", artifact, "version", version).entrySet())
            require(text(pom, POM_NS, field.getKey()).equals(field.getValue()), "Wrong POM " + field.getKey());
        for (String field : List.of("name", "description", "url", "licenses/license/name", "licenses/license/url",
                "developers/developer/id", "developers/developer/name", "scm/url", "scm/connection"))
            require(!text(pom, POM_NS, field).isEmpty(), "Missing publication metadata: " + field);
        List<Element> deps = children(child(pom, POM_NS, "dependencies"), POM_NS, "dependency");
        require(deps.size() == 1, "Wrong POM dependency count");
        var target = target(module, version);
        String[] keys = {"groupId", "artifactId", "version", "scope"};
        for (int i = 0; i < keys.length; i++) require(text(deps.getFirst(), POM_NS, keys[i]).equals(target.get(i)), "Wrong POM dependency " + keys[i]);
        Element dependency = deps.getFirst();
        for (var field : Map.of("optional", "false", "type", "jar").entrySet())
            if (!children(dependency, POM_NS, field.getKey()).isEmpty())
                require(text(dependency, POM_NS, field.getKey()).equals(field.getValue()), "Wrong dependency " + field.getKey());
        require(children(dependency, POM_NS, "classifier").isEmpty() && children(dependency, POM_NS, "exclusions").isEmpty(),
                "Unexpected classifier/exclusions");
    }
    private static List<String> target(String module, String version) {
        return module.equals("core") ? List.of("org.bouncycastle", "bcprov-jdk18on", "1.86", "runtime") : List.of("org.totipo", "totipo-core", version, "compile");
    }
    static void metadata(byte[] bytes, Path directory, String module, String artifact, String prefix, String version, Set<String> names) throws IOException {
        var m = Json.object(Json.parse(bytes, StrictJson.MODULE));
        var component = Json.object(m.get("component"));
        for (var field : Map.of("group", "org.totipo", "module", artifact, "version", version).entrySet())
            require(field.getValue().equals(component.get(field.getKey())), "Wrong module " + field.getKey());
        require("1.1".equals(m.get("formatVersion")), "Unexpected metadata format");
        List<?> variants = Json.array(m.get("variants"));
        require(variants.size() == 4, "Expected four variants");
        Set<String> seen = new HashSet<>(), variantNames = new HashSet<>();
        for (Object item : variants) {
            var v = Json.object(item); String name = Json.string(v.get("name"));
            require(!name.isBlank() && variantNames.add(name), "Missing/duplicate variant name");
            var attributes = Json.object(v.get("attributes"));
            String category = Json.string(attributes.get("org.gradle.category"));
            String usage = Json.string(attributes.get("org.gradle.usage"));
            String role;
            if (category.equals("library")) {
                require(usage.equals("java-api") || usage.equals("java-runtime"), "Wrong library usage");
                require(BigInteger.valueOf(17).equals(attributes.get("org.gradle.jvm.version")), "Wrong Java target");
                role = usage;
            } else {
                require(category.equals("documentation") && usage.equals("java-runtime"), "Wrong documentation usage");
                role = Json.string(attributes.get("org.gradle.docstype"));
                require(role.equals("sources") || role.equals("javadoc"), "Wrong documentation type");
            }
            require(seen.add(role), "Duplicate publication variant role");
            for (String prohibited : List.of("capabilities", "dependencyConstraints", "available-at")) {
                Object x = v.get(prohibited);
                require(x == null || x.equals(List.of()) || x.equals(Map.of()), "Unexpected variant metadata: " + prohibited);
            }
            var t = target(module, version);
            List<?> wanted = role.equals("java-runtime") || (module.equals("storage-nio") && role.equals("java-api"))
                    ? List.of(Map.of("group", t.get(0), "module", t.get(1), "version", Map.of("requires", t.get(2)))) : List.of();
            require(wanted.equals(v.getOrDefault("dependencies", List.of())), "Wrong module dependencies");
            List<?> files = Json.array(v.get("files")); require(files.size() == 1, "Wrong variant file count");
            var file = Json.object(files.getFirst()); String filename = prefix + (role.equals("sources") ? "-sources.jar" : role.equals("javadoc") ? "-javadoc.jar" : ".jar");
            require(filename.equals(file.get("name")) && filename.equals(file.get("url")) && names.contains(filename), "Wrong module artifact");
            byte[] data = read(directory.resolve(filename));
            require(BigInteger.valueOf(data.length).equals(file.get("size")), "Wrong module size");
            require(file.containsKey("sha256"), "Missing module SHA-256");
            for (String algorithm : HASHES) if (file.containsKey(algorithm))
                require(digest(data, algorithm).equals(file.get(algorithm)), "Wrong module checksum");
        }
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
