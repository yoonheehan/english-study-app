# English Daily (english-study-app)

A personal Android app for daily English speaking, listening and vocabulary, built for a
Galaxy Tab and a phone that share one word book and study record through Firebase.

## What it does
- **스피킹**: level-based lessons (listen, shadow, respond) and TOEIC Speaking practice in
  three levels with AI scoring by the on-device Gemma model.
- **말하기**: AI role-play on 40+ topics, with corrections afterwards.
- **단어장**: word book synced through Firestore, memorize-then-test with handwriting or keyboard,
  spaced review; collect new words from public-domain stories, YouTube videos with synced
  subtitles, or text highlighted in a browser.
- **듣기**: TOEIC-style listening tests with several accents.
- **기록**: streak, calendar, levels and activity history, synced between devices.

## Build
Kotlin + Jetpack Compose, Navigation 3, Room, Firebase (Auth, Firestore), LiteRT-LM (Gemma 4 E2B),
ML Kit (translation, digital ink).

Personal files are not in this repository:
- `app/google-services.json`: download it from the Firebase console (Project settings > Android app).
- `firestore.rules`: copy `firestore.rules.example`, put in your Gmail and device UIDs, and publish it
  in Firebase console > Firestore Database > Rules.
- `key/`: the Firebase service account key used by the PC tools in `tools/` (never commit it).

```
./gradlew testDebugUnitTest assembleDebug
```

## PC tools (`tools/`)
- `node tools/upload-content.mjs`: upload lesson and listening content to Firestore.
- `node tools/dedupe-words.mjs`: list duplicate word cards; `--apply` removes the extras.
