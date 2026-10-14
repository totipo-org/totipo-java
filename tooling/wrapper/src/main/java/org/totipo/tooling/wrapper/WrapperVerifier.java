package org.totipo.tooling.wrapper;

import java.io.IOException;
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
    private WrapperVerifier() {}

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: WrapperVerifier <wrapper-jar> <wrapper-properties>");
            System.exit(2);
            return;
        }
        try {
            verify(Path.of(args[0]), Path.of(args[1]));
            System.out.println("Gradle wrapper JAR and complete properties file verified");
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
        try (var input = Files.newInputStream(properties)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        if (!HexFormat.of().formatHex(digest.digest()).equals(PROPERTIES_SHA256)) {
            throw new IOException("Gradle wrapper properties SHA-256 mismatch: " + properties);
        }
    }
}
