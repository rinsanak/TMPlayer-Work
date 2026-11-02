# TG Stream

TG Stream is an unofficial Telegram video player for Android TV, Fire TV and Android phones. It is
based on [TMPlayer](https://github.com/dracu-lah/TMPlayer), whose streaming player, chat browsing,
series grouping and playback UI are the foundation of this new app.

TG Stream is a separate app identity and repository. It reads videos from the Telegram chats the
viewer chooses. It does not provide media. Telegram sign-in requires this project’s own API
credentials, supplied locally through `local.properties`; do not commit them.

## Build the Android app

Use JDK 21 and Android SDK platform 37. Add the Telegram credentials to `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
TG_API_ID=1234567
TG_API_HASH=your_api_hash
```

Then build a debug APK:

```bash
./gradlew assembleDebug
```

The APK is written under `app/build/outputs/apk/`. A build without Telegram credentials can open,
but Telegram sign-in is unavailable. This repository's fork setup and remaining owner steps are
tracked in [docs/TG-STREAM-PLAN.md](docs/TG-STREAM-PLAN.md).

## Upstream and license

This project derives from TMPlayer by dracu-lah. Its source history and copyright notices are
preserved. It is distributed under GPL-3.0-or-later, with the upstream OpenSSL exception and
third-party notices retained. See [LICENSE](LICENSE),
[LICENSE-OPENSSL-EXCEPTION.md](LICENSE-OPENSSL-EXCEPTION.md), and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
