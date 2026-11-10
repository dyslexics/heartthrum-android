# Changelog

All notable changes to Heartthrum Android are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added

- Add deterministic local JVM regression tests for pulse estimation and live BPM display updates.
- Add timestamp-aware resampling, interpolated peak timing, and robust inter-beat interval aggregation to improve heart-rate estimates across camera frame rates.
- Add a one-second publication limit for the live BPM number to reduce flicker, while clearing it immediately on signal loss.

### Changed

- Expire stale estimates and restart measurement acquisition after timestamp discontinuities or prolonged camera-frame loss.
- Keep measurement aggregation based on unrounded estimates and round only the final result.

### Fixed

- Fix heart-rate estimates sticking to frame-rate-quantized values.
- Serialize signal processing and resets to prevent concurrent pipeline state changes.
