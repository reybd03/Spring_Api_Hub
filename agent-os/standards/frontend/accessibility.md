# Accessibility Patterns

- Modal dialogs use native `<dialog>` with `showModal()`/`close()`.
- Trap focus inside open modal (Tab/Shift+Tab cycle through focusable elements).
- Escape closes modal; restore focus to trigger button on close.
- Announce dynamic status via `aria-live="polite"` region (`#sr-announcer`).
- Visually hidden helper uses `.sr-only` (clip/absolute).
- Provide `aria-label` on icon-only controls (e.g., theme toggle).

Exceptions: non-interactive content and purely decorative elements.
