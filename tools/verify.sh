#!/usr/bin/env bash
# Fly the whole Phase A chain headlessly against real kinetics. No Minecraft, no network.
#
# This is the phase gate's evidence. Screen Recording permission is not granted to the terminal
# in this environment, so screencapture returns desktop wallpaper and could never prove anything;
# the empire's answer is logs, and the strongest log is the pipeline actually running.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/jdks/jdk-25.0.4+7/Contents/Home}"

KIN_VERSION="$(sed -n 's/^kinetics_version=//p' gradle.properties)"
KIN_CORE="$HOME/.m2/repository/dev/lilkuzco/kinetics-core/$KIN_VERSION/kinetics-core-$KIN_VERSION.jar"
if [ ! -f "$KIN_CORE" ]; then
  echo "kinetics-core not in the local maven repo. Run:" >&2
  echo "  (cd ../lilkuzco_kinetics && ./gradlew :core:publishToMavenLocal)" >&2
  exit 1
fi

OUT=build/verify
rm -rf "$OUT" && mkdir -p "$OUT"

# Only the Minecraft-free half of cosmos: a propellant grade is two specific impulses and a
# rocket tier is masses and thrusts. Everything downstream is kinetics.
"$JAVA_HOME/bin/javac" -d "$OUT" -cp "$KIN_CORE" \
  src/main/java/dev/lilkuzco/cosmos/propellant/Propellant.java \
  src/main/java/dev/lilkuzco/cosmos/rocket/RocketTier.java \
  src/main/java/dev/lilkuzco/cosmos/rocket/LaunchPipeline.java \
  src/main/java/dev/lilkuzco/cosmos/moon/LunarLander.java \
  verify/java/dev/lilkuzco/cosmos/propellant/Propellants.java \
  verify/java/dev/lilkuzco/cosmos/verify/PhaseAVerification.java \
  verify/java/dev/lilkuzco/cosmos/verify/PhaseBVerification.java

"$JAVA_HOME/bin/java" -cp "$OUT:$KIN_CORE" dev.lilkuzco.cosmos.verify.PhaseAVerification
"$JAVA_HOME/bin/java" -cp "$OUT:$KIN_CORE" dev.lilkuzco.cosmos.verify.PhaseBVerification
