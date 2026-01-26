// PURPOSE: Application service for server lifecycle pure business logic
// PURPOSE: Provides uptime formatting and status creation

package iw.core.dashboard

import iw.core.model.{ServerState, ServerStatus, SecurityAnalysis}
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

  /** Format security warning message for non-localhost server bindings */
  def formatSecurityWarning(analysis: SecurityAnalysis): Option[String] =
    if !analysis.hasWarning then
      None
    else if analysis.bindsToAll then
      val hosts = analysis.exposedHosts.mkString(", ")
      Some(s"⚠️  WARNING: Server is accessible from all network interfaces ($hosts)\n   Ensure your firewall is properly configured.")
    else
      val hosts = analysis.exposedHosts.mkString(", ")
      Some(s"⚠️  WARNING: Server is accessible from non-localhost interfaces: $hosts\n   Ensure your firewall is properly configured.")
