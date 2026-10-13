{ lib, stdenvNoCC, fetchurl, jdk25_headless, writeShellScriptBin, runCommand, gitMinimal }:
let
  jdk = jdk25_headless;
  catalog = builtins.fromTOML (builtins.readFile ../gradle/libs.versions.toml);
  cache = builtins.fromJSON (builtins.readFile ../package-deps.json);
  repository = "https://repo.maven.apache.org/maven2";
  coordinate = "com/fasterxml/jackson/core#jackson-core/2.21.7";
  # Enumerate all locations/versions, so another accepted identity cannot silently win.
  identities = lib.concatMap
    (location:
      let entries = cache.${location}; in
      if builtins.isAttrs entries then
        map (key: { inherit location key; record = entries.${key}; })
          (builtins.filter (key: lib.hasPrefix "com/fasterxml/jackson/core#jackson-core/" key)
            (builtins.attrNames entries))
      else [ ])
    (builtins.attrNames cache);
  identity = if builtins.length identities == 1 then builtins.head identities
    else throw "Tooling requires exactly one reviewed Jackson Core identity";
  jarHash = if builtins.isAttrs identity.record && identity.record ? jar
    then identity.record.jar
    else throw "Reviewed Jackson Core record is missing its JAR hash";
  validHash = builtins.isString jarHash
    && builtins.match "sha256-[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=" jarHash != null;
  jackson =
    assert lib.assertMsg (catalog.versions.jackson == "2.21.7") "Tooling requires reviewed Jackson Core 2.21.7";
    assert lib.assertMsg (identity.location == repository && identity.key == coordinate)
      "Unsupported Jackson Core dependency location or version";
    assert lib.assertMsg (builtins.all (key: builtins.elem key [ "jar" "module" "pom" ])
      (builtins.attrNames identity.record)) "Unsupported Jackson Core artifact variant";
    assert lib.assertMsg validHash "Invalid Jackson Core SHA-256 SRI";
    fetchurl {
      url = "${repository}/com/fasterxml/jackson/core/jackson-core/${catalog.versions.jackson}/jackson-core-${catalog.versions.jackson}.jar";
      hash = jarHash;
    };
  source = lib.fileset.toSource {
    root = ../tooling;
    fileset = lib.fileset.fileFilter (file: file.hasExt "java") ../tooling/src;
  };
  # Read-only integrity fixtures: exact pin and reviewed 99-record snapshot only.
  releaseIntegritySource = lib.fileset.toSource {
    root = ../.;
    fileset = lib.fileset.unions [
      ../SPEC_PIN.md
      ../core/src/test/resources/totipo-spec/v1-pre-rc
    ];
  };
  classes = stdenvNoCC.mkDerivation {
    pname = "totipo-java-tooling-classes";
    version = "j4a";
    src = source;
    dontConfigure = true;
    buildPhase = ''
      runHook preBuild
      mkdir classes
      find src/main/java -name '*.java' -type f | sort > main-sources
      ${jdk}/bin/javac --release 25 -encoding UTF-8 -Xlint:all -Werror \
        -proc:none -sourcepath src/main/java -classpath ${jackson} \
        -d classes @main-sources
      runHook postBuild
    '';
    doCheck = true;
    checkPhase = ''
      runHook preCheck
      mkdir test-classes empty-source
      find src/test/java -name '*.java' -type f | sort > test-sources
      ${jdk}/bin/javac --release 25 -encoding UTF-8 -Xlint:all -Werror \
        -proc:none -sourcepath src/test/java -classpath "classes:${jackson}" \
        -d test-classes @test-sources
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.StrictJsonTest
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.publication.PublicationTest
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.ReleaseTest ${releaseIntegritySource}
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.AuthorizationTest ${stdenvNoCC.shell}
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.RemoteVerificationTest
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.FinalizationTest ${releaseIntegritySource}
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.PreparationApplicationTest ${releaseIntegritySource}
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.FinalizationProcessTest ${releaseIntegritySource} ${gitMinimal}/bin/git ${stdenvNoCC.shell}
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.TestResultsTest
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.WorkflowEvidenceTest
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.CentralConsumersTest
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.WorkflowPinsTest
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.ReleaseWorkflowIntegrationTest ${releaseIntegritySource}
      ${jdk}/bin/java -cp "classes:${jackson}" org.totipo.tooling.Main smoke
      # Synthetic source only: neither production sources nor Gradle enter this build.
      cat > Forbidden.java <<'JAVA'
      import org.totipo.RevisionId;
      import org.gradle.api.Project;
      final class Forbidden { RevisionId value; Project project; }
      JAVA
      if ${jdk}/bin/javac -proc:none -sourcepath empty-source \
        -classpath "classes:${jackson}" -d test-classes Forbidden.java 2> forbidden.log; then
        echo 'Production import unexpectedly compiled' >&2
        exit 1
      fi
      grep -F 'package org.totipo does not exist' forbidden.log
      grep -F 'package org.gradle.api does not exist' forbidden.log
      runHook postCheck
    '';
    installPhase = ''
      runHook preInstall
      mkdir -p "$out"
      cp -r classes "$out/classes"
      runHook postInstall
    '';
  };
  launcher = writeShellScriptBin "totipo-java-tool" ''
    exec ${jdk}/bin/java -cp "${classes}/classes:${jackson}" org.totipo.tooling.Main "$@"
  '';
  check = runCommand "totipo-java-tooling-check" { } ''
    ${launcher}/bin/totipo-java-tool smoke
    if ${launcher}/bin/totipo-java-tool unknown; then
      echo 'Unknown command unexpectedly succeeded' >&2
      exit 1
    else
      status=$?
      test "$status" -eq 2
    fi
    # Quoted "$@" must preserve argument boundaries, including an empty argument.
    if ${launcher}/bin/totipo-java-tool smoke ""; then
      echo 'Extra argument unexpectedly succeeded' >&2
      exit 1
    else
      status=$?
      test "$status" -eq 2
    fi
    touch "$out"
  '';
in
assert lib.assertMsg (lib.versions.major jdk.version == "25") "Tooling requires JDK 25";
{
  inherit classes launcher check;
}
