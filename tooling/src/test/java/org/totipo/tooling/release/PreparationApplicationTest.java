package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.totipo.tooling.release.ReleaseTest.*;

/** Permanent fixture-only application and failure tests. */
public final class PreparationApplicationTest {
    private PreparationApplicationTest() {}
    static final String HEAD = "a".repeat(40);
    static final GitQueries GIT = (query, version) -> switch (query) {
        case HEAD -> HEAD;
        case BRANCH -> "main";
        case STATUS -> "";
        default -> throw new AssertionError("Unexpected preparation Git query");
    };
    static Path fixture(Path integrity) throws Exception {
        Path root = Files.createTempDirectory("totipo-prepare-fixture-");
        ReleaseTest.fixture(root); Files.copy(integrity.resolve("SPEC_PIN.md"), root.resolve("SPEC_PIN.md")); writable(root.resolve("SPEC_PIN.md"));
        ReleaseTest.copy(integrity.resolve(SourceFiles.SNAPSHOT), root.resolve(SourceFiles.SNAPSHOT));
        return root;
    }
    static void remove(Path root) throws IOException {
        try (var paths = Files.walk(root)) { for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p); }
    }
    static void positive(Path integrity) throws Exception {
        for (String scenario : List.of("released", "prepared", "notes", "crlf", "history")) {
            Path root = fixture(integrity);
            try {
                if (scenario.equals("notes")) Files.writeString(root.resolve("review/V0_2_1_RELEASE_NOTES.md"), PreparationPlan.NOTES_TEMPLATE.formatted("0.2.1").replace("TODO:", "Reviewed:"));
                if (scenario.equals("prepared")) for (String file : List.of("README.md", "API_DESIGN.md", "RELEASE_CHECKLIST.md")) {
                    Path path = root.resolve(file); Files.writeString(path, Files.readString(path).replace("Java 0.2.0 is released.", "Java 0.2.0 is prepared locally and not yet released."));
                }
                if (scenario.equals("crlf")) for (String file : List.of("VERSION", "README.md", "API_DESIGN.md", "RELEASE_CHECKLIST.md", ".github/workflows/release.yml", "publishing/consumer-smoke/gradle.lockfile")) {
                    Path path = root.resolve(file); Files.writeString(path, Files.readString(path).replace("\n", "\r\n"));
                }
                var before = inventory(root); var reviewed = PreparationApplication.review(root, GIT, "0.2.1");
                ok(before.equals(inventory(root))); PreparationApplication.apply(root, reviewed, GIT);
                for (var entry : reviewed.plan().replacements().entrySet()) ok(java.util.Arrays.equals(Files.readAllBytes(root.resolve(entry.getKey())), reviewed.plan().bytes(entry.getKey())));
                for (String path : before.keySet()) if (!reviewed.plan().replacements().containsKey(path)) ok(inventory(root).get(path).equals(before.get(path)));
                ok(Files.readString(root.resolve("API_DESIGN.md")).contains("existing 0.2.0 API"));
                Path notes = root.resolve("review/V0_2_1_RELEASE_NOTES.md"); Files.writeString(notes, Files.readString(notes).replace("TODO:", "Reviewed:"));
                PreparationPlan.inspect(new SourceFiles(root), GIT, "0.2.1", true);
                reject(() -> PreparationApplication.apply(root, reviewed, GIT));
            } finally { remove(root); }
        }
    }
    static void negative(Path integrity) throws Exception {
        Path root = fixture(integrity);
        try {
            for (String target : List.of("0.2.0", "0.1.9", "0.02.1", "0.2.1-SNAPSHOT", "../escape", "0.2.1\n")) reject(() -> PreparationApplication.review(root, GIT, target));
            for (String branch : List.of("", "feature")) reject(() -> PreparationApplication.review(root, (q, v) -> q == GitQueries.Query.BRANCH ? branch : GIT.query(q, v), "0.2.1"));
            reject(() -> PreparationApplication.review(root, (q, v) -> q == GitQueries.Query.STATUS ? " M file" : GIT.query(q, v), "0.2.1"));
            var reviewed = PreparationApplication.review(root, GIT, "0.2.1");
            reject(() -> PreparationApplication.apply(root.getParent(), reviewed, GIT));
            reject(() -> PreparationApplication.apply(root, reviewed, (q, v) -> q == GitQueries.Query.HEAD ? "b".repeat(40) : GIT.query(q, v)));
            var before = inventory(root);
            Files.writeString(root.resolve("review/V0_2_1_RELEASE_NOTES.md"), "concurrent notes");
            var changed = inventory(root); reject(() -> PreparationApplication.apply(root, reviewed, GIT)); ok(inventory(root).equals(changed));
            Files.delete(root.resolve("review/V0_2_1_RELEASE_NOTES.md"));
            for (String path : List.of("README.md", "SPEC_PIN.md", SourceFiles.SNAPSHOT + "SNAPSHOT.sha256", "publishing/consumer-smoke/gradle.lockfile")) {
                byte[] bytes = Files.readAllBytes(root.resolve(path)); Files.writeString(root.resolve(path), "corrupt");
                changed = inventory(root); reject(() -> PreparationApplication.review(root, GIT, "0.2.1")); ok(inventory(root).equals(changed)); Files.write(root.resolve(path), bytes);
            }
            for (String path : List.of("README.md", ".github/workflows/release.yml", "RELEASE_CHECKLIST.md")) {
                String text = Files.readString(root.resolve(path)); Files.writeString(root.resolve(path), text + text);
                changed = inventory(root); reject(() -> PreparationApplication.review(root, GIT, "0.2.1")); ok(inventory(root).equals(changed)); Files.writeString(root.resolve(path), text);
            }
            Path missing = root.resolve("README.md"); byte[] content = Files.readAllBytes(missing); Files.delete(missing);
            reject(() -> PreparationApplication.review(root, GIT, "0.2.1")); Files.createSymbolicLink(missing, root.resolve("API_DESIGN.md"));
            reject(() -> PreparationApplication.review(root, GIT, "0.2.1")); reject(() -> PreparationApplication.apply(root, reviewed, GIT)); Files.delete(missing); Files.write(missing, content);
            reject(() -> new SourceFiles(root).path("../escape"));
            ok(inventory(root).equals(before));
            reject(() -> PreparationApplication.apply(root, reviewed, GIT, (path, index) -> {
                if (index == 1) Files.writeString(root.resolve(path), "concurrent edit");
            }));
            ok(Files.readString(root.resolve("VERSION")).equals("0.2.1\n")); ok(Files.readString(root.resolve("RELEASE_CHECKLIST.md")).equals("concurrent edit"));
            try (var paths = Files.walk(root)) { ok(paths.noneMatch(p -> p.getFileName().toString().startsWith(".totipo-prepare-"))); }
        } finally { remove(root); }
        Path failed = fixture(integrity); // Separate partial write failure.
        try {
            var reviewed = PreparationApplication.review(failed, GIT, "0.2.1");
            reject(() -> PreparationApplication.apply(failed, reviewed, GIT, (path, index) -> { if (index == 2) throw new IOException("injected write failure"); }));
            ok(Files.readString(failed.resolve("VERSION")).equals("0.2.1\n"));
            ok(Files.readString(failed.resolve(".github/workflows/release.yml")).contains("0.2.0"));
        } finally { remove(failed); }
    }
    static void notesRace(Path integrity) throws Exception {
        Path root = fixture(integrity);
        try {
            var reviewed = PreparationApplication.review(root, GIT, "0.2.1");
            reject(() -> PreparationApplication.apply(root, reviewed, GIT, (path, index) -> {
                if (path.startsWith("review/")) Files.writeString(root.resolve(path), "concurrent notes");
            }));
            ok(Files.readString(root.resolve("review/V0_2_1_RELEASE_NOTES.md")).equals("concurrent notes"));
        } finally { remove(root); }
    }
    static void unsafePaths(Path integrity) throws Exception {
        for (String kind : List.of("directory", "parent-symlink", "notes-directory", "symlink-during-write")) {
            Path root = fixture(integrity), outside = Files.createTempDirectory("totipo-prepare-outside-");
            try {
                Files.writeString(outside.resolve("sentinel"), "unchanged");
                var reviewed = PreparationApplication.review(root, GIT, "0.2.1");
                if (kind.equals("directory")) { Files.delete(root.resolve("README.md")); Files.createDirectory(root.resolve("README.md")); }
                if (kind.equals("parent-symlink")) {
                    Files.move(root.resolve(".github"), root.resolve("saved-github")); Files.createSymbolicLink(root.resolve(".github"), outside);
                }
                if (kind.equals("notes-directory")) Files.createDirectory(root.resolve("review/V0_2_1_RELEASE_NOTES.md"));
                if (kind.equals("symlink-during-write")) {
                    reject(() -> PreparationApplication.apply(root, reviewed, GIT, (path, index) -> {
                        if (index == 1) { Files.delete(root.resolve(path)); Files.createSymbolicLink(root.resolve(path), outside.resolve("sentinel")); }
                    }));
                } else {
                    var before = inventory(root); reject(() -> PreparationApplication.apply(root, reviewed, GIT)); ok(before.equals(inventory(root)));
                }
                ok(Files.readString(outside.resolve("sentinel")).equals("unchanged"));
            } finally { remove(root); remove(outside); }
        }
    }
    static void intermediateWrite(Path integrity) throws Exception {
        Path root = fixture(integrity);
        try {
            var reviewed = PreparationApplication.review(root, GIT, "0.2.1"); int[] writes = {0};
            reject(() -> PreparationApplication.apply(root, reviewed, GIT, (path, index) -> {}, (channel, bytes) -> {
                if (++writes[0] == 3) {
                    channel.write(java.nio.ByteBuffer.wrap(bytes, 0, bytes.length / 2)); throw new IOException("injected partial temporary-file write");
                }
                var buffer = java.nio.ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer);
            }));
            ok(writes[0] == 3 && Files.readString(root.resolve("VERSION")).equals("0.2.1\n"));
            ok(Files.readString(root.resolve(".github/workflows/release.yml")).contains("0.2.0"));
            try (var paths = Files.walk(root)) { ok(paths.noneMatch(p -> p.getFileName().toString().startsWith(".totipo-prepare-"))); }
        } finally { remove(root); }
    }
    public static void main(String[] args) throws Exception { positive(Path.of(args[0])); negative(Path.of(args[0])); notesRace(Path.of(args[0])); unsafePaths(Path.of(args[0])); intermediateWrite(Path.of(args[0])); System.out.println("PreparationApplicationTest PASS"); }
}
