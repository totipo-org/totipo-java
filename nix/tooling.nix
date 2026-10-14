{ lib, stdenvNoCC, fetchurl, jdk25_headless, gitMinimal, writeShellScriptBin, runCommand }:
let
  jdk = jdk25_headless;
  catalog = builtins.fromTOML (builtins.readFile ../gradle/libs.versions.toml);
  cache = builtins.fromJSON (builtins.readFile ../package-deps.json);
  repository = "https://repo.maven.apache.org/maven2";
  coordinate = "com/fasterxml/jackson/core#jackson-core/2.21.7";
  jarHash = cache.${repository}.${coordinate}.jar;
  jackson =
    assert lib.assertMsg (catalog.versions.jackson == "2.21.7") "Tooling requires reviewed Jackson Core 2.21.7";
    fetchurl {
      url = "${repository}/com/fasterxml/jackson/core/jackson-core/${catalog.versions.jackson}/jackson-core-${catalog.versions.jackson}.jar";
      hash = jarHash;
    };
  source = lib.fileset.toSource {
    root = ../tooling;
    fileset = lib.fileset.fileFilter (file: file.hasExt "java") ../tooling/src;
  };
  # Read-only specification pin for source identity and finalization tests.
  releaseIntegritySource = lib.fileset.toSource {
    root = ../.;
    fileset = ../SPEC_PIN.md;
  };
  classes = stdenvNoCC.mkDerivation {
    pname = "totipo-java-tooling-classes";
    version = "j4b";
    src = source;
    dontConfigure = true;
    LC_ALL = "C.UTF-8";
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
      mkdir test-classes
      find src/test/java -name '*.java' -type f | sort > test-sources
      ${jdk}/bin/javac --release 25 -encoding UTF-8 -Xlint:all -Werror \
        -proc:none -sourcepath src/test/java -classpath "classes:${jackson}" \
        -d test-classes @test-sources
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.StrictJsonTest
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.publication.PublicationTest
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.ReleaseTest ${releaseIntegritySource} ${gitMinimal}/bin/git
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.AuthorizationTest ${stdenvNoCC.shell}
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.RemoteVerificationTest
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.FinalizationTest ${releaseIntegritySource}
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.FinalizationProcessTest ${stdenvNoCC.shell}
      ${jdk}/bin/java -cp "test-classes:classes:${jackson}" org.totipo.tooling.release.TestResultsTest
      ${jdk}/bin/java -cp "classes:${jackson}" org.totipo.tooling.Main smoke
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
    exec ${jdk}/bin/java -Djava.io.tmpdir="''${TMPDIR:-/tmp}" \
      -cp "${classes}/classes:${jackson}" org.totipo.tooling.Main "$@"
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
