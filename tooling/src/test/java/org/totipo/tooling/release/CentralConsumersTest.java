package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.totipo.tooling.publication.AcceptedFixture;
import static org.totipo.tooling.release.TestResultsTest.ok;

/** Receipts require execution, both modes, reviewed version and two private empty homes. */
public final class CentralConsumersTest {
    private CentralConsumersTest() {}
    static final class Fake implements CentralConsumers.Executor {
        final Path root;
        final String scenario;
        final List<Path> homes = new ArrayList<>();
        int executed, removed;
        Fake(Path root, String scenario) { this.root = root; this.scenario = scenario; }
        @Override public Path newHome() throws IOException {
            Path home = Files.createTempDirectory("totipo-consumer-test-"); homes.add(home);
            if (scenario.equals("nonempty")) Files.writeString(home.resolve("cached"), "stale");
            if (scenario.equals("local-home")) { Files.delete(home); home = Files.createDirectory(root.resolve("local-home")); homes.set(homes.size() - 1, home); }
            return home;
        }
        @Override public CentralConsumers.Exit execute(CentralConsumers.Command command) throws IOException {
            executed++;
            ok(command.arguments().contains("-PremoteCentral") && !command.arguments().contains("--offline"));
            ok(command.arguments().subList(command.arguments().size() - 2, command.arguments().size()).equals(List.of("clean", "check")));
            ok(command.environment().keySet().equals(java.util.Set.of("GRADLE_USER_HOME", "GRADLE_OPTS")));
            if (scenario.equals("exception")) throw new IOException("process never completed");
            if (scenario.equals("null")) return null;
            if (scenario.equals("drift")) Files.writeString(root.resolve("VERSION"), "0.2.1\n");
            return new CentralConsumers.Exit(scenario.equals("module") || scenario.equals("pom") && executed == 2 ? 1 : 0);
        }
        @Override public void removeHome(Path home) throws IOException { removed++; PreparationApplicationTest.remove(home); }
        void cleanup() throws IOException { for (Path home : homes) if (Files.exists(home)) PreparationApplicationTest.remove(home); }
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("totipo-consumers-");
        try {
            AcceptedFixture.create(root);
            var remote = RemoteVerificationTest.verify(root, RemoteVerificationTest.fake(root), 2400);
            var inputs = new ReleaseInputs("0.2.0", "a".repeat(40));
            for (String name : List.of("success", "module", "pom", "exception", "null", "drift", "nonempty", "local-home", "version")) {
                Fake fake = new Fake(root, name);
                Files.writeString(root.resolve("VERSION"), name.equals("version") ? "0.2.1\n" : "0.2.0\n");
                try {
                    if (name.equals("success")) {
                        var verified = CentralConsumers.run(root, inputs, remote, fake);
                        ok(verified.prerequisites().module().mode() == ReleaseFinalizer.ConsumerMode.CENTRAL_MODULE_METADATA);
                        ok(verified.prerequisites().pom().mode() == ReleaseFinalizer.ConsumerMode.CENTRAL_POM);
                        ok(fake.executed == 2 && fake.removed == 2 && !fake.homes.getFirst().equals(fake.homes.getLast()));
                    } else ReleaseTest.reject(() -> CentralConsumers.run(root, inputs, remote, fake));
                    int expected = name.equals("success") || name.equals("pom") ? 2 : List.of("version", "local-home", "nonempty").contains(name) ? 0 : 1;
                    ok(fake.executed == expected);
                    if (!name.equals("local-home")) ok(fake.removed == fake.homes.size());
                } finally { fake.cleanup(); }
            }
            System.out.println("CentralConsumersTest PASS: 9 execution/isolation cases");
        } finally { PreparationApplicationTest.remove(root); }
    }
}
