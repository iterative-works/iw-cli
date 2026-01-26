# Constants

> Central location for environment variables, config keys, and paths.

## Import

```scala
import iw.core.model.*
```

## API

### Constants.EnvVars

Environment variable names:

```scala
object EnvVars:
  val LinearApiToken = "LINEAR_API_TOKEN"
  val YouTrackApiToken = "YOUTRACK_API_TOKEN"
  val IwHookClasses = "IW_HOOK_CLASSES"
  val IwCommandsDir = "IW_COMMANDS_DIR"
  val Tmux = "TMUX"
```

### Constants.ConfigKeys

HOCON configuration file keys:

```scala
object ConfigKeys:
  val TrackerType = "tracker.type"
  val TrackerTeam = "tracker.team"
  val TrackerBaseUrl = "tracker.baseUrl"
  val TrackerRepository = "tracker.repository"
  val TrackerTeamPrefix = "tracker.teamPrefix"
  val ProjectName = "project.name"
  val Version = "version"
```

### Constants.Paths

File paths and directory names:

```scala
object Paths:
  val IwDir = ".iw"
  val ConfigFileName = "config.conf"
  val ConfigFile = ".iw/config.conf"
```

### Constants.SystemProps

System property names:

```scala
object SystemProps:
  val UserDir = "user.dir"
```

### Constants.TrackerTypeValues

String values for tracker types:

```scala
object TrackerTypeValues:
  val Linear = "linear"
  val YouTrack = "youtrack"
  val GitHub = "github"
  val GitLab = "gitlab"
```

## Examples

```scala
// Reading API token from environment
ApiToken.fromEnv(Constants.EnvVars.LinearApiToken)

// Building config file path
val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"

// Getting current directory
val currentDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))

// Checking if inside tmux
sys.env.contains(Constants.EnvVars.Tmux)
```
