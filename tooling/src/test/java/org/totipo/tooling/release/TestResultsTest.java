package org.totipo.tooling.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Permanent disposable XML accounting fixtures. */
public final class TestResultsTest {
    private TestResultsTest() {}
    static int assertions;
    static void ok(boolean value) { if (!value) throw new AssertionError(); assertions++; }
    static String suite(String name, int count) {
        StringBuilder xml = new StringBuilder("<testsuite name=\"" + name + "\" tests=\"" + count + "\" failures=\"0\" errors=\"0\" skipped=\"0\">");
        for (int i = 0; i < count; i++) xml.append("<testcase name=\"").append(name.equals(TestResults.CORPUS) ? "everyImplementedCaseExecutesWithoutSkipping" : "case" + i).append("\"/>");
        return xml.append("</testsuite>").toString();
    }
    static Path core(Path root, String name) { return root.resolve("core/build/test-results/test/TEST-" + name + ".xml"); }
    static void write(Path file, String bytes) throws IOException { Files.createDirectories(file.getParent()); Files.writeString(file, bytes); }
    static void fixture(Path root, boolean focused) throws IOException {
        write(core(root, "snapshot"), suite(TestResults.SNAPSHOT, 3));
        write(core(root, "profile"), suite(TestResults.PROFILE, 1));
        if (!focused) {
            write(core(root, "corpus"), suite(TestResults.CORPUS, 1));
            write(root.resolve("storage-nio/build/test-results/test/TEST-storage.xml"), suite("storage", 2));
        }
    }
    static final List<String> CASES = List.of("full", "focused", "missing-xml", "malformed", "missing-name", "duplicate",
        "missing-tests", "bad-tests", "failure", "error", "skip", "missing-snapshot", "wrong-snapshot",
        "missing-profile", "wrong-profile", "missing-assertion", "root", "entity", "oversize", "empty", "incomplete",
        "missing-errors", "case-count", "embedded-failure", "integer-sign", "dtd");
    static void mutate(Path root, String scenario) throws IOException {
        Path path = core(root, "snapshot");
        String xml = Files.readString(path);
        switch (scenario) {
            case "full", "focused" -> { }
            case "missing-xml" -> Files.delete(path);
            case "malformed" -> write(path, "<testsuite");
            case "missing-name" -> write(path, xml.replace("name=\"" + TestResults.SNAPSHOT + "\"", ""));
            case "duplicate" -> write(core(root, "duplicate"), xml);
            case "missing-tests" -> write(path, xml.replace("tests=\"3\"", ""));
            case "bad-tests" -> write(path, xml.replace("tests=\"3\"", "tests=\"3.0\""));
            case "integer-sign" -> write(path, xml.replace("tests=\"3\"", "tests=\"+3\""));
            case "failure" -> write(path, xml.replace("failures=\"0\"", "failures=\"1\""));
            case "error" -> write(path, xml.replace("errors=\"0\"", "errors=\"1\""));
            case "skip" -> write(path, xml.replace("skipped=\"0\"", "skipped=\"1\""));
            case "missing-errors" -> write(path, xml.replace("errors=\"0\"", ""));
            case "missing-snapshot" -> write(path, suite("other", 3));
            case "wrong-snapshot" -> write(path, suite(TestResults.SNAPSHOT, 2));
            case "missing-profile" -> Files.delete(core(root, "profile"));
            case "wrong-profile" -> write(core(root, "profile"), suite(TestResults.PROFILE, 2));
            case "missing-assertion" -> write(core(root, "corpus"), suite(TestResults.CORPUS, 1).replace("everyImplementedCaseExecutesWithoutSkipping", "unrelated"));
            case "root" -> write(path, xml.replace("testsuite", "other"));
            case "entity" -> write(path, "<!DOCTYPE testsuite [<!ENTITY attack SYSTEM 'https://invalid.example/never'>]>" + xml.replace("case0", "&attack;"));
            case "dtd" -> write(path, "<!DOCTYPE testsuite [<!ENTITY harmless 'fixture'>]>" + xml);
            case "oversize" -> write(path, " ".repeat(TestResults.MAX_FILE + 1));
            case "empty" -> { try (var paths = Files.list(path.getParent())) { for (Path p : paths.toList()) Files.delete(p); } }
            case "incomplete" -> Files.delete(root.resolve("storage-nio/build/test-results/test/TEST-storage.xml"));
            case "case-count" -> write(path, xml.replace("<testcase name=\"case0\"/>", ""));
            case "embedded-failure" -> write(path, xml.replace("<testcase name=\"case0\"/>", "<testcase name=\"case0\"><failure/></testcase>"));
            default -> throw new AssertionError(scenario);
        }
    }
    static String outcome(Path root, boolean focused) throws IOException {
        try {
            var result = TestResults.account(root, focused);
            return "PASS\t" + result.total() + "\t" + result.snapshot() + "\t" + result.profile() + "\t" + result.conformance();
        } catch (TestResults.AccountingException failure) { return "FAIL\t" + failure.failure(); }
    }
    public static void main(String[] args) throws Exception {
        Path export = Files.createTempDirectory("totipo-xml-");
        Files.createDirectories(export);
        try {
            for (String name : CASES) {
                Path root = export.resolve(name); boolean focused = name.equals("focused");
                fixture(root, focused); mutate(root, name);
                String result = outcome(root, focused);
                ok(result.startsWith(name.equals("full") || focused ? "PASS" : "FAIL"));
                if (name.equals("full")) ok(result.equals("PASS\t7\t3\t1\ttrue"));
                if (focused) ok(result.equals("PASS\t4\t3\t1\tfalse"));
            }
            Path extra = Files.createTempDirectory("totipo-xml-limits-");
            try {
                fixture(extra, true);
                for (int i = 0; i < TestResults.MAX_FILES; i++) write(core(extra, "limit-" + i), suite("unique" + i, 0));
                ok(outcome(extra, true).equals("FAIL\tLIMIT"));
            } finally { AuthorizationTest.remove(extra); }
            System.out.println("TestResultsTest PASS: " + assertions + " assertions; " + CASES.size() + " accounting fixtures");
        } finally { AuthorizationTest.remove(export); }
    }
}
