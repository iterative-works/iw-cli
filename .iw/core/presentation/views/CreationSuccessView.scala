// PURPOSE: Presentation layer for rendering worktree creation success state
// PURPOSE: Generates HTML for success message with tmux command and copy button

package iw.core.presentation.views

import scalatags.Text.all.*
import iw.core.domain.WorktreeCreationResult

object CreationSuccessView:
  /** Render success state after worktree creation.
    *
    * Shows:
    * - Success icon and message
    * - Issue ID
    * - Tmux attach command in code block
    * - Copy button for command
    * - Worktree path information
    * - Close button to dismiss modal
    *
    * @param result Creation result with paths and commands
    * @return HTML fragment for success state
    */
  def render(result: WorktreeCreationResult): Frag =
    div(
      cls := "creation-success",
      renderSuccessIcon(),
      h3("Worktree Created!"),
      p(s"Created worktree for ${result.issueId}"),
      renderCommandSection(result),
      renderPathInfo(result),
      renderCloseButton()
    )

  /** Render success icon (checkmark).
    *
    * @return HTML fragment for icon
    */
  private def renderSuccessIcon(): Frag =
    div(
      cls := "success-icon",
      raw("&#x2714;") // Unicode checkmark
    )

  /** Render tmux command section with copy button.
    *
    * @param result Creation result
    * @return HTML fragment for command section
    */
  private def renderCommandSection(result: WorktreeCreationResult): Frag =
    div(
      cls := "tmux-command-container",
      label("To attach to the session:"),
      div(
        cls := "command-box",
        code(result.tmuxAttachCommand),
        button(
          cls := "copy-btn",
          attr("onclick") := s"navigator.clipboard.writeText('${result.tmuxAttachCommand}')",
          "Copy"
        )
      )
    )

  /** Render worktree path information.
    *
    * @param result Creation result
    * @return HTML fragment for path info
    */
  private def renderPathInfo(result: WorktreeCreationResult): Frag =
    p(
      cls := "path-info",
      s"Worktree path: ${result.worktreePath}"
    )

  /** Render close button to dismiss modal.
    *
    * @return HTML fragment for close button
    */
  private def renderCloseButton(): Frag =
    button(
      cls := "btn-secondary close-modal-btn",
      attr("hx-get") := "/api/modal/close",
      attr("hx-target") := "#modal-container",
      attr("hx-swap") := "innerHTML",
      "Close"
    )
