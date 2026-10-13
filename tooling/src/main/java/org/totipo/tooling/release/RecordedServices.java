package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Explicit offline evidence. Missing fixtures fail; there is no process/network fallback. */
public final class RecordedServices {
    private final SourceFiles files;
    public RecordedServices(Path directory) throws IOException { files = new SourceFiles(directory); }
    public GitQueries git() {
        return (query, version) -> files.text("git/" + query.name() + ".txt").strip();
    }
    public RemoteReleaseState.Http http() {
        return uri -> {
            String key = SourceFiles.sha256(uri.toASCIIString().getBytes(StandardCharsets.US_ASCII));
            String status = files.text("http/" + key + ".status");
            SourceFiles.require(status.matches("[0-9]{3}\n"), "Malformed recorded HTTP status");
            return new RemoteReleaseState.Response(Integer.parseInt(status.strip()),
                    files.read("http/" + key + ".body", RemoteReleaseState.GITHUB.bytes()));
        };
    }
}
