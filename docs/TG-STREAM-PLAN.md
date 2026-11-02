# TG Stream work plan

## Goal

Start TG Stream in this repository from the imported TMPlayer source. Do not carry over the old
TG Stream implementation. Preserve upstream source history, copyright notices and GPL terms.

## Phases

1. **Separate app identity**: app label, package ID, project name, localized product name, and fork
   documentation. Retarget update discovery and issue reports to this repository. Do not show the
   upstream author's support links as TG Stream links.
2. **Configure Telegram sign-in**: build with API credentials registered for this fork, kept out of
   Git. Check both QR and phone-number flows.
3. **Review the Android TV and phone experience**: keep the imported TMPlayer browse and player
   behavior as the reference. Check Home, chat/video lists, seasons, playback, settings, loading,
   errors, and D-pad focus on both form factors.
4. **Own the release path**: update the release workflow and APK naming for TG Stream, produce an
   installable APK signed with TG Stream's key, and make the in-app update check use only TG Stream
   releases.

## Progress / handoff

- Phase 1: Android application ID is `com.rinsanak.tgstream`; launcher name is TG Stream; release
  lookup and problem reports point to `rinsanak/TMPlayer-Work`; CI now runs on feature branch pushes.
- The existing Android namespace remains `com.tmplayer` to avoid a risky package-wide source move.
- The fork has an Android-only release workflow. It creates a signed universal APK and a `latest.json`
  asset on stable tags; beta tags produce pre-releases. The inherited multi-platform TMPlayer release
  workflow is retained as `.disabled`, so it cannot publish upstream site or desktop packages.
- Automatic support reminders are disabled. The inherited About screen still has TMPlayer contact,
  privacy/legal and translation links; replace or clearly attribute them before public release.
- No APK was built in this workspace: it has no Android SDK, no local Telegram credentials, and only
  JDK 17 while the project requires JDK 21. No signing identity exists here either.

## Owner steps to unlock a signed APK

1. Push the prepared branch to the new repository:
   `git push -u origin tg-stream-rebrand`
2. Register a dedicated Telegram application at https://my.telegram.org under **API development
   tools**. In GitHub, open `rinsanak/TMPlayer-Work` → **Settings → Environments**, create the
   `release` environment, and add `TG_API_ID` and `TG_API_HASH` as environment secrets.
3. Create and safely back up a TG Stream signing keystore. Add `KEYSTORE_BASE64`,
   `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` to the same `release` environment. Never
   commit the keystore or paste its passwords into chat. Android treats this signing key as the
   permanent identity for future app updates.
   On Windows with JDK installed, create it with `keytool -genkeypair -keystore tg-stream-release.jks -alias tg-stream -keyalg RSA -keysize 4096 -validity 10000`, then encode it for the `KEYSTORE_BASE64` secret with PowerShell:
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\tg-stream-release.jks")) | Set-Clipboard`.
   Save the generated file in two secure offline locations before adding the secrets.
4. In the repository's **Settings → Actions → General**, allow GitHub Actions to create releases
   (`contents: write` is already declared by the workflow). Keep the repository and release
   environment under the user's account.
5. After the branch's CI run succeeds and Android TV/phone sign-in and playback have been checked,
   create and push an annotated beta tag, for example `v1.0.0-beta.1`. GitHub Actions will build and
   publish the APK. A stable tag such as `v1.0.0` also publishes `latest.json` for Check for update.

The user must perform those account-bound steps and test the APK on their own devices. Optional
poster/subtitle provider keys can be added later; they are not required for Telegram sign-in or the
Android release workflow.

## 1.0.1 subtitle and selection update

See TG-STREAM-SUBTITLES.md for the Android chat subtitle picker, research and UI-selection
dependencies. The submitted 291/344 selection is preserved in TG-STREAM-SELECTION.txt.
GitHub tests and release build are the next verification steps; no signed-in device test claimed.

## 1.0.2 title page update

The supplied TMPlayer reference video shows the imported SeriesPanel design already in this
repository. Preserve that design and the submitted settings selection on Android phone and TV.
Desktop shares the episode search improvement; web is unchanged. WholeSeries now emits each
search page, folds episodes off the UI thread, keeps partial results after a failed later page,
cancels on close, limits each request to 12 seconds and the whole search to 30 seconds, and stops
unchanged or cycling cursors. Cached results appear on reopen. Tests cover progressive answers,
partial failure, non-advancing cursors and cancellation. No signed-in phone or TV test claimed.

The older TelePlay 6.2 beta has a different application identity/update channel. This release
updates the TMPlayer-Work 1.0.x fork only; do not claim it updates the separate older app.
