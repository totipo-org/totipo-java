# Totipo Java

Java libraries for encrypted TOTP vaults using Totipo Vault Format v1, revision
**r19**. Totipo provides vault creation/opening, immutable state observations,
TOKEN create/update/merge operations, conflict handling and session-backed TOTP
generation. Filesystem applications start with `NioTotipo`.

## Add to your application

Both published modules require **Java 17 or later**:

| Module | Use |
| --- | --- |
| `org.totipo:totipo-storage-nio` | Filesystem entry point and NIO storage provider; exposes core transitively. |
| `org.totipo:totipo-core` | Portable protocol/application API, cryptography and experimental storage SPI; supply your own provider. |

Version **0.2.0** is available from Maven Central
([core POM](https://repo.maven.apache.org/maven2/org/totipo/totipo-core/0.2.0/totipo-core-0.2.0.pom),
[NIO POM](https://repo.maven.apache.org/maven2/org/totipo/totipo-storage-nio/0.2.0/totipo-storage-nio-0.2.0.pom)).
For an ordinary filesystem application, use Gradle Kotlin DSL:

```kotlin
repositories {
    mavenCentral()
}
dependencies {
    implementation("org.totipo:totipo-storage-nio:0.2.0")
}
```

Or Maven (Central is available by default):

```xml
<dependency>
  <groupId>org.totipo</groupId>
  <artifactId>totipo-storage-nio</artifactId>
  <version>0.2.0</version>
</dependency>
```

Use `totipo-core` instead of `totipo-storage-nio` when you do not need NIO.
Core brings Bouncy Castle **1.86** at runtime for Argon2id; other cryptography
uses JDK providers. No native access/FFM or Android dependency is required.
Jackson, JUnit, test fixtures and the vendored specification corpus are test-only
and excluded from production publications.
The API and SPI are experimental, with no source/binary stability promise;
0.2.0 deliberately breaks compatibility with 0.1.x. See the
[0.2.0 migration notes](review/V0_2_0_RELEASE_NOTES.md#java-consumer-migration).

## Create or open a filesystem vault

The directory must already exist. This Java 17 example accepts an explicit
`create` or `open` mode and prompts for a password without echoing it. Save it as
`VaultExample.java` and run with the NIO dependency on the classpath in a terminal
providing `System.console()` (for example, `VaultExample create /path/to/vault`).

```java
import java.io.Console;
import java.nio.file.Path;
import java.util.Arrays;
import org.totipo.CreateVaultResult;
import org.totipo.OpenResult;
import org.totipo.VaultSession;
import org.totipo.storage.nio.NioTotipo;

public final class VaultExample {
    public static void main(String[] args) {
        if (args.length != 2
                || !(args[0].equals("create") || args[0].equals("open"))) {
            throw new IllegalArgumentException("Usage: VaultExample create|open directory");
        }
        Console console = System.console();
        if (console == null) throw new IllegalStateException("A terminal is required");
        char[] password = console.readPassword("Vault password: ");
        if (password == null) return;
        try {
            if (args[0].equals("create") && password.length == 0) {
                throw new IllegalArgumentException("This example requires a nonempty password");
            }
            Path directory = Path.of(args[1]);
            VaultSession session;
            if (args[0].equals("create")) {
                CreateVaultResult result = NioTotipo.create(directory, password);
                if (!(result instanceof CreateVaultResult.Created created)) {
                    throw new IllegalStateException("Creation did not succeed: " + result);
                }
                session = created.session();
            } else {
                OpenResult result = NioTotipo.open(directory, password);
                if (!(result instanceof OpenResult.Opened opened)) {
                    throw new IllegalStateException("Opening did not succeed: " + result);
                }
                session = opened.session();
            }
            try (session) {
                System.out.println("Vault ID: " + session.vaultId().hex());
                // Use session.state() and the application API here.
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
```

A successful result transfers session ownership to the caller: keep it open for
secret-backed work and close it with try-with-resources. Password characters remain
caller-owned; clearing them and library secret cleanup are best effort on the JVM.
Open/create, saves, retries and close may block; keep them off UI threads.

The example stops on every non-success result. Applications should distinguish
[the lifecycle outcomes](API_DESIGN.md#entry-points-and-lifecycle-outcomes):
`Uncertain` creation may already have published VAULT, so reobserve/open it rather
than blindly retrying initialization. `Failed(OBJECT_DATA_OBSERVED)` preserves
possible existing vault data and calls for recovery, reconfiguration or a new
location. `AuthenticationFailed` can mean corrupted/replaced authenticated bytes,
as well as a wrong password. Opening is read-only; never create automatically
in response to an arbitrary open failure.

## Integration and security boundaries

- VAULT is immutable and create-once. Password, bootstrap policy or root changes
  require a different vault; migration is an application workflow.
- State observations do not prove freshness, complete history, remote synchronization
  or rollback resistance. Older same-session snapshots remain usable under the
  [operation/state-snapshot contracts](API_DESIGN.md#operation-classes-and-state-snapshot-semantics).
  `VaultId` recognizes canonical VAULT bytes; it proves neither authenticity nor freshness.
- Applications own conflict disclosure, safe rendering of untrusted text and explicit
  confirmation of empty-password creation. Tombstones retain credentials/history;
  deletion is not secure erasure.
- Ordinary NIO publication requires hard-link no-replace installation and directory
  durability support; it has no weaker automatic fallback. Positive durability
  acknowledges configured local storage operations, not remote propagation or
  universal crash survival. See the [provider contract](SPI_DESIGN.md).
- Evidence covers the pinned **92/92 portable cases without skips** and qualified
  local case-sensitive Linux storage. Conformance is scoped to supported core
  operations (including audited TOKEN projections/metadata) and NIO/core storage
  orchestration, as detailed in the
  [conformance scope](API_DESIGN.md#r19-conformance-scope-and-application-responsibilities).
  This is not application certification, independent interoperability evidence or
  a security audit. Windows, macOS, Android, case-insensitive filesystems and
  physical crash/power-loss behavior are not qualified by these Java tests.

## Documentation

- [Application API and lifecycle contracts](API_DESIGN.md)
- [Experimental storage SPI](SPI_DESIGN.md)
- [Exact specification pin and corpus](SPEC_PIN.md)
- [Development setup, IDEs and dependency maintenance](docs/DEVELOPMENT.md)
- [Contributor policies and qualification ladder](AGENTS.md)
- [Publication verification and release procedures](RELEASE_CHECKLIST.md)
- [Security policy and private vulnerability reporting](SECURITY.md)

Library consumption requires Java 17 or later; supported repository development,
qualification and release require the locked Nix environment with JDK 25 and
Gradle 9.8.0. See the development guide before building from source.
