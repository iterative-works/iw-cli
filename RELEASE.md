# Release Process

This document describes how to create and publish releases of iw-cli.

## Release Artifacts

Each release consists of:
1. A versioned tarball: `iw-cli-X.Y.Z.tar.gz`
2. The bootstrap script: `iw-bootstrap`

## Creating a Release

Prerequisites for any release work locally:
- Mill (the `./mill` wrapper in the repo root)
- Node 20+ with Corepack enabled (`corepack enable`)
- Yarn 4 (managed via Corepack)
- `WEBAWESOME_NPM_TOKEN` exported in your environment (Web Awesome Pro npm registry credential)
- GitHub CLI (`gh`) authenticated against `iterative-works/iw-cli`

The release flow is split into two scripts so the version-bump goes
through `main`'s required PR review and the resulting tag points at the
merged bump commit, not at whatever `main` happened to be earlier.

### 1. Open the version-bump PR

From a clean `main`, run:

```bash
scripts/release-prepare.sh 0.6.3
```

This creates `chore/bump-version-0.6.3`, writes the new `VERSION`,
commits, pushes, and opens a PR against `main`. Review and merge the PR
the same way as any other change.

### 2. Publish the release

After the PR is merged, sync `main` and run:

```bash
git checkout main && git pull
scripts/release-publish.sh 0.6.3
```

This packages the tarball + `iw-bootstrap`, creates the `v0.6.3`
GitHub release pinned to the current `main` HEAD, and updates the
rolling `vlatest` release.

### 3. Package only (no publish)

For local testing you can run the packaging step on its own — it does
not touch git or the GitHub API:

```bash
WEBAWESOME_NPM_TOKEN=<your-token> scripts/package-release.sh 0.6.3
```

This produces `release/iw-cli-0.6.3.tar.gz` containing `iw-run`,
`iw-bootstrap`, `commands/`, `build/iw-core.jar`,
`build/iw-dashboard.jar`, and `core/project.scala` (deps manifest).

### 4. Test the release tarball

Before publishing (or to validate a published tarball) extract and
exercise it:

```bash
mkdir -p /tmp/iw-test
tar -xzf release/iw-cli-0.6.3.tar.gz -C /tmp/iw-test
cd /tmp/iw-test/iw-cli-0.6.3
./iw-run --bootstrap
./iw-run --list
```

Or run the automated tests:

```bash
bats test/bootstrap.bats
```

### 5. Verify Download

Test that the bootstrap script can download the release:

```bash
# In a test project
curl -L https://github.com/iterative-works/iw-cli/releases/download/v0.1.0/iw-bootstrap -o iw
chmod +x iw

# Create config with version
mkdir -p .iw
echo 'version = "0.1.0"' > .iw/config.conf

# Test bootstrap
./iw --list
```

## Release Checklist

- [ ] All tests pass (unit and integration)
- [ ] `scripts/release-prepare.sh <version>` PR opened, reviewed, merged
- [ ] `scripts/release-publish.sh <version>` succeeded on `main`
- [ ] Versioned GitHub release contains tarball + `iw-bootstrap`
- [ ] `vlatest` release updated
- [ ] Download from GitHub works correctly
- [ ] Offline operation verified after bootstrap

## Version Strategy

- **Patch (0.0.x)**: Bug fixes, no breaking changes
- **Minor (0.x.0)**: New features, backward compatible
- **Major (x.0.0)**: Breaking changes to config format or command API

## Distribution Model

iw-cli uses a shared installation model:

- Projects contain only the thin `iw-bootstrap` script
- Actual implementation lives in `~/.local/share/iw/versions/<version>/`
- Multiple projects can share the same version installation
- First run downloads and pre-compiles for offline use

## Tarball Contract

The release tarball is a **read-only artifact**. It contains no Mill, no `build.mill`, no `.mill-version`. An extracted tarball cannot rebuild itself and is not a development workspace.

The launcher (`iw-run`) resolves the core library jar in three tiers:

1. `$IW_CORE_JAR` environment variable (tests / consumer pre-provisioning)
2. `$INSTALL_DIR/build/iw-core.jar` — pre-built jar shipped in the release tarball
3. `./mill show core.jar` — dev checkout fallback; rebuilds on stale inputs

An extracted release tarball always uses tier 2. Only a dev checkout (where `./mill` is present) uses tier 3. This means an installed copy of iw-cli never requires Mill, Node, Yarn, or any build tooling at runtime.

**For development:** clone the repository (`git clone iterative-works/iw-cli`) and work from the checkout. Do not use an extracted release tarball as a workspace.

**Failure mode:** if you extract the tarball and try to run `./mill`, you will get "command not found" — a clear signal that you need a dev checkout, not a partial-build error.

## Troubleshooting

### Release tarball too large

Check what's being included. Should only contain:
- `iw-run`, `iw-bootstrap`
- `VERSION`
- `commands/**/*.scala`
- `core/project.scala` (deps manifest only — not all of `core/`)
- `build/iw-core.jar`, `build/iw-dashboard.jar`

The tarball **must not** contain `./mill`, `.mill-version`, `build.mill`, `out/`, `.bsp/`, `dashboard/jvm/`, or `dashboard/frontend/`. These belong to the dev checkout, not the release artifact.

### Bootstrap fails to download
Verify:
- GitHub release exists and is public
- Tarball was uploaded correctly
- URL matches the pattern in iw-bootstrap

### Bootstrap reports missing jars

On an installed tarball, `./iw-run --bootstrap` is a verify-only step — it confirms the pre-built jars are present at `build/iw-core.jar` and `build/iw-dashboard.jar`. It does not compile anything.

If bootstrap reports missing jars:
- Re-extract the tarball from the original `iw-cli-X.Y.Z.tar.gz` (the `build/` directory may have been deleted or corrupted)
- Confirm you downloaded a full release tarball and not a source archive

For dev-checkout flows where Mill drives the build:
- Confirm `./mill` is executable in the repo root
- Check that `WEBAWESOME_NPM_TOKEN` is set for dashboard assembly builds
- Run `./mill core.jar` and `./mill dashboard.assembly` directly to diagnose build failures
