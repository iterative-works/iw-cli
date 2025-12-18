// PURPOSE: Central location for all magic strings used across iw-cli
// PURPOSE: Groups constants by category (env vars, config keys, paths, etc.)

package iw.core

object Constants:

  /** Environment variable names */
  object EnvVars:
    val LinearApiToken = "LINEAR_API_TOKEN"
    val YouTrackApiToken = "YOUTRACK_API_TOKEN"
    val IwHookClasses = "IW_HOOK_CLASSES"
    val Tmux = "TMUX"

  /** Configuration file keys in HOCON format */
  object ConfigKeys:
    val TrackerType = "tracker.type"
    val TrackerTeam = "tracker.team"
    val TrackerBaseUrl = "tracker.baseUrl"
    val ProjectName = "project.name"
    val Version = "version"

  /** File paths and directory names */
  object Paths:
    val ConfigFile = ".iw/config.conf"
    val IwDir = ".iw"

  /** System property names */
  object SystemProps:
    val UserDir = "user.dir"

  /** Scala reflection constants */
  object ScalaReflection:
    val ModuleField = "MODULE$"

  /** Tracker type string values */
  object TrackerTypeValues:
    val Linear = "linear"
    val YouTrack = "youtrack"

  /** Character encoding names */
  object Encoding:
    val Utf8 = "UTF-8"

  // Linear team ID for IWLE (iw-cli project)
  val IwCliTeamId: String = "cf2767bc-3458-44ca-87a8-f2a512ed2b7d"
