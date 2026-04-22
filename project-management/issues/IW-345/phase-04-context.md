# Phase 4: Command integration + dev mode

**Issue:** IW-345
**Layer:** L4 — user-facing cutover
**Branch:** IW-345 (feature branch)
**Status:** Not started
**Estimated:** 3-5 hours

## Goals

- Flip `iw dashboard` and `iw server-daemon` from the in-process scala-cli bridge to `java -jar build/iw-dashboard.jar`, preserving today's CLI surface (`--state-path`, `--sample-data`, `--dev`, `--help`) and the foreground/background lifecycle distinction.
- Add a launcher-side rebuild gate so `build/iw-dashboard.jar` is regenerated via Mill whenever dashboard Scala or frontend sources change — parallel to Phase 1's `ensure_core_jar` / `core_jar_stale` pair in `iw-run`.
- Introduce a double-gated dev mode: `--dev` CLI flag AND `VITE_DEV_URL` env var together are required to route HTML templates at Vite's dev server. Scheme must be `http://`, host must be loopback. Any other combination refuses to start or silently keeps prod asset routing.
- Provide a single `assetUrl(path: String): String` template helper consumed by every template that currently hardcodes `/static/...` or `/assets/...`, so the dev/prod switch lives in one place.
- Retire the Phase 2 transitional scoped `//> using dep` lines on `commands/dashboard.scala` and `commands/server-daemon.scala` once those scripts stop importing `iw.dashboard.*` types directly.
- Replace or retire the Phase 2 `iwCoreJar` stage-and-repack. With `java -jar` taking over launches, `iw-run` no longer needs a combined core+dashboard jar — the dashboard process loads its own fat jar, and scala-cli commands keep using a core-only jar.
- Update `CLAUDE.md` and the top-level `README.md` with the two-build-tool boundary and the Node 20 / Yarn 4 / Mill 1.1.5 / `WEBAWESOME_NPM_TOKEN` dashboard-developer prereqs.

## Scope

### In scope

- **`commands/dashboard.scala` rewrite** (`/home/mph/Devel/iw/iw-cli-IW-345/commands/dashboard.scala`):
  - Remove `import iw.dashboard.{CaskServer, StateRepository}` and `import iw.dashboard.domain.SampleDataGenerator`.
  - Remove the three scoped `//> using dep` lines (cask, scalatags, flexmark) plus their `SYNC:` header.
  - Replace `startServerAndOpenBrowser`'s `CaskServer.start` call with a `java -jar $IW_INSTALL_DIR/build/iw-dashboard.jar <statePath> <port> <hosts> [--dev]` spawn in a child JVM. Retain argument parsing (`DashboardArgs`, dev-mode temp-dir construction, sample-data generation), the existing `isServerRunning` / `waitForServer` / `openBrowser` helpers.
  - Sample-data generation still needs to run before the server spawns. Either (a) move `SampleDataGenerator` / `StateRepository` usage into a short-lived `java -cp build/iw-dashboard.jar` invocation that writes the state file, or (b) add a small CLI mode on `ServerDaemon` that performs the write and exits. Option (a) keeps `ServerDaemon` focused; see CLARIFY SD.
  - Pass `VITE_DEV_URL` through to the child process via `ProcessBuilder.environment()` so the gated dev mode in the JVM can read it.
- **`commands/server-daemon.scala` rewrite** (`/home/mph/Devel/iw/iw-cli-IW-345/commands/server-daemon.scala`):
  - Delete the file entirely if it no longer has callers, OR shrink it to a thin scala-cli shim that execs `java -jar build/iw-dashboard.jar` with the same positional args (`statePath`, `port`, `hosts`). Phase 2 left it with a `package iw.dashboard` declaration plus three scoped `//> using dep` lines — both go away.
  - Primary consumer today is `core/adapters/ProcessManager.scala:spawnServerProcess` (line 89+), which invokes it via `scala-cli run`. Phase 4 redirects `spawnServerProcess` to `java -jar` directly, removing the scala-cli indirection. Once that flips, `commands/server-daemon.scala` has no callers and should be deleted.
- **`core/adapters/ProcessManager.scala` update** (`/home/mph/Devel/iw/iw-cli-IW-345/core/adapters/ProcessManager.scala:89-134`):
  - The `spawnServerProcess` method builds a `scala-cli run <daemonScript> <coreFiles...> --main-class iw.dashboard.ServerDaemon -- <args>` command. Replace the entire command-assembly block with `Seq("java", "-jar", dashboardJarPath, statePath, port.toString, hostsArg)` where `dashboardJarPath` resolves via `IW_DASHBOARD_JAR` env var or falls back to `$IW_INSTALL_DIR/build/iw-dashboard.jar`.
  - The string literal `"iw.dashboard.ServerDaemon"` at line 115 disappears along with the `--main-class` option (`java -jar` reads Main-Class from the manifest; Phase 3 set it to `iw.dashboard.ServerDaemon`).
  - Preserve the log file redirection (`redirectOutput` + `redirectErrorStream`) and the PID return semantics — only the command vector changes.
- **`iw-run` additions** (`/home/mph/Devel/iw/iw-cli-IW-345/iw-run`):
  - New `DASHBOARD_JAR="$INSTALL_DIR/build/iw-dashboard.jar"` and `export IW_DASHBOARD_JAR="$DASHBOARD_JAR"` near the existing `CORE_JAR` declaration (around line 14).
  - New `dashboard_jar_stale()` and `ensure_dashboard_jar()` helpers, mirroring `core_jar_stale` / `ensure_core_jar` (lines 37-55). Stale check inputs: `$INSTALL_DIR/dashboard/jvm/src/**/*.scala`, `$INSTALL_DIR/dashboard/frontend/src/**`, `$INSTALL_DIR/dashboard/frontend/package.json`, `$INSTALL_DIR/dashboard/frontend/yarn.lock`, `$INSTALL_DIR/dashboard/frontend/vite.config.js`, `$INSTALL_DIR/build.mill`. Exclude `out/` and `.scala-build/`.
  - Build command: `(cd "$INSTALL_DIR" && ./mill iwDashboardJar)`.
  - `ensure_dashboard_jar` is called only from command paths that actually launch the dashboard — specifically when `execute_command` dispatches to `dashboard` or `server-daemon` (or, if `server-daemon.scala` is removed, wherever `ProcessManager.spawnServerProcess` ultimately runs). Do NOT call it unconditionally like `ensure_core_jar` — non-dashboard commands shouldn't pay the mill-rebuild cost.
