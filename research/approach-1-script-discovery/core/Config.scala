// PURPOSE: Configuration loading stub for prototype
// PURPOSE: Provides basic config structure that can be expanded later

package iwcli.core

case class Config(
    tracker: Option[String] = None,
    projectName: Option[String] = None
)

object Config {

  /** Stub implementation - just returns empty config for now */
  def load(): Config = Config()
}
