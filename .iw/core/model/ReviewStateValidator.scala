// PURPOSE: Pure validation of review-state.json against the formal schema
// PURPOSE: No I/O - takes JSON string, returns ValidationResult with errors and warnings

package iw.core.model

import upickle.default.*
import scala.collection.mutable.ListBuffer
import ujson.Value
import upickle.core.LinkedHashMap

/** Validates review-state.json content against the schema contract.
  *
  * All validation is pure (no I/O). The validator checks JSON syntax,
  * required fields, field types, nested object structure, and
  * additional property restrictions.
  */
object ReviewStateValidator:

  private val AllowedRootProperties: Set[String] = Set(
    "version", "issue_id", "status", "display", "badges", "task_lists",
    "needs_attention", "message", "artifacts", "available_actions",
    "last_updated", "pr_url", "git_sha", "phase_checkpoints"
  )

  private val RequiredFields: List[String] = List(
    "version", "issue_id", "artifacts", "last_updated"
  )

  private val ValidDisplayTypes: Set[String] = Set(
    "info", "success", "warning", "error", "progress"
  )

  def validate(json: String): ValidationResult =
    val errors = ListBuffer.empty[ValidationError]
    val warnings = ListBuffer.empty[String]

    val parsed = try
      Some(ujson.read(json))
    catch
      case e: Exception =>
        errors += ValidationError("root", s"Failed to parse JSON: ${e.getMessage}")
        None

    parsed.foreach { value =>
      if !value.objOpt.isDefined then
        errors += ValidationError("root", "Expected a JSON object at root")
      else
        val obj = value.obj
        validateRequiredFields(obj, errors)
        validateFieldTypes(obj, errors, warnings)
        validateOptionalFieldTypes(obj, errors)
        validateAdditionalProperties(obj, errors)
    }

    ValidationResult(errors.toList, warnings.toList)

  private def validateRequiredFields(
    obj: LinkedHashMap[String, Value],
    errors: ListBuffer[ValidationError]
  ): Unit =
    RequiredFields.foreach { field =>
      if !obj.contains(field) then
        errors += ValidationError(field, s"Required field '$field' is missing")
    }

  private def validateFieldTypes(
    obj: LinkedHashMap[String, Value],
    errors: ListBuffer[ValidationError],
    warnings: ListBuffer[String]
  ): Unit =
    // version: integer, minimum 1
    obj.get("version").foreach { v =>
      v.numOpt match
        case Some(n) =>
          if n != n.toInt || n.toInt < 1 then
            errors += ValidationError("version", "Field 'version' must be an integer >= 1")
        case None =>
          errors += ValidationError("version", "Field 'version' must be an integer")
    }

    // issue_id: string
    obj.get("issue_id").foreach { v =>
      if !v.strOpt.isDefined then
        errors += ValidationError("issue_id", "Field 'issue_id' must be a string")
    }

    // status: optional string (machine identifier, no validation of values)
    obj.get("status").foreach { v =>
      if !v.strOpt.isDefined then
        errors += ValidationError("status", "Field 'status' must be a string")
    }

    // artifacts: array of objects
    obj.get("artifacts").foreach { v =>
      if !v.arrOpt.isDefined then
        errors += ValidationError("artifacts", "Field 'artifacts' must be an array")
      else
        val arr = v.arr
        arr.zipWithIndex.foreach { case (item, idx) =>
          validateArtifact(item, idx, errors)
        }
    }

    // last_updated: string
    obj.get("last_updated").foreach { v =>
      if !v.strOpt.isDefined then
        errors += ValidationError("last_updated", "Field 'last_updated' must be a string")
    }

  private def validateArtifact(
    item: Value,
    index: Int,
    errors: ListBuffer[ValidationError]
  ): Unit =
    if !item.objOpt.isDefined then
      errors += ValidationError(s"artifacts[$index]", "Each artifact must be a JSON object")
    else
      val artifactObj = item.obj
      // Required fields: label, path
      if !artifactObj.contains("label") then
        errors += ValidationError(s"artifacts[$index].label", "Required field 'label' is missing in artifact")
      else if !artifactObj("label").strOpt.isDefined then
        errors += ValidationError(s"artifacts[$index].label", "Field 'label' must be a string in artifact")

      if !artifactObj.contains("path") then
        errors += ValidationError(s"artifacts[$index].path", "Required field 'path' is missing in artifact")
      else if !artifactObj("path").strOpt.isDefined then
        errors += ValidationError(s"artifacts[$index].path", "Field 'path' must be a string in artifact")

      // Optional: category (string)
      artifactObj.get("category").foreach { v =>
        if !v.strOpt.isDefined then
          errors += ValidationError(s"artifacts[$index].category", "Field 'category' must be a string in artifact")
      }

      // additionalProperties: false
      val allowedArtifactProps = Set("label", "path", "category")
      artifactObj.keys.foreach { key =>
        if !allowedArtifactProps.contains(key) then
          errors += ValidationError(s"artifacts[$index]", s"Unknown property '$key' in artifact")
      }

  private def validateOptionalFieldTypes(
    obj: LinkedHashMap[String, Value],
    errors: ListBuffer[ValidationError]
  ): Unit =
    // display: object with text, optional subtext, type enum
    obj.get("display").foreach { v =>
      if !v.objOpt.isDefined then
        errors += ValidationError("display", "Field 'display' must be an object")
      else
        validateDisplay(v.obj, errors)
    }

    // badges: array of objects
    obj.get("badges").foreach { v =>
      if !v.arrOpt.isDefined then
        errors += ValidationError("badges", "Field 'badges' must be an array")
      else
        val arr = v.arr
        arr.zipWithIndex.foreach { case (item, idx) =>
          validateBadge(item, idx, errors)
        }
    }

    // task_lists: array of objects
    obj.get("task_lists").foreach { v =>
      if !v.arrOpt.isDefined then
        errors += ValidationError("task_lists", "Field 'task_lists' must be an array")
      else
        val arr = v.arr
        arr.zipWithIndex.foreach { case (item, idx) =>
          validateTaskList(item, idx, errors)
        }
    }

    // needs_attention: boolean
    obj.get("needs_attention").foreach { v =>
      if !v.boolOpt.isDefined then
        errors += ValidationError("needs_attention", "Field 'needs_attention' must be a boolean")
    }

    // pr_url: string or null
    obj.get("pr_url").foreach { v =>
      val isStr = v.strOpt.isDefined
      val isNull = v.isNull
      if !isStr && !isNull then
        errors += ValidationError("pr_url", "Field 'pr_url' must be a string or null")
    }

    // git_sha: string
    obj.get("git_sha").foreach { v =>
      if !v.strOpt.isDefined then
        errors += ValidationError("git_sha", "Field 'git_sha' must be a string")
    }

    // message: string
    obj.get("message").foreach { v =>
      if !v.strOpt.isDefined then
        errors += ValidationError("message", "Field 'message' must be a string")
    }

    // phase_checkpoints: object with values containing context_sha
    obj.get("phase_checkpoints").foreach { v =>
      if !v.objOpt.isDefined then
        errors += ValidationError("phase_checkpoints", "Field 'phase_checkpoints' must be an object")
      else
        val checkpoints = v.obj
        checkpoints.foreach { case (key, cpValue) =>
          validatePhaseCheckpoint(cpValue, key, errors)
        }
    }

    // available_actions: array of objects
    obj.get("available_actions").foreach { v =>
      if !v.arrOpt.isDefined then
        errors += ValidationError("available_actions", "Field 'available_actions' must be an array")
      else
        val arr = v.arr
        arr.zipWithIndex.foreach { case (item, idx) =>
          validateAction(item, idx, errors)
        }
    }

  private def validatePhaseCheckpoint(
    item: Value,
    key: String,
    errors: ListBuffer[ValidationError]
  ): Unit =
    if !item.objOpt.isDefined then
      errors += ValidationError(s"phase_checkpoints.$key", "Each phase checkpoint must be a JSON object")
    else
      val cpObj = item.obj
      if !cpObj.contains("context_sha") then
        errors += ValidationError(s"phase_checkpoints.$key.context_sha", "Required field 'context_sha' is missing in phase checkpoint")
      else if !cpObj("context_sha").strOpt.isDefined then
        errors += ValidationError(s"phase_checkpoints.$key.context_sha", "Field 'context_sha' must be a string in phase checkpoint")

      // additionalProperties: false
      val allowedCheckpointProps = Set("context_sha")
      cpObj.keys.foreach { prop =>
        if !allowedCheckpointProps.contains(prop) then
          errors += ValidationError(s"phase_checkpoints.$key", s"Unknown property '$prop' in phase checkpoint")
      }

  private def validateAction(
    item: Value,
    index: Int,
    errors: ListBuffer[ValidationError]
  ): Unit =
    if !item.objOpt.isDefined then
      errors += ValidationError(s"available_actions[$index]", "Each action must be a JSON object")
    else
      val actionObj = item.obj
      val requiredActionFields = List("id", "label", "skill")
      requiredActionFields.foreach { field =>
        if !actionObj.contains(field) then
          errors += ValidationError(s"available_actions[$index].$field", s"Required field '$field' is missing in action")
        else if !actionObj(field).strOpt.isDefined then
          errors += ValidationError(s"available_actions[$index].$field", s"Field '$field' must be a string in action")
      }

      // additionalProperties: false
      val allowedActionProps = Set("id", "label", "skill")
      actionObj.keys.foreach { key =>
        if !allowedActionProps.contains(key) then
          errors += ValidationError(s"available_actions[$index]", s"Unknown property '$key' in action")
      }

  private def validateDisplay(
    obj: LinkedHashMap[String, Value],
    errors: ListBuffer[ValidationError]
  ): Unit =
    // Required: text, type
    if !obj.contains("text") then
      errors += ValidationError("display.text", "Required field 'text' is missing in display")
    else if !obj("text").strOpt.isDefined then
      errors += ValidationError("display.text", "Field 'text' must be a string in display")

    if !obj.contains("type") then
      errors += ValidationError("display.type", "Required field 'type' is missing in display")
    else if !obj("type").strOpt.isDefined then
      errors += ValidationError("display.type", "Field 'type' must be a string in display")
    else
      val typeValue = obj("type").str
      if !ValidDisplayTypes.contains(typeValue) then
        errors += ValidationError("display.type", s"Field 'type' must be one of: ${ValidDisplayTypes.mkString(", ")}")

    // Optional: subtext
    obj.get("subtext").foreach { v =>
      if !v.strOpt.isDefined then
        errors += ValidationError("display.subtext", "Field 'subtext' must be a string in display")
    }

    // additionalProperties: false
    val allowedDisplayProps = Set("text", "subtext", "type")
    obj.keys.foreach { key =>
      if !allowedDisplayProps.contains(key) then
        errors += ValidationError("display", s"Unknown property '$key' in display")
    }

  private def validateBadge(
    item: Value,
    index: Int,
    errors: ListBuffer[ValidationError]
  ): Unit =
    if !item.objOpt.isDefined then
      errors += ValidationError(s"badges[$index]", "Each badge must be a JSON object")
    else
      val badgeObj = item.obj
      // Required: label, type
      if !badgeObj.contains("label") then
        errors += ValidationError(s"badges[$index].label", "Required field 'label' is missing in badge")
      else if !badgeObj("label").strOpt.isDefined then
        errors += ValidationError(s"badges[$index].label", "Field 'label' must be a string in badge")

      if !badgeObj.contains("type") then
        errors += ValidationError(s"badges[$index].type", "Required field 'type' is missing in badge")
      else if !badgeObj("type").strOpt.isDefined then
        errors += ValidationError(s"badges[$index].type", "Field 'type' must be a string in badge")
      else
        val typeValue = badgeObj("type").str
        if !ValidDisplayTypes.contains(typeValue) then
          errors += ValidationError(s"badges[$index].type", s"Field 'type' must be one of: ${ValidDisplayTypes.mkString(", ")}")

      // additionalProperties: false
      val allowedBadgeProps = Set("label", "type")
      badgeObj.keys.foreach { key =>
        if !allowedBadgeProps.contains(key) then
          errors += ValidationError(s"badges[$index]", s"Unknown property '$key' in badge")
      }

  private def validateTaskList(
    item: Value,
    index: Int,
    errors: ListBuffer[ValidationError]
  ): Unit =
    if !item.objOpt.isDefined then
      errors += ValidationError(s"task_lists[$index]", "Each task list must be a JSON object")
    else
      val taskListObj = item.obj
      // Required: label, path
      if !taskListObj.contains("label") then
        errors += ValidationError(s"task_lists[$index].label", "Required field 'label' is missing in task list")
      else if !taskListObj("label").strOpt.isDefined then
        errors += ValidationError(s"task_lists[$index].label", "Field 'label' must be a string in task list")

      if !taskListObj.contains("path") then
        errors += ValidationError(s"task_lists[$index].path", "Required field 'path' is missing in task list")
      else if !taskListObj("path").strOpt.isDefined then
        errors += ValidationError(s"task_lists[$index].path", "Field 'path' must be a string in task list")

      // additionalProperties: false
      val allowedTaskListProps = Set("label", "path")
      taskListObj.keys.foreach { key =>
        if !allowedTaskListProps.contains(key) then
          errors += ValidationError(s"task_lists[$index]", s"Unknown property '$key' in task list")
      }

  private def validateAdditionalProperties(
    obj: LinkedHashMap[String, Value],
    errors: ListBuffer[ValidationError]
  ): Unit =
    obj.keys.foreach { key =>
      if !AllowedRootProperties.contains(key) then
        errors += ValidationError("root", s"Unknown property '$key' is not allowed")
    }
