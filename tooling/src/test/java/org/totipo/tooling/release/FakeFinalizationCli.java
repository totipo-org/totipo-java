package org.totipo.tooling.release;

/** Process fixture; never contacts GitHub or invokes an uploader. */
public final class FakeFinalizationCli {
    private FakeFinalizationCli() {}
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String config = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (!config.contains("Authorization: Bearer synthetic_token")) throw new AssertionError("stdin authentication missing");
        for (String arg : args) if (arg.contains("synthetic_token")) throw new AssertionError("credential argv");
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
