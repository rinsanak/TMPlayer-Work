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

## Completed verification (2026-11-03)

App and test source: `336bfb08938724fe0f5163f470128595754d06db`.

- Android [Test and build](https://github.com/rinsanak/TMPlayer-Work/actions/runs/41652272865/job/125974002884) passed: core 2,000 tests (13 skipped), shared UI 197, Android 330. Zero failures: 2,514 passed, 13 skipped.
- The 29 focused regressions passed, including numbered log sessions, logging cards already visible at Start, phone/TV/large-font card rendering, scrolling and Glass TV navigation in LTR and RTL.
- Native phone, TV and 1.3x font renders show title/year/duration, Start/Stop controls and logo-free branding. TV fixtures now render and place real native bounds before dispatching keys. Player render cases use separate workers and dispose activities; no navigation assertions were removed.
- [Signed release](https://github.com/rinsanak/TMPlayer-Work/actions/runs/41652272849) published [v1.0.5](https://github.com/rinsanak/TMPlayer-Work/releases/tag/v1.0.5).
- APK: `com.rinsanak.tgstream`, version 1.0.5 / 1000599, min SDK 23, target SDK 35, ARM64 and ARMv7. APK v1/v2 signature verification passed; certificate SHA-256 remains `3580acc62809ab114ccb00d567d24fe3b2aee2cc4f1aa02e2d5242ae27119686`.
- Published APK downloaded and verified against the live latest.json and GitHub asset digest: 34,226,273 bytes; SHA-256 `16db2fc70abb18681116fe7a579ebc7e3631faa04039709bd09a31c96f4b4de4`. The live update feed names 1.0.5 / 1000599 and the matching URL, size and digest.
- Desktop checks are separate from the successful Android release gate; this report does not claim those suites passed. Physical signed-in Telegram streaming was not available for testing.

Stop saves/shares TXT or ZIP. The user must attach that file to the chat for analysis. Capture is explicit and app-only, credentials/typed input are excluded, and queue/storage gaps are reported; it is not a claim to capture every operating-system or CPU event.
