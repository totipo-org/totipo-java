{
  description = "Totipo Java library qualification and development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    java-dev-tools = {
      url = "github:ingon/java-dev-tools";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    llm-agents.url = "github:numtide/llm-agents.nix";
    jailed-agents = {
      url = "github:andersonjoseph/jailed-agents";
      inputs.llm-agents.follows = "llm-agents";
    };
  };
  outputs = { nixpkgs, flake-utils, jailed-agents, java-dev-tools, ... }:
    # The locked nixpkgs no longer supports Intel macOS.
    flake-utils.lib.eachSystem [ "x86_64-linux" "aarch64-linux" "aarch64-darwin" ] (system:
      let
        pkgs = import nixpkgs {
          inherit system;
        };
        tooling = pkgs.callPackage ./nix/tooling.nix { };
        jdk = pkgs.jdk25_headless;
        gradle = pkgs.gradle_9.override { java = jdk; };

        javaEditor = java-dev-tools.lib.mkJavaEditor { inherit pkgs jdk gradle; };

        javaQualification = pkgs.callPackage ./qualification.nix {
          publicationTool = tooling.launcher;
          inherit jdk gradle;
        };
      in
      assert gradle.version == "9.8.0";
      {
        formatter = pkgs.nixpkgs-fmt;
        # One library build, with a useful unsigned Maven-shaped output.
        packages = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          default = javaQualification;
          tooling = tooling.launcher;
          release-runtime = pkgs.symlinkJoin {
            name = "totipo-release-runtime";
            paths = [ tooling.launcher jdk gradle pkgs.gitMinimal pkgs.curl pkgs.cacert ];
          };
        };
        checks = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          java = javaQualification;
          tooling = tooling.check;
        };
        apps = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          tooling = {
            type = "app";
            program = "${tooling.launcher}/bin/totipo-java-tool";
            meta.description = "Standalone Java tooling bootstrap";
          };
          update-package-deps = {
            type = "app";
            program = "${javaQualification.mitmCache.updateScript}";
            meta.description = "Regenerate the fixed Java Gradle dependency cache";
          };
        };
        devShells.default = pkgs.mkShell (javaEditor.shellEnv // {
          JAVA_HOME = "${jdk}/lib/openjdk";
          TOTIPO_GRADLE = "${gradle}/bin/gradle";
          packages = [
            jdk
            gradle
          ] ++ javaEditor.packages ++ [
            (jailed-agents.lib.${system}.makeJailedCodex {
              fwdEnv = [ "JAVA_HOME" ];
              extraPkgs = [
                jdk
                gradle
              ];
            })
          ];
        });
      });
  nixConfig = {
    extra-substituters = [ "https://cache.numtide.com" ];
    extra-trusted-public-keys = [ "niks3.numtide.com-1:DTx8wZduET09hRmMtKdQDxNNthLQETkc/yaX7M4qK0g=" ];
  };
}
