package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Bounded read-only source view. Expected integrity pins are reviewed constants. */
public final class SourceFiles {
    public static final String SPEC_COMMIT = "cdb4e91be1c6d3704874b2b92457ffe7be5e9084";
    public static final String SPEC_SHA256 = "736f9489852dcf2d0f22fb13f7da9fcd2dca4de18f8d735fbb34aad6162dedd0";
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
        // Established universal newline normalization for reviewed prose.
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
    public static String notesPath(String version) throws IOException {
        require(ReleaseInputs.validVersion(version), "Invalid release version");
        return "review/V" + version.replace('.', '_') + "_RELEASE_NOTES.md";
    }
    public String notes(String version) throws IOException {
        String notes = text(notesPath(version));
        String title = notes.stripLeading().lines().findFirst().orElse("");
        require(notes.lines().dropWhile(String::isBlank).skip(1).anyMatch(line -> !line.isBlank())
            && java.util.regex.Pattern.compile("(?<![A-Za-z0-9._-])v?"
            + java.util.regex.Pattern.quote(version) + "(?![A-Za-z0-9._-])").matcher(title).find(),
            "Reviewed release notes do not match requested version");
        require(!java.util.regex.Pattern.compile("(?i)\\b(?:TODO|TBD|FIXME)\\b").matcher(notes).find(),
            "Release notes still need review");
        return notes;
    }
    static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
