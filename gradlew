#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="9.1.1"
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
GRADLE_DIST_DIR="$GRADLE_HOME/gradle-$GRADLE_VERSION"
GRADLE_ARCHIVE="$GRADLE_HOME/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

mkdir -p "$GRADLE_HOME"

if [ ! -x "$GRADLE_DIST_DIR/bin/gradle" ]; then
  echo "Downloading Gradle ${GRADLE_VERSION}..."
  curl -fsSL "$GRADLE_URL" -o "$GRADLE_ARCHIVE"
  rm -rf "$GRADLE_DIST_DIR"
  unzip -q "$GRADLE_ARCHIVE" -d "$GRADLE_HOME"
  rm -f "$GRADLE_ARCHIVE"
fi

exec "$GRADLE_DIST_DIR/bin/gradle" "$@"
