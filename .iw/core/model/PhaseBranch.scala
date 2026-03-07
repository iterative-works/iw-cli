// PURPOSE: Phase branch value object for deriving phase sub-branch names
// PURPOSE: PhaseNumber opaque type ensures valid 1-99 phase numbers with zero-padded format

package iw.core.model

opaque type PhaseNumber = String

object PhaseNumber:
  /** Parse and validate a phase number string.
    * Accepts: "01", "1", "12" -- always normalizes to zero-padded two digits.
    * Rejects: "0", "100", "-1", "abc", ""
    */
  def parse(raw: String): Either[String, PhaseNumber] =
    raw.toIntOption match
      case None =>
        Left(s"Invalid phase number: '$raw' (expected a number between 1 and 99)")
      case Some(n) if n < 1 =>
        Left(s"Invalid phase number: $n (phases are 1-based, must be >= 1)")
      case Some(n) if n > 99 =>
        Left(s"Invalid phase number: $n (exceeds maximum two-digit phase number 99)")
      case Some(n) =>
        Right(f"$n%02d")

  extension (pn: PhaseNumber)
    def value: String = pn
    def toInt: Int = Integer.parseInt(pn)

case class PhaseBranch(featureBranch: String, phaseNumber: PhaseNumber):
  /** Full sub-branch name: "{featureBranch}-phase-{NN}" */
  def branchName: String = s"$featureBranch-phase-${phaseNumber.value}"
