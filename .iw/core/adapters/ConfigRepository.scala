// PURPOSE: Configuration file repository for HOCON config persistence
// PURPOSE: Handles reading and writing ProjectConfiguration to/from .iw/config.conf

package iw.core.adapters

import iw.core.model.{ProjectConfiguration, ConfigSerializer}

import scala.util.Try

object ConfigFileRepository:
  def write(path: os.Path, config: ProjectConfiguration): Unit =
    // Create parent directories if they don't exist
    os.makeDir.all(path / os.up)

    // Serialize to HOCON and write to file
    val hocon = ConfigSerializer.toHocon(config)
    os.write.over(path, hocon)

  def read(path: os.Path): Option[ProjectConfiguration] =
    Try {
      if os.exists(path) then
        val hocon = os.read(path)
        ConfigSerializer.fromHocon(hocon).toOption
      else
        None
    }.toOption.flatten
