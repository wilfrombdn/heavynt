# Heavyn't

Minimal black & white Android workout tracker. Logs the weight on every exercise and colours an anatomy map: blue = strong, red = weak.

**Download:** https://wilfrombdn.github.io/heavynt/

## Features
- Home: today's workout + front/back body map scored from your lifts (estimated 1RM ÷ bodyweight vs. an intermediate lifter, best set in last 90 days)
- Plans built from an exercise drop-down; any on/off cycle (7 on / 7 off preset, or set every day yourself)
- Workout: tap an exercise to complete it, hold to change sets/reps/weight (asks whether to update the plan permanently)
- Plateau prompts to raise the weight after N sessions (default 3) or ~4 weeks at the same weight
- Workout date & time recorded automatically; per-exercise weight history and charts
- Add your own exercises in-app; optional reminders on workout days; copy/paste backup

## Native app (v3+)
`native/` is a true native Android app (Kotlin + Jetpack Compose), not a web view. It reads the same JSON backup format as the original web/Capacitor version (`src/` + `android/`, kept for reference), so data can be restored straight across.
```
.\scriptsuild-native.ps1                  # tests + signed APK -> dist\heavynt.apk
.\scriptselease-native.ps1 3.0.1 "notes"  # builds + publishes a GitHub Release
```
`node scripts/gen-kotlin-data.mjs` regenerates the exercise library for Kotlin from `src/data.js`.

## Develop (legacy web version)
```
npm install
npm run build        # bundles src/ -> www/app.js (open www/index.html#home in a browser to preview)
npm test             # logic tests
.\scripts\build-apk.ps1                      # signed APK -> dist\workout-tracker.apk
.\scripts\release.ps1 1.0.1 "notes"          # builds + publishes a GitHub Release
```
Needs JDK 21 and the Android SDK (`ANDROID_HOME`, default `C:\Android`). Releases are signed with `keystore/release.jks` (git-ignored, **back it up**: without it updates can't install over the old app). Bump `versionCode`/`versionName` in `android/app/build.gradle` for each release.

Starter exercises live in `src/data.js`; add more there or in the app's Exercises tab.
