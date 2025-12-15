# Phase 8: Distribution and Versioning

**Issue:** IWLE-72
**Phase:** 8
**Created:** 2025-12-15
**Status:** Planning

## Goals

Enable iw-cli to be easily installed and used across multiple projects with:
1. Version pinning per project
2. Shared installation to avoid duplication
3. Offline capability after first run
4. Simple bootstrap for new projects

## Problem Statement

Currently iw-cli is developed in-place with all code in `.iw/`. When a user wants to use it in a new project, there's no mechanism to:
- Download the tool
- Share installations across projects
- Pin specific versions
- Update to newer versions

## Design Decision

**Approach:** GitHub releases + curl (Option C from analysis)

Inspired by Mill's bootstrap model:
- Thin shell script in project downloads from GitHub releases
- Shared installation cached in user's home directory
- Pre-compilation ensures offline capability

### Architecture

**Release artifact (`iw-cli-X.Y.Z.tar.gz`):**
```
iw-cli-X.Y.Z/
├── iw-run              # shared launcher (the "smart" script)
├── core/
│   ├── project.scala
│   └── *.scala
└── commands/
    └── *.scala
```

**Local cache (`~/.local/share/iw/`):**
```
~/.local/share/iw/
└── versions/
    ├── 0.1.0/
    │   ├── iw-run
    │   ├── core/
    │   └── commands/
    ├── 0.2.0/
    │   └── ...
    └── latest/         # for development
```

**Project structure:**
```
project/
├── iw                  # thin bootstrap script (~30 lines)
└── .iw/
    └── config.conf     # includes: version = "0.1.0"
```

### Bootstrap Flow

1. User clones project containing `iw` script
2. User runs `./iw <command>`
3. Bootstrap script:
   - Reads version from `.iw/config.conf` (default: "latest")
   - Checks if version exists in `~/.local/share/iw/versions/<version>/`
   - If missing: downloads tarball from GitHub releases, extracts
   - Calls `iw-run --bootstrap` to pre-compile and cache dependencies
   - Delegates to `iw-run` for actual command execution

### Version Resolution

| Config Value | Behavior |
|--------------|----------|
| `version = "0.1.0"` | Exact version from releases |
| `version = "latest"` | Latest release (re-checked periodically?) |
| (missing) | Default to "latest" |

### Pre-compilation

On first run, `iw-run --bootstrap`:
```bash
scala-cli compile "$VERSION_DIR/commands/version.scala" "$VERSION_DIR/core"/*.scala
```

This triggers:
- Coursier downloads all dependencies (sttp, upickle, config)
- scala-cli compiles and caches bytecode
- Subsequent runs are instant and work offline

## Scope

### In Scope
- Refactor `iw` into thin bootstrap script
- Create `iw-run` with current `iw` logic + bootstrap command
- Add `version` field to config format
- Create release artifact structure
- Document installation process

### Out of Scope
- GitHub Actions release workflow (can be manual initially)
- Auto-update mechanism
- Multiple architecture support (just needs scala-cli)

## Dependencies

- GitHub repository for releases
- scala-cli (required dependency, handles everything else via Coursier)

## Testing Strategy

1. **Unit tests:** Config parsing with version field
2. **Integration tests:** Bootstrap flow simulation
3. **Manual tests:**
   - Fresh install from release
   - Version switching
   - Offline operation after bootstrap

## Acceptance Criteria

- [ ] `iw` script is thin (~30 lines) and stable
- [ ] `iw-run` contains all command logic
- [ ] Version can be pinned in config
- [ ] First run downloads and bootstraps automatically
- [ ] Subsequent runs work offline
- [ ] Release tarball can be created
