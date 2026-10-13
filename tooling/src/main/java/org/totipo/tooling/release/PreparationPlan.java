package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import static org.totipo.tooling.release.SourceFiles.require;

/** Immutable UTF-8 replacement bytes represented by immutable strings; no apply operation. */
public record PreparationPlan(Map<String, String> replacements) {
    public PreparationPlan {
        var owned = java.util.Set.of("VERSION", "RELEASE_CHECKLIST.md", ".github/workflows/release.yml",
                "publishing/consumer-smoke/gradle.lockfile", "README.md", "API_DESIGN.md");
        if (!replacements.keySet().containsAll(owned)) throw new IllegalArgumentException("Incomplete preparation plan");
        String target = replacements.get("VERSION");
        if (target == null || !target.endsWith("\n")) throw new IllegalArgumentException("Invalid plan VERSION");
        target = target.substring(0, target.length() - 1);
        if (!target.matches("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)")) throw new IllegalArgumentException("Invalid plan stable version");
        String notes = "review/V" + target.replace('.', '_') + "_RELEASE_NOTES.md";
        for (String path : replacements.keySet()) if (!owned.contains(path) && !path.equals(notes))
            throw new IllegalArgumentException("Unowned preparation path");
        replacements = Collections.unmodifiableMap(new LinkedHashMap<>(replacements));
    }
    public byte[] bytes(String path) { return replacements.get(path).getBytes(StandardCharsets.UTF_8); }
    public static final String NOTES_TEMPLATE = """
            ## v%s

            ### Highlights

            TODO: Review implementation highlights.

            ### Compatibility and scope

            TODO: Review API delta, protocol identity and qualification limits.

            ### Validation

            TODO: Record local qualification and the separate human Nix checkpoint.
            """;
    static String once(String text, String old, String replacement, String path) throws IOException {
        int i = text.indexOf(old);
        require(i >= 0 && text.indexOf(old, i + 1) < 0, "Unexpected or ambiguous release metadata in " + path);
        return text.substring(0, i) + replacement + text.substring(i + old.length());
    }
    static String owned(String text, String template, String current, String target, String path) throws IOException {
        String[] parts = template.split("\\{version\\}", -1);
        // UNICODE_CHARACTER_CLASS preserves Python's Unicode whitespace matching.
        var matcher = Pattern.compile(Pattern.quote(parts[0]) + "([^\\s\\x1c-\\x1f`\"<>/=(),]+)" + Pattern.quote(parts[1]),
                Pattern.UNICODE_CHARACTER_CLASS).matcher(text);
        require(matcher.find() && matcher.group(1).equals(current) && !matcher.find(), "Unexpected or ambiguous release metadata in " + path);
        return once(text, template.replace("{version}", current), template.replace("{version}", target), path);
    }
    static String block(String version, boolean released) {
        return "<!-- prepared-release:start -->\nJava " + version + (released ? " is released." : " is prepared locally and not yet released.")
                + " See RELEASE_CHECKLIST.md.\n<!-- prepared-release:end -->";
    }
    static String status(String text, String current, String target, String path) throws IOException {
        once(text, "<!-- prepared-release:start -->", "<!-- prepared-release:start -->", path);
        once(text, "<!-- prepared-release:end -->", "<!-- prepared-release:end -->", path);
        boolean released = text.contains(block(current, true));
        return once(text, block(current, released), block(target, released && current.equals(target)), path);
    }
    static PreparationPlan metadata(SourceFiles source, String current, String target) throws IOException {
        require(source.text("VERSION").equals(current + "\n"), "VERSION differs from current version");
        var changes = new LinkedHashMap<String, String>();
        changes.put("VERSION", target + "\n");
        String path = "RELEASE_CHECKLIST.md", text = source.text(path);
        for (String template : new String[]{"# Totipo Java {version} release checklist", "org.totipo:totipo-core:{version}",
                "org.totipo:totipo-storage-nio:{version}", "Annotated source tag convention: `v{version}`", "`version`: `{version}`"})
            text = owned(text, template, current, target, path);
        text = owned(text, "review/V{version}_RELEASE_NOTES.md", current.replace('.', '_'), target.replace('.', '_'), path);
        changes.put(path, status(text, current, target, path));
        path = ".github/workflows/release.yml";
        changes.put(path, owned(source.text(path), "Exact VERSION to release (for example, {version})", current, target, path));
        path = "publishing/consumer-smoke/gradle.lockfile"; text = source.text(path);
        for (String module : new String[]{"core", "storage-nio"}) {
            String entry = "org.totipo:totipo-" + module + ":" + current + "=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath";
            once(text, entry, entry, path);
            text = owned(text, "org.totipo:totipo-" + module + ":{version}=", current, target, path);
        }
        changes.put(path, text);
        for (String name : new String[]{"README.md", "API_DESIGN.md"}) changes.put(name, status(source.text(name), current, target, name));
        return new PreparationPlan(changes);
    }
    public static PreparationPlan inspect(SourceFiles source, GitQueries git, String target, boolean check) throws IOException {
        var desired = ReleaseInputs.stable(target);
        String versionText = source.text("VERSION");
        String current = versionText.endsWith("\n") ? versionText.substring(0, versionText.length() - 1) : versionText;
        var existing = ReleaseInputs.stable(current);
        require(versionText.equals(current + "\n"), "Unexpected current VERSION layout");
        source.integrity();
        if (check) {
            require(current.equals(target), "VERSION differs from prepared target");
            var plan = metadata(source, current, target);
            for (var e : plan.replacements().entrySet()) require(source.text(e.getKey()).equals(e.getValue()), "Inconsistent prepared metadata");
            source.notes(target, true, true);
            return plan;
        }
        require(git.query(GitQueries.Query.BRANCH, target).equals("main"), "Preparation requires main");
        int comparison = 0;
        for (int i = 0; i < 3 && comparison == 0; i++) comparison = desired.get(i).compareTo(existing.get(i));
        require(comparison > 0, "Target must be newer than current; use prepared check for prepared source");
        require(git.query(GitQueries.Query.STATUS, target).isEmpty(), "Initial preparation requires a clean working tree");
        var changes = new LinkedHashMap<>(metadata(source, current, target).replacements());
        String note = SourceFiles.notesPath(target);
        if (source.exists(note)) source.notes(target, true, false);
        else changes.put(note, NOTES_TEMPLATE.formatted(target));
        return new PreparationPlan(changes);
    }
}
