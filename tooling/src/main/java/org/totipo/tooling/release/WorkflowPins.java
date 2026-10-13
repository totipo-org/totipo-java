package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import static org.totipo.tooling.release.SourceFiles.require;

/** Approved immutable actions, workflow-commit bootstrap and the intended reusable workflow. */
public final class WorkflowPins {
    private WorkflowPins() {}
    static final String BOOTSTRAP = ".github/actions/setup-nix/action.yml";
    static final String SETUP = "uses: $/.github/actions/setup-nix";
    static void inspectSetup(String text, int expected) throws IOException {
        require(count(text, SETUP) == expected, "Missing workflow-commit bootstrap");
        for (String bad : Set.of("install-nix", "releases.nixos.org", "nix --version", "nix-daemon", "access-tokens", "curl |", "sh <("))
            require(!text.contains(bad), "Unexpected inline Nix bootstrap");
        require(!inspect(text).contains("cachix/install-nix-action"), "Duplicate installer action");
    }
    private static int count(String text, String token) { return text.split(Pattern.quote(token), -1).length - 1; }
    private record Pin(String sha, String version, String runtime) {}
    private static final Map<String, Pin> APPROVED = Map.of(
        "actions/checkout", new Pin("3d3c42e5aac5ba805825da76410c181273ba90b1", "v7.0.1", "node24"),
        "cachix/install-nix-action", new Pin("13d8dd58da0234aa297dedd986986ccb8e7f3e24", "v31.11.1", "composite"));
    private static final Pattern USES = Pattern.compile("\\buses\\s*['\"]?\\s*:");
    private static final Pattern CANONICAL = Pattern.compile("\\s*(?:-\\s*)?uses: ([\\w-]+/[\\w-]+)@([0-9a-f]{40}) # (v\\d+\\.\\d+\\.\\d+)\\s*");
    static Set<String> inspect(String text) throws IOException {
        var found = new HashSet<String>();
        for (String line : text.split("\\R")) {
            if (line.stripLeading().startsWith("#") || !USES.matcher(line).find()) continue;
            if (line.equals("    uses: ./.github/workflows/verify-release.yml")) continue;
            if (line.equals("        uses: $/.github/actions/setup-nix")) continue;
            var matcher = CANONICAL.matcher(line);
            require(matcher.matches(), "Noncanonical or unpinned workflow action");
            Pin expected = APPROVED.get(matcher.group(1));
            require(expected != null && expected.sha().equals(matcher.group(2)) && expected.version().equals(matcher.group(3))
                && Set.of("node24", "composite").contains(expected.runtime()), "Unreviewed workflow action identity");
            found.add(matcher.group(1));
        }
        return Set.copyOf(found);
    }
    public static void check(Path root) throws IOException {
        Set<String> found = new HashSet<>(); int count = 0;
        byte[] bootstrap = EvidenceFiles.read(root.resolve(BOOTSTRAP), 65_536);
        require(inspect(new String(bootstrap, java.nio.charset.StandardCharsets.UTF_8)).equals(Set.of("cachix/install-nix-action")),
            "Missing bootstrap installer action");
        found.addAll(inspect(new String(bootstrap, java.nio.charset.StandardCharsets.UTF_8)));
        var expectedWorkflows = Map.of("ci.yml", 1, "release.yml", 2, "verify-release.yml", 2);
        Path directory = root.resolve(".github/workflows");
        try (var dir = EvidenceFiles.parent(directory.resolve("anchor"))) {
            for (Path path : dir) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".yml") && !name.endsWith(".yaml")) continue;
                require(++count <= 100, "Workflow file count limit");
                byte[] bytes = EvidenceFiles.read(dir, path.getFileName(), 1_048_576);
                String text = java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                require(expectedWorkflows.containsKey(name), "Unexpected workflow file");
                inspectSetup(text, expectedWorkflows.get(name));
                found.addAll(inspect(text));
            }
        }
        require(count == expectedWorkflows.size() && found.equals(APPROVED.keySet()), "Missing workflow or approved action coverage");
    }
}
