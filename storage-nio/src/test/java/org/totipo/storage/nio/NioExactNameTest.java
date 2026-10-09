package org.totipo.storage.nio;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class NioExactNameTest {
    @TempDir Path root;
    private static final String NAME = "abcdef01".repeat(8);

    @ParameterizedTest @ValueSource(strings = {"vault", "objects-v1",
            "abcdef01abcdef01abcdef01abcdef01abcdef01abcdef01abcdef01abcdef01"})
    void selectionUsesObservedSpellingIndependentOfHostLookup(String expected) {
        Path upper = Path.of(expected.toUpperCase(Locale.ROOT));
        Path mixed = Path.of("" + Character.toUpperCase(expected.charAt(0)) + expected.substring(1));
        Path exact = Path.of(expected);
        assertTrue(NioFiles.selectExactChild(List.of(upper, mixed), expected).isEmpty());
        assertSame(exact, NioFiles.selectExactChild(List.of(upper, exact, mixed), expected).orElseThrow());
    }

    @Test void enumerationIsDirectDoesNotFollowChildAndErrorsAreNotAbsence() throws Exception {
        Path nested = Files.createDirectory(root.resolve("nested"));
        Files.write(nested.resolve("vault"), new byte[]{1});
        assertTrue(NioFiles.findExactDirectChild(root, "vault").isEmpty());
        Path link = Files.createSymbolicLink(root.resolve("vault"), root.resolve("missing"));
        assertEquals(link, NioFiles.findExactDirectChild(root, "vault").orElseThrow());
        assertThrows(IOException.class, () -> NioFiles.findExactDirectChild(root.resolve("missing"), "vault"));
    }

}
