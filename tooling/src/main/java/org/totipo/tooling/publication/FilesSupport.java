package org.totipo.tooling.publication;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

final class FilesSupport {
    static final int MAX_FILE = 64 * 1024 * 1024;
    private FilesSupport() {}
    static void require(boolean ok, String message) throws IOException { if (!ok) throw new IOException(message); }
    static String digest(byte[] b, String algorithm) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(b)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    /** Anchor every component with open directory handles; symlink swaps cannot redirect reads/writes. */
    static SecureDirectoryStream<Path> parent(Path path) throws IOException {
        Path full = path.toAbsolutePath().normalize();
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
        } catch (IOException | RuntimeException e) { current.close(); throw e; }
    }
    static byte[] read(Path p, int max) throws IOException {
        try (var dir = parent(p)) {
            var attributes = dir.getFileAttributeView(p.getFileName(), java.nio.file.attribute.BasicFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS).readAttributes();
            require(attributes.isRegularFile(), "Nonregular input file: " + p);
            require(attributes.size() <= max, "File exceeds byte limit: " + p);
            try (var channel = dir.newByteChannel(p.getFileName(), Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                 InputStream in = Channels.newInputStream(channel)) {
                byte[] bytes = in.readNBytes(max + 1);
                require(bytes.length <= max, "File exceeds byte limit: " + p);
                return bytes;
            }
        }
    }
    static byte[] read(Path p) throws IOException { return read(p, MAX_FILE); }
    static Set<String> inventory(Path root) throws IOException {
        // Walk never follows links; secure reads additionally anchor all ancestors.
        parent(root.resolve("anchor")).close();
        try (var paths = Files.walk(root)) {
            Set<String> result = new TreeSet<>();
            var iterator = paths.iterator(); int count = 0;
            while (iterator.hasNext()) {
                Path p = iterator.next();
                require(++count <= 20_000, "Repository entry limit exceeded");
                require(!Files.isSymbolicLink(p), "Symlink rejected: " + p);
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) continue;
                require(Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS), "Nonregular file: " + p);
                result.add(root.relativize(p).toString().replace(p.getFileSystem().getSeparator(), "/"));
            }
            return result;
        }
    }
    static void writeNew(Path p, byte[] bytes) throws IOException {
        try (var dir = parent(p); var channel = dir.newByteChannel(p.getFileName(),
                Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS))) {
            var buffer = java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
        }
    }
    static void writeAtomic(Path p, byte[] bytes) throws IOException {
        Path temp = p.resolveSibling(".publication-" + java.util.UUID.randomUUID() + ".tmp");
        try {
            writeNew(temp, bytes);
            try (var dir = parent(p)) { dir.move(temp.getFileName(), dir, p.getFileName()); }
        } finally { delete(temp); }
    }
    static void delete(Path p) throws IOException {
        try (var dir = parent(p)) {
            try { dir.deleteFile(p.getFileName()); }
            catch (java.nio.file.NoSuchFileException ignored) { /* No stale output. */ }
        }
    }
    static void directories(Path p) throws IOException {
        Path full = p.toAbsolutePath().normalize();
        DirectoryStream<Path> initial = Files.newDirectoryStream(full.getRoot());
        if (!(initial instanceof SecureDirectoryStream<Path> current)) {
            initial.close(); throw new IOException("Secure filesystem operations unavailable");
        }
        try {
            for (Path part : full) {
                SecureDirectoryStream<Path> next;
                try { next = current.newDirectoryStream(part, LinkOption.NOFOLLOW_LINKS); }
                catch (java.nio.file.NoSuchFileException absent) {
                    // Java has no mkdirat API. Create privately in the trusted temporary
                    // directory, then move via directory handles instead of following a
                    // possibly swapped destination parent with Files.createDirectory.
                    Path tempRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
                    parent(tempRoot.resolve("anchor")).close();
                    Path temp = Files.createTempDirectory(tempRoot, "totipo-copy-directory-");
                    try {
                        try (var source = parent(temp)) { source.move(temp.getFileName(), current, part); }
                    } finally { Files.deleteIfExists(temp); }
                    next = current.newDirectoryStream(part, LinkOption.NOFOLLOW_LINKS);
                }
                current.close(); current = next;
            }
        } finally { current.close(); }
    }
}
