package org.totipo.storage.nio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;

/** Complete-stage installation policy. Private moves require exclusive application control. */
enum NioCanonicalInstaller {
    HARD_LINK, PRIVATE_MOVE;

    void checkAbsent(Path target) throws IOException {
        // Refresh absence after complete construction. Enumeration errors are not absence.
        if (this == PRIVATE_MOVE &&
                NioFiles.findExactDirectChild(target.getParent(), target.getFileName().toString()).isPresent())
            throw new FileAlreadyExistsException(target.toString());
    }

    void installIfAbsent(Path target, Path stage) throws IOException {
        if (this == HARD_LINK) Files.createLink(target, stage);
        else Files.move(stage, target); // Neither REPLACE_EXISTING nor ATOMIC_MOVE is safe here.
    }
}
