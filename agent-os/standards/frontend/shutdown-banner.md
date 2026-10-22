# Shutdown Banner UX

During platform drain, show a fixed top banner with:

- Phase label, in-flight count, progress %, elapsed time.
- Countdown to drain timeout.
- Progress bar synced to server `progressPercent`.

Behavior:
- Disable transactional forms/buttons/links while draining (`data-shutdown-disabled`).
- Re-enable on non-draining state.
- Poll `/actuator/shutdown-status` every 2s; stop on `complete`.
- If status endpoint missing (404), show "status unavailable" instead of assuming offline.

Exceptions: read-only views that don't mutate data.
