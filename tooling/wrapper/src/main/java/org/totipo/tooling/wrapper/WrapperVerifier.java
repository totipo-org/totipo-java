package org.totipo.tooling.wrapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Authenticates reviewed wrapper inputs without executing them or using the network. */
public final class WrapperVerifier {
    private static final String JAR_SHA256 =
            "238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5";
    private static final String PROPERTIES_SHA256 =
            "e81b90975868be2513ca00d6a5a01303a406a5cb01a306210b887e983e4b17ae";
    private static final String URL =
            "https\\://services.gradle.org/distributions/gradle-9.8.0-bin.zip";
    private static final String DISTRIBUTION_SHA256 =
            "bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c";

    private WrapperVerifier() {}

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: WrapperVerifier <wrapper-jar> <wrapper-properties>");
            System.exit(2);
            return;
        }
        try {
            verify(Path.of(args[0]), Path.of(args[1]));
            System.out.println("Gradle wrapper JAR, complete properties file and distribution declarations verified");
        } catch (IOException | InvalidPathException | SecurityException failure) {
            System.err.println("Wrapper verification failed: " + failure);
            System.exit(1);
        }
    }

    static void verify(Path jar, Path properties) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("JDK must provide SHA-256", impossible);
        }
        try (var input = Files.newInputStream(jar)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        if (!HexFormat.of().formatHex(digest.digest()).equals(JAR_SHA256)) {
            throw new IOException("Gradle wrapper JAR SHA-256 mismatch: " + jar);
        }
        // Hash and decode one byte snapshot; never parse a second read of the file.
        byte[] bytes = Files.readAllBytes(properties);
        if (!HexFormat.of().formatHex(digest.digest(bytes)).equals(PROPERTIES_SHA256)) {
            throw new IOException("Gradle wrapper properties SHA-256 mismatch: " + properties);
        }
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        validateDeclarations(text, properties);
    }

    // Kept independently testable as defense in depth; verify() always authenticates first.
    static void validateDeclarations(String text, Path properties) throws IOException {
        int urls = 0;
        int checksums = 0;
        int start = 0;
        for (int end = 0; end <= text.length(); end++) {
            if (end != text.length() && !lineBoundary(text.charAt(end))) {
                continue;
            }
            String line = text.substring(start, end);
            int left = 0;
            int right = line.length();
            while (left < right && pythonWhitespace(line.charAt(left))) {
                left++;
            }
            while (right > left && pythonWhitespace(line.charAt(right - 1))) {
                right--;
            }
            boolean comment = left < right && (line.charAt(left) == '#' || line.charAt(left) == '!');
            if (!comment && right > left && line.charAt(right - 1) == '\\') {
                throw new IOException("Unexpected continued Gradle wrapper property: " + properties);
            }
            if (declaration(line, left, "distributionUrl")) {
                urls++;
                requireExact(line, "distributionUrl", URL);
            }
            if (declaration(line, left, "distributionSha256Sum")) {
                checksums++;
                requireExact(line, "distributionSha256Sum", DISTRIBUTION_SHA256);
            }
            if (end < text.length() && text.charAt(end) == '\r'
                    && end + 1 < text.length() && text.charAt(end + 1) == '\n') {
                end++;
            }
            start = end + 1;
        }
        if (urls != 1 || checksums != 1) {
            throw new IOException("Expected exactly one distributionUrl and distributionSha256Sum"
                    + " declaration; found " + urls + " and " + checksums + ": " + properties);
        }
    }

    private static void requireExact(String line, String key, String value) throws IOException {
        if (!line.equals(key + "=" + value)) {
            throw new IOException("Gradle wrapper " + key + " mismatch or duplicate");
        }
    }

    private static boolean declaration(String line, int start, String key) {
        if (!line.startsWith(key, start)) {
            return false;
        }
        int next = start + key.length();
        return next == line.length() || pythonWhitespace(line.charAt(next))
                || line.charAt(next) == '=' || line.charAt(next) == ':';
    }

    // Python str.splitlines(), including boundaries not recognized by String.lines().
    private static boolean lineBoundary(char c) {
        return c == '\n' || c == '\r' || c == '\u000b' || c == '\f'
                || c >= 0x1c && c <= 0x1e || c == 0x85 || c == 0x2028 || c == 0x2029;
    }

    // Python str.isspace()/re \s, not Java trim(), isWhitespace(), or default regex \s.
    private static boolean pythonWhitespace(char c) {
        return c >= 0x09 && c <= 0x0d || c >= 0x1c && c <= 0x20
                || c == 0x85 || c == 0xa0 || c == 0x1680
                || c >= 0x2000 && c <= 0x200a || c == 0x2028 || c == 0x2029
                || c == 0x202f || c == 0x205f || c == 0x3000;
    }
}
