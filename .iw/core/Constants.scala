// PURPOSE: Central location for all magic strings used across iw-cli
// PURPOSE: Groups constants by category (env vars, config keys, paths, etc.)

package iw.core

object Constants:

  /** Environment variable names */
  object EnvVars:
    val LinearApiToken = "LINEAR_API_TOKEN"
    val YouTrackApiToken = "YOUTRACK_API_TOKEN"
    val IwHookClasses = "IW_HOOK_CLASSES"
    val IwCommandsDir = "IW_COMMANDS_DIR"
    val Tmux = "TMUX"

  /** Configuration file keys in HOCON format */
  object ConfigKeys:
    val TrackerType = "tracker.type"
    val TrackerTeam = "tracker.team"
    val TrackerBaseUrl = "tracker.baseUrl"
    val TrackerRepository = "tracker.repository"
    val TrackerTeamPrefix = "tracker.teamPrefix"
    val ProjectName = "project.name"
    val Version = "version"

  /** File paths and directory names */
  object Paths:
    val IwDir = ".iw"
    val ConfigFileName = "config.conf"
    val ConfigFile = s"$IwDir/$ConfigFileName"  // For display purposes

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
    val GitHub = "github"
    val GitLab = "gitlab"

  /** Character encoding names */
  object Encoding:
    val Utf8 = "UTF-8"

  // Linear team ID for IWLE (iw-cli project)
  val IwCliTeamId: String = "cf2767bc-3458-44ca-87a8-f2a512ed2b7d"

  /** Linear label IDs for iw-cli feedback command */
  object IwCliLabels:
    val Bug: String = "ec3254d4-dfd5-46b0-8c07-67a0d1fe482c"
    val Feature: String = "c004bc36-9272-43b3-93f2-38337a8c950e"

  /** Feedback command configuration - always targets iw-cli repository */
  object Feedback:
    val Repository: String = "iterative-works/iw-cli"
