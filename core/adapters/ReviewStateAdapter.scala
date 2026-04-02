// PURPOSE: Encapsulates the read/merge/validate/write sequence for review-state.json
// PURPOSE: Shared adapter used by phase commands to update review state atomically

package iw.core.adapters

import iw.core.model.{ReviewStateUpdater, ReviewStateValidator}

object ReviewStateAdapter:

  /** Update review-state.json by reading, merging, validating, and writing.
    *
    * @param path
    *   Path to review-state.json
    * @param update
    *   Update input to merge
    * @return
    *   Right(()) on success, Left(error) on failure
    */
  def update(
      path: os.Path,
      update: ReviewStateUpdater.UpdateInput
  ): Either[String, Unit] =
    for
      existingJson <- read(path)
      updatedJson = ReviewStateUpdater.merge(existingJson, update)
      validationResult = ReviewStateValidator.validate(updatedJson)
      _ <-
        if validationResult.isValid then Right(())
        else
          val errorMessages = validationResult.errors
            .map(e => s"  ${e.field}: ${e.message}")
            .mkString("\n")
          Left(s"Validation failed after merge:\n$errorMessages")
      _ <- writeFile(path, updatedJson)
    yield ()

  /** Read and parse review-state.json.
    *
    * @param path
    *   Path to review-state.json
    * @return
    *   Right(json string) on success, Left(error) if file missing or unreadable
    */
  def read(path: os.Path): Either[String, String] =
    try
      if !os.exists(path) then Left(s"File not found: $path")
      else Right(os.read(path))
    catch case e: Exception => Left(s"Failed to read $path: ${e.getMessage}")

  /** Read the pr_url field from review-state.json.
    *
    * @param path
    *   Path to review-state.json
    * @return
    *   Right(prUrl) on success, Left(error) if file missing, unreadable, or
    *   pr_url absent
    */
  def readPrUrl(path: os.Path): Either[String, String] =
    for
      json <- read(path)
      url <- extractPrUrl(json)
    yield url

  private def extractPrUrl(json: String): Either[String, String] =
    try
      import ujson.*
      val parsed = ujson.read(json)
      if parsed.obj.contains("pr_url") && !parsed("pr_url").isNull then
        Right(parsed("pr_url").str)
      else
        Left("No pr_url found in review-state.json. Run 'iw phase-pr' first.")
    catch
      case e: Exception =>
        Left(s"Failed to read pr_url from review-state.json: ${e.getMessage}")

  private def writeFile(path: os.Path, content: String): Either[String, Unit] =
    try
      os.write.over(path, content)
      Right(())
    catch case e: Exception => Left(s"Failed to write $path: ${e.getMessage}")
