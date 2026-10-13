package org.totipo.tooling.release;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.totipo.tooling.release.TestResultsTest.ok;

/** Original Python pin-guard failure cases, independently executable after Python removal. */
public final class WorkflowPinsTest {
    private WorkflowPinsTest() {}
    public static void main(String[] args) throws Exception {
        String sha = "3d3c42e5aac5ba805825da76410c181273ba90b1", version = "v7.0.1";
        String valid = "  - uses: actions/checkout@" + sha + " # " + version;
        ok(WorkflowPins.inspect(valid).equals(java.util.Set.of("actions/checkout")));
        for (String entry : List.of("uses: actions/checkout@v7", "uses: actions/checkout@" + "a".repeat(40) + " # " + version,
            "uses: actions/checkout@" + sha + " # v7.0.0", "uses: other/action@" + sha + " # " + version,
            "uses: actions/checkout/subaction@" + sha + " # " + version, "uses: docker://unreviewed/image:latest",
            "uses: ./unreviewed-local-action", "'uses': actions/checkout@" + sha + " # " + version,
            "{uses: \"actions/checkout@v7\"}")) ReleaseTest.reject(() -> WorkflowPins.inspect(entry));
        ok(WorkflowPins.inspect("# uses: bad\n" + valid + "\r\n").size() == 1);
        Path root = Files.createTempDirectory("totipo-workflow-pins-");
        try {
            Path yaml = root.resolve(".github/workflows/test.yml");
            TestResultsTest.write(yaml, valid);
            ReleaseTest.reject(() -> WorkflowPins.check(root));
            TestResultsTest.write(yaml, valid + "\nuses: actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6 # v6.0.1\n"
                + "uses: cachix/install-nix-action@13d8dd58da0234aa297dedd986986ccb8e7f3e24 # v31.11.1\n");
            WorkflowPins.check(root);
            Files.delete(yaml); ReleaseTest.reject(() -> WorkflowPins.check(root));
            System.out.println("WorkflowPinsTest PASS: original guard negatives and complete pin inventory");
        } finally { PreparationApplicationTest.remove(root); }
    }
}
