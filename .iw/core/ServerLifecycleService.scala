// PURPOSE: Application service for server lifecycle pure business logic
// PURPOSE: Provides uptime formatting and status creation

package iw.core

import iw.core.domain.ServerState
import java.time.Instant
import java.time.Duration

object ServerLifecycleService:

  /** Format uptime duration as human-readable string */
  def formatUptime(startedAt: Instant, now: Instant): String =
    val duration = Duration.between(startedAt, now)
    val totalSeconds = duration.getSeconds

    if totalSeconds == 0 then
      return "0s"

    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    if days > 0 then
      s"${days}d ${hours}h"
    else if hours > 0 then
      s"${hours}h ${minutes}m"
    else if minutes > 0 then
      s"${minutes}m ${seconds}s"
    else
      s"${seconds}s"

  /** Format server hosts display message */
  def formatHostsDisplay(hosts: Seq[String], port: Int): String =
    if hosts.isEmpty then
      s"Server running on port $port"
    else
      val addresses = hosts.map(host => s"$host:$port").mkString(", ")
      s"Server running on $addresses"

  /** Create status from current server state and runtime information */
  def createStatus(
    state: ServerState,
    startedAt: Instant,
    pid: Long,
    port: Int
  ): ServerStatus =
    ServerStatus(
      running = true,
      port = port,
      worktreeCount = state.worktrees.size,
      startedAt = Some(startedAt),
      pid = Some(pid)
    )
