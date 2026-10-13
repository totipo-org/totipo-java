package org.totipo.tooling;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Independent tooling; Python retains live publication and release authority. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("release")) {
            try {
                org.totipo.tooling.release.ReleaseInspectionCli.run(java.util.Arrays.copyOfRange(args, 1, args.length), System.getenv());
            } catch (IOException | RuntimeException e) {
                System.err.println("Release inspection failed: " + e.getMessage());
                System.exit(1);
            }
            return;
        }
        if (args.length > 0 && args[0].equals("publication")) {
            try {
                org.totipo.tooling.publication.PublicationCli.run(java.util.Arrays.copyOfRange(args, 1, args.length));
            } catch (IOException | RuntimeException e) {
                System.err.println("Publication operation failed: " + e.getMessage());
                System.exit(1);
            }
            return;
        }
        if (args.length != 1 || !args[0].equals("smoke")) {
            System.err.println("Usage: totipo-java-tool smoke");
            System.exit(2);
        }
        StrictJson.validate("{\"nested\":[1,\"1\",{\"ok\":true}],\"nil\":null}"
                .getBytes(StandardCharsets.UTF_8));
        for (String invalid : new String[] {"{", "{\"a\":1,\"a\":2}", "{} []"}) {
            boolean rejected = false;
            try {
                StrictJson.validate(invalid.getBytes(StandardCharsets.UTF_8));
            } catch (IOException expected) {
                rejected = true;
            }
            if (!rejected) {
                throw new AssertionError("Malformed fixture accepted: " + invalid);
            }
        }
        System.out.println("Standalone Jackson bootstrap smoke passed");
    }
}
