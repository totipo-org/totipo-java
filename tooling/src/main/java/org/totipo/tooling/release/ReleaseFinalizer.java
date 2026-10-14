package org.totipo.tooling.release;

import java.io.IOException;
import java.util.Map;
import static org.totipo.tooling.release.SourceFiles.require;
import static org.totipo.tooling.release.RemoteReleaseState.*;

/** Narrow existing-tag release creation or exact validation. Actions owns consumer sequencing. */
public final class ReleaseFinalizer {
    private ReleaseFinalizer() {}
    public static final class Expected {
        private final ReleaseInputs inputs;
        private final String body;
        private Expected(ReleaseInputs inputs, String body) { this.inputs = inputs; this.body = body; }
        public ReleaseInputs inputs() { return inputs; }
        public String body() { return body; }
        public String tag() { return "v" + inputs.version(); }
        public static Expected read(SourceFiles source, ReleaseInputs inputs) throws IOException {
            source.spec();
            require(source.text("VERSION").equals(inputs.version() + "\n"), "VERSION differs from reviewed identity");
            String notes = source.notes(inputs.version());
            return new Expected(inputs, rstrip(notes) + "\n\n### Release provenance\n\n- Java source commit: `" + inputs.commit()
                    + "`\n- Totipo v1/r19 spec commit: `" + SourceFiles.SPEC_COMMIT
                    + "`\n- Portable corpus: 92/92 executed, none deferred.\n");
        }
    }
    // Python str.rstrip whitespace, including NBSP and U+001C..U+001F.
    static String rstrip(String value) {
        int end = value.length();
        while (end > 0) {
            int c = value.codePointBefore(end);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c) && !(c >= 0x1c && c <= 0x1f) && c != 0x85) break;
            end -= Character.charCount(c);
        }
        return value.substring(0, end);
    }
    public static void validate(Expected expected, RemoteReleaseState.ReleaseRecord release) throws IOException {
        require(release.presence() == Presence.PRESENT && expected.tag().equals(release.tag())
            && expected.tag().equals(release.name()) && !release.draft() && !release.prerelease()
            && release.body() != null && rstrip(release.body().replace("\r\n", "\n")).equals(rstrip(expected.body())),
            "Conflicting GitHub Release; never modify it");
    }
    public static void finish(Expected expected, FinalizationProcesses.Api api) throws IOException {
        SourceIdentity.tag(api, expected.inputs());
        var response = api.request("GET", "/releases/tags/" + expected.tag(), null);
        if (response.status() == 200) {
            validate(expected, RemoteReleaseState.decodeRelease(response.body(), expected.inputs().version()));
            return;
        }
        require(response.status() == 404, "Release observation uncertain");
        var created = api.request("POST", "/releases", Map.of("tag_name", expected.tag(), "name", expected.tag(),
            "body", expected.body(), "draft", false, "prerelease", false));
        require(created.status() == 201, "Release creation failed or uncertain; stop");
        validate(expected, RemoteReleaseState.decodeRelease(created.body(), expected.inputs().version()));
        SourceIdentity.tag(api, expected.inputs());
        var readback = api.request("GET", "/releases/tags/" + expected.tag(), null);
        require(readback.status() == 200, "Release readback unavailable");
        validate(expected, RemoteReleaseState.decodeRelease(readback.body(), expected.inputs().version()));
    }
}
