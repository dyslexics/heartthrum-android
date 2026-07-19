# Heartthrum (Android)

**A completely free heart rate app. No ads, no premium, no in-app purchases, no data collection. Ever.**

Heartthrum measures your pulse using the phone camera (photoplethysmography, PPG): place your fingertip on the rear camera lens, and the app detects tiny color changes in your skin caused by your heartbeat.

Website: [heartthrum.com](https://heartthrum.com)

This is the native Android port of the [iOS app](../../../heartthrum) — same PPG engine, ported 1:1 from Swift to Kotlin.

## Features

- **Measure** — 40-second PPG measurement with live pulse curve, quality indicator, and optional tag/mood
- **History** — day/week/month charts, stored locally with Room
- **Breathe** — guided breathing exercise (4s in / 6s out)
- Languages: English and German
- **No INTERNET permission.** The app collects nothing and sends nothing — Android enforces it.

## Not a medical device

Heartthrum is a wellness app. It is **not** a medical device and must not be used for diagnosis, treatment, or monitoring of any medical condition. Camera-based pulse measurement cannot measure blood pressure or blood oxygen.

## How it works

- CameraX delivers RGBA frames at 30 fps with torch enabled; exposure is locked via Camera2 interop shortly after finger placement
- PPG engine (`PulseProcessor.kt`): biquad band-pass 0.7–3.5 Hz, adaptive peak detection, median BPM from inter-beat intervals; red and green channels processed in parallel, the more pulsatile one wins. Finger detection is red-dominance based (`r > 0.18 && r/max(g,b) > 1.35`)

## Building

Requirements: JDK 17, Android SDK (target 35, minSdk 26)

```bash
gradle :app:assembleDebug
```

Release builds are signed with a keystore that is **not** part of this repository. To build your own release, create a keystore and pass it via environment variables (see `app/build.gradle.kts`):

```bash
HT_KEYSTORE=/path/to/your.jks HT_KEYSTORE_PASS=... gradle :app:bundleRelease
```

## License

[GPL-3.0](LICENSE) — © Mario Engel

Free forever. If you fork it, keep it free.
