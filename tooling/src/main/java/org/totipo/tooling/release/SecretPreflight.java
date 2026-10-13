package org.totipo.tooling.release;

import java.io.IOException;
import java.util.Map;
import static org.totipo.tooling.release.SourceFiles.require;

/** Runtime-only inspection; never logs secrets or packet dumps and never proves signing will succeed. */
public final class SecretPreflight {
    private SecretPreflight() {}
    public static final String PREFIX = "ORG_GRADLE_PROJECT_";
    public enum Protection { UNPROTECTED, PROTECTED }
    @FunctionalInterface public interface PacketInspector { Protection inspect(String key) throws IOException; }
    public static Protection check(Map<String, String> environment, PacketInspector inspector) throws IOException {
        for (String property : new String[]{"mavenCentralUsername", "mavenCentralPassword", "signingInMemoryKey"})
            require(present(environment, property), "Missing required environment secret: " + property);
        Protection protection;
        try { protection = inspector.inspect(environment.get(PREFIX + "signingInMemoryKey")); }
        catch (IOException | RuntimeException failure) { throw new IOException("Private key inspection failed"); }
        require(protection != null, "Private key inspection unavailable");
        if (protection == Protection.PROTECTED)
            require(present(environment, "signingInMemoryKeyPassword"), "Missing required environment secret: signingInMemoryKeyPassword");
        return protection;
    }
    private static boolean present(Map<String, String> env, String property) {
        String value = env.get(PREFIX + property);
        return value != null && !value.isEmpty();
    }
}
