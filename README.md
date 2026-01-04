# IRIDIS

IRIDIS is an Android RAW editor built with Jetpack Compose and a Rust-based processing engine ([RapidRaw](https://github.com/CyberTimon/RapidRAW)).

Latest pre-release builds: https://github.com/Duecki1/IRIDIS/releases

## Screenshots

<table>
  <tr>
    <td>
      <img
        alt="Gallery"
        src="https://github.com/user-attachments/assets/6b79a493-a9c6-4c6a-bdfe-0eaa9517adc6"
        width="260"
      />
    </td>
    <td>
      <img
        alt="Editor"
        src="https://github.com/user-attachments/assets/519535ad-4827-4b61-a5dc-1207d9f064d3"
        width="260"
      />
    </td>
    <td>
      <img
        alt="Editor (extra screenshot slot)"
        src="https://github.com/user-attachments/assets/5fcce0b0-6361-4a1c-90ac-13b810740a68"
        width="260"
      />
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

## Features (current)

- Import RAW files via Android’s document picker.
- Gallery with thumbnails, multi-select, bulk export, and star ratings.
- Editor with fast preview rendering (super-low → low → full).
- Adjustments: brightness, contrast, highlights/shadows, whites/blacks, saturation, temperature/tint, vibrance, etc.
- Curves (Luma/R/G/B)
- Color grading (shadows/midtones/highlights) + blending/balance.
- Effects: vignette.
- Masks: brush, linear gradient, radial,AI environment (optional), and AI subject mask (lasso-assisted)
- S-Pen support for brush masks (line width depending on pressure)
- Choose export file format and resolution 
- Button to switch between unedited and edited preview
- Widget showing all edits
- Tab for cropping and rotating the image
- Tag system for mask names
- [Immich](https://github.com/immich-app/immich) intergration (download, edit sync via desc, export & upload to immich)
- Versioning via a Version Tree
  
## Planned/Issues:

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

## License

AGPL-3.0 — see `LICENSE`.
