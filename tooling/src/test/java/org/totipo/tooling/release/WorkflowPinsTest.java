package org.totipo.tooling.release;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.totipo.tooling.release.TestResultsTest.ok;

/** Immutable action references and mutations of the actual reviewed composite/workflows. */
public final class WorkflowPinsTest {
    private WorkflowPinsTest() {}
    public static void main(String[] args) throws Exception {
        String sha = "3d3c42e5aac5ba805825da76410c181273ba90b1", version = "v7.0.1";
        String valid = "  - uses: actions/checkout@" + sha + " # " + version;
        ok(WorkflowPins.inspect(valid).equals(java.util.Set.of("actions/checkout")));
        for (String entry : List.of("uses: actions/checkout@v7", "uses: actions/checkout@" + "a".repeat(40) + " # " + version,
            "uses: actions/checkout@" + sha + " # v7.0.0", "uses: other/action@" + sha + " # " + version,
            "uses: actions/checkout/subaction@" + sha + " # " + version, "uses: docker://unreviewed/image:latest",
            "uses: ./unreviewed-local-action", "uses: $/.github/actions/other", "uses: $/.github/actions/setup-nix@main",
            "uses: owner/repo/.github/actions/setup-nix@main", "'uses': actions/checkout@" + sha + " # " + version,
            "{uses: \"actions/checkout@v7\"}")) ReleaseTest.reject(() -> WorkflowPins.inspect(entry));
        ok(WorkflowPins.inspect("# uses: bad\n" + valid + "\r\n").size() == 1);
        Path root = Files.createTempDirectory("totipo-workflow-pins-");
        try {
            Path source = Path.of(args[0]);
            for (String name : List.of("ci.yml", "release.yml", "verify-release.yml"))
                TestResultsTest.write(root.resolve(".github/workflows/" + name), Files.readString(source.resolve(".github/workflows/" + name)));
            Path action = root.resolve(WorkflowPins.BOOTSTRAP);
            String bootstrap = Files.readString(source.resolve(WorkflowPins.BOOTSTRAP));
            TestResultsTest.write(action, bootstrap);
            WorkflowPins.check(root);
            TestResultsTest.write(action, bootstrap.replace("13d8dd58da0234aa297dedd986986ccb8e7f3e24", "v31"));
            ReleaseTest.reject(() -> WorkflowPins.check(root));
            // Local source edits do not require renewing a second identity assertion.
            TestResultsTest.write(action, "# Formatting/comment edit\n" + bootstrap.replace("installed_nix", "nix_identity"));
            WorkflowPins.check(root);
            TestResultsTest.write(action, bootstrap + "    - uses: other/action@main\n");
            ReleaseTest.reject(() -> WorkflowPins.check(root));
            TestResultsTest.write(action, bootstrap);
            for (String name : List.of("ci.yml", "release.yml", "verify-release.yml")) {
                Path yaml = root.resolve(".github/workflows/" + name);
                String text = Files.readString(yaml);
                for (String[] edit : List.of(
                    new String[]{"uses: $/.github/actions/setup-nix", "uses: ./.github/actions/setup-nix"},
                    new String[]{"uses: $/.github/actions/setup-nix", "uses: $/.github/actions/other"},
                    new String[]{"uses: $/.github/actions/setup-nix", "run: true"},
                    new String[]{"      - name: Build pinned runtime", "      - run: curl https://releases.nixos.org/install | sh\n      - name: Build pinned runtime"})) {
                    ok(text.contains(edit[0]));
                    TestResultsTest.write(yaml, text.replace(edit[0], edit[1]));
                    ReleaseTest.reject(() -> WorkflowPins.check(root));
                }
                TestResultsTest.write(yaml, text);
            }
            Files.delete(action); ReleaseTest.reject(() -> WorkflowPins.check(root));
            System.out.println("WorkflowPinsTest PASS: external pin negatives and all bootstrap call sites");
        } finally { AuthorizationTest.remove(root); }
    }
}
