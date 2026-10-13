package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import static org.totipo.tooling.release.SourceFiles.require;

/** Reviewed source and inspected annotated tags; a matching existing tag never authorizes upload. */
public final class SourceIdentity {
    private SourceIdentity() {}
    public record Tag(String object, String commit, String annotation) {}
    public static void source(SourceFiles source, ReleaseInputs inputs) throws IOException {
        require(Arrays.equals(source.read("VERSION", 256), (inputs.version() + "\n").getBytes(StandardCharsets.US_ASCII)), "Exact VERSION/LF mismatch");
        source.integrity(); source.notes(inputs.version(), true, true);
        for (String path : new String[]{"README.md", "API_DESIGN.md"}) {
            String text = source.text(path), start = "<!-- prepared-release:start -->", end = "<!-- prepared-release:end -->";
            int first = text.indexOf(start), last = text.indexOf(end);
            require(first >= 0 && last > first && text.indexOf(start, first + 1) < 0 && text.indexOf(end, last + 1) < 0,
                "Ambiguous release source metadata");
            String status = text.substring(first + start.length(), last);
            require(status.equals("\nJava " + inputs.version() + " is released. See RELEASE_CHECKLIST.md.\n")
                || status.equals("\nJava " + inputs.version() + " is prepared locally and not yet released. See RELEASE_CHECKLIST.md.\n"),
                "Release source metadata version mismatch");
        }
        String lock = source.text("publishing/consumer-smoke/gradle.lockfile");
        for (String module : new String[]{"core", "storage-nio"}) {
            String prefix = "org.totipo:totipo-" + module + ":";
            var entries = lock.lines().filter(line -> line.startsWith(prefix)).toList();
            require(entries.equals(java.util.List.of(prefix + inputs.version()
                + "=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath")), "Consumer source/version lock mismatch");
        }
    }
    public static Tag tag(FinalizationProcesses.Api api, ReleaseInputs inputs) throws IOException {
        var ref = api.request("GET", "/git/ref/tags/v" + inputs.version(), null);
        require(ref.status() == 200, "Release tag absent or uncertain");
        var object = RemoteReleaseState.object(ref.body());
        require(("refs/tags/v" + inputs.version()).equals(object.get("ref")), "Wrong tag reference");
        var target = RemoteReleaseState.member(object, "object");
        require("tag".equals(target.get("type")), "Annotated tag required");
        String sha = RemoteReleaseState.sha(target.get("sha"));
        var response = api.request("GET", "/git/tags/" + sha, null);
        require(response.status() == 200, "Tag object unavailable");
        var tag = RemoteReleaseState.object(response.body());
        var commit = RemoteReleaseState.member(tag, "object");
        require(sha.equals(tag.get("sha")) && ("v" + inputs.version()).equals(tag.get("tag"))
            && "commit".equals(commit.get("type")) && inputs.commit().equals(commit.get("sha"))
            && ("totipo-java " + inputs.version() + "\n").equals(tag.get("message")), "Incorrect annotated tag identity");
        return new Tag(sha, inputs.commit(), (String) tag.get("message"));
    }
    public static void absent(FinalizationProcesses.Api api, ReleaseInputs inputs) throws IOException {
        require(api.request("GET", "/git/ref/tags/v" + inputs.version(), null).status() == 404, "Version is already reserved or uncertain");
        require(api.request("GET", "/releases/tags/v" + inputs.version(), null).status() == 404, "GitHub Release exists or uncertain");
    }
    /** Successful completion is local to this process/job. No portable reservation receipt. */
    public static void reserve(FinalizationProcesses.Api api, ReleaseInputs inputs, String attempt) throws IOException {
        require("1".equals(attempt), "Publishing reruns prohibited");
        absent(api, inputs);
        var created = api.request("POST", "/git/tags", Map.of("tag", "v" + inputs.version(),
            "message", "totipo-java " + inputs.version() + "\n", "object", inputs.commit(), "type", "commit",
            "tagger", Map.of("name", "github-actions[bot]", "email", "41898282+github-actions[bot]@users.noreply.github.com")));
        require(created.status() == 201, "Tag-object creation failed or uncertain; stop");
        String object = RemoteReleaseState.sha(RemoteReleaseState.object(created.body()).get("sha"));
        var reserved = api.request("POST", "/git/refs", Map.of("ref", "refs/tags/v" + inputs.version(), "sha", object));
        require(reserved.status() == 201, "First-time reference creation failed or uncertain; stop");
        var ref = RemoteReleaseState.object(reserved.body());
        require(("refs/tags/v" + inputs.version()).equals(ref.get("ref"))
            && object.equals(RemoteReleaseState.member(ref, "object").get("sha")), "Ambiguous reference creation; stop");
        require(tag(api, inputs).object().equals(object), "Tag readback mismatch; stop");
    }
}
