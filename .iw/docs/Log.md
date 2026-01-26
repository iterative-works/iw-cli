# Log

> Simple logging abstraction with configurable debug output.

## Import

```scala
import iw.core.adapters.*
```

## API

### Log

```scala
case class Log(debugEnabled: Boolean):
  def debug(message: String): Unit  // Only shown when IW_DEBUG=1 or IW_DEBUG=true
  def info(message: String): Unit
  def warn(message: String): Unit
  def error(message: String): Unit
```

### Log companion object

```scala
object Log:
  def fromEnv(env: Map[String, String]): Log
  def fromSystemEnv(): Log
```

## Log Levels

- **debug**: Detailed debugging info (only shown when `IW_DEBUG=1` or `IW_DEBUG=true`)
- **info**: General information
- **warn**: Warning messages
- **error**: Error messages (written to stderr)

## Examples

```scala
// Create logger from system environment
val log = Log.fromSystemEnv()

// Log at different levels
log.debug("Detailed debugging info")  // Only shown when IW_DEBUG=1
log.info("General information")
log.warn("Warning message")
log.error("Error message")

// Create logger with custom environment (for testing)
val testLog = Log.fromEnv(Map("IW_DEBUG" -> "1"))
testLog.debug("This will be shown")

// Enable debug logging
// export IW_DEBUG=1
// ./iw some-command
```

## Notes

For user-facing CLI output, prefer `Output.*` functions which provide consistent formatting. Use `Log` for diagnostic/debugging output.
