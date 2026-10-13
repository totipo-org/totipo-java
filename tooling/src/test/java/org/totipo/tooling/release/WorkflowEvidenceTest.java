package org.totipo.tooling.release;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.totipo.tooling.publication.AcceptedFixture;
import org.totipo.tooling.publication.PublicationManifest;
import static org.totipo.tooling.release.ReleaseTest.reject;
import static org.totipo.tooling.release.TestResultsTest.ok;

/** Output files are temporary injected paths; no ambient runner environment is consulted. */
public final class WorkflowEvidenceTest {
    private WorkflowEvidenceTest() {}
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("totipo-workflow-evidence-");
        try {
            AcceptedFixture.create(root);
            var inventory = AcceptedInventory.verify(root, root.resolve("build/repository"), "0.2.0");
            String compact = inventory.json();
            ok(!compact.contains("\n") && !compact.contains(": ") && !compact.contains(", "));
            ok(PublicationManifest.parse(inventory.bytes(), "0.2.0").artifacts().size() == 10);
            var manifest = root.resolve("build/publication-manifest.json"); var staging = root.resolve("build/repository");
            AcceptedInventory.compare(manifest, staging, "0.2.0", inventory.bytes());
            String different = compact.replace(PublicationManifest.parse(inventory.bytes(), "0.2.0").repositorySha256(), "f".repeat(64));
            AcceptedInventory.compare(manifest, staging, "0.2.0", ReleaseTest.bytes(different));
            reject(() -> AcceptedInventory.compare(manifest, staging, "0.2.1", inventory.bytes()));
            reject(() -> AcceptedInventory.compare(manifest, staging, "0.2.0", ReleaseTest.bytes("{}")));
            Path output = root.resolve("runner-output"); Files.createFile(output);
            var inputs = new ReleaseInputs("0.2.0", "a".repeat(40));
            GitHubOutputs.inputs(output, inputs);
            GitHubOutputs.inventory(output, inventory);
            for (var facts : List.of(new RemoteReleaseState(RemoteReleaseState.Presence.ABSENT, RemoteReleaseState.Presence.ABSENT, RemoteReleaseState.PriorAttempt.NO_RECORDED_UPLOAD),
                    new RemoteReleaseState(RemoteReleaseState.Presence.PRESENT, RemoteReleaseState.Presence.ABSENT, RemoteReleaseState.PriorAttempt.POSSIBLE_UPLOAD)))
                GitHubOutputs.candidate(output, PublicationPolicy.interpret(facts));
            ok(Files.readString(output).equals("version=0.2.0\ncommit=" + "a".repeat(40) + "\ninventory=" + compact + "\nmode=publish\nmode=verify\n"));
            String before = Files.readString(output);
            reject(() -> GitHubOutputs.candidate(output, PublicationPolicy.interpret(new RemoteReleaseState(RemoteReleaseState.Presence.PRESENT,
                RemoteReleaseState.Presence.ABSENT, RemoteReleaseState.PriorAttempt.NO_RECORDED_UPLOAD))));
            for (String bad : List.of("0.2.0\nmode=publish", "0.2.0\r", "0.2.0\u0000", "0.2.0\u2028")) reject(() -> GitHubOutputs.inputs(output, new ReleaseInputs(bad, inputs.commit())));
            ok(before.equals(Files.readString(output)));
            reject(() -> GitHubOutputs.inputs(null, inputs));
            reject(() -> GitHubOutputs.inputs(Path.of("relative"), inputs));
            reject(() -> GitHubOutputs.inputs(root.resolve("missing"), inputs));
            reject(() -> GitHubOutputs.inputs(root, inputs));
            Path link = root.resolve("link"); Files.createSymbolicLink(link, output); reject(() -> GitHubOutputs.inputs(link, inputs));
            ok(before.equals(Files.readString(output)));
            Path sidecar = staging.resolve("org/totipo/totipo-core/maven-metadata.xml");
            Files.writeString(sidecar, Files.readString(sidecar) + " ");
            reject(() -> AcceptedInventory.compare(manifest, staging, "0.2.0", inventory.bytes()));
            var renewed = AcceptedInventory.verify(root, staging, "0.2.0");
            ok(!renewed.json().equals(compact));
            // Fresh remote main is an explicit request, never rev-parse origin/main.
            String sha = FreshRemoteMain.read("totipo-org/totipo-java", (command, env) -> {
                ok(command.contains("ls-remote") && !command.contains("fetch") && !command.contains("rev-parse"));
                ok(command.getLast().equals("refs/heads/main") && env.get("GIT_ALLOW_PROTOCOL").equals("https"));
                return inputs.commit() + "\trefs/heads/main\n";
            });
            ok(sha.equals(inputs.commit()));
            for (String stale : List.of("", inputs.commit(), inputs.commit() + "\trefs/remotes/origin/main\n", inputs.commit() + "\trefs/heads/main\n" + inputs.commit() + "\trefs/heads/main\n"))
                reject(() -> FreshRemoteMain.read("totipo-org/totipo-java", (command, env) -> stale));
            System.out.println("WorkflowEvidenceTest PASS");
        } finally { PreparationApplicationTest.remove(root); }
    }
}
