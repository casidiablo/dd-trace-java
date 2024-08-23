let
  pkgs = import <nixpkgs> {};
  stdenv = pkgs.stdenv;

in stdenv.mkDerivation rec {
  name = "service-web";
  buildInputs = [
    pkgs.temurin-bin-8
  ];

  shellHook = ''
    export JAVA_HOME=${pkgs.temurin-bin-8}
    export JAVA_8_HOME=${pkgs.temurin-bin-8}
    export JAVA_11_HOME=${pkgs.temurin-bin-11}
    export JAVA_17_HOME=${pkgs.temurin-bin-17}
    export JAVA_21_HOME=${pkgs.temurin-bin-21}
    export JAVA_GRAALVM17_HOME=${pkgs.temurin-bin-17}
  '';
}
