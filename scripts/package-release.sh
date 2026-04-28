#!/usr/bin/env bash
# PURPOSE: Package iw-cli release tarball for distribution
# PURPOSE: Creates tar.gz with iw-run, commands, core deps manifest, and pre-built jars

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
RELEASE_DIR="$PROJECT_ROOT/release"

# Get version from argument or default
VERSION="${1:-0.1.0}"

echo "Packaging iw-cli version $VERSION..."

# Resolve Mill build outputs first so we fail fast if Mill / build inputs are missing.
# Parse Mill's "ref:vN:<hash>:<path>" output produced by `mill show`.
strip_mill_ref() {
    jq -r '.' | sed -E 's#^ref:v[0-9]+:[a-f0-9]+:##'
}

validate_jar() {
    local label="$1"
    local path="$2"
    if [[ -z "$path" ]]; then
        echo "Error: Mill returned empty path for $label" >&2
        exit 1
    fi
    if [[ ! -f "$path" ]]; then
        echo "Error: $label not found at $path" >&2
        exit 1
    fi
    if [[ "$path" != *.jar ]]; then
        echo "Error: $label is not a .jar: $path" >&2
        exit 1
    fi
    if [[ ! -s "$path" ]]; then
        echo "Error: $label is zero bytes: $path" >&2
        exit 1
    fi
}

echo "Building core.jar via Mill..."
CORE_JAR_PATH="$(cd "$PROJECT_ROOT" && ./mill --ticker false show core.jar | strip_mill_ref)"
validate_jar "core.jar" "$CORE_JAR_PATH"

echo "Building dashboard.assembly via Mill..."
DASHBOARD_JAR_PATH="$(cd "$PROJECT_ROOT" && ./mill --ticker false show dashboard.assembly | strip_mill_ref)"
validate_jar "dashboard.assembly" "$DASHBOARD_JAR_PATH"

# Create release directory structure
RELEASE_PACKAGE_DIR="$RELEASE_DIR/iw-cli-$VERSION"
rm -rf "$RELEASE_PACKAGE_DIR"
mkdir -p "$RELEASE_PACKAGE_DIR"/{commands,core,build}

# Copy iw-run launcher and bootstrap script
cp "$PROJECT_ROOT/iw-run" "$RELEASE_PACKAGE_DIR/"
cp "$PROJECT_ROOT/iw-bootstrap" "$RELEASE_PACKAGE_DIR/"

# Copy VERSION file
cp "$PROJECT_ROOT/VERSION" "$RELEASE_PACKAGE_DIR/"

# Copy all command files (recursively, preserving directory structure)
# Exclude .scala-build/ directories
rsync -a \
    --exclude='.scala-build/' \
    --exclude='.bsp/' \
    --include='*/' \
    --include='*.scala' \
    --exclude='*' \
    "$PROJECT_ROOT/commands/" "$RELEASE_PACKAGE_DIR/commands/"

# Ship only the deps manifest from core/. Compiled classes live in build/iw-core.jar;
# core/project.scala is read at runtime by iw-run as the scala-cli deps file.
cp "$PROJECT_ROOT/core/project.scala" "$RELEASE_PACKAGE_DIR/core/project.scala"

# Copy resolved Mill build outputs into build/. Names are literal — iw-run does a
# direct path test on $INSTALL_DIR/build/iw-core.jar and iw-dashboard.jar.
cp "$CORE_JAR_PATH" "$RELEASE_PACKAGE_DIR/build/iw-core.jar"
cp "$DASHBOARD_JAR_PATH" "$RELEASE_PACKAGE_DIR/build/iw-dashboard.jar"

# Create tarball
cd "$RELEASE_DIR"
tar -czf "iw-cli-${VERSION}.tar.gz" "iw-cli-$VERSION"

# Clean up staging directory (only the tarball matters)
rm -rf "iw-cli-$VERSION"

tarball="iw-cli-${VERSION}.tar.gz"
echo "Release tarball created: $RELEASE_DIR/$tarball"
echo "Contents (first 30 entries):"
tar -tzf "$tarball" | head -30 || true
echo ""
echo "Pre-built jar entries:"
tar -tzvf "$tarball" | grep -E "iw-cli-${VERSION}/build/" || {
    echo "Error: tarball is missing build/ entries" >&2
    exit 1
}
tar -tzf "$tarball" | grep -q "iw-cli-${VERSION}/core/project.scala" || {
    echo "Error: tarball is missing core/project.scala" >&2
    exit 1
}
