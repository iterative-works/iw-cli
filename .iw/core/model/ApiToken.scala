// PURPOSE: Secure API token wrapper with masked toString to prevent accidental logging
// PURPOSE: Case class wrapper provides type safety and prevents accidental exposure

package iw.core.model

/** API token wrapper that masks the value in toString to prevent accidental logging.
  * The actual value is private and only accessible via the `value` method.
  */
final case class ApiToken private (private val rawValue: String):
  /** Get the actual token value - use only when sending to API */
  def value: String = rawValue

  /** Check if token is empty (should never be true for valid ApiToken) */
  def isEmpty: Boolean = rawValue.isEmpty

  /** Masked string representation safe for logging */
  override def toString: String =
    val len = rawValue.length
    if len <= 4 then
      "ApiToken(***)"
    else
      val prefix = rawValue.take(4)
      s"ApiToken($prefix***)"

object ApiToken:
  /** Create an ApiToken from a string, returning None if empty or whitespace-only */
  def apply(raw: String): Option[ApiToken] =
    val trimmed = raw.trim
    if trimmed.isEmpty then None
    else Some(new ApiToken(trimmed))

  /** Read an ApiToken from an environment variable */
  def fromEnv(envVar: String): Option[ApiToken] =
    sys.env.get(envVar).flatMap(apply)
