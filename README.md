# IRIDIS

Android RAW editor built with Jetpack Compose + a Rust native decoder/processor.

If you are interested in this Project and want it to be continued, let me know by giving this repo a star :)

Download the newest pre-release here: [Releases](https://github.com/Duecki1/IRIDIS/releases)

## Screenshots

<table>
  <tr>
    <td>
      <img
        alt="Gallery"
        src="https://github.com/user-attachments/assets/34615820-48c3-4b3a-be25-9d5afe6268df"
        width="260"
      />
    </td>
    <td>
      <img
        alt="Editor"
        src="https://github.com/user-attachments/assets/1c80c594-7a39-4f69-8172-d45a15f63ffa"
        width="260"
      />
    </td>
    <td>
      <img
        alt="Editor (extra screenshot slot)"
        src="https://github.com/user-attachments/assets/3966ea9b-6c6d-4d2a-b677-8e4926262af7"
        width="260"
      />
    </td>
  </tr>
</table>

## Status

Inspired by/using backend processing maths from RapidRAW: https://github.com/CyberTimon/RapidRAW

## CI artifacts (GitHub Actions)

- `Android CI` uploads a debug APK artifact.
- `iOS CI (KMP shared)` always uploads:
  - `shared-xcframework` (use this in Xcode)
  - `ios-simulator-app` (runs on simulator only)
- To also get an installable `ios-ipa` (for a real device), add these repo secrets and rerun the workflow:
  - `IOS_CERT_P12_BASE64` (your signing cert `.p12`, base64-encoded)
  - `IOS_CERT_P12_PASSWORD`
  - `IOS_PROVISIONING_PROFILE_BASE64` (your `.mobileprovision`, base64-encoded)
  - `IOS_TEAM_ID`

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

- Tab for cropping and rotating the image
- S-Pen support for brush masks (line width depending on pressure)
- Clarity and such slider need to actually work
- Highlight recovery needs to be better for the highlight slider. (use the exposure slider for now)
- Holding down on the Preview should show the original picture until let go
- Use a more intuitive rating system
- Rename files
- Choose export file format and resolution 
- Automatic masks for part of landscapes (like architecture or sky)
- Be able to reorder subtract and add masks
- UI needs to be improved for masking. (And more)

## License

AGPL-3.0 — see `LICENSE`.
