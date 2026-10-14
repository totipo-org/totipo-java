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
            && publish.indexOf(":core:signMavenPublication") < publish.indexOf("release reserve")
            && publish.indexOf(":core:signMavenPublication") < publish.indexOf("release signed")
            && publish.indexOf("release signed") < publish.indexOf("release reserve")
            && publish.indexOf("release reserve") < publish.indexOf("release tagged")
            && publish.indexOf("release tagged") < publish.lastIndexOf("release signed")
            && publish.lastIndexOf("release signed") < publish.indexOf("publishAndReleaseToMavenCentral"));
        require(publish.indexOf(".#release-runtime") < publish.indexOf("secrets."));
        require(publish.contains("export ORG_GRADLE_PROJECT_centralUpload=true"));
        require(build.contains("Accepted publication inputs must not rebuild during upload") && build.contains("Signing-input acceptance failed"));
        require(build.contains("dependsOn(rootProject.tasks.named(\"verifyCentralSigningInputs\"))")
            && build.contains("dependsOn(\":core:signMavenPublication\", \":storage-nio:signMavenPublication\")"));
        require(call.contains("    needs: [preflight, publish-central]\n")
            && call.contains("    uses: ./.github/workflows/verify-release.yml\n") && call.contains("      contents: write\n"));
        require(!call.contains("secrets"));
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
        require(preflight.contains("test \"$DISPATCH_SHA\" = \"$REQUESTED_COMMIT\""));
        for (String text : List.of(release, verify)) {
            require(text.contains("persist-credentials: false") && text.contains("GIT_NO_REPLACE_OBJECTS=1")
                && text.contains("merge-base --is-ancestor") && text.contains("https://github.com/totipo-org/totipo-java.git")
                && text.contains("--no-update-lock-file --no-write-lock-file"));
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
        validate(release, verify, build, settings);
        String action = Files.readString(root.resolve(".github/actions/setup-nix/action.yml"));
        String acquire = actionRun(action, "Acquire reviewed Nix installer");
        String cleanup = actionRun(action, "Validate Nix identity");
        // These are external acquisition identities, not a checksum of local source.
        require(acquire.contains("https://releases.nixos.org/nix/nix-2.35.2/install"));
        require(acquire.contains("9adda97297d9e8ab360df95c729eabff4f4f93d6db091953c3a68f29e3fb130c"));
        // Model both presence probes explicitly: the Nix build host may already
        // have Nix on PATH or at the default profile, unlike a fresh Actions runner.
        String downloadFakes = "RUNNER_TEMP=/fixture;\n"
            + "command() { [[ $* == '-v nix' ]] || exit 99; return COMMAND_STATUS; };\n"
            + "test() { [[ $* == '! -e /nix/var/nix/profiles/default/bin/nix' ]] || exit 99; return PROFILE_STATUS; };\n"
            + "curl() { return DOWNLOAD_STATUS; }; sha256sum() { return HASH_STATUS; };\n";
        for (String[] example : List.of(new String[]{"1", "0", "0", "0", "0"},
            new String[]{"1", "0", "0", "7", "7"}, new String[]{"1", "0", "8", "0", "8"},
            new String[]{"0", "0", "99", "99", "1"}, new String[]{"1", "1", "99", "99", "1"},
            new String[]{"0", "1", "99", "99", "1"})) {
            String script = downloadFakes.replace("COMMAND_STATUS", example[0]).replace("PROFILE_STATUS", example[1])
                .replace("DOWNLOAD_STATUS", example[2]).replace("HASH_STATUS", example[3]) + acquire;
            require(shell(args[1], script) == Integer.parseInt(example[4]));
        }
        // Execute only actual shell bodies with built-in function fakes: no Nix,
        // downloader, sudo, daemon or real configuration is ever invoked here.
        require(shell(args[1], "nix() { :; }; curl() { exit 99; };\n" + acquire) == 1);
        String fakes = "sudo() { :; }; sed() { :; };\n"
            + "nix() { if [[ $1 == --version ]]; then printf '%s\\n' 'nix (Nix) 2.35.2'; return VERSION_STATUS; "
            + "else printf '%s' 'TOKEN_VALUE'; return TOKEN_STATUS; fi; };\n";
        for (String[] example : List.of(new String[]{"0", "0", "", "0"},
            new String[]{"7", "0", "", "7"}, new String[]{"0", "7", "", "7"},
            new String[]{"0", "0", "github.com=fixture", "1"})) {
            String script = fakes.replace("VERSION_STATUS", example[0]).replace("TOKEN_STATUS", example[1])
                .replace("TOKEN_VALUE", example[2]) + cleanup;
            require(shell(args[1], script) == Integer.parseInt(example[3]));
        }
        require(shell(args[1], fakes.replace("VERSION_STATUS", "7").replace("TOKEN_STATUS", "0")
            .replace("TOKEN_VALUE", "").replace("nix (Nix) 2.35.2", "") + cleanup) == 7);
        for (String[] edit : List.of(new String[]{"false && github.run_attempt == 1", "github.run_attempt == 1"},
            new String[]{"release reserve", "release tagged"}, new String[]{"needs: [preflight, publish-central]", "needs: preflight"},
            new String[]{"export ORG_GRADLE_PROJECT_centralUpload=true", "true"}))
            reject(() -> validate(release.replace(edit[0], edit[1]), verify, build, settings));
        for (String[] edit : List.of(new String[]{"${{ false }}", "${{ true }}"}, new String[]{"needs: verify", "needs: []"},
            new String[]{"central-pom.XXXXXX", "central-module.XXXXXX"}, new String[]{"-PremoteCentral -PpomOnly", "-PremoteCentral"},
            new String[]{"clean verifyPublication", "true"}))
            reject(() -> validate(release, verify.replace(edit[0], edit[1]), build, settings));
        reject(() -> validate(release, verify + "\nsecrets: inherit\n", build, settings));
        signingOrder(release, args[1]);
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
    static void signingOrder(String release, String bash) throws Exception {
        String publish = job(release, "publish-central");
        int start = publish.lastIndexOf("        run: |\n") + "        run: |\n".length();
        String body = publish.substring(start).stripIndent();
        Path events = Files.createTempFile("signing-order-", ".txt");
        try {
            for (String failure : List.of("none", "credentials", "sign", "signed", "reserve", "tagged")) {
                Files.writeString(events, "");
                String script = "GITHUB_RUN_ATTEMPT=1; ORG_GRADLE_PROJECT_mavenCentralUsername=fixture; "
                    + "ORG_GRADLE_PROJECT_mavenCentralPassword=fixture; ORG_GRADLE_PROJECT_signingInMemoryKey=fixture; "
                    + "ORG_GRADLE_PROJECT_signingInMemoryKeyId=; ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=;\n"
                    + (failure.equals("credentials") ? "ORG_GRADLE_PROJECT_signingInMemoryKey=;\n" : "")
                    + "totipo-java-tool() { printf '%s\\n' \"$2\" >> '" + events + "'; "
                    + "[[ $2 != " + failure + " ]]; };\n"
                    + "./gradlew() { if [[ $* == *publishAndReleaseToMavenCentral* ]]; then "
                    + "printf 'upload\\n' >> '" + events + "'; else printf 'sign\\n' >> '" + events
                    + "'; [[ " + failure + " != sign ]]; fi; };\n" + body;
                int status = shell(bash, script);
                String observed = Files.readString(events);
                require((status == 0) == failure.equals("none"));
                if (List.of("credentials", "sign", "signed").contains(failure)) require(!observed.contains("reserve"));
                if (!failure.equals("none")) require(!observed.contains("upload"));
                else require(observed.equals("compare\nsign\nsigned\neligible\nreserve\ntagged\nsigned\nupload\n"));
            }
        } finally { Files.delete(events); }
    }
    static String actionRun(String action, String name) throws Exception {
        int step = action.indexOf("    - name: " + name); require(step >= 0);
        int start = action.indexOf("      run: |\n", step) + "      run: |\n".length();
        int end = action.indexOf("    - name:", start);
        return action.substring(start, end < 0 ? action.length() : end).stripIndent();
    }
    static int shell(String bash, String script) throws Exception {
        var process = new ProcessBuilder(bash, "--noprofile", "--norc", "-e", "-o", "pipefail", "-c", script);
        process.environment().clear(); process.environment().put("HOME", "/nonexistent-totipo-shell-fixture");
        process.redirectOutput(ProcessBuilder.Redirect.DISCARD); process.redirectError(ProcessBuilder.Redirect.DISCARD);
        return process.start().waitFor();
    }
}
