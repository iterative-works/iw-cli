// PURPOSE: Validate a review-state.json file against the formal schema
// PURPOSE: Reports errors and warnings, exits 0 if valid, 1 if invalid
// USAGE: iw validate-review-state <file-path>
// USAGE: iw validate-review-state --stdin
// ARGS:
//   file-path: Path to the review-state.json file to validate
//   --stdin: Read JSON from standard input instead of a file
// EXAMPLE: iw validate-review-state project-management/issues/IW-42/review-state.json
// EXAMPLE: cat review-state.json | iw validate-review-state --stdin

import iw.core.model.*
import iw.core.output.*

@main def `validate-review-state`(args: String*): Unit =
  val useStdin = args.contains("--stdin")
  val filePaths = args.filterNot(_.startsWith("--"))

  if !useStdin && filePaths.isEmpty then
    Output.error("No file path provided. Usage: iw validate-review-state <file-path> or --stdin")
    sys.exit(1)

  val json =
    if useStdin then
      scala.io.Source.stdin.mkString
    else
      val path = os.Path(filePaths.head, os.pwd)
      if !os.exists(path) then
        Output.error(s"File not found: $path")
        sys.exit(1)
      os.read(path)

  val result = ReviewStateValidator.validate(json)

  if result.isValid then
    Output.success("Review state is valid")
    result.warnings.foreach { warning =>
      Output.warning(warning)
    }
    sys.exit(0)
  else
    Output.error("Review state validation failed")
    result.errors.foreach { err =>
      Output.info(s"  ${err.field}: ${err.message}")
    }
    result.warnings.foreach { warning =>
      Output.warning(warning)
    }
    sys.exit(1)
