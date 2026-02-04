#!/usr/bin/env bash
# PURPOSE: Package iw-cli release tarball for distribution
# PURPOSE: Creates tar.gz with iw-run, commands, and core files

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../.."
RELEASE_DIR="$PROJECT_ROOT/release"

# Get version from argument or default
VERSION="${1:-0.1.0}"

echo "Packaging iw-cli version $VERSION..."

# Create release directory structure
RELEASE_PACKAGE_DIR="$RELEASE_DIR/iw-cli-$VERSION"
rm -rf "$RELEASE_PACKAGE_DIR"
mkdir -p "$RELEASE_PACKAGE_DIR"/{commands,core,scripts}

# Copy iw-run launcher
cp "$PROJECT_ROOT/iw-run" "$RELEASE_PACKAGE_DIR/"

# Copy all command files
cp "$PROJECT_ROOT/.iw/commands"/*.scala "$RELEASE_PACKAGE_DIR/commands/"

# Copy claude-skill-prompt.md template (needed by claude-sync command)
cp "$PROJECT_ROOT/.iw/scripts/claude-skill-prompt.md" "$RELEASE_PACKAGE_DIR/scripts/"

# Copy iw-command-creation skill (needed by claude-sync command)
if [ -d "$PROJECT_ROOT/.claude/skills/iw-command-creation" ]; then
    mkdir -p "$RELEASE_PACKAGE_DIR/.claude/skills"
    cp -r "$PROJECT_ROOT/.claude/skills/iw-command-creation" "$RELEASE_PACKAGE_DIR/.claude/skills/"
fi

# Copy all core files (recursively, preserving directory structure)
# Exclude test/ and .scala-build/ directories
rsync -a \
    --exclude='test/' \
    --exclude='.scala-build/' \
    --include='*/' \
    --include='*.scala' \
    --exclude='*' \
    "$PROJECT_ROOT/.iw/core/" "$RELEASE_PACKAGE_DIR/core/"

# Create tarball
cd "$RELEASE_DIR"
tar -czf "iw-cli-${VERSION}.tar.gz" "iw-cli-$VERSION"

echo "Release tarball created: $RELEASE_DIR/iw-cli-${VERSION}.tar.gz"
echo "Contents:"
tar -tzf "iw-cli-${VERSION}.tar.gz" | head -20
