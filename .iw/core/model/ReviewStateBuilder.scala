// PURPOSE: Pure JSON construction for review-state.json from typed inputs
// PURPOSE: Builds valid JSON string without any I/O operations

package iw.core.model

object ReviewStateBuilder:

  case class BuildInput(
    version: Int = 1,
    issueId: String,
    status: String,
    lastUpdated: String,
    artifacts: List[(String, String)] = Nil,
    phase: Option[Either[Int, String]] = None,
    step: Option[String] = None,
    branch: Option[String] = None,
    prUrl: Option[String] = None,
    gitSha: Option[String] = None,
    message: Option[String] = None,
    batchMode: Option[Boolean] = None,
    phaseCheckpoints: Map[String, String] = Map.empty,
    actions: List[(String, String, String)] = Nil
  )

  def build(input: BuildInput): String =
    val obj = ujson.Obj(
      "version" -> ujson.Num(input.version),
      "issue_id" -> ujson.Str(input.issueId),
      "status" -> ujson.Str(input.status),
      "artifacts" -> ujson.Arr(input.artifacts.map { case (label, path) =>
        ujson.Obj("label" -> ujson.Str(label), "path" -> ujson.Str(path))
      }*),
      "last_updated" -> ujson.Str(input.lastUpdated)
    )

    input.phase.foreach {
      case Left(n)  => obj("phase") = ujson.Num(n)
      case Right(s) => obj("phase") = ujson.Str(s)
    }

    input.step.foreach(v => obj("step") = ujson.Str(v))
    input.branch.foreach(v => obj("branch") = ujson.Str(v))
    input.prUrl.foreach(v => obj("pr_url") = ujson.Str(v))
    input.gitSha.foreach(v => obj("git_sha") = ujson.Str(v))
    input.message.foreach(v => obj("message") = ujson.Str(v))
    input.batchMode.foreach(v => obj("batch_mode") = ujson.Bool(v))

    if input.phaseCheckpoints.nonEmpty then
      val checkpoints = ujson.Obj()
      input.phaseCheckpoints.toList.sortBy(_._1).foreach { case (key, sha) =>
        checkpoints(key) = ujson.Obj("context_sha" -> ujson.Str(sha))
      }
      obj("phase_checkpoints") = checkpoints

    if input.actions.nonEmpty then
      obj("available_actions") = ujson.Arr(input.actions.map { case (id, label, skill) =>
        ujson.Obj("id" -> ujson.Str(id), "label" -> ujson.Str(label), "skill" -> ujson.Str(skill))
      }*)

    ujson.write(obj, indent = 2)
