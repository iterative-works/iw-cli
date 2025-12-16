// PURPOSE: Configuration file repository for HOCON config persistence
// PURPOSE: Handles reading and writing ProjectConfiguration to/from .iw/config.conf

package iw.core

import java.nio.file.{Path, Files}
import scala.util.Try

object ConfigFileRepository:
  def write(path: Path, config: ProjectConfiguration): Unit =
    // Create parent directories if they don't exist
    Files.createDirectories(path.getParent)

    // Serialize to HOCON and write to file
    val hocon = ConfigSerializer.toHocon(config)
    Files.writeString(path, hocon)

  def read(path: Path): Option[ProjectConfiguration] =
    Try {
      if Files.exists(path) then
        val hocon = Files.readString(path)
        ConfigSerializer.fromHocon(hocon).toOption
      else
        None
    }.toOption.flatten
