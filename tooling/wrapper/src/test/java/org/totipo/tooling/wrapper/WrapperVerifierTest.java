package org.totipo.tooling.wrapper;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/** Deterministic JDK-only fixtures; Python source pin audit; optional fourth argument retains parity evidence. */
public final class WrapperVerifierTest {
    // Independent reviewed test pins, never obtained from the inputs being checked.
    private static final String JAR_SHA256 =
            "238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5";
    private static final String PROPERTIES_SHA256 =
            "e81b90975868be2513ca00d6a5a01303a406a5cb01a306210b887e983e4b17ae";
    private record SemanticFixture(String name, String text, boolean accept) {}
    private final List<SemanticFixture> semantics = new ArrayList<>();
    private record Fixture(String name, Path jar, Path properties, boolean accept) {}
    private final Path root;
    private final Path jar;
    private final String original;
    private final String url;
    private final String checksum;
    private final List<Fixture> fixtures = new ArrayList<>();

    private WrapperVerifierTest(Path root, Path sourceJar, Path sourceProperties) throws IOException {
        this.root = root;
        jar = Files.copy(sourceJar, root.resolve("reviewed.jar"));
        original = Files.readString(sourceProperties);
        url = original.lines().filter(s -> s.startsWith("distributionUrl=")).findFirst().orElseThrow();
        checksum = original.lines().filter(s -> s.startsWith("distributionSha256Sum="))
                .findFirst().orElseThrow();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("Expected canonical JAR, properties, Python source, optional evidence directory");
        }
        Path root = args.length == 4 ? Files.createDirectories(Path.of(args[3]))
                : Files.createTempDirectory("wrapper-verifier-test-");
        try {
            var test = new WrapperVerifierTest(root, Path.of(args[0]), Path.of(args[1]));
            test.identities(Path.of(args[2]));
            test.fixtures();
            test.run();
        } finally {
            if (args.length == 3) {
                try (var paths = Files.walk(root)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                }
            }
        }
    }

    private void property(String name, String value, boolean accept) throws IOException {
        Path properties = root.resolve(name + ".properties");
        Files.writeString(properties, value);
        // Retain J2A raw-declaration expectations independently; file authentication
        // accepts only the original bytes, including all otherwise harmless variants.
        semantics.add(new SemanticFixture(name, value, accept));
        fixtures.add(new Fixture(name, jar, properties, value.equals(original)));
    }

    private void identities(Path pythonSource) throws Exception {
        String python = Files.readString(pythonSource);
        for (String name : List.of("JAR_SHA256", "PROPERTIES_SHA256")) {
            String expected = name.equals("JAR_SHA256") ? JAR_SHA256 : PROPERTIES_SHA256;
            Path input = name.equals("JAR_SHA256") ? jar : root.resolve("identity.properties");
            if (name.equals("PROPERTIES_SHA256")) {
                Files.writeString(input, original);
            }
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
            var field = WrapperVerifier.class.getDeclaredField(name);
            field.setAccessible(true);
            String pythonName = name.equals("JAR_SHA256") ? "expected_jar" : "expected_properties";
            var matcher = Pattern.compile("(?m)^" + pythonName + " = \"([0-9a-f]{64})\"$")
                    .matcher(python);
            if (!expected.equals(actual) || !expected.equals(field.get(null))
                    || !matcher.find() || !expected.equals(matcher.group(1)) || matcher.find()) {
                throw new AssertionError("Independent pin mismatch: " + name);
            }
        }
        // Canonical verification enforces Java declarations; independently assert Python pins.
        if (!python.contains("\"distributionUrl\": r\"" + url.substring(url.indexOf('=') + 1) + "\"")
                || !python.contains("\"distributionSha256Sum\": \""
                + checksum.substring(checksum.indexOf('=') + 1) + "\"")) {
            throw new AssertionError("Python distribution declaration pins differ");
        }
        System.out.println("Independent canonical digests and Java/Python pin consistency passed");
    }

    private void fixtures() throws IOException {
        property("original", original, true);
        property("lf", url + "\n" + checksum + "\n", true);
        property("crlf", url + "\r\n" + checksum + "\r\n", true);
        property("no-final-newline", url + "\n" + checksum, true);
        property("comments-and-unrelated", "# comment\\\n \t! comment\\\nother=value\n" + original, true);
        property("commented-misleading-extras", "#distributionUrl=bad\n!distributionSha256Sum=bad\n"
                + original, true);
        property("key-prefix-unrelated", original + "distributionUrlExtra=bad\n", true);
        property("wrong-url", original.replace(url, "distributionUrl=https://example.invalid/x"), false);
        property("wrong-checksum", original.replace(checksum, "distributionSha256Sum=00"), false);
        property("missing-url", original.replace(url, ""), false);
        property("missing-checksum", original.replace(checksum, ""), false);
        property("duplicate-url", original + url + "\n", false);
        property("duplicate-checksum", original + checksum + "\n", false);
        property("different-duplicate-url", original + "distributionUrl=bad\n", false);
        property("commented-only-url", original.replace(url, "#" + url), false);
        property("commented-only-checksum", original.replace(checksum, "!" + checksum), false);
        property("url-leading-space", original.replace(url, " " + url), false);
        property("url-trailing-space", original.replace(url, url + " "), false);
        property("url-leading-character", original.replace(url, "x" + url), false);
        property("url-trailing-character", original.replace(url, url + "x"), false);
        property("checksum-leading-tab", original.replace(checksum, "\t" + checksum), false);
        property("checksum-trailing-comment", original.replace(checksum, checksum + "#comment"), false);
        property("url-colon-separator", original.replace(url, url.replaceFirst("=", ":")), false);
        property("url-space-separator", original.replace(url, url.replaceFirst("=", " ")), false);
        property("url-space-before-equals", original.replace(url, url.replaceFirst("=", " =")), false);
        property("url-space-after-equals", original.replace(url, url.replaceFirst("=", "= ")), false);
        property("unescaped-url", original.replace(url, url.replace("\\:", ":")), false);
        property("escaped-key-extra", original + "distribution\\u0055rl=bad\n", true);
        property("escaped-key-only", original.replace("distributionUrl=", "distribution\\u0055rl="), false);
        property("protected-continuation", original.replace(url, url + "\\\ncontinued"), false);
        property("unrelated-continuation", original + "other=x\\\ncontinued\n", false);
        property("double-backslash-continuation", original + "other=x\\\\\ncontinued\n", false);
        property("continuation-trailing-space", original + "other=x\\ \t\ncontinued\n", false);
        property("empty-properties", "", false);
        property("bom-url", "\ufeff" + url + "\n" + checksum, false);
        property("nul-url", original.replace(url, url + "\0"), false);
        property("non-whitespace-prefix-extra", original + "\u200bdistributionUrl=bad\n", true);
        property("non-whitespace-suffix-extra", original + "distributionUrl\u200b=bad\n", true);
        // All Python line boundaries, individually, including CRLF's single-boundary behavior.
        int[] boundaries = {0x0a, 0x0d, 0x0b, 0x0c, 0x1c, 0x1d, 0x1e, 0x85, 0x2028, 0x2029};
        for (int cp : boundaries) {
            property("line-boundary-" + Integer.toHexString(cp), url + (char) cp + checksum, true);
            property("boundary-duplicate-" + Integer.toHexString(cp),
                    original + "#" + (char) cp + url, false);
        }
        // Every Python whitespace code point: leading declarations and rstrip continuations.
        int[] whitespace = {9, 10, 11, 12, 13, 28, 29, 30, 31, 32, 0x85, 0xa0, 0x1680,
                0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007,
                0x2008, 0x2009, 0x200a, 0x2028, 0x2029, 0x202f, 0x205f, 0x3000};
        for (int cp : whitespace) {
            String id = Integer.toHexString(cp);
            property("whitespace-duplicate-" + id, original + (char) cp + url, false);
            property("whitespace-key-delimiter-" + id,
                    original + "distributionUrl" + (char) cp + "=bad", false);
            property("whitespace-continuation-" + id, original + "other=x\\" + (char) cp, false);
            property("whitespace-comment-" + id, original + (char) cp + "#comment\\", true);
        }

        property("added-comment", original + "# harmless comment\n", true);
        property("added-unrelated", original + "unrelated=value\n", true);
        property("canonical-crlf", original.replace("\n", "\r\n"), true);
        property("canonical-no-final-newline", original.substring(0, original.length() - 1), true);
        property("reordered-declarations", original.replace(url, "PLACEHOLDER")
                .replace(checksum, url).replace("PLACEHOLDER", checksum), true);
        property("trailing-whitespace", original + " \n", true);
        property("truncated-properties", original.substring(0, original.length() / 2), false);
        property("one-byte-properties", original.replace("networkTimeout=10000", "networkTimeout=10001"), true);
        String escapedUrl = "distribution\\u0055rl";
        String escapedChecksum = "distributionSha256\\u0053um";
        String override = escapedUrl + "=https\\://example.invalid/other.zip\n";
        property("escaped-url-override-after", original + override, true);
        property("escaped-url-override-before", override + original, true);
        property("escaped-url-same-after", original + escapedUrl + url.substring(url.indexOf('=')) + "\n", true);
        property("escaped-url-same-before", escapedUrl + url.substring(url.indexOf('=')) + "\n" + original, true);
        property("escaped-checksum-after", original + escapedChecksum + "=00\n", true);
        property("escaped-checksum-before", escapedChecksum + "=00\n" + original, true);
        property("escaped-multiple-after", original + "distrib\\u0075tion\\u0055rl=bad\n", true);
        property("escaped-multiple-before", "distrib\\u0075tion\\u0055rl=bad\n" + original, true);
        // Prove the actual JDK parser interprets the appended escaped key as an override.
        Path bypass = root.resolve("escaped-url-override-after.properties");
        Properties loaded = new Properties();
        try (var input = Files.newInputStream(bypass)) {
            loaded.load(input);
        }
        if (!"https://example.invalid/other.zip".equals(loaded.getProperty("distributionUrl"))) {
            throw new AssertionError("Properties.load did not interpret the escaped URL override");
        }
        Properties before = new Properties();
        before.load(new StringReader(override + original));
        if (!"https://services.gradle.org/distributions/gradle-9.8.0-bin.zip"
                .equals(before.getProperty("distributionUrl"))) {
            throw new AssertionError("Properties.load did not honor declaration order");
        }
        System.out.println("Properties.load escaped-key override and declaration order proved");
        Path invalid = root.resolve("invalid-utf8.properties");
        Files.write(invalid, new byte[] {(byte) 0xc3, 0x28});
        fixtures.add(new Fixture("invalid-utf8", jar, invalid, false));
        Path invalidComment = root.resolve("invalid-utf8-comment.properties");
        Files.write(invalidComment, concat(original.getBytes(StandardCharsets.UTF_8),
                new byte[] {'#', (byte) 0xff}));
        fixtures.add(new Fixture("invalid-utf8-comment", jar, invalidComment, false));
        Path props = root.resolve("original.properties");
        byte[] jarBytes = Files.readAllBytes(jar);
        byte[] changed = jarBytes.clone();
        changed[changed.length / 2] ^= 1;
        for (var entry : List.of(
                new JarMutation("one-byte-jar", changed),
                new JarMutation("empty-jar", new byte[0]),
                new JarMutation("truncated-jar", java.util.Arrays.copyOf(jarBytes, jarBytes.length / 2)))) {
            Path path = root.resolve(entry.name() + ".jar");
            Files.write(path, entry.bytes());
            fixtures.add(new Fixture(entry.name(), path, props, false));
        }
        fixtures.add(new Fixture("missing-jar", root.resolve("absent.jar"), props, false));
        fixtures.add(new Fixture("missing-properties", jar, root.resolve("absent.properties"), false));
        fixtures.add(new Fixture("directory-jar", root, props, false));
        fixtures.add(new Fixture("directory-properties", jar, root, false));
    }

    private record JarMutation(String name, byte[] bytes) {}

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = java.util.Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private void run() throws Exception {
        for (SemanticFixture fixture : semantics) {
            boolean actual;
            try {
                WrapperVerifier.validateDeclarations(fixture.text(), root.resolve(fixture.name()));
                actual = true;
            } catch (IOException expected) {
                actual = false;
            }
            if (actual != fixture.accept()) {
                throw new AssertionError("Raw declaration regression: " + fixture.name());
            }
        }
        System.out.println("Independent raw declaration tests passed: " + semantics.size());
        List<String> evidence = new ArrayList<>();
        evidence.add("name\tjar\tproperties\texpected\tjava");
        int accepted = 0;
        for (Fixture fixture : fixtures) {
            boolean actual;
            try {
                WrapperVerifier.verify(fixture.jar(), fixture.properties());
                actual = true;
            } catch (IOException expected) {
                if (expected.toString().isBlank()) {
                    throw new AssertionError("Missing diagnostic: " + fixture.name());
                }
                actual = false;
            }
            if (actual != fixture.accept()) {
                throw new AssertionError("Unexpected disposition: " + fixture.name());
            }
            // Exercise the real CLI as well as the direct API for every input.
            cli(fixture.accept() ? 0 : 1, fixture.jar().toString(), fixture.properties().toString());
            if (actual) {
                accepted++;
            }
            evidence.add(fixture.name() + "\t" + fixture.jar() + "\t" + fixture.properties()
                    + "\t" + disposition(fixture.accept()) + "\t" + disposition(actual));
        }
        cli(2);
        cli(2, jar.toString());
        cli(2, jar.toString(), root.resolve("original.properties").toString(), "extra");
        cli(1, "", root.resolve("original.properties").toString());
        try {
            Path.of("\0");
            throw new AssertionError("NUL path accepted");
        } catch (java.nio.file.InvalidPathException expectedFailure) {
            // NUL cannot be passed through OS argv; main catches this same path exception.
        }
        Files.write(root.resolve("fixtures.tsv"), evidence);
        System.out.println("Wrapper tests passed: " + fixtures.size() + " fixtures ("
                + accepted + " ACCEPT, " + (fixtures.size() - accepted)
                + " REJECT), direct API and CLI; 4 additional CLI probes and an invalid-path assertion");
    }

    private static String disposition(boolean accept) {
        return accept ? "ACCEPT" : "REJECT";
    }

    private static void cli(int expected, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), WrapperVerifier.class.getName()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (var input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (process.waitFor() != expected || output.isBlank()) {
            throw new AssertionError("CLI expected exit " + expected + ": " + output);
        }
    }
}
