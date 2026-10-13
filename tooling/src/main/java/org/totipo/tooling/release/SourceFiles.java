package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;

/** Bounded read-only source view. Expected integrity pins are reviewed constants. */
public final class SourceFiles {
    public static final String SPEC_COMMIT = "cdb4e91be1c6d3704874b2b92457ffe7be5e9084";
    public static final String SPEC_SHA256 = "736f9489852dcf2d0f22fb13f7da9fcd2dca4de18f8d735fbb34aad6162dedd0";
    public static final String SNAPSHOT_SHA256 = "8b8a6660923a425bc68d9bcd3843bd4cc35db642c494c4812f510c0fba7f0905";
    public static final String SNAPSHOT = "core/src/test/resources/totipo-spec/v1-pre-rc/";
    private final Path root;
    public SourceFiles(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        require(Files.isDirectory(this.root, LinkOption.NOFOLLOW_LINKS), "Missing source root");
        for (Path p = this.root; p != null; p = p.getParent()) require(!Files.isSymbolicLink(p), "Symlink source root");
    }
    Path path(String relative) throws IOException {
        Path p = Path.of(relative);
        require(!p.isAbsolute() && !relative.contains("\\") && !relative.isEmpty(), "Invalid source path");
        for (Path part : p) require(!part.toString().equals("..") && !part.toString().equals("."), "Unsafe source path");
        Path resolved = root.resolve(p);
        for (Path q = resolved; q != null && q.startsWith(root); q = q.getParent())
            require(!Files.isSymbolicLink(q), "Symlink source path: " + relative);
        return resolved;
    }
    public boolean exists(String relative) throws IOException { return Files.exists(path(relative), LinkOption.NOFOLLOW_LINKS); }
    public byte[] read(String relative, int limit) throws IOException {
        Path p = path(relative);
        require(Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS), "Missing/nonregular source file: " + relative);
        try (var in = Files.newInputStream(p, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(limit + 1);
            require(bytes.length <= limit, "Source byte limit: " + relative);
            return bytes;
        }
    }
    public String text(String relative) throws IOException {
        // Match Python text-mode universal newline translation; emitted plan bytes are UTF-8/LF.
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(read(relative, 2 * 1024 * 1024)))
                .toString().replace("\r\n", "\n").replace('\r', '\n');
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public void spec() throws IOException {
        require(sha256(read("SPEC_PIN.md", 65_536)).equals(SPEC_SHA256), "Exact reviewed r19 spec pin changed");
    }
    public void integrity() throws IOException {
        spec();
        byte[] records = read(SNAPSHOT + "SNAPSHOT.sha256", 65_536);
        require(sha256(records).equals(SNAPSHOT_SHA256), "Exact r19 snapshot inventory changed");
        String[] lines = new String(records, StandardCharsets.UTF_8).split("\n");
        require(lines.length == 99, "Expected 99 r19 snapshot records");
        var seen = new HashSet<String>();
        long total = 0;
        for (String line : lines) {
            require(line.matches("[0-9a-f]{64}  [^\\r\\n]+"), "Malformed snapshot record");
            String name = line.substring(66);
            require(seen.add(name), "Duplicate snapshot record");
            byte[] bytes = read(SNAPSHOT + name, 16 * 1024 * 1024);
            total += bytes.length;
            require(total <= 128L * 1024 * 1024, "Snapshot total byte limit");
            require(sha256(bytes).equals(line.substring(0, 64)), "Snapshot integrity failure: " + name);
        }
    }
    public static String notesPath(String version) throws IOException {
        require(ReleaseInputs.validVersion(version), "Invalid release version");
        return "review/V" + version.replace('.', '_') + "_RELEASE_NOTES.md";
    }
    public String notes(String version, boolean sections, boolean completed) throws IOException {
        String notes = text(notesPath(version));
        require(notes.startsWith("## v" + version + "\n"), "Reviewed release notes do not match requested version");
        if (sections) for (String section : new String[]{"Highlights", "Compatibility and scope", "Validation"})
            require(notes.contains("### " + section + "\n"), "Missing release-note section contract");
        if (completed) require(!notes.contains("TODO:"), "Release notes still need review");
        return notes;
    }
    static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
