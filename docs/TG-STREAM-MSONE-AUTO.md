# Automatic MSone subtitles

Android phone and TV: use MSone's official addon directly, with no TMPlayer gateway key. Start
one background search once video playback is ready. Resolve IMDb from existing metadata, or
match one unambiguous exact title/year in the addon's catalog. Require an exact season and
episode for series; do not guess season packs, date-only or multi-episode files. Prefer a release
whose filename shares the video's release tags. Keep playback position and pause state when
attaching the validated UTF-8 subtitle. Respect explicit global subtitle-off, manual subtitle
interaction, cancellation, item changes and an automatic-loading setting (on by default).

Reuse the existing subtitle menu, download cache and decoder. No new focus-stealing dialog.
Search failures, 403/429, no match, expired links, empty/HTML/oversized responses and low disk space
must leave playback usable, with the subtitle menu available for retry or manual choice. Limit
auto work to one search/download attempt per foreground visit, with a 25-second coroutine budget
and transport timeouts. Do not retry errors in a loop. Resolve fresh signed links on every search;
cache downloaded files under stable provider/item IDs. Check free space before writes.

Desktop keeps its gateway behavior; direct addon and automatic loading are enabled only by the
Android fork's constructor option. Web is unchanged. Unit tests cover parsing, safe links,
exact/ambiguous catalog matches, series identity, release ranking, caching and rejected downloads.
No account-backed phone or TV playback test is claimed by CI.
