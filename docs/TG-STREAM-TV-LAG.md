# TG Stream TV lag investigation

## Evidence

- TV diagnostic session 4: first Home row at about 40 seconds, first video frame at 8.3 seconds, second at 10.1 seconds. The local chat pass reported 182 chats in 15 seconds.
- Session 5: local pass reported 40 chats in 8 seconds, while the first Home row arrived at about 46 seconds. CPU remained above 300 percent for much of the wait. This session did not include playback.
- Session 6 on beta 4: the first fresh Home fetch finished at 7.7 seconds, but a Home row did not reach the screen until about 47.6 seconds. A completed row build took 4.5 seconds, and Home picking restarted repeatedly. The main and Default dispatcher threads were among the busiest; remote input dispatch was mostly under 32 ms, while the next frame sometimes took over a second. No play request or track picker was captured.
- These timings establish lag and CPU contention. They do not identify the worker consuming the CPU or prove a playback fix.

## Plan and platform scope

- Android TV: sample busy threads, time Home picking, building and searches, time player setup, and record remote press, focus and track picker actions. Keep entries free of chat, account and media identifiers.
- Android phone: retain its existing diagnostic capture and track picker action timing. The new thread sampling is TV only.
- Desktop and web: no diagnostic UI or playback changes in this Android investigation.
- Recording remains bounded by the journal's existing space limit. A stopped or failed export retains the recorded parts for recovery.

## Progress and handoff

- Added targeted timing and TV thread CPU samples locally. No performance fix is yet proven. The next TV ZIP should identify the busiest threads and separate Home fetch from Home composition. Use that evidence to change the expensive path, then compare startup, first frame, remote latency and idle CPU on the same TV.
- Prepared `v1.0.16-beta.4` as a diagnostic prerelease with focused Android CI. `git diff --check` passed. Gradle tests and APK compilation could not start here because the Gradle 9.8.1 distribution is absent and the distribution download connection was refused. No APK or release was produced.
- Beta 4 was published and its GitHub Android CI and signed release checks passed. Session 6 points to repeated, expensive Home row builds. `TitleIdentity.parsed` was reparsing every unchanged file name despite `MediaItem.parsedName` caching the exact same result. Reuse that cache for uncorrected items and keep a separate parse for viewer renames. The first beta 5 build failed because timing code in `pureMain` referenced `PerfLog` from `commonMain`; that timing line was removed before retrying.
- Session 7 on beta 5 contains about 97 seconds of an idle process with Telegram initialization, but no Activity, Home, remote input, play request, or Stop event. It cannot measure the beta 5 Home change. Auto recording began from `Application.onCreate`, which can run for a background job before the viewer opens the TV UI. Start auto recording at first Activity creation, retaining buffered startup phases, so a future ZIP covers the viewed session. A pending interrupted session still requires Stop/export before another automatic session starts.
