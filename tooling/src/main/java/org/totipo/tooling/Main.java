package org.totipo.tooling;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Bootstrap only; no publication or release operations. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
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
