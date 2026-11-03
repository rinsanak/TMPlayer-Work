# 1.0.4 Home, recovery and glass controls

1. Android Home: artwork-only cards, no captions below posters, movies/series only. Keep other
   library pages available. Fold duplicate titles across Home rows, merging copies before display.
2. Stall recovery: the current 10-second watchdog is too eager and only follows download-prefix
   and buffered-position changes. Count actual data-source reads, allow a longer wait, and use
   distinct opening/rebuffer thresholds. The screenshot is a timeout report, not proof of corrupt
   media; an account/device log is required to identify a network outage conclusively.
3. User-initiated Try again: capture the playback position, release readers first, cancel active
   streaming/warm-up requests, clear the current video's Telegram cache (all joined parts) and
   transient indexes, restart from the captured position. Do not delete unrelated videos or user
   files outside the app's managed stream cache. Serialize double taps and report failures.
4. New Android adaptive vector icon, with a matching monochrome/legacy icon and in-app mark.
5. Add a persistent Glass theme on phone/TV, translucent panels, highlights, rounded controls and
   spring transitions; preserve readable contrast, remote focus, reduced motion and old-API support.
6. Move small transport controls to a bottom dock on phone and TV. Keep minimum 48dp touch targets,
   use smaller glyphs, remove large center discs, keep seek/time and secondary controls separate.
7. Core recovery/filter/dedup tests, Android layout/focus/render checks, signed release and feed.

Shared core policy is opt-in for the Android fork where appropriate. Desktop and web keep their
existing Home and transport layouts. Real signed-in TV/phone playback cannot be claimed from CI.
