# TG Stream subtitle work

## Android phone and TV

The Subtitles picker now offers a same-chat document browser for Telegram videos. Local videos
have no chat browser. It searches only the playing video's chat, shows file names and sizes,
supports file-name searches and explicit older/newer pages of at most 100 documents, and never
scans an account or a whole chat automatically. An empty document page still allows moving to
older documents. Search is debounced and timed out; leaving the page cancels its work.

Only SRT, ASS, SSA, VTT and text SUB documents of at most 4 MiB are offered. The chosen file is
downloaded and checked for completion and actual size. It then uses the existing subtitle file
loader for encoding conversion, MicroDVD conversion, preserving playback position and selecting
the new track. Binary VobSub remains refused by that loader. ZIP archives are not subtitle tracks;
extract one and use Load subtitle file, or post its text subtitle to the chat.

Phone and TV use the existing shared ChoiceSheet with remote focus and a search field. Desktop
and web retain their current UI; this feature is scoped to the Android app. Actual signed-in
phone/TV Telegram and offline tests still require the user's account and devices.

## MSone routes researched on 2026-11-02

1. Download with MSone's official website or its official Android/iOS app, then use Load subtitle
   file in TG Stream. Extract an archive first if the download contains several files.
   Source: https://malayalamsubtitles.org/msone-app/
2. Post that subtitle document to the same Telegram chat as the video and use Subtitles from
   this Telegram chat. This needs no MSone API key, only the user's existing Telegram session.
3. Use the imported direct search integration through an authorized subtitle gateway key.
   `MsoneApi` calls `https://subs.tmplayer.org/msone/search`; the build supplies
   `MSONE_GATEWAY_KEY`. A fork needs permission and a valid key for that service, or its own
   authorized gateway implementation. OpenSubtitles has a separate key and is not required by
   the MSone player search. Telegram API credentials cannot substitute for either provider key.

The official app developer's older MSoneBot source describes an MSone API, but that repository
alone does not establish current API availability, authorization or compatibility. The candidate
addon endpoint could not be verified by this research; it is not used by the implementation.
Source: https://github.com/anzilr/MSoneBot

The Android release workflow now passes optional MSone/OpenSubtitles secrets through when set.
This does not create provider credentials or claim live MSone search works without them.

## Selection dependencies

The attached selection is preserved in TG-STREAM-SELECTION.txt and its 53 hidden entries are
recorded in TG-STREAM-SELECTION.json. UI removals are Android-specific. Downloading stays while
its profile shortcut and folder/gallery export UI are hidden. Starred rows stay while their
settings toggle and the Favourites profile shortcut are hidden. Allowed-chats-only keeps its
necessary PIN creation and chat picker even though standalone PIN controls were unchecked.
Selected sleep timer/proxy child values do not reintroduce their unchecked parent menu entries.

## Progress / handoff

- Implemented same-chat subtitle search, explicit document pagination and file loading.
- Applied the submitted Android UI selection, retaining dependencies described above.
- Added localized chat picker labels and targeted core tests.
- Pending GitHub compilation, tests and any corrections from them. No device test claimed.
