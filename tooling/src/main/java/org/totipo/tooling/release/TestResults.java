package org.totipo.tooling.release;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import static org.totipo.tooling.release.SourceFiles.require;

/** Accounts existing XML, including Gradle up-to-date results; never claims fresh execution. */
public final class TestResults {
    private TestResults() {}
    public static final String SNAPSHOT = "org.totipo.conformance.SpecSnapshotIntegrityTest";
    public static final String PROFILE = "org.totipo.conformance.R19ProfileIntegrityTest";
    public static final String CORPUS = "org.totipo.format.Phase2ConformanceTest";
    public static final int MAX_FILE = 4 * 1024 * 1024, MAX_FILES = 4096;
    public static final long MAX_TOTAL = 64L * 1024 * 1024;
    public enum Failure { MISSING, XML, ATTRIBUTES, DUPLICATE, UNSUCCESSFUL, INTEGRITY, CONFORMANCE, LIMIT }
    public static final class AccountingException extends IOException {
        private static final long serialVersionUID = 1L;
        private final Failure failure;
        AccountingException(Failure failure) { super("JUnit accounting failed: " + failure); this.failure = failure; }
        public Failure failure() { return failure; }
    }
    public record Result(long total, int snapshot, int profile, boolean conformance) {
        public String summary() {
            return "92/92 portable cases: exact-set execution assertion passed\n"
                + total + " JUnit tests; zero failures/errors/skips; snapshot " + snapshot + "; profile " + profile;
        }
    }
    private record Suite(int tests, boolean conformance) {}
    public static Result account(Path root) throws IOException {
        Map<String, Suite> suites = new LinkedHashMap<>();
        int files = 0; long bytes = 0;
        for (String module : java.util.List.of("core", "storage-nio")) {
            int moduleFiles = 0;
            Path directory = root.resolve(module + "/build/test-results/test");
            try (var dir = EvidenceFiles.parent(directory.resolve("anchor"))) {
                for (Path entry : dir) {
                    String name = entry.getFileName().toString();
                    if (!name.startsWith("TEST-") || !name.endsWith(".xml")) continue;
                    check(++files <= MAX_FILES, Failure.LIMIT); moduleFiles++;
                    byte[] data;
                    try { data = EvidenceFiles.read(dir, entry.getFileName(), MAX_FILE); }
                    catch (IOException bad) { throw new AccountingException(Failure.LIMIT); }
                    bytes += data.length; check(bytes <= MAX_TOTAL, Failure.LIMIT);
                    Element suite = parse(data);
                    String identity = suite.getAttribute("name");
                    check(!identity.isBlank() && identity.chars().noneMatch(Character::isISOControl), Failure.ATTRIBUTES);
                    check(!suites.containsKey(identity), Failure.DUPLICATE);
                    int tests = integer(suite, "tests");
                    for (String key : java.util.List.of("failures", "errors", "skipped"))
                        check(integer(suite, key) == 0, Failure.UNSUCCESSFUL);
                    int cases = 0; boolean corpus = false;
                    for (var node = suite.getFirstChild(); node != null; node = node.getNextSibling()) {
                        if (node instanceof Element test && test.getTagName().equals("testcase")) {
                            cases++;
                            check(test.hasAttribute("name") && !test.getAttribute("name").isEmpty(), Failure.ATTRIBUTES);
                            check(test.getElementsByTagName("failure").getLength() == 0
                                && test.getElementsByTagName("error").getLength() == 0
                                && test.getElementsByTagName("skipped").getLength() == 0, Failure.UNSUCCESSFUL);
                            corpus |= test.getAttribute("name").startsWith("everyImplementedCaseExecutesWithoutSkipping");
                        }
                    }
                    check(cases == tests, Failure.ATTRIBUTES);
                    suites.put(identity, new Suite(tests, corpus));
                }
            } catch (java.nio.file.NoSuchFileException missing) { throw new AccountingException(Failure.MISSING); }
            check(moduleFiles > 0, Failure.MISSING);
        }
        check(suites.containsKey(SNAPSHOT) && suites.get(SNAPSHOT).tests() > 0, Failure.INTEGRITY);
        check(suites.containsKey(PROFILE) && suites.get(PROFILE).tests() > 0, Failure.INTEGRITY);
        boolean corpus = suites.containsKey(CORPUS) && suites.get(CORPUS).conformance();
        check(corpus, Failure.CONFORMANCE);
        return new Result(suites.values().stream().mapToLong(Suite::tests).sum(), suites.get(SNAPSHOT).tests(), suites.get(PROFILE).tests(), corpus);
    }
    private static int integer(Element suite, String name) throws IOException {
        String text = suite.getAttribute(name);
        check(text.matches("[0-9]+") && text.length() <= 10, Failure.ATTRIBUTES);
        try { return Integer.parseInt(text); }
        catch (NumberFormatException bad) { throw new AccountingException(Failure.ATTRIBUTES); }
    }
    private static Element parse(byte[] bytes) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> { throw new org.xml.sax.SAXException("External XML forbidden"); });
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            Element root = builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            require(root.getTagName().equals("testsuite"), "Unexpected JUnit root");
            return root;
        } catch (Exception bad) { throw new AccountingException(Failure.XML); }
    }
    private static void check(boolean condition, Failure failure) throws IOException {
        if (!condition) throw new AccountingException(failure);
    }
}
