# TV startup, playback and diagnostic export

## Verified baseline, 2026-11-14

- Clean `tg-stream-rebrand` checkout at `ef2f486bca703f8558d6015df654c4cd5e50593b`, matching the live branch.
- Latest published stable release: v1.0.15, 2026-11-06, universal APK and latest.json present.
- Application ID remains `com.rinsanak.tgstream`, namespace `com.tmplayer`, min SDK 23, target SDK 35.
- Release workflow pins certificate SHA-256 `3580acc62809ab114ccb00d567d24fe3b2aee2cc4f1aa02e2d5242ae27119686`. The APK certificate has not been independently read in this session.
- Release secret presence cannot be checked with the available GitHub connector. CLI has no authenticated account. No secret values were read.
- Drive searches for TG Stream, TMPlayer, TelePlay and keystore returned no matches. Original release-key backup remains unverified. No release may be attempted without resolving this.
- No retained Actions runs or commit statuses were returned. Previous tests are recorded in the v1.0.15 plan; this is not a fresh passing CI result.
- Existing Start/Stop controls record lifecycle, logcat and player samples, but Stop uses a share chooser rather than direct Saved Messages upload.
- Existing PlayerStart stages, deferred TV metadata work and bounded Home fetches must be retained. No live evidence yet identifies the dominant playback delay.
- Only the older 291/344 selection is in this checkout. The newer 102/35 selection was not located in current files/context searches. No feature removals are authorized by that older list.

## Implementation plan

Android phone and TV: reuse Settings controls and current package/data/signing. Always export a diagnostic ZIP, send only to the current account's Saved Messages, and report success only after TDLib confirms sending. Retain local parts/export on failure, timeout or cancellation, with the existing share action as recovery. Do not upload until the user presses Stop. No unrelated UI changes.

Shared Home (Android and desktop): keep the immediate in-memory cache path; perform cold disk reads on the disk dispatcher. A late cache restore must not overwrite fresh rows or restore rows removed by a newer chat selection. Web is unchanged.

Android diagnostics: capture bounded startup phase records before Start, then replay those with original elapsed timestamps into the explicitly started recording. Measure main-thread scheduling delay and process CPU time during recording, with at most one outstanding main-thread probe. Preserve existing player stages; add source-open and decoder timing. Do not collect message bodies or typed text.

Edge cases: disconnected/not authorized, account changes, pending sends, failed sends, rotation/navigation during upload, repeated Stop, disk full, interrupted recording, stale cache arriving after fresh data, empty cache, low-memory TV. No automatic unbounded retries or background library scans.

## Validation and progress / handoff

JDK 21 is installed. Gradle cannot fetch gradle-9.8.1-bin.zip: network unreachable. Android SDK is absent. The focused Gradle test command did not execute any tests. No APK was built, installed, device tested or published. D-pad navigation, phone behavior, TV idle CPU, playback/seek and live Saved Messages delivery remain device checks. No version or workflow release request has been changed.

Implemented locally: nonblocking cold Home cache restore with fresh-row and selection protection; bounded memory startup trail including auth/connection and existing PlayerStart phases; process CPU/main-thread delay samples while recording; source lookup/open, player state and decoder timings; always-ZIP exports in a scoped persistent app directory with disk-space preflight; Stop Log sends that ZIP to the current account's Saved Messages. Pending sends are not called successful. Navigation/rotation does not restart uploads. Repeated Stop reuses the finalized ZIP and in-process upload attempt. Failed or unconfirmed sends retain the export and offer the existing local copy/share recovery. Receipt deduplication is process-local: delivery across app death remains a live test and follow-up if duplicate exports are observed. An unconfirmed send is deliberately not resent automatically.

Regression tests added/updated: cold storage does not block Home construction, late cache cannot overwrite fresh rows or removed selections, ZIP contents/serials/repeated Stop, early/unrelated/failed/missing Telegram acknowledgements, prefixed and quoted credential redaction. Current CI's focused path now includes these suites. These Kotlin tests are authored, not run. Static validation passed: `git diff --check`, resource XML parsing, catalog JSON parsing, application identity and signing guard assertions, and `scripts/release-version.py --check` (31 cases, zero wrong).

Playback lag is still an open investigation, not a verified fix. Capture on the affected TV: restart app, Start Log, leave Home idle, open a representative video, Stop Log. Startup trail supplies pre-Start phase timestamps. Compare SOURCE_LOOKUP/SOURCE_OPEN, PlayerStart first bytes/prepare/first frame, DECODER, CPU and MAIN delays. Check a resumed video, tail-index MP4, local download, rapid seek, disconnection/recovery, phone playback and TV remote focus. Confirm ZIP arrives in Saved Messages. Measure TV idle CPU with logging off too, since recording adds overhead.

Next validation commands in an environment with the documented SDK and reachable dependencies:

```sh
./gradlew :core:jvmTest --tests '*DiagnosticJournalTest' :ui:jvmTest --tests '*HomeViewModelCacheTest' --tests '*HomeScaleTest'
./gradlew :app:testDebugUnitTest --tests '*DiagnosticSessionTest' --tests '*DiagnosticUploadTest' --tests '*SystemInstallerTest' --tests '*CompactControlsTest'
./gradlew :app:assembleDebug
```

Do not tag, push the build-request marker, run a release workflow or publish this checkpoint. Build compilation, device evidence, signing backup verification and owner review are still required.

## APK preparation, 2026-11-14

Owner requested an APK update to install on TV and return diagnostics, confirming Stop Log must deliver to Saved Messages. Prepared `v1.0.16-beta.1` locally (versionCode `1001601`) and its release notes for that device test. This intentionally selects full CI rather than v1.0.15's focused checks. The beta does not replace stable latest.json. The signed universal APK would be `TG-Stream-1.0.16-beta.1-universal.apk`; it does not exist yet.

The earlier explicit prohibition on agent publishing commands is still a blocker until the owner explicitly lifts it. No tag, push, remote source update, workflow run or release was performed. The actual signing backup is still unverified; approval must also resolve whether the existing Environment signing key, guarded by the pinned certificate check, may be used before locating its offline backup.

## TV Home recording control

The TV Home top bar has a single remote-focusable control. The circle starts a recording. The
square stops it and exports the ZIP through the existing Saved Messages path. Settings and Home
share the same action and export state, so leaving Home while an upload is pending keeps the
same attempt. After an interrupted recording, the square lets the viewer export the retained
session.

TV ZIPs include a bounded trail from the current process launch and structured timing during
recording. Compare first window pre-draw, Home loader and row readiness, local and connected chat
loading, Play preparation, source open and first readable bytes, and the first rendered frame.
Monotonic event timestamps, main-thread scheduling delay, process CPU and memory samples help
align the phases. TV capture uses structured events and warning types, excluding raw logcat,
settings values and media identifiers.

For a cold-start sample, open TG Stream, press the circle on Home, wait briefly and play a video
that has startup lag. Return to Home in the same process and press the square. Confirm that the
ZIP appears as a document in the signed-in Telegram account's Saved Messages. The TV log is
needed to locate the lag and to choose the next performance change.

Local Android validation passed for all seven focused tests covering D-pad focus, phone ZIP
reading, startup timing, TV log privacy, and send acknowledgement. The debug APK assembled
successfully. Real TV timing, live Saved Messages delivery and a signed release remain pending.
