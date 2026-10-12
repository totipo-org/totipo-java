package org.totipo.tooling.wrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic JDK-only fixtures; optional third argument retains parity evidence. */
public final class WrapperVerifierTest {
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
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Expected canonical JAR, properties, optional evidence directory");
        }
        Path root = args.length == 3 ? Files.createDirectories(Path.of(args[2]))
                : Files.createTempDirectory("wrapper-verifier-test-");
        try {
            var test = new WrapperVerifierTest(root, Path.of(args[0]), Path.of(args[1]));
            test.fixtures();
            test.run();
        } finally {
            if (args.length == 2) {
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
        fixtures.add(new Fixture(name, jar, properties, accept));
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
