{ lib, stdenvNoCC, jdk25_headless }:
let
  jdk = jdk25_headless;
  source = lib.fileset.toSource {
    root = ../tooling/wrapper;
    fileset = lib.fileset.unions [
      ../tooling/wrapper/src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java
      ../tooling/wrapper/src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
    ];
  };
in
assert lib.assertMsg (lib.versions.major jdk.version == "25") "Wrapper verification requires JDK 25";
stdenvNoCC.mkDerivation {
  pname = "totipo-java-wrapper-integrity";
  version = "j2a";
  src = source;
  dontConfigure = true;
  buildPhase = ''
    runHook preBuild
    mkdir classes test-classes empty-classpath
    ${jdk}/bin/javac --release 25 -encoding UTF-8 -Xlint:all -Werror \
      -proc:none -sourcepath src/main/java -classpath empty-classpath \
      -d classes src/main/java/org/totipo/tooling/wrapper/WrapperVerifier.java
    ${jdk}/bin/javac --release 25 -encoding UTF-8 -Xlint:all -Werror \
      -proc:none -sourcepath src/test/java -classpath classes \
      -d test-classes src/test/java/org/totipo/tooling/wrapper/WrapperVerifierTest.java
    runHook postBuild
  '';
  doCheck = true;
  checkPhase = ''
    runHook preCheck
    ${jdk}/bin/java -cp test-classes:classes org.totipo.tooling.wrapper.WrapperVerifierTest \
      ${../gradle/wrapper/gradle-wrapper.jar} \
      ${../gradle/wrapper/gradle-wrapper.properties}
    ${jdk}/bin/java -cp classes org.totipo.tooling.wrapper.WrapperVerifier \
      ${../gradle/wrapper/gradle-wrapper.jar} \
      ${../gradle/wrapper/gradle-wrapper.properties}
    runHook postCheck
  '';
  installPhase = ''
    runHook preInstall
    touch "$out"
    runHook postInstall
  '';
}
