package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.Set;
import static org.totipo.tooling.release.SourceFiles.require;

/** Explicit runner-owned existing output file. No ambient GITHUB_OUTPUT and no arbitrary names. */
public final class GitHubOutputs {
    private GitHubOutputs() {}
    public static void inputs(Path output, ReleaseInputs inputs) throws IOException {
        append(output, "version=" + line(inputs.version()) + "\ncommit=" + line(inputs.commit()) + "\n");
    }
    public static void inventory(Path output, AcceptedInventory inventory) throws IOException {
        append(output, "inventory=" + line(inventory.json()) + "\n");
    }
    /** Candidate mode transports a branch only; it confers no upload authority. */
    public static void candidate(Path output, PublicationPolicy.Decision decision) throws IOException {
        String mode = switch (decision.disposition()) {
            case CANDIDATE_PUBLISH -> "publish";
            case VERIFY_EXISTING -> "verify";
            case BLOCKED -> throw new IOException("Blocked observation has no successful output");
        };
        append(output, "mode=" + mode + "\n");
    }
    private static String line(String text) throws IOException {
        require(text != null && !text.isEmpty() && text.codePoints().noneMatch(c -> Character.isISOControl(c)
            || c == 0x2028 || c == 0x2029), "Invalid single-line workflow output");
        return text;
    }
    private static void append(Path path, String value) throws IOException {
        require(path != null && path.isAbsolute(), "Explicit absolute runner output path required");
        try (var parent = EvidenceFiles.parent(path)) {
            var attrs = parent.getFileAttributeView(path.getFileName(), BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
            require(attrs.isRegularFile(), "Runner output must be an existing regular file");
            try (var channel = parent.newByteChannel(path.getFileName(), Set.of(StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS))) {
                var bytes = ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) channel.write(bytes);
            }
        }
    }
}
