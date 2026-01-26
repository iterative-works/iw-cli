# ServerTypes

> Domain models for dashboard server configuration and status.

## Import

```scala
import iw.core.model.*
```

## API

### ServerConfig

```scala
case class ServerConfig(port: Int, hosts: Seq[String] = Seq("localhost"))

object ServerConfig:
  val DefaultPort: Int = 9876
  val MinPort: Int = 1024
  val MaxPort: Int = 65535

  def validate(port: Int): Either[String, Int]
  def validateHost(host: String): Either[String, String]
  def validateHosts(hosts: Seq[String]): Either[String, Seq[String]]
  def create(port: Int): Either[String, ServerConfig]
  def create(port: Int, hosts: Seq[String]): Either[String, ServerConfig]
  def default: ServerConfig
  def isLocalhostVariant(host: String): Boolean
  def analyzeHostsSecurity(hosts: Seq[String]): SecurityAnalysis
```

### SecurityAnalysis

```scala
case class SecurityAnalysis(
  exposedHosts: Seq[String],
  bindsToAll: Boolean,
  hasWarning: Boolean
)
```

### ServerStatus

```scala
case class ServerStatus(
  running: Boolean,
  port: Int,
  worktreeCount: Int,
  startedAt: Option[Instant],
  pid: Option[Long]
)
```

### ServerState

```scala
case class ServerState(
  worktrees: Map[String, WorktreeRegistration],
  issueCache: Map[String, CachedIssue] = Map.empty,
  progressCache: Map[String, CachedProgress] = Map.empty,
  prCache: Map[String, CachedPR] = Map.empty,
  reviewStateCache: Map[String, CachedReviewState] = Map.empty
):
  def listByActivity: List[WorktreeRegistration]
  def removeWorktree(issueId: String): ServerState
```

### CacheConfig

```scala
case class CacheConfig(env: Map[String, String]):
  def issueCacheTTL: Int   // Minutes, default 30
  def prCacheTTL: Int      // Minutes, default 15

object CacheConfig:
  val Default: CacheConfig
```

Configurable via environment variables:
- `IW_ISSUE_CACHE_TTL_MINUTES`
- `IW_PR_CACHE_TTL_MINUTES`

### DeletionSafety

```scala
case class DeletionSafety(
  hasUncommittedChanges: Boolean,
  isActiveSession: Boolean
)

object DeletionSafety:
  def isSafe(safety: DeletionSafety): Boolean
```

## Examples

```scala
// Create server config with validation
ServerConfig.create(9876) match
  case Right(config) => startServer(config)
  case Left(error) => Output.error(error)

// Check port range
ServerConfig.validate(8080)  // Right(8080)
ServerConfig.validate(80)    // Left("Port must be between 1024...")

// Analyze host security
val analysis = ServerConfig.analyzeHostsSecurity(Seq("localhost", "0.0.0.0"))
if analysis.hasWarning then
  Output.warning("Server exposed to network")
```
