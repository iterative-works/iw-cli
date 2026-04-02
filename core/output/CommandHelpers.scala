// PURPOSE: Exit helpers for CLI commands to reduce boilerplate when handling Either/Option
// PURPOSE: Prints error to stderr and exits with code 1 on Left or None

package iw.core.output

object CommandHelpers:
  /** Unwrap a Right value, or print the Left error to stderr and exit 1. */
  def exitOnError[A](result: Either[String, A]): A =
    result match
      case Right(value) => value
      case Left(err)    =>
        Output.error(err)
        sys.exit(1)

  /** Unwrap a Some value, or print the provided error message to stderr and
    * exit 1.
    */
  def exitOnNone[A](option: Option[A], errorMsg: String): A =
    option match
      case Some(value) => value
      case None        =>
        Output.error(errorMsg)
        sys.exit(1)
