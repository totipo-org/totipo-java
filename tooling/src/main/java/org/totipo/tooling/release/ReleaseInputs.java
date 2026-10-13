package org.totipo.tooling.release;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Operator identity, independent of any publication authorization. */
public record ReleaseInputs(String version, String commit) {
    public ReleaseInputs {
        if (!validVersion(version)) throw new IllegalArgumentException("Invalid release version or SNAPSHOT");
        if (commit == null || !commit.matches("[0-9a-f]{40}"))
            throw new IllegalArgumentException("commit must be exactly 40 lowercase hexadecimal characters");
    }
    public static boolean validVersion(String value) {
        return value != null && value.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?")
                && !value.toUpperCase(Locale.ROOT).endsWith("-SNAPSHOT");
    }
    public static List<BigInteger> stable(String value) throws IOException {
        SourceFiles.require(validVersion(value) && value.matches("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)"),
                "Preparation requires canonical stable major.minor.patch");
        return java.util.Arrays.stream(value.split("\\.")).map(BigInteger::new).toList();
    }
    public static ReleaseInputs from(Map<String, String> env) throws IOException {
        var result = new ReleaseInputs(required(env, "REQUESTED_VERSION"), required(env, "REQUESTED_COMMIT"));
        SourceFiles.require(required(env, "DISPATCH_REF").equals("refs/heads/main"), "Dispatch must select main");
        SourceFiles.require(required(env, "DISPATCH_SHA").equals(result.commit()), "Dispatch SHA differs from reviewed commit");
        return result;
    }
    static String required(Map<String, String> env, String key) throws IOException {
        String value = env.get(key);
        SourceFiles.require(value != null, "Missing required environment variable: " + key);
        return value;
    }
}
