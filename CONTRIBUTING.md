# Contributing to iw-cli

## CI Checks

Pull requests to `main` trigger a GitHub Actions CI pipeline that validates:

1. **Compile** — core modules compile without errors
2. **Format** — code conforms to scalafmt rules
3. **Lint** — scalafix rules pass (DisableSyntax: no nulls, vars, throws, returns)
4. **Test** — unit tests, command compilation, and E2E tests all pass

The test job runs after compile succeeds; the other three jobs run in parallel for fast feedback.

## Git Hooks

Install the project git hooks:

```bash
git config core.hooksPath .git-hooks
```

### pre-commit

Runs a format check (`scala-cli fmt --check .`). Blocks the commit if formatting issues are found. Fix with:

```bash
scala-cli fmt .
```

Then re-stage your changes and commit again.

### pre-push

Runs two checks before allowing a push:

1. **Clean compile** — compiles the core module and blocks on any `[warn]` output
2. **Full test suite** — runs `./iw test` (unit tests, command compilation, E2E tests)

## Local Development

Run checks locally before pushing:

| Check | Command |
|-------|---------|
| Format check | `scala-cli fmt --check .` |
| Auto-format | `scala-cli fmt .` |
| Lint | `scalafix --check` |
| All tests | `./iw test` |
| Unit tests only | `./iw test unit` |
| E2E tests only | `./iw test e2e` |

## Troubleshooting

### Hook not running

Make sure hooks are installed:

```bash
git config core.hooksPath .git-hooks
```

Verify the hook scripts are executable:

```bash
chmod +x .git-hooks/pre-commit .git-hooks/pre-push
```

### Format failures after merge

After merging or rebasing, formatting may need to be reapplied:

```bash
scala-cli fmt .
```

### Bypassing hooks

In rare cases you can bypass hooks with `--no-verify`:

```bash
git commit --no-verify
git push --no-verify
```

This is not recommended — fix the underlying issue instead.
