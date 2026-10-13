{ lib, stdenv, jdk, gradle, publicationTool }:
let
  version = lib.removeSuffix "\n" (builtins.readFile ./VERSION);
  cache = builtins.fromJSON (builtins.readFile ./package-deps.json);
  cacheReady = builtins.any (key: lib.hasPrefix "https://" key) (builtins.attrNames cache);
  trees = [ "core/src" "storage-nio/src" "gradle" "publishing" "nix" ];
  files = [
    "VERSION" "SPEC_PIN.md" "LICENSE"
    "build.gradle.kts" "settings.gradle.kts" "gradle.properties" "settings-gradle.lockfile"
    "core/build.gradle.kts" "core/gradle.lockfile"
    "storage-nio/build.gradle.kts" "storage-nio/gradle.lockfile"
  ];
in
assert gradle.version == "9.8.0";
stdenv.mkDerivation (finalAttrs: {
  pname = "totipo-java-qualification";
  inherit version;
  src = lib.cleanSourceWith {
    src = ./.;
    filter = path: type:
      let
        rel = lib.removePrefix (toString ./. + "/") (toString path);
        parts = lib.splitString "/" rel;
        excluded = builtins.any (p: builtins.elem p [ ".git" ".gradle" "build" ".direnv" ".idea" ".vscode" "__pycache__" ]) parts
          # Generated only after the publication verifier accepts staged bytes.
          || rel == "publishing/consumer-smoke/gradle/verification-metadata.xml"
          || builtins.any (suffix: lib.hasSuffix suffix rel) [ "~" ".tmp" ".swp" ".pyc" ];
        selected = builtins.elem rel files
          || builtins.any (tree: rel == tree || lib.hasPrefix (tree + "/") rel) trees
          || (type == "directory" && builtins.any (entry: lib.hasPrefix (rel + "/") entry) (trees ++ files));
      in !excluded && type != "symlink" && selected;
  };
  mitmCache = gradle.fetchDeps {
    pkg = finalAttrs.finalPackage;
    data = ./package-deps.json;
  };
  nativeBuildInputs = [ gradle ];
  JAVA_HOME = "${jdk}/lib/openjdk";
  TOTIPO_PUBLICATION_TOOL = "${publicationTool}/bin/totipo-java-tool";
  LC_ALL = "C.UTF-8";
  gradleFlags = [
    "--no-configuration-cache"
    "--no-build-cache"
    "--rerun-tasks"
    "--dependency-verification=strict"
    "-PconsumerGradleExecutable=${gradle}/bin/gradle"
  ];
  # Same task set for recording and replay; all consumers use local staging.
  gradleBuildTask = "clean test build verifyPublication consumerSmoke";
  gradleUpdateTask = finalAttrs.gradleBuildTask;
  preBuild = ''
    if [ -z "''${IN_GRADLE_UPDATE_DEPS:-}" ] && [ "${if cacheReady then "yes" else "no"}" != yes ]; then
      echo 'package-deps.json is ungenerated; run nix run .#update-package-deps' >&2
      exit 1
    fi
    # The sandbox has no external network. Only fixed URLs/hashes replay here.
    # The updater alone records remote acquisition into the reviewed manifest.
    gradle --init-script nix/dependencies.gradle qualificationDependencies
    # All meaningful qualification, including the independent consumer, is offline.
    gradleFlagsArray+=(--offline)
  '';
  doCheck = true;
  checkPhase = ''
    set -o pipefail
    runHook preCheck
    ${publicationTool}/bin/totipo-java-tool release test-results "$PWD" | tee test-summary.txt
    ${publicationTool}/bin/totipo-java-tool publication check "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json"
    gradle -p publishing/consumer-smoke -PpomOnly clean check
    echo 'Independent module-metadata and POM-only consumers passed' >> test-summary.txt
    # Account for the full suite above before focused tests replace core XML.
    gradle :core:test --tests org.totipo.conformance.SpecSnapshotIntegrityTest \
      --tests org.totipo.conformance.R19ProfileIntegrityTest
    ${publicationTool}/bin/totipo-java-tool release focused-test-results "$PWD" | tee -a test-summary.txt
    (cd core/src/test/resources/totipo-spec/v1-pre-rc && sha256sum -c SNAPSHOT.sha256)
    runHook postCheck
  '';
  installPhase = ''
    runHook preInstall
    mkdir -p "$out/publication"
    # Copy only the verifier's ten unsigned artifacts, excluding timestamped indexes.
    # Secure directory creation uses atomic moves; keep its temporary directories
    # on the output filesystem, which can differ from the build's TMPDIR mount.
    TMPDIR="$out" ${publicationTool}/bin/totipo-java-tool publication copy "$PWD" "$PWD/build/repository" "$PWD/build/publication-manifest.json" "$out/publication"
    cp build/publication-manifest.json "$out/publication-manifest.json"
    cp test-summary.txt "$out/test-summary.txt"
    cat core/gradle.lockfile storage-nio/gradle.lockfile \
      settings-gradle.lockfile publishing/consumer-smoke/gradle.lockfile > "$out/dependency-summary.txt"
    echo 'Totipo Java qualification passed' > "$out/qualified"
    runHook postInstall
  '';
  meta = {
    description = "Qualified unsigned Totipo Maven library artifacts";
    license = lib.licenses.asl20;
    platforms = [ "x86_64-linux" ];
  };
})
