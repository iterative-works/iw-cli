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

  private val KnownStatuses: Set[String] = Set(
    "analysis_ready", "context_ready", "tasks_ready", "implementing",
    "awaiting_review", "review_failed", "phase_merged",
    "refactoring_complete", "all_complete", "complete"
  )

  private val AllowedRootProperties: Set[String] = Set(
    "version", "issue_id", "status", "artifacts", "last_updated",
    "phase", "step", "branch", "pr_url", "git_sha", "message",
    "batch_mode", "phase_checkpoints", "available_actions"
  )

  private val RequiredFields: List[String] = List(
    "version", "issue_id", "status", "artifacts", "last_updated"
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

    // status: string, warn if not known
    obj.get("status").foreach { v =>
      if !v.strOpt.isDefined then
        errors += ValidationError("status", "Field 'status' must be a string")
      else
        val statusValue = v.str
        if !KnownStatuses.contains(statusValue) then
          warnings += s"Unknown status value '$statusValue'. Known values: ${KnownStatuses.toList.sorted.mkString(", ")}"
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

      // additionalProperties: false
      val allowedArtifactProps = Set("label", "path")
      artifactObj.keys.foreach { key =>
        if !allowedArtifactProps.contains(key) then
          errors += ValidationError(s"artifacts[$index]", s"Unknown property '$key' in artifact")
      }

  private def validateOptionalFieldTypes(
    obj: LinkedHashMap[String, Value],
    errors: ListBuffer[ValidationError]
  ): Unit =
    // phase: integer or string
    obj.get("phase").foreach { v =>
      val isInt = v.numOpt.isDefined && v.numOpt.exists(n => n == n.toInt)
      val isStr = v.strOpt.isDefined
      if !isInt && !isStr then
        errors += ValidationError("phase", "Field 'phase' must be an integer or string")
    }

    // step: string
    obj.get("step").foreach { v =>
      if !v.strOpt.isDefined then
        errors += ValidationError("step", "Field 'step' must be a string")
    }

    // branch: string
    obj.get("branch").foreach { v =>
      if !v.strOpt.isDefined then
        errors += ValidationError("branch", "Field 'branch' must be a string")
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

    // batch_mode: boolean
    obj.get("batch_mode").foreach { v =>
      if !v.boolOpt.isDefined then
        errors += ValidationError("batch_mode", "Field 'batch_mode' must be a boolean")
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

  private def validateAdditionalProperties(
    obj: LinkedHashMap[String, Value],
    errors: ListBuffer[ValidationError]
  ): Unit =
    obj.keys.foreach { key =>
      if !AllowedRootProperties.contains(key) then
        errors += ValidationError("root", s"Unknown property '$key' is not allowed")
    }
