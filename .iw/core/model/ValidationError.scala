// PURPOSE: Domain model for field-level validation errors
// PURPOSE: Used by validators to report specific field problems

package iw.core.model

/** A single field-level validation error.
  *
  * @param field Path to the field that failed validation (e.g., "version", "artifacts[0].label")
  * @param message Human-readable description of the validation failure
  */
case class ValidationError(field: String, message: String)
