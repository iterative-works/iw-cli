#!/usr/bin/env bash
# PURPOSE: Package and publish an iw-cli release after the version-bump PR merged
# PURPOSE: Creates the v<version> tag pinned to the current main HEAD and updates vlatest
#
# Usage: scripts/release-publish.sh <version>
# Example: scripts/release-publish.sh 0.6.3
#
# Run AFTER scripts/release-prepare.sh <version> has been opened and merged.
# Requires VERSION on main to match <version>; refuses to run otherwise.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
RELEASE_DIR="$PROJECT_ROOT/release"

if [ $# -lt 1 ]; then
    echo "Usage: $0 <version>" >&2
    echo "Example: $0 0.6.3" >&2
    exit 1
fi

VERSION="$1"

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: Version must be in format X.Y.Z (e.g., 0.6.3)" >&2
    exit 1
fi

if ! command -v gh &> /dev/null; then
    echo "Error: GitHub CLI (gh) not found. Please install it first." >&2
    exit 1
fi

if ! gh auth status &> /dev/null; then
    echo "Error: Not logged in to GitHub CLI. Run 'gh auth login' first." >&2
    exit 1
fi

cd "$PROJECT_ROOT"

if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "Error: Working tree has uncommitted changes. Commit or stash before running." >&2
    git status --short >&2
    exit 1
fi

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$CURRENT_BRANCH" != "main" ]; then
    echo "Error: release-publish must be run from 'main' (currently on '$CURRENT_BRANCH')." >&2
    exit 1
fi

echo "=== Publishing iw-cli release v$VERSION ==="
echo ""

echo "Step 1: Syncing main with origin..."
git fetch origin
git pull --ff-only origin main

VERSION_FILE="$PROJECT_ROOT/VERSION"
ON_DISK_VERSION="$(tr -d '[:space:]' < "$VERSION_FILE")"
if [ "$ON_DISK_VERSION" != "$VERSION" ]; then
    echo "Error: VERSION on main is $ON_DISK_VERSION, not $VERSION." >&2
    echo "       Run scripts/release-prepare.sh $VERSION and merge the PR first." >&2
    exit 1
fi

MAIN_SHA="$(git rev-parse HEAD)"
echo "  main HEAD: $MAIN_SHA"

if gh release view "v$VERSION" &> /dev/null; then
    echo "Error: GitHub release v$VERSION already exists. Delete it first if you intend to recreate." >&2
    exit 1
fi

echo ""
echo "Step 2: Packaging release..."
"$SCRIPT_DIR/package-release.sh" "$VERSION"

TARBALL="$RELEASE_DIR/iw-cli-${VERSION}.tar.gz"
if [ ! -f "$TARBALL" ]; then
    echo "Error: Tarball not found: $TARBALL" >&2
    exit 1
fi

BOOTSTRAP="$PROJECT_ROOT/iw-bootstrap"
if [ ! -f "$BOOTSTRAP" ]; then
    echo "Error: iw-bootstrap not found: $BOOTSTRAP" >&2
    exit 1
fi

echo ""
echo "Step 3: Creating GitHub release v$VERSION pinned to $MAIN_SHA..."
gh release create "v$VERSION" "$TARBALL" "$BOOTSTRAP" \
    --target "$MAIN_SHA" \
    --title "iw-cli v$VERSION" \
    --notes "Release v$VERSION

See [CHANGELOG](https://github.com/iterative-works/iw-cli/blob/main/CHANGELOG.md) for details."

echo "  Created release: https://github.com/iterative-works/iw-cli/releases/tag/v$VERSION"

echo ""
echo "Step 4: Updating 'vlatest' release..."
LATEST_TARBALL="$RELEASE_DIR/iw-cli-latest.tar.gz"
cp "$TARBALL" "$LATEST_TARBALL"

if gh release view vlatest &> /dev/null; then
    gh release upload vlatest "$LATEST_TARBALL" "$BOOTSTRAP" --clobber
    gh release edit vlatest --notes "Latest stable release. Currently points to v$VERSION."
else
    gh release create vlatest "$LATEST_TARBALL" "$BOOTSTRAP" \
        --title "iw-cli latest" \
        --notes "Latest stable release. Currently points to v$VERSION."
fi
echo "  Updated 'vlatest' to point to v$VERSION"

echo ""
echo "=== Release v$VERSION published ==="
echo ""
echo "  Versioned: https://github.com/iterative-works/iw-cli/releases/tag/v$VERSION"
echo "  Latest:    https://github.com/iterative-works/iw-cli/releases/tag/vlatest"
