package org.totipo.tooling.release;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.totipo.tooling.release.RemoteReleaseState.*;

/** Permanent offline tests. Unknown fake requests always fail closed. */
public final class ReleaseTest {
    private static int assertions;
    private static final String SHA = "a".repeat(40), OTHER = "b".repeat(40), REPO = "totipo-org/totipo-java";
    private ReleaseTest() {}
    @FunctionalInterface interface Checked { void run() throws Exception; }
    static void ok(boolean condition) { assertions++; if (!condition) throw new AssertionError("Assertion " + assertions); }
    static void reject(Checked action) throws Exception {
        assertions++;
        try { action.run(); } catch (IOException | IllegalArgumentException | UnsupportedOperationException e) { return; }
        throw new AssertionError("Expected rejection at " + assertions);
    }
    static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    static String step(String start, String conclusion) {
        return "{\"name\":\"" + UPLOAD_STEP + "\",\"started_at\":" + start + ",\"conclusion\":" + conclusion + "}";
    }
    static String job(String name, String sha, String steps) {
        return "{\"name\":\"" + name + "\",\"head_sha\":\"" + sha + "\",\"steps\":[" + steps + "]}";
    }
    static String page(String jobs) { return "{\"jobs\":[" + jobs + "]}"; }
    static String publish(String start, String conclusion) { return job("publish-central", SHA, step(start, conclusion)); }
    static final class Fake implements Http {
        final Map<URI, Response> fixtures = new HashMap<>();
        final java.util.ArrayList<URI> seen = new java.util.ArrayList<>();
        void put(String url, int status, String body) { fixtures.put(URI.create(url), new Response(status, bytes(body))); }
        @Override public Response get(URI uri) throws IOException {
            seen.add(uri); Response result = fixtures.get(uri);
            if (result == null) throw new IOException("Unknown fake HTTP request");
            return result;
        }
    }
    static String history(int attempt, int page) { return "https://api.github.com/repos/" + REPO + "/actions/runs/123/attempts/" + attempt + "/jobs?per_page=100&page=" + page; }
    static GitHub github(Fake fake) throws IOException { return new GitHub(fake, REPO); }
    static void write(Path root, String path, String text) throws IOException {
        Path file = root.resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file, text);
    }
    static void copy(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path p : paths.toList()) {
                Path target = destination.resolve(source.relativize(p));
                if (Files.isDirectory(p)) Files.createDirectories(target); else { Files.copy(p, target); writable(target); }
            }
        }
    }
    static void writable(Path path) throws IOException {
        // Nix store fixtures are read-only; only disposable copies receive write permission.
        Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
    }
    static Map<String, String> inventory(Path root) throws IOException {
        var result = new java.util.TreeMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (Path p : paths.filter(Files::isRegularFile).toList()) result.put(root.relativize(p).toString(), SourceFiles.sha256(Files.readAllBytes(p)));
        }
        return result;
    }
    static void fixture(Path root) throws IOException {
        String status = PreparationPlan.block("0.2.0", true) + "\n";
        write(root, "VERSION", "0.2.0\n");
        write(root, "RELEASE_CHECKLIST.md", "# Totipo Java 0.2.0 release checklist\norg.totipo:totipo-core:0.2.0\norg.totipo:totipo-storage-nio:0.2.0\nAnnotated source tag convention: `v0.2.0`\n`version`: `0.2.0`\nreview/V0_2_0_RELEASE_NOTES.md\n" + status);
        write(root, ".github/workflows/release.yml", "Exact VERSION to release (for example, 0.2.0)\n");
        write(root, "publishing/consumer-smoke/gradle.lockfile", "org.bouncycastle:bcprov-jdk18on:1.86=runtimeClasspath\n"
                + "org.totipo:totipo-core:0.2.0=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath\n"
                + "org.totipo:totipo-storage-nio:0.2.0=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath\n");
        write(root, "README.md", status + "Historical org.totipo:totipo-core:0.1.4\n");
        write(root, "API_DESIGN.md", status + "The existing 0.2.0 API.\n");
        write(root, "review/V0_2_0_RELEASE_NOTES.md", PreparationPlan.NOTES_TEMPLATE.formatted("0.2.0").replace("TODO:", "Reviewed:"));
    }
    static void versions() throws Exception {
        for (String v : List.of("0.0.0", "00.02.000", "1.2.3-rc.1", "1.2.3-alpha-2", "999999999999999999999.2.3")) ok(ReleaseInputs.validVersion(v));
        for (String v : List.of("1.2", "1.2.3\n", "1.2.3-SNAPSHOT", "1.2.3-snapshot", "$(id)", "1.2.3-", "1.2.3-rc..1", "１.2.3")) {
            ok(!ReleaseInputs.validVersion(v)); reject(() -> ReleaseInputs.stable(v));
        }
        for (String v : List.of("01.2.3", "1.02.3", "1.2.03", "1.2.3-rc1")) reject(() -> ReleaseInputs.stable(v));
        ok(ReleaseInputs.stable("999999999999999999999.2.3").size() == 3);
        var env = new HashMap<>(Map.of("REQUESTED_VERSION", "0.2.0", "REQUESTED_COMMIT", SHA, "DISPATCH_REF", "refs/heads/main", "DISPATCH_SHA", SHA));
        ok(ReleaseInputs.from(env).commit().equals(SHA));
        for (String key : List.copyOf(env.keySet())) {
            String old = env.remove(key); reject(() -> ReleaseInputs.from(env)); env.put(key, old);
            env.put(key, old + "\n"); reject(() -> ReleaseInputs.from(env)); env.put(key, old);
        }
        for (String value : List.of(OTHER, SHA.toUpperCase(java.util.Locale.ROOT), "a".repeat(39))) {
            env.put("REQUESTED_COMMIT", value); reject(() -> ReleaseInputs.from(env));
        }
    }
    static void historyTests() throws Exception {
        Fake fake = new Fake(); ok(github(fake).prior("123", 1, SHA) == PriorAttempt.NO_RECORDED_UPLOAD); ok(fake.seen.isEmpty());
        reject(() -> github(fake).prior("123", 2, SHA));
        for (String conclusion : List.of("failure", "success", "cancelled", "timed_out", "neutral")) {
            fake.put(history(1, 1), 200, page(publish("\"now\"", "\"" + conclusion + "\"")));
            ok(github(fake).prior("123", 2, SHA) == PriorAttempt.POSSIBLE_UPLOAD);
        }
        for (String[] fields : List.of(new String[]{"null", "null"}, new String[]{"\"now\"", "\"skipped\""}, new String[]{"\"\"", "\"failure\""})) {
            fake.put(history(1, 1), 200, page(publish(fields[0], fields[1])));
            ok(github(fake).prior("123", 2, SHA) == PriorAttempt.NO_RECORDED_UPLOAD);
        }
        for (String body : List.of(page(publish("null", "null")).replace("\"head_sha\":", "\"run_id\":124,\"head_sha\":"),
                page(publish("null", "null")).replace("\"head_sha\":", "\"run_attempt\":2,\"head_sha\":"),
                page(publish("null", "null")).replace("{\"jobs\":", "{\"total_count\":2,\"jobs\":"),
                page(publish("null", "null")).replace("{\"jobs\":", "{\"total_count\":0,\"jobs\":"),
                "{}", page(""), page(job("other", SHA, "")), page(publish("null", "null").replace(SHA, OTHER)),
                "{\"jobs\":null}", "{\"jobs\":[]", "{\"jobs\":[],\"jobs\":[]}", page(publish("null", "null")) + "{}",
                page("{\"name\":\"publish-central\",\"head_sha\":\"" + SHA + "\"}"),
                page(job("publish-central", SHA, "")), page(job("publish-central", SHA, "{\"name\":\"" + UPLOAD_STEP + "\"}")),
                page(publish("null", "null") + "," + publish("null", "null")),
                page(job("publish-central", SHA, step("null", "null") + "," + step("null", "null"))))) {
            fake.put(history(1, 1), 200, body); reject(() -> github(fake).prior("123", 2, SHA));
        }
        var many = new java.util.ArrayList<String>(); many.add(publish("\"now\"", "\"failure\""));
        for (int i = 1; i < 100; i++) many.add(job("job-" + i, SHA, ""));
        fake.put(history(1, 1), 200, page(String.join(",", many)));
        fake.fixtures.remove(URI.create(history(1, 2))); reject(() -> github(fake).prior("123", 2, SHA));
        fake.put(history(1, 2), 200, page("")); ok(github(fake).prior("123", 2, SHA) == PriorAttempt.POSSIBLE_UPLOAD);
        fake.put(history(1, 2), 200, page(job("next", SHA, ""))); ok(github(fake).prior("123", 2, SHA) == PriorAttempt.POSSIBLE_UPLOAD);
        for (int status : new int[]{401, 403, 404, 429, 500, 503}) { fake.put(history(1, 2), status, ""); reject(() -> github(fake).prior("123", 2, SHA)); }
        fake.put(history(1, 1), 200, page(publish("\"now\"", "\"failure\"")));
        reject(() -> github(fake).prior("123", 3, SHA)); // possible upload does not mask later missing attempt
        fake.put(history(2, 1), 200, page(publish("null", "null"))); ok(github(fake).prior("123", 3, SHA) == PriorAttempt.POSSIBLE_UPLOAD);
        reject(() -> github(fake).prior("123/evil", 2, SHA)); reject(() -> github(fake).prior("123", 101, SHA));
        reject(() -> new GitHub(fake, "host/../../evil"));
        // Exactly full pages never imply complete history; distinct names prevent duplicate short circuit.
        Fake budget = new Fake();
        for (int p = 1; p <= MAX_PAGES; p++) {
            var jobs = new java.util.ArrayList<String>();
            for (int j = 0; j < 100; j++) jobs.add(job("p" + p + "j" + j, SHA, ""));
            budget.put(history(1, p), 200, page(String.join(",", jobs)));
        }
        reject(() -> github(budget).prior("123", 2, SHA)); ok(budget.seen.size() == MAX_PAGES);
    }
    static void remoteTests() throws Exception {
        reject(() -> new RemoteReleaseState(null, Presence.ABSENT, PriorAttempt.NO_RECORDED_UPLOAD));
        reject(() -> new RemoteReleaseState(Presence.ABSENT, Presence.ABSENT, null));
        for (int core : new int[]{200, 404}) for (int storage : new int[]{200, 404}) for (boolean prior : new boolean[]{false, true}) {
            Fake fake = new Fake();
            for (String module : List.of("core", "storage-nio")) fake.put(CENTRAL + "org/totipo/totipo-" + module + "/0.2.0/totipo-" + module + "-0.2.0.pom", module.equals("core") ? core : storage, "");
            fake.put(history(1, 1), 200, page(publish("\"now\"", "\"failure\"")));
            var state = inspect("0.2.0", fake, github(fake), "123", prior ? 2 : 1, SHA);
            ok((state.core() == Presence.PRESENT) == (core == 200)); ok((state.storage() == Presence.PRESENT) == (storage == 200));
            if (prior && core == 404 && storage == 404) ok(state.classification() == Classification.POSSIBLE_UPLOAD_NOT_VISIBLE);
        }
        Fake fake = new Fake(); String core = CENTRAL + "org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.pom";
        for (int status : new int[]{201, 301, 401, 403, 429, 500, 503}) { fake.put(core, status, ""); reject(() -> inspect("0.2.0", fake, github(fake), "123", 1, SHA)); }
        reject(() -> inspect("0.2.0", uri -> { throw new IOException("timeout"); }, github(fake), "123", 1, SHA));
        String url = "https://api.github.com/repos/" + REPO + "/releases/tags/v0.2.0";
        fake.put(url, 404, ""); ok(github(fake).release("0.2.0").presence() == Presence.ABSENT);
        String release = "{\"tag_name\":\"v0.2.0\",\"name\":\"v0.2.0\",\"body\":\"reviewed\",\"draft\":false,\"prerelease\":false}";
        fake.put(url, 200, release); ok(github(fake).release("0.2.0").presence() == Presence.PRESENT);
        for (String body : List.of("null", "{}", release.replace("v0.2.0", "v0.2.1"), release + "[]", release.replace("false", "0"), release.replace("\"body\":\"reviewed\",", ""))) {
            fake.put(url, 200, body); reject(() -> github(fake).release("0.2.0"));
        }
        for (int status : new int[]{401, 403, 429, 500}) { fake.put(url, status, ""); reject(() -> github(fake).release("0.2.0")); }
    }
    static void jsonLimits() throws Exception {
        String base = page(publish("null", "null"));
        for (String body : List.of("{", base + " true", "{\"jobs\":[],\"jobs\":[]}", "{\"jobs\":[],\"x\":NaN}",
                "{\"jobs\":[],\"x\":\"" + "x".repeat(GITHUB.string() + 1) + "\"}",
                "{\"jobs\":[],\"" + "n".repeat(GITHUB.name() + 1) + "\":0}",
                "{\"jobs\":[],\"x\":" + "1".repeat(GITHUB.number() + 1) + "}",
                "{\"jobs\":[],\"x\":" + "[".repeat(GITHUB.depth()) + "0" + "]".repeat(GITHUB.depth()) + "}",
                "{\"jobs\":[],\"x\":[" + "0,".repeat(GITHUB.tokens()) + "0]}",
                base + " ".repeat(GITHUB.bytes()))) reject(() -> decodePage(bytes(body)));
        for (String body : List.of("{\"jobs\":sensitive_secret_value}", "{\"tag_name\":sensitive_secret_value}")) {
            try {
                if (body.contains("jobs")) decodePage(bytes(body)); else decodeRelease(bytes(body), "0.2.0");
                throw new AssertionError("Expected sanitized rejection");
            } catch (IOException error) { ok(!error.toString().contains("sensitive_secret_value") && error.getCause() == null); }
        }
        ok(decodePage(bytes(base.substring(0, base.length() - 1) + ",\"unused\":\"" + "a".repeat(20_000) + "\"}")).jobs().size() == 1);
        reject(() -> decodePage(new byte[]{(byte) 0xff}));
    }
    static void httpBoundaries() throws Exception {
        ok(HttpQueries.DEADLINE.equals(java.time.Duration.ofSeconds(20)));
        ok(HttpQueries.endpointLimit(URI.create(CENTRAL + "org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.pom")) == 65_536);
        ok(HttpQueries.endpointLimit(URI.create(history(1, 1))) == GITHUB.bytes());
        for (String url : List.of("http://api.github.com/repos/x/y/releases/tags/v0.2.0", "https://evil.invalid/", "https://token@api.github.com/repos/x/y/releases/tags/v0.2.0",
                "https://api.github.com:443/repos/x/y/releases/tags/v0.2.0", "https://api.github.com/repos/x/y/releases/tags/v0.2.0#fragment",
                "https://api.github.com/repos/x/y/releases/tags/v0.2.0?token=secret", "https://api.github.com/repos/x/y/releases/tags/v0.2.0%2fother",
                CENTRAL + "org/totipo/totipo-core/0.2.0/totipo-storage-nio-0.2.0.pom", history(1, 101)))
            reject(() -> HttpQueries.endpointLimit(URI.create(url)));
        class Subscription implements java.util.concurrent.Flow.Subscription {
            boolean cancelled;
            @Override public void request(long n) { ok(n == 1); }
            @Override public void cancel() { cancelled = true; }
        }
        var body = new HttpQueries.BoundedBody(4); var subscription = new Subscription(); body.onSubscribe(subscription);
        body.onNext(List.of(java.nio.ByteBuffer.wrap(bytes("ab")), java.nio.ByteBuffer.wrap(bytes("cd")))); body.onComplete();
        ok(java.util.Arrays.equals(body.getBody().toCompletableFuture().join(), bytes("abcd"))); ok(!subscription.cancelled);
        body = new HttpQueries.BoundedBody(4); subscription = new Subscription(); body.onSubscribe(subscription);
        body.onNext(List.of(java.nio.ByteBuffer.wrap(bytes("abcde")))); body.onComplete();
        ok(subscription.cancelled && body.getBody().toCompletableFuture().isCompletedExceptionally());
        body = new HttpQueries.BoundedBody(4); body.onError(new IOException("transport failure"));
        ok(body.getBody().toCompletableFuture().isCompletedExceptionally());
    }
    static void sourceTests(Path canonical, Path root) throws Exception {
        fixture(root);
        Files.copy(canonical.resolve("SPEC_PIN.md"), root.resolve("SPEC_PIN.md")); writable(root.resolve("SPEC_PIN.md"));
        copy(canonical.resolve(SourceFiles.SNAPSHOT), root.resolve(SourceFiles.SNAPSHOT));
        SourceFiles source = new SourceFiles(root); source.integrity(); ok(true);
        var before = inventory(root);
        reject(() -> new PreparationPlan(Map.of("../outside", "bad")));
        GitQueries clean = (q, v) -> switch (q) { case BRANCH -> "main"; case STATUS -> ""; default -> throw new IOException("Unexpected fake Git query"); };
        var plan = PreparationPlan.inspect(source, clean, "0.2.1", false);
        ok(plan.replacements().size() == 7); ok(inventory(root).equals(before));
        ok(plan.replacements().get("README.md").contains("totipo-core:0.1.4"));
        ok(plan.replacements().get("API_DESIGN.md").contains("existing 0.2.0 API"));
        reject(() -> plan.replacements().put("outside", "bad"));
        byte[] changed = plan.bytes("VERSION"); changed[0] = '9'; ok(plan.bytes("VERSION")[0] == '0');
        for (String target : List.of("0.2.0", "0.1.9", "0.2.1-rc1", "0.02.1", "0.2.1-SNAPSHOT")) reject(() -> PreparationPlan.inspect(source, clean, target, false));
        reject(() -> PreparationPlan.inspect(source, (q,v) -> "", "0.2.1", false));
        reject(() -> PreparationPlan.inspect(source, (q,v) -> q == GitQueries.Query.BRANCH ? "main" : "?? untracked", "0.2.1", false));
        for (String name : List.of("README.md", "API_DESIGN.md", "RELEASE_CHECKLIST.md", ".github/workflows/release.yml", "publishing/consumer-smoke/gradle.lockfile")) {
            String original = Files.readString(root.resolve(name));
            Files.writeString(root.resolve(name), original + original);
            reject(() -> PreparationPlan.inspect(source, clean, "0.2.1", false)); Files.writeString(root.resolve(name), original);
            Files.writeString(root.resolve(name), "missing markers\n");
            reject(() -> PreparationPlan.inspect(source, clean, "0.2.1", false)); Files.writeString(root.resolve(name), original);
        }
        String lock = Files.readString(root.resolve("publishing/consumer-smoke/gradle.lockfile"));
        Files.writeString(root.resolve("publishing/consumer-smoke/gradle.lockfile"), lock.replace("=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath", "=runtimeClasspath"));
        reject(() -> PreparationPlan.inspect(source, clean, "0.2.1", false)); Files.writeString(root.resolve("publishing/consumer-smoke/gradle.lockfile"), lock);
        PreparationPlan.inspect(source, (q,v) -> { throw new AssertionError("Prepared check must not call Git"); }, "0.2.0", true); ok(true);
        // Applying plans exists only here, and only inside a newly created disposable directory.
        for (var e : plan.replacements().entrySet()) write(root, e.getKey(), e.getValue());
        reject(() -> PreparationPlan.inspect(source, clean, "0.2.1", true));
        write(root, SourceFiles.notesPath("0.2.1"), PreparationPlan.NOTES_TEMPLATE.formatted("0.2.1").replace("TODO:", "Reviewed:"));
        PreparationPlan.inspect(source, (q,v) -> { throw new AssertionError("No Git"); }, "0.2.1", true); ok(true);
        write(root, SourceFiles.notesPath("0.2.1"), "## v0.2.0\n"); reject(() -> source.notes("0.2.1", false, false));
        Files.delete(root.resolve(SourceFiles.notesPath("0.2.1"))); reject(() -> source.notes("0.2.1", false, false));
        Path record = root.resolve(SourceFiles.SNAPSHOT + "requirements/v1-pre-rc.json");
        Files.writeString(record, Files.readString(record) + " "); reject(source::integrity);
        write(root, "SPEC_PIN.md", "changed"); reject(source::integrity);
        reject(() -> source.read("../outside", 10)); reject(() -> source.read("VERSION", 1));
        Files.createSymbolicLink(root.resolve("symlink"), Path.of("VERSION")); reject(() -> source.read("symlink", 1024));
    }
    static void identityTests(Path canonical, Path root) throws Exception {
        fixture(root); Files.copy(canonical.resolve("SPEC_PIN.md"), root.resolve("SPEC_PIN.md")); writable(root.resolve("SPEC_PIN.md"));
        var source = new SourceFiles(root); var inputs = new ReleaseInputs("0.2.0", SHA);
        var results = new java.util.EnumMap<GitQueries.Query,String>(GitQueries.Query.class);
        results.put(GitQueries.Query.HEAD, SHA); results.put(GitQueries.Query.STATUS, ""); results.put(GitQueries.Query.REMOTE_MAIN, SHA);
        results.put(GitQueries.Query.LOCAL_TAG, ""); results.put(GitQueries.Query.REMOTE_TAG, "");
        GitQueries git = (q,v) -> { String s = results.get(q); if (s == null) throw new IOException("Unknown Git fixture"); return s; };
        Fake fake = new Fake(); fake.put("https://api.github.com/repos/" + REPO + "/releases/tags/v0.2.0", 404, "");
        SourceIdentity.inspect(source, inputs, git, github(fake), false); ok(true);
        for (GitQueries.Query q : List.of(GitQueries.Query.HEAD, GitQueries.Query.REMOTE_MAIN, GitQueries.Query.STATUS)) {
            String original = results.put(q, OTHER); reject(() -> SourceIdentity.inspect(source, inputs, git, github(fake), false)); results.put(q, original);
        }
        String ref = "refs/tags/v0.2.0";
        for (String tag : List.of(SHA + "\t" + ref, OTHER + "\t" + ref + "\n" + SHA + "\t" + ref + "^{}")) {
            results.put(GitQueries.Query.REMOTE_TAG, tag); reject(() -> SourceIdentity.inspect(source, inputs, git, github(fake), false));
            ok(SourceIdentity.inspect(source, inputs, git, github(fake), true).remoteTag().commit().equals(SHA));
        }
        for (String tag : List.of(OTHER + "\t" + ref, SHA + "\t" + ref + "\n" + OTHER + "\t" + ref + "^{}",
                SHA + "\t" + ref + "^{}", SHA + "\t" + ref + "\n" + SHA + "\t" + ref, "bad")) {
            results.put(GitQueries.Query.REMOTE_TAG, tag); reject(() -> SourceIdentity.inspect(source, inputs, git, github(fake), true));
        }
        results.put(GitQueries.Query.REMOTE_TAG, ""); results.put(GitQueries.Query.LOCAL_TAG, "v0.2.0"); results.put(GitQueries.Query.LOCAL_TAG_COMMIT, SHA); results.put(GitQueries.Query.LOCAL_TAG_TYPE, "tag");
        reject(() -> SourceIdentity.inspect(source, inputs, git, github(fake), false)); SourceIdentity.inspect(source, inputs, git, github(fake), true); ok(true);
        results.put(GitQueries.Query.LOCAL_TAG_TYPE, "commit");
        ok(!SourceIdentity.inspect(source, inputs, git, github(fake), true).localTag().annotated());
        results.put(GitQueries.Query.LOCAL_TAG_COMMIT, OTHER); reject(() -> SourceIdentity.inspect(source, inputs, git, github(fake), true));
        reject(() -> SourceIdentity.inspect(source, inputs, (q,v) -> { throw new IOException("Git failure"); }, github(fake), false));
    }
    public static void main(String[] args) throws Exception {
        versions(); historyTests(); remoteTests(); jsonLimits(); httpBoundaries();
        Path temp = Files.createTempDirectory("totipo-release-tests-");
        try {
            sourceTests(Path.of(args[0]), Files.createDirectory(temp.resolve("source")));
            identityTests(Path.of(args[0]), Files.createDirectory(temp.resolve("identity")));
            var recorded = new RecordedServices(temp);
            reject(() -> recorded.git().query(GitQueries.Query.HEAD, "0.2.0"));
            reject(() -> recorded.http().get(URI.create("https://api.github.com/missing")));
            reject(() -> GitQueries.local(temp).query(GitQueries.Query.REMOTE_MAIN, "0.2.0"));
            for (String command : List.of("publish", "upload-ready", "sign", "tag-release", "release-create", "prepare-apply", "unknown"))
                reject(() -> ReleaseInspectionCli.run(new String[]{command}, Map.of()));
        } finally {
            try (var files = Files.walk(temp)) { for (Path p : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p); }
        }
        System.out.println("Release inspection tests: " + assertions + " assertions passed; offline fixtures only");
    }
}
