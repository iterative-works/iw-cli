#!/usr/bin/env bash
# PURPOSE: Create a new iw-cli release
# PURPOSE: Packages tarball, creates GitHub release, and updates 'latest'
#
# Usage: .iw/scripts/release.sh <version>
# Example: .iw/scripts/release.sh 0.2.0

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../.."
RELEASE_DIR="$PROJECT_ROOT/release"

# Validate version argument
if [ $# -lt 1 ]; then
    echo "Usage: $0 <version>" >&2
    echo "Example: $0 0.2.0" >&2
    exit 1
fi

VERSION="$1"

# Validate version format (semver-like)
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: Version must be in format X.Y.Z (e.g., 0.2.0)" >&2
    exit 1
fi

# Check if gh CLI is available
if ! command -v gh &> /dev/null; then
    echo "Error: GitHub CLI (gh) not found. Please install it first." >&2
    exit 1
fi

# Check if logged in to gh
if ! gh auth status &> /dev/null; then
    echo "Error: Not logged in to GitHub CLI. Run 'gh auth login' first." >&2
    exit 1
fi

echo "=== Creating iw-cli release v$VERSION ==="
echo ""

# Step 1: Update version in version.scala
VERSION_FILE="$PROJECT_ROOT/.iw/commands/version.scala"
echo "Step 1: Updating version in $VERSION_FILE..."
sed -i "s/val iwVersion = \"[^\"]*\"/val iwVersion = \"$VERSION\"/" "$VERSION_FILE"
echo "  Version updated to $VERSION"

# Step 2: Package the release
echo ""
echo "Step 2: Packaging release..."
"$SCRIPT_DIR/package-release.sh" "$VERSION"

# Step 3: Create GitHub release
TARBALL="$RELEASE_DIR/iw-cli-${VERSION}.tar.gz"
if [ ! -f "$TARBALL" ]; then
    echo "Error: Tarball not found: $TARBALL" >&2
    exit 1
fi

echo ""
echo "Step 3: Creating GitHub release v$VERSION..."
gh release create "v$VERSION" "$TARBALL" \
    --title "iw-cli v$VERSION" \
    --notes "Release v$VERSION

See [CHANGELOG](https://github.com/iterative-works/iw-cli/blob/main/CHANGELOG.md) for details."

echo "  Created release: https://github.com/iterative-works/iw-cli/releases/tag/v$VERSION"

# Step 4: Update 'latest' release
echo ""
echo "Step 4: Updating 'latest' release..."
LATEST_TARBALL="$RELEASE_DIR/iw-cli-latest.tar.gz"
cp "$TARBALL" "$LATEST_TARBALL"

# Check if vlatest exists, create or update
if gh release view vlatest &> /dev/null; then
    gh release upload vlatest "$LATEST_TARBALL" --clobber
    gh release edit vlatest --notes "Latest stable release. Currently points to v$VERSION."
else
    gh release create vlatest "$LATEST_TARBALL" \
        --title "iw-cli latest" \
        --notes "Latest stable release. Currently points to v$VERSION."
fi
echo "  Updated 'latest' to point to v$VERSION"

# Step 5: Commit version bump
echo ""
echo "Step 5: Committing version bump..."
cd "$PROJECT_ROOT"
git add "$VERSION_FILE"
git commit -m "chore: Bump version to $VERSION"
git push

echo ""
echo "=== Release v$VERSION complete! ==="
echo ""
echo "  Versioned: https://github.com/iterative-works/iw-cli/releases/tag/v$VERSION"
echo "  Latest:    https://github.com/iterative-works/iw-cli/releases/tag/vlatest"
