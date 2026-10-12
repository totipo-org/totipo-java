package org.totipo.tooling.publication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;
import static org.totipo.tooling.publication.FilesSupport.*;

public final class PublicationRepository {
    private PublicationRepository() {}
    public static String fingerprint(Path repository) throws IOException {
        var hashes = new LinkedHashMap<String, Object>();
        long total = 0;
        for (String path : inventory(repository)) {
            byte[] b = read(repository.resolve(path));
            total += b.length;
            require(total <= 256L * 1024 * 1024, "Repository byte limit exceeded");
            hashes.put(path, digest(b, "SHA-256"));
        }
        return digest(Json.dumps(hashes, false).getBytes(StandardCharsets.UTF_8), "SHA-256");
    }
    public static void checkArtifacts(PublicationManifest manifest, Path repository) throws IOException {
        for (var a : manifest.artifacts()) require(digest(read(repository.resolve(a.path())), "SHA-256").equals(a.sha256()),
                "Artifact hash mismatch: " + a.path());
    }
    public static void checkStaging(PublicationManifest manifest, Path repository) throws IOException {
        checkArtifacts(manifest, repository);
        require(fingerprint(repository).equals(manifest.repositorySha256()), "Staged repository changed since semantic acceptance");
    }
    public static void copy(PublicationManifest manifest, Path repository, Path destination) throws IOException {
        // Revalidate even caller-constructed records before any filesystem write.
        manifest = PublicationManifest.parse(manifest.bytes(), manifest.version());
        checkStaging(manifest, repository);
        Path dest = destination.toAbsolutePath().normalize(), repo = repository.toAbsolutePath().normalize();
        require(!dest.startsWith(repo) && !repo.startsWith(dest), "Copy source and destination overlap");
        directories(dest);
        Set<String> wanted = manifest.artifacts().stream().map(PublicationManifest.Artifact::path).collect(Collectors.toSet());
        require(inventory(dest).isEmpty(), "Copy destination must be empty");
        for (var a : manifest.artifacts()) {
            Path target = dest.resolve(a.path());
            directories(target.getParent());
            byte[] b = read(repo.resolve(a.path()));
            require(digest(b, "SHA-256").equals(a.sha256()), "Source changed during copy: " + a.path());
            writeNew(target, b);
        }
        checkArtifacts(manifest, dest);
        require(inventory(dest).equals(wanted), "Unexpected copied artifacts");
        checkStaging(manifest, repo);
    }
}
