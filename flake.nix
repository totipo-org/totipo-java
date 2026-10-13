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
        tooling = pkgs.callPackage ./nix/tooling.nix { };
        wrapper = pkgs.callPackage ./nix/wrapper-verifier.nix { };
        javaQualification = pkgs.callPackage ./qualification.nix {
          publicationTool = tooling.launcher;
          jdk = pkgs.jdk25_headless;
          gradle = pkgs.gradle_9.override { java = pkgs.jdk25_headless; };
        };
      in
      {
        formatter = pkgs.nixpkgs-fmt;

        # One library build, with a useful unsigned Maven-shaped output.
        packages = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          default = javaQualification;
          tooling = tooling.launcher;
          release-runtime = pkgs.symlinkJoin {
            name = "totipo-release-runtime";
            paths = [ tooling.launcher wrapper pkgs.jdk25_headless pkgs.gitMinimal pkgs.curl pkgs.cacert ];
          };
        };
        checks = pkgs.lib.optionalAttrs (system == "x86_64-linux") {
          java = javaQualification;
          tooling = tooling.check;
          inherit wrapper;
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

        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jdk25_headless
            gradle_9
            (jailed-agents.lib.${system}.makeJailedCodex {
              fwdEnv = [ "JAVA_HOME" ];
              extraPkgs = with pkgs; [
                jdk25_headless
                gradle_9
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
