// PURPOSE: Data types for JSON output of phase lifecycle commands
// PURPOSE: StartOutput, CommitOutput, and PrOutput serialize to pretty-printed JSON

package iw.core.model

object PhaseOutput:

  /** Output of `phase-start` command */
  case class StartOutput(
    issueId: String,
    phaseNumber: String,
    branch: String,
    baselineSha: String
  ):
    def toJson: String =
      val obj = ujson.Obj(
        "issueId"     -> ujson.Str(issueId),
        "phaseNumber" -> ujson.Str(phaseNumber),
        "branch"      -> ujson.Str(branch),
        "baselineSha" -> ujson.Str(baselineSha)
      )
      ujson.write(obj, indent = 2)

  /** Output of `phase-commit` command */
  case class CommitOutput(
    issueId: String,
    phaseNumber: String,
    commitSha: String,
    filesCommitted: Int,
    message: String
  ):
    def toJson: String =
      val obj = ujson.Obj(
        "issueId"        -> ujson.Str(issueId),
        "phaseNumber"    -> ujson.Str(phaseNumber),
        "commitSha"      -> ujson.Str(commitSha),
        "filesCommitted" -> ujson.Num(filesCommitted),
        "message"        -> ujson.Str(message)
      )
      ujson.write(obj, indent = 2)

  /** Output of `phase-pr` command */
  case class PrOutput(
    issueId: String,
    phaseNumber: String,
    prUrl: String,
    headBranch: String,
    baseBranch: String,
    merged: Boolean
  ):
    def toJson: String =
      val obj = ujson.Obj(
        "issueId"     -> ujson.Str(issueId),
        "phaseNumber" -> ujson.Str(phaseNumber),
        "prUrl"       -> ujson.Str(prUrl),
        "headBranch"  -> ujson.Str(headBranch),
        "baseBranch"  -> ujson.Str(baseBranch),
        "merged"      -> ujson.Bool(merged)
      )
      ujson.write(obj, indent = 2)
