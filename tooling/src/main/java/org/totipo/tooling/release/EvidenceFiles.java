package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.totipo.tooling.release.SourceFiles.require;

/** Release evidence reads anchored at directory handles, with no symlink following. */
final class EvidenceFiles {
    private EvidenceFiles() {}
    static SecureDirectoryStream<Path> parent(Path file) throws IOException {
        Path full = file.toAbsolutePath().normalize();
        DirectoryStream<Path> initial = Files.newDirectoryStream(full.getRoot());
        if (!(initial instanceof SecureDirectoryStream<Path> current)) {
            initial.close(); throw new IOException("Secure filesystem operations unavailable");
        }
        try {
            for (Path part : full.getParent()) {
                var next = current.newDirectoryStream(part, LinkOption.NOFOLLOW_LINKS);
                current.close(); current = next;
            }
            return current;
        } catch (IOException | RuntimeException error) { current.close(); throw error; }
    }
    static byte[] read(Path file, int limit) throws IOException {
        try (var dir = parent(file)) { return read(dir, file.getFileName(), limit); }
    }
    static byte[] read(SecureDirectoryStream<Path> dir, Path name, int limit) throws IOException {
        var a = dir.getFileAttributeView(name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
        require(a.isRegularFile() && a.size() <= limit, "Nonregular or oversized release evidence");
        try (var channel = dir.newByteChannel(name, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             var in = Channels.newInputStream(channel)) {
            byte[] bytes = in.readNBytes(limit + 1);
            require(bytes.length <= limit, "Release evidence byte limit");
            return bytes;
        }
    }
    record SigningInput(byte[] unsigned, byte[] signature) {}
    static SigningInput signingInput(Path tree, String filename) throws IOException {
        var matches = new ArrayList<SigningInput>();
        try (var dir = parent(tree.resolve("anchor"))) {
            scan(dir, filename, matches, new int[]{0}, 0);
        }
        require(matches.size() == 1, "Expected exactly one signing output");
        return matches.getFirst();
    }
    private static void scan(SecureDirectoryStream<Path> dir, String filename, List<SigningInput> matches,
                             int[] entries, int depth) throws IOException {
        require(depth <= 32, "Signing tree depth limit");
        for (Path entry : dir) {
            require(++entries[0] <= 20_000, "Signing tree entry limit");
            Path name = entry.getFileName();
            var a = dir.getFileAttributeView(name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
            require(!a.isSymbolicLink() && (a.isRegularFile() || a.isDirectory()), "Unsafe signing tree entry");
            if (a.isDirectory()) {
                require(!name.toString().equals(filename + ".asc"), "Nonregular signing output");
                try (var child = dir.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
                    scan(child, filename, matches, entries, depth + 1);
                }
            } else if (name.toString().equals(filename + ".asc")) {
                require(matches.isEmpty(), "Ambiguous signing outputs");
                matches.add(new SigningInput(read(dir, Path.of(filename), 64 * 1024 * 1024), read(dir, name, 65_536)));
            }
        }
    }
}
