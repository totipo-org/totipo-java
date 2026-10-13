package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.totipo.tooling.release.ReleaseTest.*;
import static org.totipo.tooling.release.RemoteReleaseState.*;

/** Focused create-only reservation and existing-tag release tests. No simulated release coordinator. */
public final class FinalizationTest {
    private FinalizationTest() {}
    static final ReleaseInputs INPUTS = new ReleaseInputs("0.2.0", "a".repeat(40));
    static final String OBJECT = "b".repeat(40);
    static Response response(int status, Map<String, ?> body) throws IOException {
        return new Response(status, bytes(FinalizationProcesses.json(body)));
    }
    static Map<String, Object> ref() { return Map.of("ref", "refs/tags/v0.2.0", "object", Map.of("type", "tag", "sha", OBJECT)); }
    static Map<String, Object> tag() { return Map.of("tag", "v0.2.0", "sha", OBJECT, "message", "totipo-java 0.2.0\n", "object", Map.of("type", "commit", "sha", INPUTS.commit())); }
    static final class Api implements FinalizationProcesses.Api {
        boolean reserved, ambiguous, failBefore, ambiguousRelease;
        Map<String, Object> tag = tag(), ref = ref();
        Map<String, Object> release;
        final AtomicInteger refs = new AtomicInteger(), releases = new AtomicInteger();
        @Override public synchronized Response request(String method, String path, Map<String, ?> body) throws IOException {
            if (path.equals("/git/ref/tags/v0.2.0")) return response(reserved ? 200 : 404, ref);
            if (path.equals("/git/tags/" + OBJECT)) return response(200, tag);
            if (path.equals("/git/tags") && method.equals("POST")) {
                if (failBefore) throw new IOException("before tag");
                ok(body.get("message").equals("totipo-java 0.2.0\n")); return response(201, Map.of("sha", OBJECT));
            }
            if (path.equals("/git/refs") && method.equals("POST")) {
                refs.incrementAndGet();
                if (reserved) return response(422, Map.of());
                reserved = true;
                if (ambiguous) throw new IOException("response lost");
                return response(201, ref);
            }
            if (path.equals("/releases/tags/v0.2.0")) return response(release == null ? 404 : 200, release == null ? Map.of() : release);
            if (path.equals("/releases") && method.equals("POST")) {
                releases.incrementAndGet(); release = new java.util.LinkedHashMap<>(body);
                if (ambiguousRelease) throw new IOException("response lost");
                return response(201, release);
            }
            throw new AssertionError("Unexpected operation " + method + " " + path);
        }
    }
    static void reservations() throws Exception {
        Api api = new Api(); SourceIdentity.reserve(api, INPUTS, "1"); ok(api.reserved && api.refs.get() == 1);
        RemoteReleaseState.absent(INPUTS.version(), uri -> response(404, Map.of()));
        // A new dispatch at attempt 1, reruns and a reservation with no upload all remain consumed.
        for (String attempt : new String[]{"1", "2", "3"}) reject(() -> SourceIdentity.reserve(api, INPUTS, attempt));
        ok(api.refs.get() == 1);
        Api ambiguous = new Api(); ambiguous.ambiguous = true;
        reject(() -> SourceIdentity.reserve(ambiguous, INPUTS, "1")); ok(ambiguous.reserved && ambiguous.refs.get() == 1);
        reject(() -> SourceIdentity.reserve(ambiguous, INPUTS, "1")); ok(ambiguous.refs.get() == 1);
        Api before = new Api(); before.failBefore = true; reject(() -> SourceIdentity.reserve(before, INPUTS, "1")); ok(!before.reserved && before.refs.get() == 0);
        for (int status : new int[]{200, 403, 422, 500}) {
            Api underlying = new Api();
            FinalizationProcesses.Api uncertain = (method, path, body) -> path.equals("/git/refs")
                ? response(status, ref()) : underlying.request(method, path, body);
            reject(() -> SourceIdentity.reserve(uncertain, INPUTS, "1"));
        }
        Api unreadable = new Api();
        FinalizationProcesses.Api wrongReadback = (method, path, body) -> path.equals("/git/tags/" + OBJECT)
            ? response(200, Map.of()) : unreadable.request(method, path, body);
        reject(() -> SourceIdentity.reserve(wrongReadback, INPUTS, "1")); ok(unreadable.reserved);
        Api rerun = new Api(); reject(() -> SourceIdentity.reserve(rerun, INPUTS, "2")); ok(rerun.refs.get() == 0);
        // Competing creators both observe absence; only atomic create-reference wins.
        Api race = new Api(); var barrier = new java.util.concurrent.CyclicBarrier(2);
        FinalizationProcesses.Api competing = (method, path, body) -> {
            if (method.equals("POST") && path.equals("/git/tags")) {
                try { barrier.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                catch (Exception e) { throw new IOException("barrier failed"); }
            }
            return race.request(method, path, body);
        };
        AtomicInteger winners = new AtomicInteger();
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.List.of(workers.submit(() -> reserve(competing, winners)), workers.submit(() -> reserve(competing, winners)));
            for (var task : tasks) task.get();
        }
        ok(winners.get() == 1 && race.refs.get() == 2);
    }
    static void reserve(FinalizationProcesses.Api api, AtomicInteger winners) {
        try { SourceIdentity.reserve(api, INPUTS, "1"); winners.incrementAndGet(); } catch (IOException expected) { /* loser stops */ }
    }
    public static void main(String[] args) throws Exception {
        reservations();
        Path root = Files.createTempDirectory("release-finalizer-test-");
        try {
            fixture(root); Files.copy(Path.of(args[0]).resolve("SPEC_PIN.md"), root.resolve("SPEC_PIN.md"));
            var expected = ReleaseFinalizer.Expected.read(new SourceFiles(root), INPUTS);
            Api api = new Api(); api.reserved = true;
            ReleaseFinalizer.finish(expected, api); ok(api.releases.get() == 1);
            ReleaseFinalizer.finish(expected, api); ok(api.releases.get() == 1);
            api.release.put("body", expected.body().replace("\n", "\r\n") + " \n"); ReleaseFinalizer.finish(expected, api);
            for (String field : new String[]{"tag_name", "name", "body", "draft", "prerelease"}) {
                Object old = api.release.get(field); api.release.put(field, field.equals("draft") || field.equals("prerelease") ? true : "wrong");
                reject(() -> ReleaseFinalizer.finish(expected, api)); api.release.put(field, old);
            }
            Api uncertain = new Api(); uncertain.reserved = true; uncertain.ambiguousRelease = true;
            reject(() -> ReleaseFinalizer.finish(expected, uncertain)); ok(uncertain.releases.get() == 1);
            ReleaseFinalizer.finish(expected, uncertain); ok(uncertain.releases.get() == 1);
            Api missing = new Api(); reject(() -> ReleaseFinalizer.finish(expected, missing)); ok(missing.releases.get() == 0);
            Api lightweight = new Api(); lightweight.reserved = true;
            lightweight.ref = Map.of("ref", "refs/tags/v0.2.0", "object", Map.of("type", "commit", "sha", OBJECT));
            reject(() -> ReleaseFinalizer.finish(expected, lightweight));
            for (Map<String, Object> bad : java.util.List.of(
                Map.<String, Object>of("tag", "v0.2.0", "sha", OBJECT, "message", "wrong", "object", Map.of("type", "commit", "sha", INPUTS.commit())),
                Map.<String, Object>of("tag", "v0.2.0", "sha", OBJECT, "message", "totipo-java 0.2.0\n", "object", Map.of("type", "commit", "sha", "c".repeat(40))))) {
                Api wrong = new Api(); wrong.reserved = true; wrong.tag = bad; reject(() -> ReleaseFinalizer.finish(expected, wrong)); ok(wrong.releases.get() == 0);
            }
        } finally { AuthorizationTest.remove(root); }
        System.out.println("Create-only tag and release tests PASS");
    }
}
