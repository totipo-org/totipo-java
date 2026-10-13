package org.totipo.tooling.release;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.totipo.tooling.release.ReleaseTest.*;

/** Checks the actual Actions files and executable precheckout shell, not a Java workflow simulation. */
public final class WorkflowBoundaryTest {
    private WorkflowBoundaryTest() {}
    static void require(boolean condition) throws java.io.IOException {
        if (!condition) throw new java.io.IOException("Release workflow boundary violated");
    }
    static String job(String text, String name) throws java.io.IOException {
        String start = "\n  " + name + ":\n";
        int i = text.indexOf(start); require(i >= 0 && text.indexOf(start, i + 1) < 0);
        int end = text.indexOf("\n  ", i + start.length());
        while (end >= 0 && text.charAt(end + 3) == ' ') end = text.indexOf("\n  ", end + 1);
        return text.substring(i, end < 0 ? text.length() : end);
    }
    static int count(String text, String word) { return text.split(java.util.regex.Pattern.quote(word), -1).length - 1; }
    static void validate(String release, String verify, String build, String settings) throws Exception {
        String publish = job(release, "publish-central"), preflight = job(release, "preflight"), call = job(release, "call-verification");
        String read = job(verify, "verify"), finish = job(verify, "finalize");
        require(publish.contains("    if: ${{ false && github.run_attempt == 1 }}\n"));
        require(finish.contains("    if: ${{ false }}\n"));
        require(count(release, "publishAndReleaseToMavenCentral") == 1 && count(release, " release reserve ") == 1);
        require(publish.contains("test \"$GITHUB_RUN_ATTEMPT\" = 1") && publish.contains("    needs: preflight\n"));
        require(publish.contains("    environment: release\n") && publish.contains("      contents: write\n"));
        require(preflight.contains("      contents: read\n") && !preflight.contains("secrets."));
        require(publish.indexOf("release compare") < publish.indexOf("release reserve")
            && publish.indexOf("release reserve") < publish.indexOf(":core:signMavenPublication")
            && publish.indexOf(":core:signMavenPublication") < publish.indexOf("release signed")
            && publish.indexOf("release signed") < publish.indexOf("publishAndReleaseToMavenCentral"));
        require(publish.indexOf(".#release-runtime") < publish.indexOf("secrets."));
        require(publish.contains("export ORG_GRADLE_PROJECT_centralUpload=true"));
        require(build.contains("Accepted publication inputs must not rebuild during upload") && build.contains("Signing-input acceptance failed"));
        require(count(build, "tasks.register<Exec>(\"verifyCentralSigningInputs\")") == 1
            && build.contains("dependsOn(rootProject.tasks.named(\"verifyCentralSigningInputs\"))")
            && build.contains("dependsOn(\":core:signMavenPublication\", \":storage-nio:signMavenPublication\")"));
        require(call.contains("    needs: [preflight, publish-central]\n")
            && call.contains("    uses: ./.github/workflows/verify-release.yml\n") && call.contains("      contents: write\n"));
        require(!call.contains("secrets") && count(release, "uses: ./") == 1 && !verify.contains("uses: ./"));
        require(release.contains("group: release-publish-${{ inputs.version }}") && verify.contains("group: release-verify-${{ inputs.version }}"));
        require(release.contains("cancel-in-progress: false") && verify.contains("cancel-in-progress: false"));
        require(verify.contains("  workflow_call:\n") && verify.contains("  workflow_dispatch:\n") && release.contains("  workflow_dispatch:\n"));
        require(!release.contains("  push:") && !release.contains("workflow_run"));
        for (String bad : List.of("secrets:", "secrets.", "environment:", "centralRelease", "centralUpload", "publishAndRelease", "signMaven", "release reserve", "download-artifact", "EXPECTED_INVENTORY", "actions/runs/")) require(!verify.contains(bad));
        for (String bad : List.of("continue-on-error", "always()", "secrets: inherit", "git push", "git tag", "git update-ref", "workflow_dispatch: {}", "upload-ready", "prior_upload")) require(!release.contains(bad) && !verify.contains(bad));
        require(read.contains("      contents: read\n") && !read.contains("contents: write") && !read.contains("release finalize"));
        require(read.contains("clean verifyPublication") && read.contains("publication check") && read.contains("release remote"));
        require(read.indexOf("release remote") < read.indexOf("-PremoteCentral clean check")
            && read.indexOf("-PremoteCentral clean check") < read.indexOf("-PremoteCentral -PpomOnly clean check"));
        require(read.contains("$RUNNER_TEMP/central-module.XXXXXX") && read.contains("$RUNNER_TEMP/central-pom.XXXXXX")
            && count(read, "GRADLE_USER_HOME=\"$consumer_home\"") == 2 && count(read, "--dependency-verification=strict -PremoteCentral") == 2);
        require(finish.contains("    needs: verify\n") && finish.contains("      contents: write\n") && finish.contains("release finalize"));
        require(!finish.contains("./gradlew") && !finish.contains("release reserve"));
        require(!read.contains("test \"$DISPATCH_SHA\" = \"$REQUESTED_COMMIT\""));
        require(preflight.contains("test \"$DISPATCH_SHA\" = \"$REQUESTED_COMMIT\""));
        for (String text : List.of(release, verify)) {
            require(text.contains("persist-credentials: false") && text.contains("GIT_NO_REPLACE_OBJECTS=1")
                && text.contains("merge-base --is-ancestor") && text.contains("https://github.com/totipo-org/totipo-java.git")
                && text.contains("--no-update-lock-file --no-write-lock-file") && text.contains("test -z \"$(nix config show access-tokens)\""));
            require(!text.contains("run: ${{") && !text.contains("${{ inputs.commit }}\n          "));
        }
        require(settings.contains("remoteCentral") && settings.contains("pomOnly") && settings.contains("repo.maven.apache.org/maven2")
            && !settings.contains("mavenLocal()") && !settings.contains("includeBuild("));
    }
    static String validation(String release) {
        int start = release.indexOf("        run: |\n") + "        run: |\n".length();
        int end = release.indexOf("      - name:", start);
        return release.substring(start, end).stripIndent();
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        String release = Files.readString(root.resolve(".github/workflows/release.yml"));
        String verify = Files.readString(root.resolve(".github/workflows/verify-release.yml"));
        String build = Files.readString(root.resolve("build.gradle.kts"));
        String settings = Files.readString(root.resolve("publishing/consumer-smoke/settings.gradle.kts"));
        validate(release, verify, build, settings); WorkflowPins.check(root);
        for (String[] edit : List.of(new String[]{"false && github.run_attempt == 1", "github.run_attempt == 1"},
            new String[]{"release reserve", "release tagged"}, new String[]{"needs: [preflight, publish-central]", "needs: preflight"},
            new String[]{"export ORG_GRADLE_PROJECT_centralUpload=true", "true"}))
            reject(() -> validate(release.replace(edit[0], edit[1]), verify, build, settings));
        for (String[] edit : List.of(new String[]{"${{ false }}", "${{ true }}"}, new String[]{"needs: verify", "needs: []"},
            new String[]{"central-pom.XXXXXX", "central-module.XXXXXX"}, new String[]{"-PremoteCentral -PpomOnly", "-PremoteCentral"},
            new String[]{"clean verifyPublication", "true"}))
            reject(() -> validate(release, verify.replace(edit[0], edit[1]), build, settings));
        reject(() -> validate(release, verify + "\nsecrets: inherit\n", build, settings));
        Path output = Files.createTempFile("release-input-test-", ".txt");
        try {
            for (String value : List.of("0.2.0", "0.2.0\ncommit=evil", "$(touch injected)", "../bad", "0.2.0-SNAPSHOT", "1".repeat(129))) {
                var process = new ProcessBuilder(args[1], "-e", "-o", "pipefail", "-c", validation(release));
                process.environment().clear(); process.environment().putAll(Map.of("REQUESTED_VERSION", value,
                    "REQUESTED_COMMIT", "a".repeat(40), "DISPATCH_SHA", "a".repeat(40), "DISPATCH_REF", "refs/heads/main", "GITHUB_OUTPUT", output.toString()));
                ok((process.start().waitFor() == 0) == value.equals("0.2.0"));
            }
            ok(Files.readString(output).equals("version=0.2.0\ncommit=" + "a".repeat(40) + "\n"));
        } finally { Files.delete(output); }
        System.out.println("Actual workflow activation, one-invocation, isolation and input regressions PASS");
    }
}
