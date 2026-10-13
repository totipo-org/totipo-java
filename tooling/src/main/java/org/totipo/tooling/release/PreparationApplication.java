package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.totipo.tooling.release.SourceFiles.require;

/** Apply only a freshly revalidated J3A plan. No CLI; use explicit disposable roots in J3B.2.
 * Individual replacements are atomic. The multi-file operation is not a transaction; failed
 * application may leave partial edits and never rolls back over potentially changed files.
 */
public final class PreparationApplication {
    private PreparationApplication() {}
    public static final class ReviewedPlan {
        private final Path root;
        private final String head, target;
        private final PreparationPlan plan;
        private final Map<String, byte[]> before;
        private ReviewedPlan(Path root, String head, String target, PreparationPlan plan, Map<String, byte[]> before) {
            this.root = root; this.head = head; this.target = target; this.plan = plan; this.before = before;
        }
        public PreparationPlan plan() { return plan; }
    }
    /** The immutable planner remains the only owner of output bytes and path membership. */
    public static ReviewedPlan review(Path root, GitQueries git, String target) throws IOException {
        root = root.toAbsolutePath().normalize();
        var source = new SourceFiles(root);
        String head = git.query(GitQueries.Query.HEAD, target);
        require(head.matches("[0-9a-f]{40}"), "Missing starting source identity");
        var before = new LinkedHashMap<String, byte[]>();
        for (String path : PreparationPlan.metadata(source, source.text("VERSION").strip(), source.text("VERSION").strip()).replacements().keySet())
            before.put(path, EvidenceFiles.read(source.path(path), 2 * 1024 * 1024));
        String notes = SourceFiles.notesPath(target);
        before.put(notes, source.exists(notes) ? EvidenceFiles.read(source.path(notes), 2 * 1024 * 1024) : null);
        var plan = PreparationPlan.inspect(source, git, target, false);
        checkBytes(source, before);
        require(head.equals(git.query(GitQueries.Query.HEAD, target)), "Source identity changed during planning");
        return new ReviewedPlan(root, head, target, plan, before);
    }
    @FunctionalInterface interface BeforeWrite { void run(String path, int index) throws IOException; }
    public static void apply(Path root, ReviewedPlan reviewed, GitQueries git) throws IOException {
        apply(root, reviewed, git, (path, index) -> {});
    }
    static void apply(Path root, ReviewedPlan reviewed, GitQueries git, BeforeWrite hook) throws IOException {
        apply(root, reviewed, git, hook, PreparationApplication::write);
    }
    @FunctionalInterface interface Writer { void write(java.nio.channels.SeekableByteChannel channel, byte[] bytes) throws IOException; }
    static void apply(Path root, ReviewedPlan reviewed, GitQueries git, BeforeWrite hook, Writer writer) throws IOException {
        require(root.toAbsolutePath().normalize().equals(reviewed.root), "Preparation root differs from reviewed root");
        var source = new SourceFiles(root);
        require(reviewed.head.equals(git.query(GitQueries.Query.HEAD, reviewed.target)), "Starting source identity changed");
        checkBytes(source, reviewed.before);
        var fresh = PreparationPlan.inspect(source, git, reviewed.target, false);
        require(fresh.equals(reviewed.plan), "Preparation plan differs from reviewed bytes");
        var parents = new LinkedHashMap<String, SecureDirectoryStream<Path>>();
        try {
            // Resolve, anchor and validate every destination before the first edit.
            for (String path : reviewed.plan.replacements().keySet()) {
                Path file = source.path(path);
                var parent = EvidenceFiles.parent(file);
                parents.put(path, parent);
                check(parent, file.getFileName(), reviewed.before.get(path));
            }
            int index = 0;
            for (String path : reviewed.plan.replacements().keySet()) {
                hook.run(path, index++);
                Path file = source.path(path), name = file.getFileName();
                var parent = parents.get(path);
                byte[] original = reviewed.before.get(path), replacement = reviewed.plan.bytes(path);
                check(parent, name, original);
                if (original == null) {
                    // A racing appearance cannot be overwritten.
                    try (var channel = parent.newByteChannel(name, Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS),
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")))) {
                        writer.write(channel, replacement);
                    }
                } else {
                    Path temporary = Path.of(".totipo-prepare-" + java.util.UUID.randomUUID() + ".tmp");
                    var permissions = parent.getFileAttributeView(name, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes().permissions();
                    boolean created = false;
                    try {
                        try (var channel = parent.newByteChannel(temporary, Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS),
                                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                            created = true; writer.write(channel, replacement);
                        }
                        parent.getFileAttributeView(temporary, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).setPermissions(permissions);
                        check(parent, name, original);
                        parent.move(temporary, parent, name);
                        created = false;
                    } finally { if (created) parent.deleteFile(temporary); }
                }
            }
            for (String path : reviewed.plan.replacements().keySet())
                require(Arrays.equals(EvidenceFiles.read(source.path(path), 2 * 1024 * 1024), reviewed.plan.bytes(path)), "Preparation postcondition failed; partial application possible");
        } catch (IOException | RuntimeException failure) {
            throw new IOException("Preparation application failed; partial edits may remain; inspect before retry");
        } finally {
            IOException failure = null;
            for (var parent : parents.values()) try { parent.close(); } catch (IOException e) { failure = e; }
            if (failure != null) throw new IOException("Preparation handle cleanup failed");
        }
    }
    private static void write(java.nio.channels.SeekableByteChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer);
    }
    private static void checkBytes(SourceFiles source, Map<String, byte[]> before) throws IOException {
        for (var entry : before.entrySet()) {
            if (entry.getValue() == null) require(!source.exists(entry.getKey()), "Notes appeared after planning");
            else require(Arrays.equals(EvidenceFiles.read(source.path(entry.getKey()), 2 * 1024 * 1024), entry.getValue()), "Source bytes changed after planning");
        }
    }
    private static void check(SecureDirectoryStream<Path> parent, Path name, byte[] expected) throws IOException {
        if (expected == null) {
            try {
                parent.getFileAttributeView(name, java.nio.file.attribute.BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
                throw new IOException("New preparation destination already exists");
            } catch (java.nio.file.NoSuchFileException absent) { /* CREATE_NEW rechecks at mutation. */ }
        } else require(Arrays.equals(EvidenceFiles.read(parent, name, 2 * 1024 * 1024), expected), "Concurrent preparation source change");
    }
}
