package org.totipo.tooling.publication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import static org.totipo.tooling.publication.FilesSupport.*;

/** Independent verifier: explicit evidence outputs, with a narrow Gradle integration destination. */
public final class PublicationCli {
    private PublicationCli() {}
    public static void run(String[] args) throws IOException {
        require(args.length >= 1, usage());
        for (String arg : args) require(!arg.isEmpty(), "Empty publication argument");
        String operation = args[0];
        int count = switch (operation) { case "verify", "copy" -> 5; case "check" -> 4; default -> -1; };
        require(args.length == count, usage());
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        Path repo = Path.of(args[2]).toAbsolutePath().normalize();
        Path manifestPath = Path.of(args[3]).toAbsolutePath().normalize();
        if (operation.equals("verify")) {
            Path metadata = Path.of(args[4]).toAbsolutePath().normalize();
            for (Path output : new Path[] {manifestPath, metadata})
                require(!output.startsWith(repo) && (!output.startsWith(root)
                    || output.equals(root.resolve("build/publication-manifest.json"))
                    || output.equals(root.resolve("publishing/consumer-smoke/gradle/verification-metadata.xml"))),
                    "Unapproved evidence output location");
            require(!manifestPath.equals(metadata), "Evidence output paths overlap");
            // Fresh Git checkouts omit ignored/generated directories. Create them
            // through the same anchored NOFOLLOW operations used for artifact copies.
            directories(manifestPath.getParent()); directories(metadata.getParent());
            // Remove stale evidence before semantic checks. Manifest is the last successful write.
            delete(manifestPath); delete(metadata);
            try {
                String version = version(root);
                var manifest = PublicationVerifier.verify(repo, version);
                byte[] consumer = PublicationVerifier.consumerMetadata(manifest,
                        read(root.resolve("publishing/consumer-smoke/verification-metadata-template.xml"), 65_536));
                writeEvidence(manifest, consumer, manifestPath, metadata,
                        () -> PublicationRepository.checkStaging(manifest, repo));
            } catch (IOException | RuntimeException e) {
                try { delete(manifestPath); delete(metadata); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
                throw e;
            }
        } else {
            String version = version(root);
            var manifest = PublicationManifest.parse(read(manifestPath, org.totipo.tooling.StrictJson.MANIFEST.bytes()), version);
            if (operation.equals("check")) PublicationRepository.checkStaging(manifest, repo);
            else PublicationRepository.copy(manifest, repo, Path.of(args[4]));
        }
        System.out.println("publication " + operation + ": 10/10 accepted artifacts passed");
    }
    @FunctionalInterface interface Recheck { void run() throws IOException; }
    // Package-private failure-injection seam; production passes the fresh staging check.
    static void writeEvidence(PublicationManifest manifest, byte[] consumer, Path manifestPath,
                              Path metadata, Recheck recheck) throws IOException {
        try {
            writeAtomic(metadata, consumer);
            recheck.run();
            writeAtomic(manifestPath, manifest.bytes());
        } catch (IOException | RuntimeException e) {
            try { delete(manifestPath); delete(metadata); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
    private static String version(Path root) throws IOException {
        String version = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(read(root.resolve("VERSION"), 256))).toString();
        if (version.endsWith("\n")) version = version.substring(0, version.length() - 1);
        PublicationManifest.expected(version);
        return version;
    }
    public static String usage() {
        return "publication verify <root> <repository> <manifest-output> <consumer-metadata-output> | "
                + "publication check <root> <repository> <manifest> | publication copy <root> <repository> <manifest> <destination>";
    }
}
