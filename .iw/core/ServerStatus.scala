// PURPOSE: Server status domain model for runtime information
// PURPOSE: Represents current server state including port, worktrees, and uptime

package iw.core

import upickle.default.*
import java.time.Instant

case class ServerStatus(
  running: Boolean,
  port: Int,
  worktreeCount: Int,
  startedAt: Option[Instant],
  pid: Option[Long]
)

object ServerStatus:
  given ReadWriter[Instant] = readwriter[String].bimap[Instant](
    instant => instant.toString,
    str => Instant.parse(str)
  )

  given ReadWriter[ServerStatus] = macroRW
