package org.totipo.tooling.release;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.totipo.tooling.StrictJson;
import static org.totipo.tooling.release.SourceFiles.require;

/** Facts only. No method returns permission to publish or retry. Errors throw, never mean absence. */
public record RemoteReleaseState(Presence core, Presence storage, PriorAttempt priorAttempt) {
    public RemoteReleaseState {
        if (core == null || storage == null || priorAttempt == null)
            throw new IllegalArgumentException("Incomplete remote observations");
    }
    public enum Presence { PRESENT, ABSENT }
    public enum PriorAttempt { POSSIBLE_UPLOAD, NO_RECORDED_UPLOAD }
    public enum Classification { COORDINATES_ABSENT, COORDINATES_PRESENT, PARTIAL_COORDINATES,
                                 POSSIBLE_UPLOAD_NOT_VISIBLE, POSSIBLE_UPLOAD_VISIBLE }
    public Classification classification() {
        boolean any = core == Presence.PRESENT || storage == Presence.PRESENT;
        if (priorAttempt == PriorAttempt.POSSIBLE_UPLOAD)
            return any ? Classification.POSSIBLE_UPLOAD_VISIBLE : Classification.POSSIBLE_UPLOAD_NOT_VISIBLE;
        if (!any) return Classification.COORDINATES_ABSENT;
        return core == storage ? Classification.COORDINATES_PRESENT : Classification.PARTIAL_COORDINATES;
    }
    public record Response(int status, byte[] body) {
        public Response { body = body.clone(); }
        @Override public byte[] body() { return body.clone(); }
    }
    @FunctionalInterface public interface Http { Response get(URI uri) throws IOException; }
    public record ReleaseRecord(Presence presence, String tag, String name, boolean draft, boolean prerelease, String body) {}
    public record Step(String name, String startedAt, String conclusion) {}
    public record Job(String name, String head, List<Step> steps, String id, String runId, Integer attempt) { public Job { steps = List.copyOf(steps); } }
    public record Page(List<Job> jobs, Integer totalCount) { public Page { jobs = List.copyOf(jobs); } }
    public static final StrictJson.Profile GITHUB = new StrictJson.Profile(4_194_304, 32, 262_144, 128, 128, 500_000);
    public static final int MAX_PAGES = 100, MAX_ATTEMPTS = 100;
    public static final String UPLOAD_STEP = "Publish and release to Maven Central";
    public static final String CENTRAL = "https://repo.maven.apache.org/maven2/";

