# Heartthrum (Android)

**A completely free heart rate app. No ads, no premium, no in-app purchases, no data collection. Ever.**

Heartthrum measures your pulse using the phone camera (photoplethysmography, PPG): place your fingertip on the rear camera lens, and the app detects tiny color changes in your skin caused by your heartbeat.

Website: [heartthrum.com](https://heartthrum.com)

This is the native Android port of the [iOS app](../../../heartthrum), with an Android-specific timestamp-aware PPG engine.

## Features

- **Measure** — 40-second PPG measurement with live pulse curve, quality indicator, and optional tag/mood
- **History** — day/week/month charts, stored locally with Room
- **Breathe** — guided breathing exercise (4s in / 6s out)
- Languages: English and German
- **No INTERNET permission.** The app collects nothing and sends nothing — Android enforces it.

## Not a medical device

Heartthrum is a wellness app. It is **not** a medical device and must not be used for diagnosis, treatment, or monitoring of any medical condition. Camera-based pulse measurement cannot measure blood pressure or blood oxygen.

## How it works

- CameraX delivers timestamped RGBA frames with torch enabled; the device chooses the frame rate and analysis can drop frames. Exposure is locked via Camera2 interop shortly after finger placement.
- [PPG engine](app/src/main/java/de/dvld/heartthrum/PulseProcessor.kt): timestamp-aware input smoothing, uniform resampling, biquad band-pass 0.7–3.5 Hz, interpolated peak detection, and robust mean inter-beat intervals. Red and green channels run in parallel; selection favors pulsatile channels with usable, higher-confidence estimates. Finger detection remains red-dominance based (`r > 0.18 && r/max(g,b) > 1.35`).

### Timing, estimation, and validity

- Supported/tested camera rates are 15, 24, 30, and 60 FPS. Two time-aware 5 Hz RC low-pass poles attenuate high-frequency input before linear interpolation onto a 120 Hz grid. This grid avoids downsampling at supported camera rates; it cannot undo aliasing already present in camera samples. Higher input rates are not device-validated.
- Gaps up to 250 ms can be interpolated. Longer gaps restart filters, peak history, and measurement acquisition; no signal is synthesized across them. Duplicate/backward timestamps or non-finite/out-of-range inputs also invalidate acquisition. Finger removal immediately clears DSP history (the existing 1.5-second measurement-flow grace period remains).
- After two seconds of filter warmup, local maxima use guarded three-point parabolic interpolation on the **uniform** grid. The waveform still displays approximately 30 points per second over four seconds.
- The estimator requires at least three usable intervals in an eight-second window. It rejects intervals outside 40–215 BPM (guard bands around the intended 42–210 BPM range), then rejects intervals differing by more than 25% from the median. BPM is `60 / mean(accepted intervals)`, not a median of frame-count intervals. Intervals always refer to originally adjacent detected peaks; rejecting one never joins nonconsecutive beats or implicitly invents missing beats.
- An eight-second history trades noise rejection for responsiveness: gradual changes use the recent mean; abrupt changes can take roughly one window plus filter/beat acquisition time. The deterministic 70→90 BPM step settles within 1 BPM in 4.6 seconds (the regression allows ten seconds).
- Estimates expire three seconds after the last accepted interval's ending peak, even if a finger remains detected. Filter ringing can briefly produce additional peaks after pulsation stops; tests require complete invalidation within six seconds of flat input. Missing camera delivery clears acquisition after three wall-clock seconds (checked every 250 ms).
- Quality combines interval regularity, accepted-interval coverage, startup evidence, freshness, and an absolute pulsatility gate. It is a heuristic, **not** proof of a physiological signal: periodic movement or lighting can still mimic a pulse.
- [Measurement aggregation](app/src/main/java/de/dvld/heartthrum/MeasureViewModel.kt) keeps the existing robust median of **unrounded** usable estimates from the latter 24 seconds (falling back to the full measurement if fewer than five are available). This is a measurement summary, not an instantaneous endpoint rate. Finishing requires a usable reading within the last three seconds. Only the displayed/stored result is rounded to an integer; the Room schema is unchanged.
- The [live numeric display](app/src/main/java/de/dvld/heartthrum/LiveBpmDisplay.kt) publishes the latest estimate at most once per second of monotonic time, avoiding rapid flicker around rounding boundaries. The first reading appears immediately; signal loss and resets clear it immediately, and reacquisition can show a new reading immediately. There is no additional averaging: presentation adds at most one second plus the next delivered frame of delay, without changing the estimator, final aggregation, waveform, or quality updates.
- DSP processing, resets, and measurement state are main-dispatcher confined. Camera pixel extraction stays on its single-thread executor; generation checks discard callbacks from stopped/replaced sessions.

### Automated regression results

[Local JVM tests](app/src/test/java/de/dvld/heartthrum/PulseProcessorTest.kt) exercise production processing without a camera: clean rates from 42–210 BPM at all four frame rates, adjacent 68–80 BPM rates, timestamp jitter, dropped frames, long gaps, resets, finger removal, flat/tiny signals, stale estimates, missed beats, seeded noise/artifacts, changing rates, invalid numerical inputs, and large timestamp origins. Final aggregation is tested for precision and freshness too.

[Live display tests](app/src/test/java/de/dvld/heartthrum/LiveBpmDisplayTest.kt) verify the one-second update limit at all four frame rates, rounding-boundary flicker, queued samples, long pauses, immediate signal-loss clearing, and reset/reacquisition behavior.

The following comparison ran the original production processor at commit `8701f2d` and the corrected processor against identical 40-second, 30 FPS synthetic RGB samples: `cos(phase) + 0.25*cos(2*phase + 0.5) + 0.08*cos(3*phase + 0.8)`. Entries are the final unrounded median over the last 24 seconds, followed by the displayed integer. At 80 BPM the original estimator alternates between the 23- and 22-frame plateaus, so its final median depends on frame phase:

| Input BPM | Before | After |
|---:|---:|---:|
| 68 | 69.23077 → 69 | 68.00001 → 68 |
| 70 | 69.23077 → 69 | 70.00027 → 70 |
| 73 | 72.00000 → 72 | 72.99987 → 73 |
| 74 | 75.00000 → 75 | 74.00000 → 74 |
| 76 | 75.00000 → 75 | 76.00008 → 76 |
| 80 | 78.26087 or 81.81818 → 78 or 82 | 80.00000 → 80 |

After 16 seconds of settling, maximum individual clean-estimate error across the 52 rate/FPS combinations was 0.101 BPM or less (target: 1 BPM). With ±20% timestamp jitter and one dropped frame every 17 frames, maximum error for 68/74/80 BPM was 0.234 BPM or less. Seeded moderate noise plus occasional artifacts produced mean absolute errors of 0.386/0.230/0.078 BPM at 15/30/60 FPS respectively; these are synthetic results, not accuracy claims for real fingers.

A physical check on one Android 16 phone (model CPH2581) used a separate debug build with the same processing code and aggregate timing/signal diagnostics. During a 40-second measurement, 1,200 camera intervals averaged 29.981 FPS (median and 95th percentile 33.327 ms; maximum 66.654 ms), with no gaps over 250 ms or non-monotonic timestamps. In a separate manual check, removing the finger cleared the live reading and restarted acquisition; replacing it restored a reading. No reference sensor was available, so the accuracy of the device reading was not verified. Other camera timestamp distributions, motion/lighting behavior, skin/perfusion variation, thermal load, and comparison with a reference sensor still need device testing. The iOS estimator is outside this change.

## Building

Requirements: JDK 17, Android SDK (compile/target 36, minSdk 26)

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Set `ANDROID_HOME` to your SDK location if needed. `./gradlew :app:lintDebug` also runs Android lint; the current repository has pre-existing Camera2 opt-in errors and a missing German `app_name` translation, independent of the estimator regression tests.

Release builds are signed with a keystore that is **not** part of this repository. To build your own release, create a keystore and pass it via environment variables (see `app/build.gradle.kts`):

```bash
HT_KEYSTORE=/path/to/your.jks HT_KEYSTORE_PASS=... gradle :app:bundleRelease
```

## License

[GPL-3.0](LICENSE) — © Mario Engel

Free forever. If you fork it, keep it free.
