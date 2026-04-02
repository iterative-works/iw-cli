# ApiToken

> Secure API token wrapper with masked toString to prevent accidental logging.

## Import

```scala
import iw.core.model.*
```

## API

### ApiToken

```scala
final case class ApiToken private (private val rawValue: String):
  def value: String       // Get actual token value (use only when sending to API)
  def isEmpty: Boolean    // Check if token is empty
  override def toString: String  // Masked: "ApiToken(abc1***)"
```

### ApiToken companion object

```scala
object ApiToken:
  def apply(raw: String): Option[ApiToken]
  def fromEnv(envVar: String): Option[ApiToken]
```

## Examples

```scala
// From issue.scala - reading Linear token
ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
  case None =>
    Output.error(s"${Constants.EnvVars.LinearApiToken} environment variable is not set")
    sys.exit(1)
  case Some(token) =>
    LinearClient.fetchIssue(issueId, token)

// Token is safe to log (masked automatically)
println(token)  // "ApiToken(abc1***)"

// Access actual value only when needed for API calls
request.header("Authorization", token.value)
```
