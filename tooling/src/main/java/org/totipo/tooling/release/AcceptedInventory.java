package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.totipo.tooling.publication.PublicationManifest;
import org.totipo.tooling.publication.PublicationVerifier;

/** Cross-job schema-1 transport. Received JSON is an expectation, never staging acceptance. */
public final class AcceptedInventory {
    private final PublicationManifest manifest;
    private AcceptedInventory(PublicationManifest manifest) { this.manifest = manifest; }
    public static AcceptedInventory verify(Path root, Path repository, String version) throws IOException {
        SourceFiles.require(new SourceFiles(root).text("VERSION").equals(version + "\n"), "Inventory VERSION differs");
        return new AcceptedInventory(PublicationVerifier.verify(root, repository, version));
    }
    public String json() throws IOException { return manifest.compact(); }
    public byte[] bytes() throws IOException { return json().getBytes(StandardCharsets.UTF_8); }
    public String version() { return manifest.version(); }
    public static void compare(Path manifest, Path staging, String version, byte[] expected) throws IOException {
        AcceptedArtifacts.compare(manifest, staging, version, expected);
    }
}
