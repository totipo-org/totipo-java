{
  description = "Totipo Java library qualification and development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    llm-agents.url = "github:numtide/llm-agents.nix";
    jailed-agents = {
      url = "github:andersonjoseph/jailed-agents";
      inputs.llm-agents.follows = "llm-agents";
    };
  };

  outputs = { nixpkgs, flake-utils, jailed-agents, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
        };
        javaQualification = pkgs.callPackage ./qualification.nix {
          jdk = pkgs.jdk25_headless;
          gradle = pkgs.gradle_9.override { java = pkgs.jdk25_headless; };
        };
      in
      {
        formatter = pkgs.nixpkgs-fmt;

        # One library build, with a useful unsigned Maven-shaped output.
        packages = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          default = javaQualification;
        };
        checks = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          java = javaQualification;
          wrapper = pkgs.runCommand "totipo-java-wrapper-integrity" {
            nativeBuildInputs = [ pkgs.python3 ];
          } ''
            python3 -B ${./nix/verify-wrapper.py} \
              ${./gradle/wrapper/gradle-wrapper.jar} \
              ${./gradle/wrapper/gradle-wrapper.properties}
            touch "$out"
          '';
        };
        apps = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          update-package-deps = {
            type = "app";
            program = "${javaQualification.mitmCache.updateScript}";
            meta.description = "Regenerate the fixed Java Gradle dependency cache";
          };
        };

        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jdk25_headless
            gradle_9
            python3
            (jailed-agents.lib.${system}.makeJailedCodex {
              fwdEnv = [ "JAVA_HOME" ];
              extraPkgs = with pkgs; [
                jdk25_headless
                gradle_9
                python3
              ];
            })
          ];
        };
      });

  nixConfig = {
    extra-substituters = [ "https://cache.numtide.com" ];
    extra-trusted-public-keys = [ "niks3.numtide.com-1:DTx8wZduET09hRmMtKdQDxNNthLQETkc/yaX7M4qK0g=" ];
  };
}
