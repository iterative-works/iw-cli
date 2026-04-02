# ConfigRepository

> Configuration file repository for HOCON config persistence.

## Import

```scala
import iw.core.adapters.*
```

## API

### ConfigFileRepository.write(path: os.Path, config: ProjectConfiguration): Unit

Write a ProjectConfiguration to a HOCON file. Creates parent directories if needed.

### ConfigFileRepository.read(path: os.Path): Option[ProjectConfiguration]

Read a ProjectConfiguration from a HOCON file. Returns None if file doesn't exist or is invalid.

## Examples

```scala
// From issue.scala - loading configuration
val configPath = os.Path(System.getProperty(Constants.SystemProps.UserDir)) /
  Constants.Paths.IwDir / "config.conf"

ConfigFileRepository.read(configPath) match
  case Some(config) => Right(config)
  case None => Left("Configuration file not found. Run 'iw init' first.")

// From start.scala - reading config
val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"
val config = ConfigFileRepository.read(configPath) match
  case None =>
    Output.error("Cannot read configuration")
    Output.info("Run './iw init' to initialize the project")
    sys.exit(1)
  case Some(c) => c

// Writing configuration (typically in init command)
val config = ProjectConfiguration(
  trackerType = IssueTrackerType.GitHub,
  team = "",
  projectName = "my-project",
  repository = Some("owner/repo"),
  teamPrefix = Some("PROJ")
)
ConfigFileRepository.write(configPath, config)
```
