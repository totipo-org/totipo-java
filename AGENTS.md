# Java qualification

Agents must not run Nix. The human routine Java Nix gate is exactly:

```sh
nix flake check path:.
```

Do not request a second build for safety. `nix build` only materializes the same
qualified unsigned library artifact output and is not another qualification gate.
Dependency changes alone require the separate human cache update checkpoint:
`nix run path:.#update-package-deps`, followed by cache review before qualification.
Keep the protected release workflow and its normal/offline ten-artifact comparison
separate from routine qualification. Preserve Gradle locks, strict verification,
consumer isolation, Java 17 bytecode and r19 integrity.

## Application operation model

Before changing concurrency, operation scheduling, VaultState handling, session
lifecycle, publication/retry, or UI/client interaction with Java operations, read
[API_DESIGN.md's operation/state-snapshot guidance](API_DESIGN.md#operation-classes-and-state-snapshot-semantics).
Do not infer `newer VaultState == old work invalid` unless the relevant Java
operation contract says so. Preserve the distinction between snapshot validity,
application presentation relevance, and operation-specific freshness checks.
