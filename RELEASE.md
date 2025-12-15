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

Run the packaging script:

```bash
.iw/scripts/package-release.sh 0.1.0
```

This creates:
- `release/iw-cli-0.1.0.tar.gz` - Contains iw-run, commands, and core files

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
bats .iw/test/bootstrap.bats
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

## Troubleshooting

### Release tarball too large
Check what's being included. Should only contain:
- iw-run script
- .iw/commands/*.scala
- .iw/core/*.scala

### Bootstrap fails to download
Verify:
- GitHub release exists and is public
- Tarball was uploaded correctly
- URL matches the pattern in iw-bootstrap

### Pre-compilation fails
Check:
- scala-cli is installed and in PATH
- All dependencies in project.scala are valid
- No compilation errors in source files
