package org.totipo.tooling.release;

import java.nio.file.Files;
import java.nio.file.Path;

/** Host migration bridge only. Not installed in main classes or reachable from Main. */
public final class WorkflowOutputFixture {
    private WorkflowOutputFixture() {}
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "pins" -> WorkflowPins.check(Path.of(args[1]));
            case "inputs" -> GitHubOutputs.inputs(Path.of(args[1]), ReleaseInputs.from(System.getenv()));
            case "inventory" -> GitHubOutputs.inventory(Path.of(args[2]), AcceptedInventory.verify(Path.of(args[1]),
                Path.of(args[1]).resolve("build/repository"), ReleaseInputs.from(System.getenv()).version()));
            case "state" -> GitHubOutputs.candidate(Path.of(args[4]), PublicationPolicy.interpret(new RemoteReleaseState(
                args[1].equals("200") ? RemoteReleaseState.Presence.PRESENT : RemoteReleaseState.Presence.ABSENT,
                args[2].equals("200") ? RemoteReleaseState.Presence.PRESENT : RemoteReleaseState.Presence.ABSENT,
                Boolean.parseBoolean(args[3]) ? RemoteReleaseState.PriorAttempt.POSSIBLE_UPLOAD : RemoteReleaseState.PriorAttempt.NO_RECORDED_UPLOAD)));
            case "compare" -> AcceptedInventory.compare(Path.of(args[1]).resolve("build/publication-manifest.json"),
                Path.of(args[1]).resolve("build/repository"), "0.2.0", Files.readAllBytes(Path.of(args[2])));
            default -> throw new IllegalArgumentException("Unknown fixture command");
        }
    }
}
