#!/usr/bin/env bash
# PURPOSE: Open a version-bump PR for an iw-cli release
# PURPOSE: Updates VERSION on a release branch, pushes, and opens the PR for review
#
# Usage: scripts/release-prepare.sh <version>
# Example: scripts/release-prepare.sh 0.6.3
#
# After the PR is merged, run scripts/release-publish.sh <version> to
# package the tarball, create the GitHub release pinned to the merged
# commit, and update the 'vlatest' pointer.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."

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

# Refuse to operate on a dirty tree; the bump commit must be the only change.
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "Error: Working tree has uncommitted changes. Commit or stash before running." >&2
    git status --short >&2
    exit 1
fi

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$CURRENT_BRANCH" != "main" ]; then
    echo "Error: release-prepare must be run from 'main' (currently on '$CURRENT_BRANCH')." >&2
    exit 1
fi

echo "=== Preparing iw-cli release v$VERSION ==="
echo ""

echo "Step 1: Syncing main with origin..."
git fetch origin
git pull --ff-only origin main

VERSION_FILE="$PROJECT_ROOT/VERSION"
CURRENT_VERSION="$(tr -d '[:space:]' < "$VERSION_FILE")"
if [ "$CURRENT_VERSION" = "$VERSION" ]; then
    echo "Error: VERSION already at $VERSION on main; nothing to prepare." >&2
    exit 1
fi
echo "  Bumping VERSION: $CURRENT_VERSION -> $VERSION"

BRANCH="chore/bump-version-$VERSION"
echo ""
echo "Step 2: Creating branch $BRANCH..."
if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
    echo "Error: Local branch $BRANCH already exists. Delete it or pick a different version." >&2
    exit 1
fi
if git ls-remote --exit-code --heads origin "$BRANCH" &> /dev/null; then
    echo "Error: Remote branch $BRANCH already exists on origin. Delete it or pick a different version." >&2
    exit 1
fi
git checkout -b "$BRANCH"

echo ""
echo "Step 3: Writing VERSION and committing..."
echo "$VERSION" > "$VERSION_FILE"
git add "$VERSION_FILE"
git commit -m "chore: Bump version to $VERSION"

echo ""
echo "Step 4: Pushing branch to origin..."
git push -u origin "$BRANCH"

echo ""
echo "Step 5: Opening PR..."
PR_URL="$(gh pr create \
    --base main \
    --head "$BRANCH" \
    --title "chore: Bump version to $VERSION" \
    --body "Bumps VERSION to $VERSION ahead of the v$VERSION release.

After merging, run \`scripts/release-publish.sh $VERSION\` from \`main\` to package the tarball, create the v$VERSION GitHub release pinned to the merged commit, and update the \`vlatest\` pointer.")"

echo ""
echo "=== Release v$VERSION prepared ==="
echo ""
echo "  PR: $PR_URL"
echo ""
echo "Next steps:"
echo "  1. Merge the PR above."
echo "  2. git checkout main && git pull"
echo "  3. scripts/release-publish.sh $VERSION"
