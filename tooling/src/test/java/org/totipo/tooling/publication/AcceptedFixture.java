package org.totipo.tooling.publication;

import java.nio.file.Files;
import java.nio.file.Path;

/** Reuses J2B semantic fixtures; no new publication-verifier implementation. */
public final class AcceptedFixture {
    private AcceptedFixture() {}
    public static void create(Path root) throws Exception {
        PublicationTest.fixture(root);
        var accepted = PublicationVerifier.verify(root, root.resolve("build/repository"), "0.2.0");
        Files.write(root.resolve("build/publication-manifest.json"), accepted.bytes());
    }
}