    static Response response(Http http, URI uri, int limit) throws IOException {
        Response r = http.get(uri);
        require(r != null && (r.status() == 200 || r.status() == 404), "Unexpected HTTP status");
        require(r.body().length <= limit, "HTTP response byte limit");
        return r;
    }
    public static RemoteReleaseState inspect(String version, Http http, GitHub github, String run, int attempt, String commit) throws IOException {
        require(ReleaseInputs.validVersion(version), "Invalid release version");
        var observations = new ArrayList<Presence>();
        for (String module : List.of("core", "storage-nio")) {
            String artifact = "totipo-" + module;
            var r = response(http, URI.create(CENTRAL + "org/totipo/" + artifact + "/" + version + "/" + artifact + "-" + version + ".pom"), 65_536);
            observations.add(r.status() == 200 ? Presence.PRESENT : Presence.ABSENT);
        }
        return new RemoteReleaseState(observations.get(0), observations.get(1), github.prior(run, attempt, commit));
    }
    public static final class GitHub {
        private final Http http;
        private final String repository;
        public GitHub(Http http, String repository) throws IOException {
            require(repository != null && repository.matches("[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+")
                    && !repository.endsWith("/.") && !repository.endsWith("/.."), "Invalid GitHub repository");
            this.http = http; this.repository = repository;
        }
        URI uri(String path) { return URI.create("https://api.github.com/repos/" + repository + path); }
        public ReleaseRecord release(String version) throws IOException {
            require(ReleaseInputs.validVersion(version), "Invalid release version");
            var response = response(http, uri("/releases/tags/v" + version), GITHUB.bytes());
            if (response.status() == 404) return new ReleaseRecord(Presence.ABSENT, null, null, false, false, null);
            return decodeRelease(response.body(), version);
        }
        public PriorAttempt prior(String run, int attempt, String commit) throws IOException {
            require(run != null && run.matches("[1-9][0-9]{0,19}") && attempt >= 1 && attempt <= MAX_ATTEMPTS
                    && commit != null && commit.matches("[0-9a-f]{40}"), "Invalid workflow history identity/budget");
            boolean possible = false;
            for (int previous = 1; previous < attempt; previous++) {
                boolean complete = false;
                Set<String> jobNames = new HashSet<>(), jobIds = new HashSet<>();
                boolean publishFound = false;
                Integer expectedCount = null;
                int observedCount = 0;
                for (int page = 1; page <= MAX_PAGES; page++) {
                    var r = response(http, uri("/actions/runs/" + run + "/attempts/" + previous + "/jobs?per_page=100&page=" + page), GITHUB.bytes());
                    require(r.status() == 200, "Cannot establish previous workflow history");
                    Page decoded = decodePage(r.body());
                    require(decoded.jobs().size() <= 100, "Too many jobs on API page");
                    if (decoded.totalCount() != null) {
                        require(expectedCount == null || expectedCount.equals(decoded.totalCount()), "Contradictory history total_count");
                        expectedCount = decoded.totalCount();
                    }
                    observedCount += decoded.jobs().size();
                    require(expectedCount == null || observedCount <= expectedCount, "History exceeds declared total_count");
                    for (Job job : decoded.jobs()) {
                        require(job.head().equals(commit), "Previous attempt commit differs");
                        require(job.runId() == null || job.runId().equals(run), "Previous job run ID differs");
                        require(job.attempt() == null || job.attempt() == previous, "Previous job attempt differs");
                        require(job.id() == null || jobIds.add(job.id()), "Duplicate/contradictory job ID");
                        require(jobNames.add(job.name()), "Duplicate/contradictory job identity");
                        if (job.name().equals("publish-central")) {
                            publishFound = true;
                            boolean uploadFound = false;
                            Set<String> stepNames = new HashSet<>();
                            for (Step step : job.steps()) {
                                require(stepNames.add(step.name()), "Duplicate/contradictory step identity");
                                if (step.name().equals(UPLOAD_STEP)) {
                                    uploadFound = true;
                                    if (step.startedAt() != null && !step.startedAt().isEmpty() && !"skipped".equals(step.conclusion())) possible = true;
                                }
                            }
                            require(uploadFound, "Missing upload step history");
                        }
                    }
                    if (decoded.jobs().size() < 100) { complete = true; break; }
                }
                require(complete, "Workflow page budget exhausted; incomplete history");
                require(expectedCount == null || observedCount == expectedCount, "Incomplete history total_count");
                // Empty history or a missing publication job cannot prove a clean earlier attempt.
                require(publishFound, "Missing publish-central history");
            }
            return possible ? PriorAttempt.POSSIBLE_UPLOAD : PriorAttempt.NO_RECORDED_UPLOAD;
        }
    }
    // Schema decoding uses the existing strict Jackson stream infrastructure, including unknown fields.
    static JsonParser parser(byte[] bytes) throws IOException {
        java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
        require(!(bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf), "JSON BOM rejected");
        for (byte b : bytes) require(b != 0, "Raw NUL in JSON");
        return StrictJson.open(bytes, GITHUB);
    }
    static void expect(JsonParser p, JsonToken token) throws IOException { require(p.currentToken() == token, "Malformed GitHub JSON field"); }
    static String string(JsonParser p) throws IOException { expect(p, JsonToken.VALUE_STRING); return p.getText(); }
    static String nullable(JsonParser p) throws IOException { return p.currentToken() == JsonToken.VALUE_NULL ? null : string(p); }
    static boolean bool(JsonParser p) throws IOException {
        require(p.currentToken() == JsonToken.VALUE_TRUE || p.currentToken() == JsonToken.VALUE_FALSE, "Expected boolean GitHub field");
        return p.currentToken() == JsonToken.VALUE_TRUE;
    }
    static void skip(JsonParser p) throws IOException {
        // Force lazy string validation even in irrelevant API metadata. Never use skipChildren().
        if (p.currentToken() == JsonToken.START_ARRAY || p.currentToken() == JsonToken.START_OBJECT) {
            JsonToken end = p.currentToken() == JsonToken.START_ARRAY ? JsonToken.END_ARRAY : JsonToken.END_OBJECT;
            while (p.nextToken() != end) { require(p.currentToken() != null, "Truncated GitHub JSON"); skip(p); }
        } else if (p.currentToken() == JsonToken.VALUE_STRING) p.getText();
        else require(p.currentToken() != null, "Truncated GitHub JSON");
    }
    static String field(JsonParser p) throws IOException {
        expect(p, JsonToken.FIELD_NAME); String name = p.currentName(); p.nextToken(); return name;
    }
    public static ReleaseRecord decodeRelease(byte[] bytes, String version) throws IOException {
        try (var p = parser(bytes)) {
            p.nextToken(); expect(p, JsonToken.START_OBJECT);
            String tag = null, name = null, body = null; Boolean draft = null, prerelease = null;
            while (p.nextToken() != JsonToken.END_OBJECT) switch (field(p)) {
                case "tag_name" -> tag = string(p);
                case "name" -> name = string(p);
                case "body" -> body = string(p);
                case "draft" -> draft = bool(p);
                case "prerelease" -> prerelease = bool(p);
                default -> skip(p);
            }
            require(p.nextToken() == null, "Trailing GitHub JSON");
            require(tag != null && tag.equals("v" + version) && name != null && body != null && draft != null && prerelease != null,
                    "Missing/inconsistent GitHub release fields");
            return new ReleaseRecord(Presence.PRESENT, tag, name, draft, prerelease, body);
        } catch (IOException | RuntimeException error) {
            // Jackson diagnostics may include source tokens. Never expose response contents.
            throw new IOException("Malformed or out-of-bounds GitHub release response");
        }
    }
    public static Page decodePage(byte[] bytes) throws IOException {
        try (var p = parser(bytes)) {
            p.nextToken(); expect(p, JsonToken.START_OBJECT); List<Job> jobs = null; Integer totalCount = null;
            while (p.nextToken() != JsonToken.END_OBJECT) {
                switch (field(p)) {
                    case "jobs" -> {
                        expect(p, JsonToken.START_ARRAY); jobs = new ArrayList<>();
                        while (p.nextToken() != JsonToken.END_ARRAY) jobs.add(job(p));
                    }
                    case "total_count" -> {
                        var count = StrictJson.exactInteger(p);
                        require(count.signum() >= 0 && count.compareTo(java.math.BigInteger.valueOf(100L * MAX_PAGES)) <= 0, "Invalid history total_count");
                        totalCount = count.intValueExact();
                    }
                    default -> skip(p);
                }
            }
            require(p.nextToken() == null && jobs != null, "Missing jobs or trailing GitHub JSON");
            return new Page(jobs, totalCount);
        } catch (IOException | RuntimeException error) {
            throw new IOException("Malformed or out-of-bounds GitHub jobs response");
        }
    }
    static Job job(JsonParser p) throws IOException {
        expect(p, JsonToken.START_OBJECT); String name = null, head = null, id = null, runId = null; Integer attempt = null; List<Step> steps = null;
        while (p.nextToken() != JsonToken.END_OBJECT) switch (field(p)) {
            case "name" -> name = string(p);
            case "head_sha" -> head = string(p);
            case "id" -> id = positiveInteger(p);
            case "run_id" -> runId = positiveInteger(p);
            case "run_attempt" -> {
                String value = positiveInteger(p);
                require(new java.math.BigInteger(value).compareTo(java.math.BigInteger.valueOf(MAX_ATTEMPTS)) <= 0, "Invalid job attempt");
                attempt = Integer.valueOf(value);
            }
            case "steps" -> {
                expect(p, JsonToken.START_ARRAY); steps = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) steps.add(step(p));
            }
            default -> skip(p);
        }
        require(name != null && !name.isEmpty() && head != null && head.matches("[0-9a-f]{40}"), "Missing/invalid job identity");
        require(steps != null, "Missing job steps");
        return new Job(name, head, steps, id, runId, attempt);
    }
    static String positiveInteger(JsonParser p) throws IOException {
        var number = StrictJson.exactInteger(p);
        require(number.signum() > 0 && number.toString().length() <= 20, "Invalid GitHub numeric identity");
        return number.toString();
    }
    static Step step(JsonParser p) throws IOException {
        expect(p, JsonToken.START_OBJECT); String name = null, start = null, conclusion = null;
        boolean sawStart = false, sawConclusion = false;
        while (p.nextToken() != JsonToken.END_OBJECT) switch (field(p)) {
            case "name" -> name = string(p);
            case "started_at" -> { start = nullable(p); sawStart = true; }
            case "conclusion" -> { conclusion = nullable(p); sawConclusion = true; }
            default -> skip(p);
        }
        require(name != null && !name.isEmpty() && sawStart && sawConclusion, "Missing step fields");
        if (conclusion != null) require(Set.of("success", "failure", "neutral", "cancelled", "skipped", "timed_out", "action_required").contains(conclusion), "Unknown step conclusion");
        return new Step(name, start, conclusion);
    }
}
