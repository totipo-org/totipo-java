package org.totipo.tooling.release;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/** Bounded release identity. Only initial dispatch binds the current workflow SHA. */
public record ReleaseInputs(String version, String commit) {
    public ReleaseInputs {
        if (!validVersion(version)) throw new IllegalArgumentException("Invalid release version");
        if (commit == null || !commit.matches("[0-9a-f]{40}"))
            throw new IllegalArgumentException("Expected exact lowercase source SHA");
    }
    public static boolean validVersion(String value) {
        return value != null && value.length() <= 128
            && value.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?")
            && !value.toUpperCase(Locale.ROOT).endsWith("-SNAPSHOT");
    }
    public static ReleaseInputs from(Map<String, String> env) throws IOException {
        return new ReleaseInputs(required(env, "REQUESTED_VERSION"), required(env, "REQUESTED_COMMIT"));
    }
    public void initial(Map<String, String> env) throws IOException {
        SourceFiles.require(required(env, "DISPATCH_REF").equals("refs/heads/main"), "Dispatch must select main");
        SourceFiles.require(required(env, "DISPATCH_SHA").equals(commit), "Dispatch SHA differs from reviewed commit");
    }
    static String required(Map<String, String> env, String key) throws IOException {
        String value = env.get(key);
        SourceFiles.require(value != null && !value.isEmpty(), "Missing required environment variable: " + key);
        return value;
    }
}
