# Release Process

This document describes how to create and publish releases of iw-cli.

## Release Artifacts

Each release consists of:
1. A versioned tarball: `iw-cli-X.Y.Z.tar.gz`
2. The bootstrap script: `iw-bootstrap`

## Creating a Release

### 1. Update Version

Decide on the version number (e.g., `0.1.0`) following semantic versioning.

### 2. Package the Release

Prerequisites for running `package-release.sh` locally:
- Mill (the `./mill` wrapper in the repo root)
- Node 20+ with Corepack enabled (`corepack enable`)
- Yarn 4 (managed via Corepack)
- `WEBAWESOME_NPM_TOKEN` exported in your environment (Web Awesome Pro npm registry credential)

Run the packaging script:

```bash
WEBAWESOME_NPM_TOKEN=<your-token> scripts/package-release.sh 0.1.0
```

This creates:
- `release/iw-cli-0.1.0.tar.gz` - Contains iw-run, iw-bootstrap, commands, build/iw-core.jar, build/iw-dashboard.jar, and core/project.scala (deps manifest)

The tarball's `build/` directory holds two pre-built jars produced by Mill:
- `build/iw-core.jar` — compiled core library (model, adapters, output)
- `build/iw-dashboard.jar` — dashboard server assembly (includes frontend assets)

### 3. Test the Release

Before publishing, test the release package:

```bash
# Extract to test directory
mkdir -p /tmp/iw-test
tar -xzf release/iw-cli-0.1.0.tar.gz -C /tmp/iw-test

# Test bootstrap
cd /tmp/iw-test/iw-cli-0.1.0
./iw-run --bootstrap

# Test commands
./iw-run --list
```

Or run the automated tests:

```bash
bats test/bootstrap.bats
```

### 4. Create GitHub Release

1. Create a new tag:
   ```bash
   git tag -a v0.1.0 -m "Release 0.1.0"
   git push origin v0.1.0
   ```

2. Create a GitHub release:
   - Go to https://github.com/iterative-works/iw-cli/releases/new
   - Select the tag (v0.1.0)
   - Add release notes
   - Upload artifacts:
     - `release/iw-cli-0.1.0.tar.gz`
     - `iw-bootstrap`

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
- [ ] Version number updated in release script
- [ ] Release tarball created and tested
- [ ] Bootstrap script works with new release
- [ ] Git tag created and pushed
- [ ] GitHub release created with artifacts
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
