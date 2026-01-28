// PURPOSE: Aggregate validation result with errors and warnings
// PURPOSE: Provides isValid check based on presence of errors

package iw.core.model

/** Aggregate result of validating a document against a schema.
  *
  * @param errors List of field-level errors (empty means valid)
  * @param warnings List of non-fatal warning messages
  */
case class ValidationResult(
  errors: List[ValidationError],
  warnings: List[String]
):
  def isValid: Boolean = errors.isEmpty
