package org.totipo.tooling.release;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Synthetic executable; no network or real credentials. */
public final class FakeFinalizationCli {
    private FakeFinalizationCli() {}
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        if (mode.equals("timeout")) { new java.util.concurrent.CountDownLatch(1).await(); return; }
        if (mode.equals("overflow")) { System.out.write(new byte[2 * 1024 * 1024]); return; }
        if (mode.equals("failure")) { System.err.println(System.getenv("GH_TOKEN")); System.exit(1); }
        if (mode.equals("malformed")) { System.out.write(new byte[]{(byte) 0xff}); return; }
        if (mode.equals("git")) {
            var props = new java.util.Properties(); try (var in = Files.newInputStream(Path.of(args[1]))) { props.load(in); }
            List<String> command = List.of(args).subList(2, args.length);
            if (command.stream().anyMatch(v -> v.contains("synthetic-token") || v.contains("AUTHORIZATION"))) throw new AssertionError("Credential in argv");
            if (System.getenv("HOME") != null || System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) throw new AssertionError("Ambient environment inherited");
            int start = 0;
            if (!command.get(start++).equals("--no-optional-locks")) throw new AssertionError("Git isolation missing");
            while (start < command.size() && command.get(start).equals("-c")) start += 2;
            List<String> operation = command.subList(start, command.size());
            String object = props.getProperty("object"), commit = props.getProperty("commit");
            if (operation.equals(List.of("config", "--local", "--null", "--list"))) return;
            if (operation.equals(List.of("rev-parse", "--verify", "refs/tags/v0.2.0"))) { System.out.println(object); return; }
            if (operation.equals(List.of("rev-parse", "--verify", "refs/tags/v0.2.0^{commit}"))) { System.out.println(commit); return; }
            if (operation.equals(List.of("cat-file", "-t", object))) { System.out.println("tag"); return; }
            if (operation.equals(List.of("cat-file", "-p", object))) {
                System.out.print("object " + commit + "\ntype commit\ntag v0.2.0\ntagger fixture <fixture@example.invalid> 1000000000 +0000\n\ntotipo-java 0.2.0\n"); return;
            }
            if (operation.equals(List.of("push", "--no-verify", "--", "https://github.com/totipo-org/totipo-java.git", object + ":refs/tags/v0.2.0"))) {
                if (!"1".equals(System.getenv("GIT_CONFIG_COUNT"))
                        || !"http.https://github.com/totipo-org/totipo-java.git.extraheader".equals(System.getenv("GIT_CONFIG_KEY_0"))
                        || !("AUTHORIZATION: basic " + java.util.Base64.getEncoder().encodeToString("x-access-token:synthetic-token".getBytes(java.nio.charset.StandardCharsets.UTF_8))).equals(System.getenv("GIT_CONFIG_VALUE_0"))) throw new AssertionError("Wrong ephemeral scoped authentication");
                return;
            }
            throw new AssertionError("Unexpected fake Git command");
        }
        if (!mode.equals("gh")) throw new AssertionError("Unexpected fake executable mode");
        List<String> tail = List.of(args).subList(2, args.length);
        if (tail.size() != 10 || !tail.subList(0, 9).equals(List.of("release", "create", "v0.2.0", "--repo", "totipo-org/totipo-java", "--verify-tag", "--title", "v0.2.0", "--notes-file"))) throw new AssertionError("Unexpected gh arguments");
        Path notes = Path.of(tail.getLast());
        if (!java.util.Arrays.equals(Files.readAllBytes(notes), Files.readAllBytes(Path.of(args[1])))) throw new AssertionError("Wrong release body bytes");
        if (!Files.getPosixFilePermissions(notes).equals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))) throw new AssertionError("Notes file not private");
        if (!"synthetic-token".equals(System.getenv("GH_TOKEN")) || System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) throw new AssertionError("Environment isolation");
    }
}
