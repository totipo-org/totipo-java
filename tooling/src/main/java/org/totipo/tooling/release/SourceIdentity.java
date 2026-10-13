package org.totipo.tooling.release;

import java.io.IOException;
import java.util.HashMap;
import static org.totipo.tooling.release.SourceFiles.require;

/** Read-only identity observations; no repair, tag creation, or release authorization. */
public record SourceIdentity(ReleaseInputs reviewed, String head, String remoteMain,
                             Tag localTag, Tag remoteTag, RemoteReleaseState.ReleaseRecord release) {
    public record Tag(boolean present, String object, String commit, boolean annotated) {
        static Tag absent() { return new Tag(false, null, null, false); }
    }
    public static Tag remoteTag(String text, String version) throws IOException {
        if (text.isEmpty()) return Tag.absent();
        String ref = "refs/tags/v" + version;
        var refs = new HashMap<String, String>();
        for (String line : text.split("\n", -1)) {
            String[] fields = line.split("\\s+");
            require(fields.length == 2 && fields[0].matches("[0-9a-f]{40}")
                    && (fields[1].equals(ref) || fields[1].equals(ref + "^{}")), "Malformed remote tag observation");
            require(refs.putIfAbsent(fields[1], fields[0]) == null, "Duplicate/conflicting remote tag observation");
        }
        require(refs.containsKey(ref), "Peeled tag lacks tag object");
        return new Tag(true, refs.get(ref), refs.getOrDefault(ref + "^{}", refs.get(ref)), refs.containsKey(ref + "^{}"));
    }
    public static SourceIdentity inspect(SourceFiles source, ReleaseInputs inputs, GitQueries git,
                                         RemoteReleaseState.GitHub github, boolean allowExistingTag) throws IOException {
        String version = inputs.version(), commit = inputs.commit();
        String head = git.query(GitQueries.Query.HEAD, version);
        require(head.equals(commit), "Checkout differs from reviewed commit");
        require(source.text("VERSION").equals(version + "\n"), "VERSION differs from requested version");
        require(git.query(GitQueries.Query.STATUS, version).isEmpty(), "Checkout must be clean");
        source.spec(); source.notes(version, false, false);
        // Explicit fresh remote observation, never a cached refs/remotes value and never fetch.
        String main = git.query(GitQueries.Query.REMOTE_MAIN, version);
        require(main.equals(commit), "Current origin/main differs from reviewed commit or is unavailable");
        Tag remote = remoteTag(git.query(GitQueries.Query.REMOTE_TAG, version), version);
        String local = git.query(GitQueries.Query.LOCAL_TAG, version);
        require(local.isEmpty() || local.equals("v" + version), "Malformed local tag observation");
        Tag localTag = Tag.absent();
        if (!local.isEmpty()) {
            if (!allowExistingTag) throw new IOException("Release tag already exists");
            String target = git.query(GitQueries.Query.LOCAL_TAG_COMMIT, version);
            require(target.equals(commit), "Local tag points elsewhere; never move it");
            String type = git.query(GitQueries.Query.LOCAL_TAG_TYPE, version);
            require(type.equals("tag") || type.equals("commit"), "Invalid local tag object type");
            localTag = new Tag(true, null, target, type.equals("tag"));
        }
        if (allowExistingTag) require(!remote.present() || remote.commit().equals(commit), "Remote tag points elsewhere; never move it");
        else require(!remote.present(), "Release tag already exists");
        var release = github.release(version);
        if (!allowExistingTag) require(release.presence() == RemoteReleaseState.Presence.ABSENT, "GitHub release already exists");
        return new SourceIdentity(inputs, head, main, localTag, remote, release);
    }
}
