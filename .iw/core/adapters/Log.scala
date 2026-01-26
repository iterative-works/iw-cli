// PURPOSE: Simple logging abstraction for debugging and diagnostics
// PURPOSE: Supports log levels with configurable debug output via IW_DEBUG environment variable
//
// Usage:
//   val log = Log.fromSystemEnv()
//   log.debug("Detailed debugging info")  // Only shown when IW_DEBUG=1 or IW_DEBUG=true
//   log.info("General information")
//   log.warn("Warning message")
//   log.error("Error message")

package iw.core.adapters

enum LogLevel:
  case Debug, Info, Warn, Error

case class Log(debugEnabled: Boolean):
  def debug(message: String): Unit =
    if debugEnabled then
      System.out.println(s"[DEBUG] $message")

  def info(message: String): Unit =
    System.out.println(s"[INFO] $message")

  def warn(message: String): Unit =
    System.out.println(s"[WARN] $message")

  def error(message: String): Unit =
    System.err.println(s"[ERROR] $message")

object Log:
  def fromEnv(env: Map[String, String]): Log =
    val debugEnabled = env.get("IW_DEBUG") match
      case Some("1") | Some("true") => true
      case _ => false
    Log(debugEnabled = debugEnabled)

  def fromSystemEnv(): Log =
    val env = sys.env
    fromEnv(env)