- **Double-gated dev mode in `dashboard/jvm/src/`**:
  - New `DevModeConfig` object (path TBD — see CLARIFY DM-LOC) exposing a pure function `resolve(devFlag: Boolean, viteDevUrlEnv: Option[String]): Either[String, DevModeConfig]` that:
    - returns `Right(DevModeConfig.Off)` when `devFlag` is false (regardless of env var),
    - returns `Right(DevModeConfig.Off)` when `devFlag` is true but `viteDevUrlEnv` is unset or empty (dev-flag alone is insufficient, log a warning that dev mode was requested but no URL provided — keep prod routing),
    - validates the URL: scheme must equal `http`, host must be `localhost` / `127.0.0.1` / `::1` (IPv6 loopback), returns `Left(errorMessage)` if either check fails,
    - returns `Right(DevModeConfig.On(viteDevUrl))` when both gates pass and validation succeeds.
  - `CaskServer.start(..., devMode: Boolean)` is called at the edge: read `VITE_DEV_URL` from env, call `DevModeConfig.resolve(devMode, env)`, on `Left(err)` print to stderr and `sys.exit(1)`, on `Right(DevModeConfig.On(url))` log `⚠ Dev mode: serving assets from $url` at INFO level, on `Right(DevModeConfig.Off)` proceed silently.
  - Thread the resolved `DevModeConfig` through into the presentation layer — at minimum into `PageLayout.render` (replacing the current `devMode: Boolean`). `AssetContext` alternative: wrap the `DevModeConfig` plus any other per-request config into a small case class passed as a template argument. Pick one; see CLARIFY DM-THREAD.
- **`assetUrl(path: String)(using AssetContext): String` template helper**:
  - Placed alongside `PageLayout` at `dashboard/jvm/src/presentation/views/`. Signature: `def assetUrl(path: String)(using ctx: AssetContext): String`. Behavior:
    - `AssetContext` with `DevModeConfig.Off`: returns `s"/assets/$path"`.
    - `AssetContext` with `DevModeConfig.On(baseUrl)`: returns `s"${baseUrl.stripSuffix("/")}/src/$path"` — assuming Vite entries map `src/main.js` and `src/main.css`. CLARIFY AU-PATH if Vite uses a different entry layout (validate against `dashboard/frontend/vite.config.js`; currently the entry is `src/main.js`).
  - Replace every hardcoded occurrence of `/assets/main.js` (currently one site: `PageLayout.scala:48`) with `assetUrl("main.js")`. `/static/*` stays hardcoded — those are bundled resources, not Vite-owned, and do not dev-route.
- **`dashboard/frontend/start-dev.sh`** (`/home/mph/Devel/iw/iw-cli-IW-345/dashboard/frontend/start-dev.sh`):
  - New executable script. Starts `yarn dev` on port 5173 (Vite's default). Exports `VITE_DEV_URL=http://localhost:5173` for convenience when launched from the same shell, but is advisory — `iw dashboard --dev` still requires the developer to set the env var explicitly. Keep simple: no Tailscale detection in the first cut (analysis mentions it but it's out-of-scope elaboration for v1 — add later if needed).
  - Verify no Phase 3 script already covers this before creating. `ls dashboard/frontend/` at the time of this writing shows no `start-dev.sh`.
- **Documentation**:
  - `CLAUDE.md` top-level (`/home/mph/Devel/iw/iw-cli-IW-345/CLAUDE.md`): update "Tech Stack" and "Architecture" sections. "Tech Stack" currently lists `Build: scala-cli` — change to "Build: scala-cli (core/commands) + Mill 1.1.5 (dashboard)". "Architecture" tree is missing `dashboard/`, `build.mill`, `mill`, `.mill-version` — add them. Add a short "Dashboard development" subsection covering Node 20+, Yarn 4 via Corepack (`corepack enable`), `WEBAWESOME_NPM_TOKEN` provisioning, and the `./iw dashboard --dev` + `VITE_DEV_URL` dev workflow.
  - Top-level `README.md` (`/home/mph/Devel/iw/iw-cli-IW-345/README.md`): extend the "Requirements" section to note that dashboard contributors additionally need Node 20+, Corepack-enabled Yarn 4, Mill 1.1.5 (via `./mill` wrapper), and `WEBAWESOME_NPM_TOKEN`. End-user requirements (JVM + scala-cli) stay unchanged.
- **`iwCoreJar` retirement** (`/home/mph/Devel/iw/iw-cli-IW-345/build.mill:97-106`):
  - Delete the `iwCoreJar` `Task.Command` and the `core/iw-core.jar` output. `iw-run`'s `build_core_jar()` flips from `./mill iwCoreJar` to `./mill core.jar` (Mill's built-in thin-jar task that produces `out/core/jar.dest/out.jar` — then copy or symlink to `build/iw-core.jar` via a tiny wrapper `iwCoreJar` replacement, OR drop the `build/iw-core.jar` on-disk contract entirely and teach `iw-run` to resolve the latest Mill-built jar).
  - The Phase 2 decision to merge core+dashboard into `iw-core.jar` was a transitional bridge so scala-cli commands could resolve `iw.dashboard.*` types. After Phase 4, scala-cli command scripts no longer import `iw.dashboard.*` — `build/iw-core.jar` can shrink back to a core-only jar. Verify with `rg 'iw\.dashboard' commands/` post-rewrite: must return zero matches.
