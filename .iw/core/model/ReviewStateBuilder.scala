// PURPOSE: Pure JSON construction for review-state.json from typed inputs
// PURPOSE: Builds valid JSON string without any I/O operations

package iw.core.model

object ReviewStateBuilder:

  case class BuildInput(
    version: Int = 2,
    issueId: String,
    lastUpdated: String,
    artifacts: List[(String, String)] = Nil,
    status: Option[String] = None,
    display: Option[(String, Option[String], String)] = None,
    badges: List[(String, String)] = Nil,
    taskLists: List[(String, String)] = Nil,
    needsAttention: Option[Boolean] = None,
    message: Option[String] = None,
    actions: List[(String, String, String)] = Nil,
    prUrl: Option[String] = None,
    gitSha: Option[String] = None,
    phaseCheckpoints: Map[String, String] = Map.empty
  )

  def build(input: BuildInput): String =
    val obj = ujson.Obj(
      "version" -> ujson.Num(input.version),
      "issue_id" -> ujson.Str(input.issueId),
      "artifacts" -> ujson.Arr(input.artifacts.map { case (label, path) =>
        ujson.Obj("label" -> ujson.Str(label), "path" -> ujson.Str(path))
      }*),
      "last_updated" -> ujson.Str(input.lastUpdated)
    )

    input.status.foreach(v => obj("status") = ujson.Str(v))

    input.display.foreach { case (text, subtext, displayType) =>
      val displayObj = ujson.Obj(
        "text" -> ujson.Str(text),
        "type" -> ujson.Str(displayType)
      )
      subtext.foreach(st => displayObj("subtext") = ujson.Str(st))
      obj("display") = displayObj
    }

    if input.badges.nonEmpty then
      obj("badges") = ujson.Arr(input.badges.map { case (label, badgeType) =>
        ujson.Obj("label" -> ujson.Str(label), "type" -> ujson.Str(badgeType))
      }*)

    if input.taskLists.nonEmpty then
      obj("task_lists") = ujson.Arr(input.taskLists.map { case (label, path) =>
        ujson.Obj("label" -> ujson.Str(label), "path" -> ujson.Str(path))
      }*)

    input.needsAttention.foreach(v => obj("needs_attention") = ujson.Bool(v))
    input.message.foreach(v => obj("message") = ujson.Str(v))

    if input.actions.nonEmpty then
      obj("available_actions") = ujson.Arr(input.actions.map { case (id, label, skill) =>
        ujson.Obj("id" -> ujson.Str(id), "label" -> ujson.Str(label), "skill" -> ujson.Str(skill))
      }*)

    input.prUrl.foreach(v => obj("pr_url") = ujson.Str(v))
    input.gitSha.foreach(v => obj("git_sha") = ujson.Str(v))

    if input.phaseCheckpoints.nonEmpty then
      val checkpoints = ujson.Obj()
      input.phaseCheckpoints.toList.sortBy(_._1).foreach { case (key, sha) =>
        checkpoints(key) = ujson.Obj("context_sha" -> ujson.Str(sha))
      }
      obj("phase_checkpoints") = checkpoints

    ujson.write(obj, indent = 2)
