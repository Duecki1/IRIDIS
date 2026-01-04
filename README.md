# IRIDIS

IRIDIS is an Android RAW editor built with Jetpack Compose and a Rust-based processing engine ([RapidRaw](https://github.com/CyberTimon/RapidRAW)).

Latest pre-release builds: https://github.com/Duecki1/IRIDIS/releases

## Screenshots

<table>
  <tr>
    <td align="center">
      <img
        alt="Gallery"
        src="https://github.com/user-attachments/assets/65bdca87-b4d2-429b-a882-47fe9e1f86f0"
        width="260"
      />
      <br/>
      <sub><b>Gallery</b></sub>
    </td>
    <td align="center">
      <img
        alt="Editor"
        src="https://github.com/user-attachments/assets/02e336a3-2a0d-4f2a-a3f5-41bece9de03e"
        width="260"
      />
      <br/>
      <sub><b>Editor (Light & Tone)</b></sub>
    </td>
    <td align="center">
      <img
        alt="Editor (extra screenshot slot)"
        src="https://github.com/user-attachments/assets/6e63a75a-7d51-423c-a813-76eade9e6047"
        width="260"
      />
      <br/>
      <sub><b>Editor (Color Grading)</b></sub>
    </td>
      <td align="center">
      <img
        alt="Editor (extra screenshot slot)"
        src="https://github.com/user-attachments/assets/00798813-4560-41fd-a362-5861aa66709e"
        width="260"
      />
      <br/>
      <sub><b>Masks (AI Train Mask)</b></sub>
    </td>
      <td align="center">
      <img
        alt="Editor (extra screenshot slot)"
        src="https://github.com/user-attachments/assets/9352b80c-d35e-4182-859c-824c97e8772e"
        width="260"
      />
      <br/>
      <sub><b>Masks (AI Sky Mask)</b></sub>
    </td>
  </tr>
</table>


## Highlights

- Fast RAW preview pipeline with multi-stage rendering.
- Non-destructive edits + a version tree.
- Local adjustments with multiple mask types.
- Optional AI masking models for subject and environment.
- Immich integration for download, edit sync, and export.

## Features

- RAW import via Android document picker.
- Gallery with thumbnails, multi-select, and star ratings.
- Core adjustments: exposure, contrast, highlights/shadows, whites/blacks, saturation, temperature/tint, vibrance.
- Detail controls: sharpness, clarity, structure, chromatic aberration.
- Curves (luma, red, green, blue).
- Color grading (shadows, midtones, highlights) with blending and balance.
- Effects: vignette.
- Masks: brush, linear gradient, radial, AI subject, AI environment.
- S Pen pressure support for brush masks.
- Crop/rotate workflow with live preview.
- Export format, resolution, and destination controls.
- Toggle for before/after preview.
- Widget.
- Mask tagging and naming.
- Copy and paste adjustments (including ai environment masks)

## Tech stack

- Android UI: Jetpack Compose
- Native processing: Rust
- RAW decoding: rawler
- Build: Gradle + Cargo (via Android Studio)

## AI models

IRIDIS can download optional ML models for subject and environment masks. Downloads are user-confirmed in-app.

## Immich integration

Connect to an Immich server to download assets, sync edits via sidecar metadata, and upload exports.

## Build

Requirements:
- Android Studio with Android SDK and NDK installed
- Rust toolchain (rustup + cargo)

Steps:
1. Open the project in Android Studio.
2. Sync Gradle.
3. Build a debug APK:
   ./gradlew assembleDebug

## Roadmap

- Improve highlight recovery
- Tablet-optimized layout
- Persisted AI mask caching
- Rating workflow improvements

## Credits

Inspired by RapidRAW: https://github.com/CyberTimon/RapidRAW

## To Do:

- Highlight recovery needs to be better for the highlight slider. (use the exposure slider for now)
- Use a more intuitive rating system
- Save calculated AI Masks
- app should only open once
- folder management
- maybe video of adjustments to export
- lens corrections?
- Make the curves on the right, left side the L RGB  and reset (also add aniamtion when switching like bottom to top)
- make the hsl more compact by putting hte colors on the left side in a vertical row
- maybe try magnetic scrolling
- fix color grading wheels tab on tablets

## License

AGPL-3.0 — see `LICENSE`.