- **Scoped `//> using dep` cleanup**:
  - `commands/dashboard.scala` lines 5-9: remove the SYNC comment + three `//> using dep` lines for cask, scalatags, flexmark-all. Validate post-removal: `rg '//> using dep .*cask|scalatags|flexmark' commands/` returns zero matches.
  - `commands/server-daemon.scala` lines 4-8: same cleanup if the file is shrunk rather than deleted.

### Out of scope — deferred past IW-345

- **Procedures alignment** on Yarn 4 / Mill versioning — Michal plans this separately.
- **`ivy2Local` resolver** for in-progress `scalatags-webawesome` SNAPSHOT work — Phase 3 deliberately pinned 3.2.1.1 from Maven Central.
- **IW-346 packaging / release-tarball restructure** — affects how `build/iw-core.jar` and `build/iw-dashboard.jar` ship, but not how they're built or consumed by `iw-run`.
- **Tapir/Netty migration** from Cask — out of scope for this whole issue.
- **Security headers, cache-control, 404 body contract** on static routes — flagged as deferred in Phase 3's review notes; Phase 4 does not revisit.
- **Broader `ProcessManager` refactor** — only `spawnServerProcess` is touched. The PID-file helpers and process lifecycle code remain as-is.
- **BATS coverage of the dashboard UI rendering** — the existing BATS suite covers `iw dashboard` launch behavior; adding full UI-level E2E tests is not a Phase 4 deliverable.

## Dependencies

### Incoming (from prior phases)

