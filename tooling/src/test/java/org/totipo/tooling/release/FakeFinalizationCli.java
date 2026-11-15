package org.totipo.tooling.release;

/** Process fixture; never contacts GitHub or invokes an uploader. */
public final class FakeFinalizationCli {
    private FakeFinalizationCli() {}
    // Synthetic JWT-shaped token with dots, base64url punctuation and modern length.
    static final String STATELESS_TOKEN = "ghs_12345_eyJhbGciOiJSUzI1NiJ9." + "a".repeat(160) + "." + "b_-".repeat(110);
    static final java.util.List<String> TOKENS = java.util.List.of("synthetic_token", STATELESS_TOKEN,
        "opaque+token/with~padding==", "opaque:token%with!punctuation", "x".repeat(1024));
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String config = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String token = TOKENS.stream().filter(value -> config.contains("Authorization: Bearer " + value)).findFirst()
            .orElseThrow(() -> new AssertionError("stdin authentication missing"));
        for (String arg : args) if (arg.contains(token)) throw new AssertionError("credential argv");
        if (args.length > 1 && (!args[1].equals("--disable") || !java.util.List.of(args).contains("--retry")
                || !args[args.length - 1].startsWith("https://api.github.com/repos/totipo-org/totipo-java/")))
            throw new AssertionError("Unapproved curl arguments");
        if (System.getenv("GH_TOKEN") != null || System.getenv("GITHUB_TOKEN") != null) throw new AssertionError("inherited credentials");
        if (mode.equals("timeout")) { Thread.sleep(30_000); return; }
        if (mode.equals("overflow")) { System.out.print("x".repeat(4_194_305)); return; }
        if (mode.equals("fail")) { System.err.println(config); System.exit(1); }
        if (mode.equals("bad")) { System.out.print("unknown"); return; }
        System.out.print("{}\n404");
    }
}
