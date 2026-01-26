// PURPOSE: Presentation layer for rendering worktree creation loading state
// PURPOSE: Generates HTML for loading spinner and message

package iw.core.dashboard.presentation.views

import scalatags.Text.all.*

object CreationLoadingView:
  /** Render loading state during worktree creation.
    *
    * Shows:
    * - Spinner animation
    * - "Creating worktree..." message
    *
    * HTMX will automatically show/hide this element when requests are in progress
    * by using the htmx-indicator class and matching hx-indicator attribute.
    *
    * @return HTML fragment for loading state
    */
  def render(): Frag =
    div(
      id := "creation-spinner",
      cls := "htmx-indicator",
      div(cls := "spinner"),
      span("Creating worktree...")
    )
