package org.totipo.tooling.release;

import java.nio.file.Files;
import java.nio.file.Path;
import org.totipo.tooling.publication.AcceptedFixture;

/** Host differential bridge, test classes only. No live authorization or mutation beyond disposable fixtures. */
public final class AuthorizationFixture {
    private AuthorizationFixture() {}
    public static void main(String[] args) throws Exception {
        try {
            Path root = Path.of(args[1]);
            switch (args[0]) {
                case "create" -> { Files.createDirectories(root); AcceptedFixture.create(root); AuthorizationTest.signatures(root); }
                case "policy" -> {
                    var services = new RecordedServices(root);
                    var inputs = ReleaseInputs.from(System.getenv());
                    var github = new RemoteReleaseState.GitHub(services.http(), System.getenv("GITHUB_REPOSITORY"));
                    var decision = PublicationPolicy.assess(PublicationPolicy.Boundary.IMMEDIATELY_BEFORE_UPLOAD,
                            () -> RemoteReleaseState.inspect(inputs.version(), services.http(), github, System.getenv("GITHUB_RUN_ID"),
                                    Integer.parseInt(System.getenv("GITHUB_RUN_ATTEMPT")), inputs.commit()));
                    System.out.println(decision.disposition());
                }
                case "inventory" -> AcceptedArtifacts.inventory(AuthorizationTest.manifest(root), AuthorizationTest.staging(root), "0.2.0")
                        .entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(e -> System.out.println(e.getKey() + "\t" + e.getValue()));
                case "compare" -> AcceptedArtifacts.compare(AuthorizationTest.manifest(root), AuthorizationTest.staging(root), "0.2.0",
                        EvidenceFiles.read(Path.of(args[2]), org.totipo.tooling.StrictJson.MANIFEST.bytes()));
                case "signed" -> AuthorizationTest.signed(root);
                case "secrets" -> SecretPreflight.check(System.getenv(), key -> {
                    if (!args[2].equals("0")) throw new java.io.IOException("fake packet failure");
                    return GpgPacketInspector.metadata(EvidenceFiles.read(root, GpgPacketInspector.MAX_OUTPUT));
                });
                default -> throw new AssertionError("Unexpected fixture command");
            }
        } catch (java.io.IOException | RuntimeException failure) {
            System.err.println("Fixture evidence rejected"); System.exit(1);
        }
    }
}
