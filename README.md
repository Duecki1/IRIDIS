# KawaiiRawEditor

Draft / test Android RAW editor built with Jetpack Compose + a Rust native decoder/processor.

Pretty web version: [`README.html`](README.html)

## Screenshots

![Gallery](https://github.com/user-attachments/assets/c1088104-acea-435e-a874-04b29267131b)
![Editor](https://github.com/user-attachments/assets/f662f9fc-3bde-4c8d-8344-9ae6784a31da)

## Status

> This mobile port is a **draft/test**. Expect rough edges and rapid iteration.

Inspired by / forked from RapidRAW: https://github.com/CyberTimon/RapidRAW

## Features (current)

- Import RAW files via Android’s document picker (SAF).
- Gallery with thumbnails, multi-select, bulk export, and star ratings.
- Editor with fast preview rendering (super-low → low → full).
- Adjustments: brightness, contrast, highlights/shadows, whites/blacks, saturation, temperature/tint, vibrance, etc.
- Curves (Luma/R/G/B) + histogram overlay.
- Color grading (shadows/midtones/highlights) + blending/balance.
- Effects: vignette.
- Masks: brush, linear gradient, radial, and AI subject mask (lasso-assisted).
- Export full-resolution JPEG to `Pictures/KawaiiRawEditor`.

## Build from source (Android)

The `:app` module builds a Rust shared library from `rust/` and copies the `.so` into `app/src/main/jniLibs` during `preBuild`.

### Requirements

- Android Studio + Android SDK Platform `36`
- Android NDK `27.0.12077973` (pinned in `app/build.gradle.kts`)
- Rust toolchain (see `rust/Cargo.toml`, `rust-version = "1.70"`)
- Rust Android targets

### One-time setup

```bash
# from repo root
rustup target add \
  aarch64-linux-android \
  armv7-linux-androideabi \
  i686-linux-android \
  x86_64-linux-android

# make sure Android SDK path is configured (Android Studio usually writes this)
cat local.properties
# sdk.dir=/path/to/Android/Sdk
```

### Build / install

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

## Using the app

- **Gallery:** tap **+** to import a RAW file. Long-press to multi-select.
- **Bulk export:** select multiple items and tap the download action.
- **Editor:** adjust sliders/curves/color, then export JPEG.
- **Masks:** add a mask and paint/position submasks (brush/gradient/radial/subject).

## AI subject mask (important)

> The first time you use the AI subject mask, the app downloads the U²-Net ONNX model from Hugging Face and caches it on-device.

- Implemented in `app/src/main/java/com/dueckis/kawaiiraweditor/AiSubjectMaskGenerator.kt`
- Explains `android.permission.INTERNET` in `app/src/main/AndroidManifest.xml`
- The download is hash-verified (SHA-256) before use

## Storage

Imported RAW files and edits are stored in the app’s internal files directory (see `ProjectStorage`):

```text
filesDir/
  projects/
    <project-id>/
      image.raw
      adjustments.json
      thumbnail.jpg
  projects.json
```

Exports are written via MediaStore to `Pictures/KawaiiRawEditor` (Android 10+).

## License

AGPL-3.0 — see `LICENSE`.
