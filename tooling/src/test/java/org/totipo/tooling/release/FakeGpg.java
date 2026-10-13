package org.totipo.tooling.release;

/** Deterministic subprocess fixture. No OpenPGP key material, GPG dependency or signing operation. */
public final class FakeGpg {
    private FakeGpg() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 9 || !args[1].equals("--no-options") || !args[2].equals("--batch")
                || !args[3].equals("--no-autostart") || !args[4].equals("--homedir")
                || !args[6].equals("--pinentry-mode") || !args[7].equals("error") || !args[8].equals("--list-packets"))
            throw new AssertionError("Unexpected arguments");
        if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) throw new AssertionError("Inherited secret");
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[5], "cleanup-sentinel"), "synthetic");
        if (args[0].equals("stdin-block")) { Thread.sleep(60_000); return; }
        if (!java.util.Arrays.equals(System.in.readAllBytes(), "synthetic-key".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            throw new AssertionError("Incorrect stdin key delivery");
        switch (args[0]) {
            case "ok" -> System.out.print(":secret key packet:\n\tskey[3]: [2048 bits]\n");
            case "protected" -> System.out.print(":secret key packet:\n\tskey[3]: [v4 protected]\n");
            case "fail" -> { System.out.print(":secret key packet:\n"); System.err.print("private packet diagnostic"); System.exit(1); }
            case "timeout" -> Thread.sleep(60_000);
            case "oversized" -> System.out.print("x".repeat(GpgPacketInspector.MAX_OUTPUT + 1));
            case "stderr" -> { System.err.print("x".repeat(GpgPacketInspector.MAX_OUTPUT + 1)); System.out.print(":secret key packet:\n"); }
            case "malformed" -> System.out.print("unexpected extra output\n:secret key packet:\n");
            default -> throw new AssertionError("Unknown fixture");
        }
    }
}
