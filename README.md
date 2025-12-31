# IRIDIS

Android RAW editor built with Jetpack Compose + a Rust native decoder/processor.

If you are interested in this Project and want it to be continued, let me know by giving this repo a star and sharing it :)

Download the newest pre-release here: [Releases](https://github.com/Duecki1/IRIDIS/releases)

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

## Status

Inspired by RapidRAW: https://github.com/CyberTimon/RapidRAW

## Features (current)

- Import RAW files via Android’s document picker (SAF).
- Gallery with thumbnails, multi-select, bulk export, and star ratings.
- Editor with fast preview rendering (super-low → low → full).
- Adjustments: brightness, contrast, highlights/shadows, whites/blacks, saturation, temperature/tint, vibrance, etc.
- Curves (Luma/R/G/B) + histogram overlay.
- Color grading (shadows/midtones/highlights) + blending/balance.
- Effects: vignette.
- Masks: brush, linear gradient, radial, and AI subject mask (lasso-assisted).
- S-Pen support for brush masks (line width depending on pressure)
- Choose export file format and resolution 
- Automatic masks for part of landscapes (like architecture or sky)
- Button to switch between unedited and edited preview
- Widget showing all edits
- Tab for cropping and rotating the image (Buggy with masks)

## Planned/Issues:

- Clarity and such slider need to actually work
- Highlight recovery needs to be better for the highlight slider. (use the exposure slider for now)
- Use a more intuitive rating system
- Rename files
- Be able to reorder subtract and add masks
- UI needs to be improved for masking. (And more)
- Fix overlapping Add and subtract submasks generate hard cut
- Tag system for mask names
- Mask preview needs improvement
- Subject masks should be stored
- Save calculated AI Masks

## License

AGPL-3.0 — see `LICENSE`.
