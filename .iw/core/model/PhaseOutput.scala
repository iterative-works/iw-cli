// PURPOSE: Data types for JSON output of phase lifecycle commands
// PURPOSE: StartOutput, CommitOutput, PrOutput, and AdvanceOutput serialize to pretty-printed JSON

package iw.core.model

object PhaseOutput:

  /** Serialize key-value pairs to pretty-printed JSON */
  private def toJson(fields: (String, ujson.Value)*): String =
    ujson.write(ujson.Obj.from(fields), indent = 2)

  /** Output of `phase-start` command */
  case class StartOutput(
    issueId: String,
    phaseNumber: String,
    branch: String,
    baselineSha: String
  ):
    def toJson: String = PhaseOutput.toJson(
      "issueId"     -> ujson.Str(issueId),
      "phaseNumber" -> ujson.Str(phaseNumber),
      "branch"      -> ujson.Str(branch),
      "baselineSha" -> ujson.Str(baselineSha)
    )

  /** Output of `phase-commit` command */
  case class CommitOutput(
    issueId: String,
    phaseNumber: String,
    commitSha: String,
    filesCommitted: Int,
    message: String
  ):
    def toJson: String = PhaseOutput.toJson(
      "issueId"        -> ujson.Str(issueId),
      "phaseNumber"    -> ujson.Str(phaseNumber),
      "commitSha"      -> ujson.Str(commitSha),
      "filesCommitted" -> ujson.Num(filesCommitted),
      "message"        -> ujson.Str(message)
    )

  /** Output of `phase-pr` command */
  case class PrOutput(
    issueId: String,
    phaseNumber: String,
    prUrl: String,
    headBranch: String,
    baseBranch: String,
    merged: Boolean
  ):
    def toJson: String = PhaseOutput.toJson(
      "issueId"     -> ujson.Str(issueId),
      "phaseNumber" -> ujson.Str(phaseNumber),
      "prUrl"       -> ujson.Str(prUrl),
      "headBranch"  -> ujson.Str(headBranch),
      "baseBranch"  -> ujson.Str(baseBranch),
      "merged"      -> ujson.Bool(merged)
    )

  /** Output of `phase-advance` command */
  case class AdvanceOutput(
    issueId: String,
    phaseNumber: String,
    branch: String,
    previousBranch: String,
    headSha: String
  ):
    def toJson: String = PhaseOutput.toJson(
      "issueId"        -> ujson.Str(issueId),
      "phaseNumber"    -> ujson.Str(phaseNumber),
      "branch"         -> ujson.Str(branch),
      "previousBranch" -> ujson.Str(previousBranch),
      "headSha"        -> ujson.Str(headSha)
    )
