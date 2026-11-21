# TG Stream TV lag investigation

## Evidence

- TV diagnostic session 4: first Home row at about 40 seconds, first video frame at 8.3 seconds, second at 10.1 seconds. The local chat pass reported 182 chats in 15 seconds.
- Session 5: local pass reported 40 chats in 8 seconds, while the first Home row arrived at about 46 seconds. CPU remained above 300 percent for much of the wait. This session did not include playback.
- These timings establish lag and CPU contention. They do not identify the worker consuming the CPU or prove a playback fix.

## Plan and platform scope

- Android TV: sample busy threads, time Home picking, building and searches, time player setup, and record remote press, focus and track picker actions. Keep entries free of chat, account and media identifiers.
- Android phone: retain its existing diagnostic capture and track picker action timing. The new thread sampling is TV only.
- Desktop and web: no diagnostic UI or playback changes in this Android investigation.
- Recording remains bounded by the journal's existing space limit. A stopped or failed export retains the recorded parts for recovery.

## Progress and handoff

- Added targeted timing and TV thread CPU samples locally. No performance fix is yet proven. The next TV ZIP should identify the busiest threads and separate Home fetch from Home composition. Use that evidence to change the expensive path, then compare startup, first frame, remote latency and idle CPU on the same TV.
- Prepared `v1.0.16-beta.4` as a diagnostic prerelease with focused Android CI. `git diff --check` passed. Gradle tests and APK compilation could not start here because the Gradle 9.8.1 distribution is absent and the distribution download connection was refused. No APK or release was produced.
