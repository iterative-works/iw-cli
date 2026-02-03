// PURPOSE: Pure merge logic for updating existing review-state.json with partial updates
// PURPOSE: Handles scalar field updates, object merges, and array operations (replace/append/clear)

package iw.core.model

object ReviewStateUpdater:

  case class UpdateInput(
    // Scalar fields (provided value replaces existing)
    status: Option[String] = None,
    message: Option[String] = None,
    needsAttention: Option[Boolean] = None,
    prUrl: Option[String] = None,
    gitSha: Option[String] = None,

    // Display object fields (merge individual properties)
    displayText: Option[String] = None,
    displaySubtext: Option[String] = None,
    displayType: Option[String] = None,
    clearDisplaySubtext: Boolean = false,

    // Array fields with merge modes
    artifacts: Option[List[(String, String, Option[String])]] = None,  // (label, path, category)
    artifactsMode: ArrayMergeMode = ArrayMergeMode.Replace,

    badges: Option[List[(String, String)]] = None,  // (label, type)
    badgesMode: ArrayMergeMode = ArrayMergeMode.Replace,

    taskLists: Option[List[(String, String)]] = None,  // (label, path)
    taskListsMode: ArrayMergeMode = ArrayMergeMode.Replace,

    actions: Option[List[(String, String, String)]] = None,  // (id, label, skill)
    actionsMode: ArrayMergeMode = ArrayMergeMode.Replace,

    phaseCheckpoints: Option[Map[String, String]] = None,
    phaseCheckpointsMode: ArrayMergeMode = ArrayMergeMode.Replace,

    // Clear flags for optional fields
    clearStatus: Boolean = false,
    clearMessage: Boolean = false,
    clearNeedsAttention: Boolean = false,
    clearPrUrl: Boolean = false,
    clearDisplay: Boolean = false
  )

  enum ArrayMergeMode:
    case Replace  // Replace entire array with provided values
    case Append   // Add provided values to existing array
    case Clear    // Remove all values (provided values ignored)

  /**
   * Merge updates into existing review-state JSON.
   *
   * Rules:
   * - Scalar fields: provided value replaces existing
   * - Display object: merge individual properties
   * - Arrays: behavior depends on mode (replace/append/clear)
   * - last_updated: always updated to current timestamp
   * - version, issue_id: always preserved
   * - git_sha: preserved unless explicitly provided in update
   */
  def merge(existingJson: String, update: UpdateInput): String =
    val existing = ujson.read(existingJson)
    val currentTimestamp = java.time.Instant.now().toString

    // Always update last_updated
    existing("last_updated") = ujson.Str(currentTimestamp)

    // Update scalar fields
    update.status.foreach { v =>
      if update.clearStatus then existing.obj.remove("status")
      else existing("status") = ujson.Str(v)
    }
    if update.clearStatus && update.status.isEmpty then existing.obj.remove("status")

    update.message.foreach { v =>
      if update.clearMessage then existing.obj.remove("message")
      else existing("message") = ujson.Str(v)
    }
    if update.clearMessage && update.message.isEmpty then existing.obj.remove("message")

    update.needsAttention.foreach { v =>
      if update.clearNeedsAttention then existing.obj.remove("needs_attention")
      else existing("needs_attention") = ujson.Bool(v)
    }
    if update.clearNeedsAttention && update.needsAttention.isEmpty then existing.obj.remove("needs_attention")

    update.prUrl.foreach { v =>
      if update.clearPrUrl then existing.obj.remove("pr_url")
      else existing("pr_url") = ujson.Str(v)
    }
    if update.clearPrUrl && update.prUrl.isEmpty then existing.obj.remove("pr_url")

    update.gitSha.foreach(v => existing("git_sha") = ujson.Str(v))

    // Update display object (merge individual properties)
    if update.clearDisplay then
      existing.obj.remove("display")
    else if update.displayText.isDefined || update.displaySubtext.isDefined || update.displayType.isDefined || update.clearDisplaySubtext then
      val displayObj = existing.obj.get("display") match
        case Some(obj: ujson.Obj) => obj
        case _ => ujson.Obj()

      update.displayText.foreach(v => displayObj("text") = ujson.Str(v))
      update.displayType.foreach(v => displayObj("type") = ujson.Str(v))

      if update.clearDisplaySubtext then
        displayObj.obj.remove("subtext")
      else
        update.displaySubtext.foreach(v => displayObj("subtext") = ujson.Str(v))

      existing("display") = displayObj

    // Update array fields based on mode
    update.artifacts.foreach { values =>
      mergeArray(existing, "artifacts", values, update.artifactsMode) { case (label, path, category) =>
        val obj = ujson.Obj("label" -> ujson.Str(label), "path" -> ujson.Str(path))
        category.foreach(c => obj("category") = ujson.Str(c))
        obj
      }
    }

    update.badges.foreach { values =>
      mergeArray(existing, "badges", values, update.badgesMode) { case (label, badgeType) =>
        ujson.Obj("label" -> ujson.Str(label), "type" -> ujson.Str(badgeType))
      }
    }

    update.taskLists.foreach { values =>
      mergeArray(existing, "task_lists", values, update.taskListsMode) { case (label, path) =>
        ujson.Obj("label" -> ujson.Str(label), "path" -> ujson.Str(path))
      }
    }

    update.actions.foreach { values =>
      mergeArray(existing, "available_actions", values, update.actionsMode) { case (id, label, skill) =>
        ujson.Obj("id" -> ujson.Str(id), "label" -> ujson.Str(label), "skill" -> ujson.Str(skill))
      }
    }

    update.phaseCheckpoints.foreach { values =>
      update.phaseCheckpointsMode match
        case ArrayMergeMode.Clear =>
          existing.obj.remove("phase_checkpoints")
        case ArrayMergeMode.Replace =>
          if values.nonEmpty then
            val checkpoints = ujson.Obj()
            values.toList.sortBy(_._1).foreach { case (key, sha) =>
              checkpoints(key) = ujson.Obj("context_sha" -> ujson.Str(sha))
            }
            existing("phase_checkpoints") = checkpoints
          else
            existing.obj.remove("phase_checkpoints")
        case ArrayMergeMode.Append =>
          val checkpoints = existing.obj.get("phase_checkpoints") match
            case Some(obj: ujson.Obj) => obj
            case _ => ujson.Obj()
          values.foreach { case (key, sha) =>
            checkpoints(key) = ujson.Obj("context_sha" -> ujson.Str(sha))
          }
          existing("phase_checkpoints") = checkpoints
    }

    ujson.write(existing, indent = 2)

  private def mergeArray[T](
    existing: ujson.Value,
    fieldName: String,
    values: List[T],
    mode: ArrayMergeMode
  )(toJson: T => ujson.Value): Unit =
    mode match
      case ArrayMergeMode.Clear =>
        if existing.obj.contains(fieldName) then
          existing(fieldName) = ujson.Arr()
      case ArrayMergeMode.Replace =>
        if values.nonEmpty then
          existing(fieldName) = ujson.Arr(values.map(toJson)*)
        else if existing.obj.contains(fieldName) then
          existing(fieldName) = ujson.Arr()
      case ArrayMergeMode.Append =>
        val existingArr = existing.obj.get(fieldName) match
          case Some(arr: ujson.Arr) => arr.arr
          case _ => collection.mutable.ArrayBuffer.empty[ujson.Value]
        existingArr ++= values.map(toJson)
        existing(fieldName) = ujson.Arr(existingArr.toSeq*)
