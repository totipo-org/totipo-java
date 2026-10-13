package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.totipo.tooling.StrictJson;
import org.totipo.tooling.publication.PublicationManifest;
import org.totipo.tooling.publication.PublicationRepository;
import static org.totipo.tooling.release.SourceFiles.require;

/** Existing J2B acceptance schema and fresh staging authority; never accepts hashes alone. */
public final class AcceptedArtifacts {
    private AcceptedArtifacts() {}
    public static PublicationManifest fresh(Path manifest, Path staging, String version) throws IOException {
        require(ReleaseInputs.validVersion(version), "Invalid release version");
        var accepted = PublicationManifest.parse(EvidenceFiles.read(manifest, StrictJson.MANIFEST.bytes()), version);
        PublicationRepository.checkStaging(accepted, staging);
        return accepted;
    }
    private static Map<String, String> hashes(PublicationManifest manifest) {
        var result = new LinkedHashMap<String, String>();
        for (var artifact : manifest.artifacts()) result.put(artifact.path(), artifact.sha256());
        return Map.copyOf(result);
    }
    public static Map<String, String> inventory(Path manifest, Path staging, String version) throws IOException {
        return hashes(fresh(manifest, staging, version));
    }
    public static void compare(Path manifest, Path staging, String version, byte[] expectedInventory) throws IOException {
        var expected = PublicationManifest.parse(expectedInventory, version);
        require(inventory(manifest, staging, version).equals(hashes(expected)), "Fresh artifact inventory differs from preflight");
    }
    public static String signingFilename(PublicationManifest.Artifact artifact) {
        return switch (artifact.role()) {
            case "pom" -> "pom-default.xml";
            case "module" -> "module.json";
            default -> Path.of(artifact.path()).getFileName().toString();
        };
    }
    /** Marker/byte identity evidence only: no cryptographic validation, signer identity or trust claim. */
    public static void signed(Path root, Path manifest, Path staging, String version) throws IOException {
        var accepted = fresh(manifest, staging, version);
        for (var artifact : accepted.artifacts()) {
            var input = EvidenceFiles.signingInput(root.resolve(artifact.module()).resolve("build"), signingFilename(artifact));
            require(SourceFiles.sha256(input.unsigned()).equals(artifact.sha256()), "Signed input differs from reviewed unsigned artifact");
            String signature = new String(input.signature(), StandardCharsets.US_ASCII);
            require(signature.contains("-----BEGIN PGP SIGNATURE-----") && signature.contains("-----END PGP SIGNATURE-----"),
                    "Missing detached signature markers");
        }
        // Detect staging edits during evidence acquisition, without claiming an atomic filesystem snapshot.
        PublicationRepository.checkStaging(accepted, staging);
    }
}
