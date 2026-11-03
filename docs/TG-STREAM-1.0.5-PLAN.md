# 1.0.5 cards, Glass and numbered diagnostic sessions

Android phone and TV scope. Desktop/web keep existing branding and styling.

- Restore title, year, duration and copy facts inside artwork cards, keeping the area below clean.
- Deduplicate by normalized names, year, duration and file identity. Never use repeated channel
  thumbnails as proof of equal content. Keep remakes, different titles and alternate copies.
- Shared Glass backdrop and translucent surface roles cover screens, sheets and dialogs. Native
  window blur is enabled where supported. One-time migration selects Glass, preserving future
  user choices, reduced motion and readable remote focus.
- In-app brand components are text-only. Native phone and TV launcher vectors are redesigned.
- Explicit recording captures app logcat, lifecycle, navigation, touch/navigation-key events,
  core errors with stacks, periodic device/network/player state, settings and card identity.
  Credentials and typed text are excluded. Session and record numbers are persistent/ordered.
  Storage/queue limits are explicit, not a claim of literally every CPU event.
- Stop flushes accepted records, exports text or ZIP, saves to Downloads where possible and opens
  the system share sheet. A user must share/upload the file into this ChatGPT chat; there is no
  authenticated automatic upload destination configured. Interrupted parts are retained.
- Check parser, deduplication, journal numbering/concurrency/redaction and Start/Stop regressions,
  phone/TV/large-text renders, full shared and Android CI, signed APK identity and live update feed.
  Physical signed-in device playback is not available here.

## Progress / handoff

Restored the interrupted 1.0.5 implementation from conversation context on 2026-11-03. Fixed the
translation catalog structure: new session keys are nested within logs, with snake_case names.
Native and automated checks must finish before claiming this update is ready.
