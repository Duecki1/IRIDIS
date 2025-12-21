# IRIDIS

Draft / test Android RAW editor built with Jetpack Compose + a Rust native decoder/processor.

If you are interested in this Project and want it to be continued, let me know by giving this repo a star :)

Download the newest release here (alpha version): [Releases](https://github.com/Duecki1/IRIDIS/releases)

## Screenshots

<table>
  <tr>
    <td>
      <img
        alt="Gallery"
        src="https://github.com/user-attachments/assets/30c52607-c961-4265-b8c5-a9610407d063"
        width="260"
      />
    </td>
    <td>
      <img
        alt="Editor"
        src="https://github.com/user-attachments/assets/658364d8-d192-4952-8e16-11425abdc404"
        width="260"
      />
    </td>
    <td>
      <img
        alt="Editor (extra screenshot slot)"
        src="https://github.com/user-attachments/assets/c943ccd0-989e-4354-b7d8-e794b39ee2ff"
        width="260"
      />
    </td>
  </tr>
</table>

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

## Planned:

- Holding down on the Preview should show the original picture until let go
- Highlight recovery doesn't seem to be very good (?)
- UI needs to be improved for masking. (And more)

## How It Works

IRIDIS is a single-activity Jetpack Compose app backed by:

- An on-device project store (imported RAWs + thumbnails + adjustments).
- A native Rust processing pipeline exposed to Kotlin via JNI (`LibRawDecoder`).
- A reactive UI that renders previews as you edit (multi-stage or full-only depending on settings).

### App Flow

1. **Startup**: `MainActivity` hosts the Compose UI.
2. **Gallery**: Imported RAWs are listed here.
3. **Editor**: Selecting an item opens the editor.
4. **Export**: The current edit state can be exported to JPEG.

### Storage Model

Each imported RAW becomes a project in app-private storage.

- Non-Destructive
- A small JPEG thumbnail is stored for fast gallery display.
- Adjustments are stored as JSON and re-applied at render time.
- Search metadata is stored as a small key/value map (camera/lens/ISO/etc.).
- Tags are stored per raw.

The storage implementation lives in `app/src/main/java/com/dueckis/kawaiiraweditor/ProjectStorage.kt`.

### Native Decoder / Processor

Kotlin calls into the Rust pipeline through `app/src/main/java/com/dueckis/kawaiiraweditor/LibRawDecoder.kt`.

There are two main usage patterns:

- **One-shot decode from bytes**: used for quick tasks like generating tagger input during import/backfill.
- **Session-based decode**: used in the editor; a session handle is created once and reused for repeated preview renders.

### Preview Rendering Pipeline (Editor)

When you edit, the UI sends render requests containing the current adjustments JSON.

Depending on the **Low quality preview** setting:

- **Enabled**: a multi-stage pipeline is used:
  - super-low preview (`lowlowdecode*`) for immediate feedback while dragging
  - low preview (`lowdecode*`) for a clearer intermediate result
  - full preview (`decode*`) for the final high-quality frame
- **Disabled**: only the full preview (`decode*`) is rendered; completed renders are still applied as they finish (you may see more latency because full renders are heavier).

Settings are stored in `app/src/main/java/com/dueckis/kawaiiraweditor/AppPreferences.kt` and configured via `app/src/main/java/com/dueckis/kawaiiraweditor/SettingsScreen.kt`.

### Adjustments, Curves, Color Grading, Masks

The editor state is represented as Kotlin data classes and serialized to JSON, which is then passed to the native renderer.

- Basic sliders: brightness/contrast/highlights/shadows/etc.
- Curves: Luma/R/G/B curves.
- Color grading: shadows/midtones/highlights with blending/balance.
- Masks: brush, linear gradient, radial, AI subject mask.

Model/state classes live in `app/src/main/java/com/dueckis/kawaiiraweditor/EditorModels.kt`.

### Auto-Tagging

IRIDIS can generate tags for a RAW by rendering a preview bitmap and passing it through a CLIP-based tagger:

- Tagger: `app/src/main/java/com/dueckis/kawaiiraweditor/ClipAutoTagger.kt`
- AI subject mask generator: `app/src/main/java/com/dueckis/kawaiiraweditor/AiSubjectMaskGenerator.kt`

### Export

Export renders a full-resolution JPEG using the current adjustments JSON and writes it using Android’s media APIs:

- Media helpers: `app/src/main/java/com/dueckis/kawaiiraweditor/MediaUtils.kt`

### Settings / About

- Settings screen: `app/src/main/java/com/dueckis/kawaiiraweditor/SettingsScreen.kt`
- About dialog: `app/src/main/java/com/dueckis/kawaiiraweditor/AboutDialog.kt`

## License

AGPL-3.0 — see `LICENSE`.
