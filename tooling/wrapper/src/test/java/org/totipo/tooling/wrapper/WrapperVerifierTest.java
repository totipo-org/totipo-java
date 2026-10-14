package org.totipo.tooling.wrapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Two-file identity and public CLI regression tests; JDK only. */
public final class WrapperVerifierTest {
    private WrapperVerifierTest() {}
    static void reject(Path jar, Path properties) throws Exception {
        try { WrapperVerifier.verify(jar, properties); }
        catch (IOException expected) { return; }
        throw new AssertionError("Modified/missing wrapper accepted");
    }
    static void cli(int status, String... args) throws Exception {
        var command = new java.util.ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(),
            "-cp", System.getProperty("java.class.path"), WrapperVerifier.class.getName()));
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if (process.waitFor() != status) throw new AssertionError("Wrong CLI status");
    }
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]), properties = Path.of(args[1]);
        WrapperVerifier.verify(jar, properties); cli(0, jar.toString(), properties.toString());
        Path temp = Files.createTempDirectory("wrapper-identity-");
        try {
            Path bad = temp.resolve("bad"), missing = temp.resolve("missing");
            Files.writeString(bad, "modified");
            reject(bad, properties); reject(jar, bad);
            reject(missing, properties); reject(jar, missing); reject(temp, properties);
            cli(1, jar.toString(), missing.toString()); cli(1, "", properties.toString());
            cli(2); cli(2, jar.toString()); cli(2, jar.toString(), properties.toString(), "extra");
        } finally { Files.deleteIfExists(temp.resolve("bad")); Files.delete(temp); }
        System.out.println("Wrapper identity and CLI tests PASS");
    }
}
