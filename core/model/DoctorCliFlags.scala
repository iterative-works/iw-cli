// PURPOSE: Pure parsing of `iw doctor` command-line flags
// PURPOSE: Decouples flag interpretation from the command script so it can be unit-tested

package iw.core.model

final case class DoctorCliFlags(
    fixMode: Boolean,
    filterCategory: Option[String]
)

object DoctorCliFlags:
  /** Parse the `iw doctor` argument list.
    *
    *   - `--fix` implies the Quality category (we only auto-fix quality gates).
    *   - `--quality` filters to the Quality category.
    *   - `--env` filters to the Environment category.
    *   - `--fix` wins over `--env` to preserve historical behaviour.
    */
  def parse(args: Seq[String]): DoctorCliFlags =
    val fixMode = args.contains("--fix")
    val filterCategory =
      if args.contains("--quality") || fixMode then Some("Quality")
      else if args.contains("--env") then Some("Environment")
      else None
    DoctorCliFlags(fixMode, filterCategory)
