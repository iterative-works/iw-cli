// PURPOSE: Pure argument parsing helpers for phase commands
// PURPOSE: Extracts named args, flags, and resolves issue ID and phase number from branch or CLI args

package iw.core.model

object PhaseArgs:
  /** Extract the value for a named argument (--name value) from an argument
    * list.
    */
  def namedArg(args: List[String], name: String): Option[String] =
    args
      .sliding(2)
      .collectFirst { case flag :: value :: Nil if flag == name => value }

  /** Check if a flag is present in the argument list. */
  def hasFlag(args: List[String], flag: String): Boolean =
    args.contains(flag)

  /** Resolve issue ID from CLI arg or feature branch name.
    *
    * Returns Left with an error message if neither source yields a valid issue
    * ID.
    */
  def resolveIssueId(
      issueIdArg: Option[String],
      featureBranch: String
  ): Either[String, IssueId] =
    issueIdArg match
      case Some(rawId) =>
        IssueId.parse(rawId)
      case None =>
        IssueId
          .fromBranch(featureBranch)
          .left
          .map(err =>
            s"Cannot determine issue ID from branch '$featureBranch': $err"
          )

  /** Resolve phase number from CLI arg or a raw phase number string from the
    * branch.
    *
    * Returns Left with an error message if parsing fails.
    */
  def resolvePhaseNumber(
      phaseNumberArg: Option[String],
      fromBranch: String
  ): Either[String, PhaseNumber] =
    phaseNumberArg match
      case Some(raw) =>
        PhaseNumber
          .parse(raw)
          .left
          .map(err => s"Invalid --phase-number '$raw': $err")
      case None =>
        PhaseNumber
          .parse(fromBranch)
          .left
          .map(err => s"Could not parse phase number from branch: $err")