- **Phase 1**: Mill 1.1.5 via `./mill` wrapper + `.mill-version`. `iw-run`'s rebuild-gate pattern (`ensure_core_jar` / `core_jar_stale` at lines 29-55) — Phase 4 mirrors it for the dashboard jar.
- **Phase 2**: `dashboard/jvm/src/` layout, `iw.dashboard.*` package, `ProcessManager.scala:115` FQCN already renamed to `iw.dashboard.ServerDaemon`, scoped `//> using dep` bridge on the two command scripts, combined core+dashboard `iwCoreJar` still in place.
- **Phase 3**: `build/iw-dashboard.jar` produced by `./mill iwDashboardJar` with `Main-Class: iw.dashboard.ServerDaemon` in the manifest (`build.mill:36`); Vite assets embedded at classpath root under `/assets/*`; pre-existing resources at `/static/*`; `CaskServer` serves both route prefixes via `getResourceAsStream` (lines 610-649); `VITE_DEV_URL` plumbing NOT yet wired (Phase 4's responsibility); `DashboardArgs.devMode` and `CaskServer.start(..., devMode)` already exist as a passthrough boolean — Phase 4 replaces it with a richer config.
- **Phase 3 contract (from implementation-log.md):** `build/iw-dashboard.jar` is the sole artifact Phase 4's launcher consumes. `iwCoreJar` survived Phase 3 deliberately as the still-consumed bridge; Phase 4 retires it.

### External

- Mill 1.1.5 (already in CI image and dev shell from Phase 1).
- Node 20 + Yarn 4 (already in CI image from Phase 1; dashboard devs need `corepack enable` locally).
- `WEBAWESOME_NPM_TOKEN` (already in CI secrets + dev envs from Phase 3).
- No new GitHub Actions secrets; no new CI image changes.
- No new external runtime services.

## Approach

### 4.1 Rewrite `commands/dashboard.scala` to spawn the jar

The script keeps its current responsibilities: parse args, construct dev-mode temp dir / config, generate sample data if requested, decide whether the server is already running, start it or just open the browser. Only the `startServerAndOpenBrowser` internals change.

Shape:

```scala
def startServerAndOpenBrowser(
    statePath: String,
    port: Int,
    url: String,
    hosts: Seq[String],
    devMode: Boolean
): Unit =
  val installDir = sys.env.getOrElse("IW_INSTALL_DIR", /* sensible fallback */)
  val dashboardJar = s"$installDir/build/iw-dashboard.jar"

  val cmd = Seq(
    "java", "-jar", dashboardJar,
    statePath, port.toString, hosts.mkString(",")
  ) ++ (if devMode then Seq("--dev") else Seq.empty)

  val pb = new ProcessBuilder(cmd*)
  pb.inheritIO() // foreground mode: child stdout/stderr to parent TTY
  // VITE_DEV_URL inherits via default env; explicit documentation in --help.
  val process = pb.start()

  // Wait for health endpoint (existing waitForServer helper), open browser,
  // then process.waitFor() to block until child exits.
```

The `waitForServer` + `openBrowser` helpers stay unchanged. The foreground-blocking `serverThread.join()` is replaced by `process.waitFor()` on the child JVM.

Sample-data generation currently runs *in-process* via `StateRepository(effectiveStatePath).write(sampleState)` (lines 112-126). Post-rewrite the script no longer has `StateRepository` on its classpath. Options:
- **CLARIFY SD**: (a) shell out to `java -cp build/iw-dashboard.jar iw.dashboard.SampleDataCli <statePath>` for sample-data materialization (needs a small new `SampleDataCli` entry point in the dashboard module), OR (b) teach `ServerDaemon` a `--sample-data` flag that writes the state file before starting the HTTP listener, OR (c) let sample data be generated on first startup inside `CaskServer` when state file is absent AND `--sample-data` was passed. Option (b) is the smallest change and keeps sample-data behavior a dashboard-module concern; default to (b) unless reviewer picks otherwise.

### 4.2 Rewrite `commands/server-daemon.scala` to spawn the jar in background

Today this script is a 24-line `object ServerDaemon` with a `main` that forwards to `CaskServer.start(statePath, port, hosts)`. It's invoked by `ProcessManager.spawnServerProcess` via `scala-cli run server-daemon.scala <coreFiles> --main-class iw.dashboard.ServerDaemon -- <args>`.

Since `ProcessManager.spawnServerProcess` is the sole caller and 4.3 rewrites it to invoke `java -jar` directly, `commands/server-daemon.scala` becomes dead code. **Delete it.** Post-deletion check: `rg 'server-daemon' commands/ core/ iw-run test/` must yield zero scala-cli-indirection hits (BATS test file names are fine; `ProcessManager.serverLogPath` is unrelated).

If reviewer objects, the minimum-change alternative is to shrink `server-daemon.scala` to a `java -jar $IW_INSTALL_DIR/build/iw-dashboard.jar "$@"` wrapper — but that defeats the point of retiring scala-cli from the daemon path.

### 4.3 Update `core/adapters/ProcessManager.scala` FQCN reference

`spawnServerProcess` at lines 89-134 currently:

```scala
val command = Seq("scala-cli", "run", daemonScript) ++
  coreFiles ++
  Seq(
    "--main-class",
    "iw.dashboard.ServerDaemon",
    "--",
    statePath,
    port.toString,
    hostsArg
  )
```

Replace the entire block with:

```scala
val installDir = sys.env.getOrElse("IW_INSTALL_DIR", os.pwd.toString)
val dashboardJar = sys.env.getOrElse(
  "IW_DASHBOARD_JAR",
  s"$installDir/build/iw-dashboard.jar"
)

val command = Seq(
  "java", "-jar", dashboardJar,
  statePath,
  port.toString,
  hostsArg
)
```

The FQCN literal `"iw.dashboard.ServerDaemon"` goes away entirely — `java -jar` reads Main-Class from the jar manifest (set by Phase 3 at `build.mill:36`).

The log redirection block (lines 122-128) stays untouched: the `java` child still has its stdout/stderr captured to the server log file.

### 4.4 `ensure_dashboard_jar` helpers in `iw-run`

Pattern from Phase 1, applied to the dashboard jar. Near lines 14-15, add:

```bash
DASHBOARD_JAR="${IW_DASHBOARD_JAR:-$INSTALL_DIR/build/iw-dashboard.jar}"
export IW_DASHBOARD_JAR="$DASHBOARD_JAR"
```

Near lines 29-55 (after `ensure_core_jar`), add:

```bash
build_dashboard_jar() {
    echo "Rebuilding dashboard jar..." >&2
    mkdir -p "$(dirname "$DASHBOARD_JAR")"
    (cd "$INSTALL_DIR" && ./mill iwDashboardJar)
}

dashboard_jar_stale() {
    [ ! -f "$DASHBOARD_JAR" ] && return 0

    local newer
    newer=$(find "$INSTALL_DIR/dashboard/jvm/src" \
                  "$INSTALL_DIR/dashboard/frontend/src" \
                  "$INSTALL_DIR/dashboard/frontend/package.json" \
                  "$INSTALL_DIR/dashboard/frontend/yarn.lock" \
                  "$INSTALL_DIR/dashboard/frontend/vite.config.js" \
                  "$INSTALL_DIR/build.mill" \
        -type f \
        -not -path "*/out/*" \
        -not -path "*/node_modules/*" \
        -not -path "*/dist/*" \
        -not -path "*/.scala-build/*" \
        -newer "$DASHBOARD_JAR" \
        -print -quit 2>/dev/null)

    [ -n "$newer" ]
}

ensure_dashboard_jar() {
    if dashboard_jar_stale; then
        build_dashboard_jar
    fi
}
```

`ensure_dashboard_jar` is called only from the dashboard-launching commands. Options for wiring:
- Inside `execute_command` when `$cmd_name` is `dashboard` (shared) or when `$cmd_file` is `server-daemon.scala`.
- Simpler: call it inside `commands/dashboard.scala`'s Scala code by shelling out to `$IW_INSTALL_DIR/mill iwDashboardJar` before the `java -jar` spawn. That keeps the gate in one language.

Pick the shell-side gate to match Phase 1's pattern — see CLARIFY GATE-LOC.

### 4.5 Double-gated dev mode

**Pure resolution function**, in a new file `dashboard/jvm/src/DevModeConfig.scala` (or similarly scoped — see CLARIFY DM-LOC):

```scala
package iw.dashboard

enum DevModeConfig:
  case Off
  case On(viteDevUrl: String)

object DevModeConfig:
  def resolve(
      devFlag: Boolean,
      viteDevUrlEnv: Option[String]
  ): Either[String, DevModeConfig] =
    if !devFlag then Right(Off)
    else
      viteDevUrlEnv.filter(_.nonEmpty) match
        case None => Right(Off) // dev flag alone is insufficient; warn at caller
        case Some(raw) =>
          validate(raw).map(On.apply)

  private def validate(raw: String): Either[String, String] =
    scala.util.Try(java.net.URI.create(raw)) match
      case scala.util.Failure(e) =>
        Left(s"VITE_DEV_URL is not a valid URI: ${e.getMessage}")
      case scala.util.Success(uri) =>
        val scheme = Option(uri.getScheme).getOrElse("")
        val host = Option(uri.getHost).getOrElse("")
        if scheme != "http" then
          Left(s"VITE_DEV_URL scheme must be http (got: $scheme)")
        else if !loopbackHosts.contains(host) then
          Left(s"VITE_DEV_URL host must be loopback (got: $host)")
        else Right(raw)

  private val loopbackHosts = Set("localhost", "127.0.0.1", "::1")
```

**Wiring at startup** in `CaskServer.start` (currently at `CaskServer.scala:1531-1549`):

```scala
def start(
    statePath: String,
    port: Int = 9876,
    hosts: Seq[String] = Seq("localhost"),
    devMode: Boolean = false
): Unit =
  val viteEnv = sys.env.get("VITE_DEV_URL").map(_.trim).filter(_.nonEmpty)
  val resolved = DevModeConfig.resolve(devMode, viteEnv) match
    case Left(err) =>
      System.err.println(s"ERROR: $err")
      System.err.println("Refusing to start dashboard.")
      sys.exit(1)
    case Right(DevModeConfig.Off) if devMode && viteEnv.isEmpty =>
      System.err.println(
        "WARNING: --dev was passed but VITE_DEV_URL is not set; " +
          "continuing with bundled assets (dev mode inactive)."
      )
      DevModeConfig.Off
    case Right(cfg) => cfg

  resolved match
    case DevModeConfig.On(url) =>
      System.err.println(s"⚠ Dev mode: serving assets from $url")
    case DevModeConfig.Off => ()

  val startedAt = Instant.now()
  val server = new CaskServer(statePath, port, hosts, startedAt, resolved)
  // ... existing Undertow builder chain
```

The `CaskServer` constructor's last parameter changes from `devMode: Boolean` to `devMode: DevModeConfig`. Every internal call site (`PageLayout.render(... devMode = devMode)` at CaskServer lines 150, 195, 217, 264 and in `DashboardService.renderDashboard`) updates to pass the config through.

### 4.6 `assetUrl(path: String): String` template helper

New helper in `dashboard/jvm/src/presentation/views/AssetUrl.scala`:

```scala
package iw.dashboard.presentation.views

import iw.dashboard.DevModeConfig

final case class AssetContext(devMode: DevModeConfig)

object AssetContext:
  val prod: AssetContext = AssetContext(DevModeConfig.Off)

object AssetUrl:
  def apply(path: String)(using ctx: AssetContext): String =
    ctx.devMode match
      case DevModeConfig.Off        => s"/assets/$path"
      case DevModeConfig.On(baseUrl) =>
        s"${baseUrl.stripSuffix("/")}/src/$path"
```

`PageLayout.render` gains `(using AssetContext)` and replaces the hardcoded `/assets/main.js` at line 48 with `AssetUrl("main.js")`. Each caller of `PageLayout.render` (in `CaskServer` and `DashboardService`) needs the context in scope — provide it as a `given` when entering the route:

```scala
given AssetContext = AssetContext(devMode) // devMode is the server's DevModeConfig
```

**CLARIFY DM-THREAD**: The alternative is plumbing `AssetContext` as an explicit parameter rather than a `given`. Scala 3 `using` keeps the template call sites clean at the cost of a bit of implicit magic. Existing code style in `dashboard/jvm/src/presentation/` already uses implicit-less scalatags; a `given` at the route boundary is a single addition per route. Reviewer can flip to explicit if preferred.

**CLARIFY AU-PATH**: In dev mode the helper returns `${viteDevUrl}/src/main.js`. This is correct for Vite's default dev-server behavior when the entry is at `src/main.js` (matches current `dashboard/frontend/vite.config.js` `input: "src/main.js"`). For `main.css`, Tailwind is imported via `main.css` which `main.js` imports — so only `AssetUrl("main.js")` is typically threaded, and Vite's HMR pulls the CSS via the JS module graph. Validate by manually loading `http://localhost:5173/src/main.js` with the dev server running and confirming it returns the expected module source.

### 4.7 `dashboard/frontend/start-dev.sh`

Small convenience wrapper:

```bash
#!/usr/bin/env bash
# PURPOSE: Start the Vite dev server for the dashboard frontend
# PURPOSE: Use alongside `iw dashboard --dev` + `VITE_DEV_URL=http://localhost:5173`

set -euo pipefail

cd "$(dirname "$0")"

if [ -z "${WEBAWESOME_NPM_TOKEN:-}" ]; then
    echo "WARNING: WEBAWESOME_NPM_TOKEN not set; yarn install may fail" >&2
fi

yarn install --immutable
exec yarn dev --port 5173 --host localhost
```

No Tailscale detection in v1. Contributor runs this in one terminal, `iw dashboard --dev` with `VITE_DEV_URL=http://localhost:5173` in another. Executable bit set; added to `.gitignore` NOT necessary — this is a tracked script.

### 4.8 Documentation updates

**`CLAUDE.md` (top-level)** edits:

- "Tech Stack" block (lines 7-11): replace `- **Build**: scala-cli` with:
  ```
  - **Build**: scala-cli (core, commands) + Mill 1.1.5 (dashboard)
  - **Frontend toolchain**: Node 20, Yarn 4 via Corepack, Vite 8, Tailwind v4
  ```
- "Architecture" ASCII tree (lines 15-28): add `dashboard/` with `jvm/` + `frontend/` subtrees, `build.mill`, `mill`, `.mill-version`.
- New "Dashboard development" section between "Server Configuration" and "Testing":
  - Prereq: Node 20, `corepack enable`, `WEBAWESOME_NPM_TOKEN` in shell env.
  - Jar rebuild: `./iw dashboard` triggers `ensure_dashboard_jar`.
  - Dev mode: two terminals — `dashboard/frontend/start-dev.sh` (or `yarn dev`) + `VITE_DEV_URL=http://localhost:5173 ./iw dashboard --dev`.
  - Why two build tools: scala-cli stays for script-like commands; Mill owns the JVM + JS pipeline because scala-cli has no first-class JS story.

**`README.md` (top-level)** edits:

- "Requirements" section (lines 47-52): keep the existing end-user list intact; append a "Dashboard contributor requirements" sub-bullet:
  - Node 20+ (Corepack-enabled)
  - Yarn 4 via `corepack enable` (driven by `package.json`'s `packageManager` field)
  - Mill 1.1.5 (via the committed `./mill` wrapper)
  - `WEBAWESOME_NPM_TOKEN` for Web Awesome Pro registry access

### 4.9 Transitional `//> using dep` cleanup

- `commands/dashboard.scala:5-9`: delete lines 5 (SYNC comment opener), 6 (SYNC comment body), 7 (`//> using dep com.lihaoyi::cask:0.11.3`), 8 (`//> using dep com.lihaoyi::scalatags:0.13.1`), 9 (`//> using dep com.vladsch.flexmark:flexmark-all:0.64.8`).
- `commands/server-daemon.scala`: file is deleted (see 4.2), so cleanup is automatic.
- Post-rewrite grep invariant: `rg -n 'cask:|scalatags:|flexmark-all' commands/` matches zero lines.

## Files to modify / create

| Path | Action | Notes |
|------|--------|-------|
| `/home/mph/Devel/iw/iw-cli-IW-345/commands/dashboard.scala` | Modify | Drop dashboard imports + `//> using dep` lines; rewrite spawn to `java -jar`; pass `VITE_DEV_URL` through env |
| `/home/mph/Devel/iw/iw-cli-IW-345/commands/server-daemon.scala` | Delete | No callers after `ProcessManager.spawnServerProcess` flips |
| `/home/mph/Devel/iw/iw-cli-IW-345/core/adapters/ProcessManager.scala` | Modify | `spawnServerProcess` command vector swap; FQCN string literal removed |
| `/home/mph/Devel/iw/iw-cli-IW-345/iw-run` | Modify | `DASHBOARD_JAR` decl; `dashboard_jar_stale` / `ensure_dashboard_jar` helpers; call site for dashboard commands |
| `/home/mph/Devel/iw/iw-cli-IW-345/build.mill` | Modify | Delete `iwCoreJar`; consider adding a thin `iwCoreJar` wrapper that calls `core.jar` only if on-disk `build/iw-core.jar` contract is retained |
| `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/src/DevModeConfig.scala` | Create | Pure `resolve` + validator |
| `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/src/CaskServer.scala` | Modify | `start` reads `VITE_DEV_URL` + calls `DevModeConfig.resolve`; constructor `devMode: Boolean` → `devMode: DevModeConfig`; internal `PageLayout.render` callsites update |
| `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/src/DashboardService.scala` | Modify | `renderDashboard(..., devMode: DevModeConfig)` signature update; threads through to `PageLayout.render` |
| `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/src/presentation/views/PageLayout.scala` | Modify | `render` signature gains `(using AssetContext)`; `devMode: Boolean` → `devMode: DevModeConfig`; replace hardcoded `/assets/main.js` with `AssetUrl("main.js")` |
| `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/src/presentation/views/AssetUrl.scala` | Create | `AssetContext` + `AssetUrl` helper |
| `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/test/src/DevModeConfigTest.scala` | Create | Unit tests for `DevModeConfig.resolve` |
| `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/test/src/AssetUrlTest.scala` | Create | Unit tests for `AssetUrl.apply` (prod + dev variants) |
| `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/frontend/start-dev.sh` | Create | Convenience Vite launcher |
| `/home/mph/Devel/iw/iw-cli-IW-345/CLAUDE.md` | Modify | Tech stack, architecture tree, Dashboard development section |
| `/home/mph/Devel/iw/iw-cli-IW-345/README.md` | Modify | Dashboard contributor requirements |
| `/home/mph/Devel/iw/iw-cli-IW-345/test/dashboard-*.bats` (TBD path) | Create or modify | Dev-mode gate + jar launch + rebuild gate coverage |

## Testing strategy

### Unit (munit, under `./mill dashboard.test`)

- `DevModeConfigTest`:
  - `resolve(false, None) == Right(Off)` — flag off regardless of env.
  - `resolve(false, Some("http://localhost:5173")) == Right(Off)` — env-var alone never activates.
  - `resolve(true, None) == Right(Off)` — flag alone never activates (warning is a side effect at the edge, not tested here).
  - `resolve(true, Some("")) == Right(Off)` — empty string treated as unset.
  - `resolve(true, Some("http://localhost:5173")) == Right(On("http://localhost:5173"))`.
  - `resolve(true, Some("http://127.0.0.1:5173"))` — loopback IPv4 accepted.
  - `resolve(true, Some("http://::1:5173"))` — loopback IPv6 accepted (verify URI parser handles bracketed host correctly; may require `http://[::1]:5173` form — see CLARIFY DM-IPV6).
  - `resolve(true, Some("https://localhost:5173")) == Left(_)` — `https` rejected.
  - `resolve(true, Some("http://example.com:5173")) == Left(_)` — non-loopback host rejected.
  - `resolve(true, Some("http://10.0.0.5:5173")) == Left(_)` — private IP still non-loopback.
  - `resolve(true, Some("not a url")) == Left(_)` — malformed URI rejected.
- `AssetUrlTest`:
  - Given `AssetContext.prod`, `AssetUrl("main.js") == "/assets/main.js"`.
  - Given `AssetContext(DevModeConfig.On("http://localhost:5173"))`, `AssetUrl("main.js") == "http://localhost:5173/src/main.js"`.
  - Trailing slash on base URL: `AssetUrl("main.js") == "http://localhost:5173/src/main.js"` (no double slash).
  - Path with subdirectory: `AssetUrl("components/foo.js")` resolves correctly in both modes.

### Integration (new `./mill dashboard.itest` cases)

- Existing itest cases continue to assert `GET /` returns HTML referencing `/assets/main.js` — verify post-helper-refactor that the helper output is identical in prod mode to the Phase 3 hardcoded string.
- NEW: a constructor-level test that instantiates `CaskServer` with `devMode = DevModeConfig.On("http://localhost:5173")` and asserts the rendered `/` HTML contains `http://localhost:5173/src/main.js` (no `/assets/main.js`).

### E2E (BATS, at `test/dashboard-*.bats`)

Export `IW_SERVER_DISABLED=1` in `setup()` as per project convention.

- `dashboard-jar-launch.bats`: `./iw dashboard --help` exits 0; the `java -jar` path is reached (verify via a smoke-grep on `ps` output or by asserting the dashboard jar is newer after touching a Scala source — the rebuild gate).
- `dashboard-dev-gate.bats`:
  - `VITE_DEV_URL=http://localhost:5173 ./iw dashboard` (NO `--dev`) — the started server responds on `/` with HTML containing `/assets/main.js` (NOT the Vite URL).
  - `./iw dashboard --dev` (NO env var) — the server starts, WARNING logged to stderr, HTML contains `/assets/main.js`.
  - `VITE_DEV_URL=http://example.com:5173 ./iw dashboard --dev` — refuses to start, stderr contains the validation error, exit code non-zero.
  - `VITE_DEV_URL=https://localhost:5173 ./iw dashboard --dev` — refuses to start, stderr contains `scheme must be http`, exit code non-zero.
- `dashboard-rebuild-gate.bats`: touch `dashboard/jvm/src/CaskServer.scala` (or any dashboard source); run `./iw dashboard --help`; assert `build/iw-dashboard.jar`'s mtime moved forward. Then re-run `./iw dashboard --help` with no intervening source change; assert mtime unchanged (rebuild NOT triggered).
- `server-daemon-launch.bats` (may already exist for Phase 2 coverage): adapt to assert the daemon starts via `java -jar` path rather than `scala-cli run`. If this file doesn't exist, the existing lifecycle coverage in `test/` covers the path.

### Manual (release notes / CLAUDE.md)

- With `dashboard/frontend/start-dev.sh` running in one terminal and `VITE_DEV_URL=http://localhost:5173 ./iw dashboard --dev` in another:
  - Browser loads — network tab shows `http://localhost:5173/src/main.js` request.
  - Edit `dashboard/frontend/src/main.css` (add an obvious Tailwind class like `text-red-500` to a rule) — browser HMR fires, style applies without reload.
  - Edit `dashboard/jvm/src/presentation/views/PageLayout.scala` — no HMR (Scala is compiled by Mill, not Vite), but a jar rebuild + browser refresh shows the change.
- Without the Vite dev server running: `./iw dashboard --dev` with `VITE_DEV_URL=http://localhost:5173` set — server starts, browser opens, `/assets/main.js` request 404s visibly (or times out connecting to port 5173). This is the "loud failure in dev" contract; prod is unaffected because the gate requires `--dev`.

## Acceptance criteria

- [ ] `commands/dashboard.scala` no longer imports `iw.dashboard.*` or references `StateRepository` / `SampleDataGenerator` / `CaskServer`. `rg 'iw\.dashboard' commands/dashboard.scala` returns zero matches.
- [ ] `commands/dashboard.scala` contains no `//> using dep` lines for cask, scalatags, flexmark. `rg 'cask:|scalatags:|flexmark-all' commands/` returns zero matches.
- [ ] `commands/server-daemon.scala` is deleted (or shrunk to a `java -jar` shim; reviewer chooses).
- [ ] `core/adapters/ProcessManager.scala:spawnServerProcess` spawns `java -jar build/iw-dashboard.jar`; the string literal `"iw.dashboard.ServerDaemon"` is gone.
- [ ] `iw-run` declares `DASHBOARD_JAR` and exports `IW_DASHBOARD_JAR`. `dashboard_jar_stale` / `ensure_dashboard_jar` helpers exist and mirror the `core_jar_stale` / `ensure_core_jar` shape. `ensure_dashboard_jar` is invoked only on dashboard-launching command paths.
- [ ] `./iw dashboard --help` launches via the new jar path without NoClassDefFoundError; browser-open behavior preserved; `--state-path`, `--sample-data`, `--dev` flags all still parse.
- [ ] `./iw dashboard --dev` WITHOUT `VITE_DEV_URL` set logs a warning and serves bundled `/assets/*`; dev routing does NOT activate.
- [ ] `VITE_DEV_URL=http://example.com ./iw dashboard --dev` refuses to start with a clear stderr error citing the loopback-host requirement.
- [ ] `VITE_DEV_URL=https://localhost:5173 ./iw dashboard --dev` refuses to start with a clear stderr error citing the `http`-scheme requirement.
- [ ] `VITE_DEV_URL=http://localhost:5173 ./iw dashboard --dev` logs `⚠ Dev mode: serving assets from http://localhost:5173` on startup and the HTML served at `/` references `http://localhost:5173/src/main.js`.
- [ ] `VITE_DEV_URL=http://localhost:5173 ./iw dashboard` (no `--dev`) does NOT activate dev routing; HTML references `/assets/main.js`.
- [ ] `DevModeConfig.resolve` unit tests cover flag-off-env-on, flag-on-env-off, flag-on-env-on-valid, flag-on-env-on-invalid-scheme, flag-on-env-on-invalid-host, flag-on-env-on-malformed, and IPv6 loopback.
- [ ] `AssetUrl.apply` unit tests cover prod + dev paths, trailing-slash handling, subdirectory path segments.
- [ ] Touching a dashboard source file triggers a jar rebuild on the next `./iw dashboard ...` invocation; no source change means no rebuild (mtime observation).
- [ ] `CLAUDE.md` reflects the two-build-tool boundary and lists dashboard-dev prereqs. `README.md` lists dashboard contributor requirements distinct from end-user requirements.
- [ ] `dashboard/frontend/start-dev.sh` exists, is executable, and runs `yarn install --immutable && yarn dev --port 5173`.
- [ ] All unit tests (core + dashboard) and all integration tests still pass. `./iw ./test` (unit + itest + e2e) runs green.
- [ ] `iwCoreJar` either retired or shrunk to a core-only thin-jar wrapper; `build/iw-core.jar` no longer contains `iw.dashboard.*` classes (verify via `unzip -l build/iw-core.jar | grep dashboard`).
- [ ] No file violates the CLAUDE.md rules: PURPOSE header on new files, no temporal/historical naming, no emoji in code (the `⚠` character in the dev-mode log line is an exception since it's user-facing output, not code identifier — reviewer confirms).

## CLARIFY markers

- **CLARIFY SD** (sample data): the post-rewrite `commands/dashboard.scala` no longer has `StateRepository` / `SampleDataGenerator` on its classpath. Three options: (a) add a `SampleDataCli` entry point to the dashboard module invoked via `java -cp build/iw-dashboard.jar`, (b) extend `ServerDaemon` with a `--sample-data` flag that writes the state file before starting the HTTP listener, (c) let `CaskServer` auto-generate sample data on first start when the state file is absent. Default recommendation: (b) — smallest change, keeps sample-data concerns inside the dashboard module, no new CLI entry point.
- **CLARIFY DM-LOC** (DevModeConfig placement): `dashboard/jvm/src/DevModeConfig.scala` top-level, or nested under `dashboard/jvm/src/model/` (parity with core's `model/` convention)? The file has no I/O — it's pure. Recommendation: top-level for now; moved into a `model/` or `config/` subdirectory is a cheap follow-up if the dashboard module grows more pure-domain types.
- **CLARIFY DM-THREAD** (threading `AssetContext` through templates): `given` vs explicit parameter. Recommendation: `given` at the route boundary, `(using AssetContext)` on `PageLayout.render`. Reviewer can flip if implicit context is deemed too magical.
- **CLARIFY DM-IPV6** (IPv6 loopback URI): `java.net.URI.create("http://::1:5173")` may or may not parse the way we expect — bracketed form `http://[::1]:5173` is standard. Verify at implementation and adjust the test + validator accordingly.
- **CLARIFY AU-PATH** (Vite entry mapping): dev-mode `assetUrl("main.js")` returns `${VITE_DEV_URL}/src/main.js` assuming the Vite entry in `dashboard/frontend/vite.config.js` is `src/main.js`. That's the current config. If the entry changes, the helper needs to change in lockstep.
- **CLARIFY GATE-LOC** (rebuild gate placement): two options — (i) shell-side in `iw-run` parallel to `ensure_core_jar`, (ii) Scala-side inside `commands/dashboard.scala` via a pre-spawn `os.proc("./mill", "iwDashboardJar").call(...)`. Option (i) matches the Phase 1 pattern and keeps Scala scripts focused; pick (i) unless reviewer raises a concrete concern.
- **CLARIFY BATS-VS-SCALA** (test placement): the dev-mode gating logic is tested at the pure-function level in `DevModeConfigTest`; the startup-refusal + warning-output behavior is tested in BATS. Reviewer may argue for more Scala-side integration coverage (spinning up `CaskServer` with mocked env) over BATS. Recommendation: pure logic in Scala, user-facing behavior in BATS.
- **CLARIFY IWCOREJAR** (core jar shape post-Phase-4): with the launcher retired, `iwCoreJar`'s stage-and-repack becomes unnecessary. Minimal retirement: replace `iwCoreJar` with a simpler wrapper that copies `core.jar()` to `build/iw-core.jar` (core-only, no dashboard classes). The `iw-run` `build_core_jar()` call site stays the same name. Reviewer: confirm this is acceptable vs. dropping `build/iw-core.jar` on-disk contract entirely and teaching `iw-run` to resolve from `out/`.

## Review checklist

- [ ] **Dev-mode double-gate is non-bypassable.** Env-var-alone must never activate dev routing. The only path to `DevModeConfig.On` is through `resolve(devFlag = true, viteDevUrlEnv = Some(_))` with a validated URL. Verify by reading the single call site in `CaskServer.start`.
- [ ] **Loopback validation is airtight.** No `.contains("localhost")` substring checks — it's exact equality against `localhost` / `127.0.0.1` / `::1` (or the IPv6 bracketed variant). A URL like `http://localhost.example.com` must be rejected.
- [ ] **Scheme validation is airtight.** Only `http` passes. `https`, `file`, `ftp`, empty string, null scheme all refused.
- [ ] **Startup log line is loud.** `⚠ Dev mode: serving assets from <URL>` goes to stderr at INFO or WARN level on every dashboard start when dev mode is active — reviewer confirms it's hard to miss in the terminal output.
- [ ] **Refusal exit code is non-zero.** Validation failures `sys.exit(1)`, not `sys.exit(0)` with a warning; callers depend on the exit code.
- [ ] **No `iw.dashboard.*` references survive in `commands/`.** Grep confirms zero hits post-rewrite.
- [ ] **`ProcessManager.spawnServerProcess` has no remaining scala-cli indirection.** The command vector is `java -jar <jar>`; no `--main-class` flag, no `coreFiles` glob.
- [ ] **Rebuild gate is correctly scoped.** Non-dashboard commands (`./iw status`, `./iw worktrees`, etc.) do not incur a dashboard-jar rebuild. Verify by running one and observing no `Rebuilding dashboard jar...` line.
- [ ] **Sample-data generation still works.** `./iw dashboard --sample-data --state-path /tmp/foo.json` writes a state file; subsequent `./iw dashboard --state-path /tmp/foo.json` picks it up.
- [ ] **`build/iw-core.jar` is core-only post-retirement.** `unzip -l build/iw-core.jar | grep -c 'iw/dashboard/'` returns `0`.
- [ ] **`start-dev.sh` is executable and independent.** Running it without `./iw dashboard` running still boots Vite; running `iw dashboard --dev` without it doesn't activate dev routing gracefully (the warning-with-no-env-var branch or the loud-failure branch, depending on env state).
- [ ] **Docs mention two build tools.** CLAUDE.md + README sections reviewed by a contributor who has never seen the codebase — can they tell what tool they need for what task.

## Notes for reviewer

Phase 4 is mechanically small (a few hundred lines of churn) but contains the only user-visible behavior change in the issue: `iw dashboard` now starts a distinct JVM. Watch for:

1. **Classpath drift**: sample-data generation used to share the command's scala-cli classpath with the server; splitting processes means serialization of state goes through a jar-to-jar boundary. The `StateRepository` file format is unchanged, but any in-memory object passed across the boundary needs explicit serialization (we only persist via `write`/`read` to JSON files — no in-memory passthrough — so this should be a non-issue in practice).
2. **Dev-mode failure modes are user-facing.** A contributor running `iw dashboard --dev` without the env var set gets a warning and a working prod-mode dashboard — graceful. A contributor with a misconfigured `VITE_DEV_URL` gets a refusal — loud. Both are correct; verify neither silently produces the wrong behavior.
3. **The `iwCoreJar` retirement is subtle.** Phase 2 grew it into a combined jar specifically to make the scala-cli bridge work. Shrinking it back to core-only is correct now — but there must be zero `iw.dashboard.*` imports in scripts for it to work. The grep check in acceptance criteria is the guard.
4. **No new CI secrets or jobs.** Phase 4 doesn't change CI topology — the `dashboard-build` job added in Phase 3 continues to exercise the jar path. Local rebuild gate is a pure developer-experience feature; CI rebuilds from scratch every run.

If any of phases 1-3 left loose ends (e.g., flexmark still in `dashboard.mvnDeps`, test placement arguments), Phase 4 is the natural home for tidying — but scope strictly: a tidy-pass PR should go separately unless the change is blocking the rewrite.
