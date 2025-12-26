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
        src="https://github.com/user-attachments/assets/6b79a493-a9c6-4c6a-bdfe-0eaa9517adc6"
        width="260"
      />
    </td>
    <td>
      <img
        alt="Editor"
        src="https://github.com/user-attachments/assets/397e808f-f8bc-4779-8340-5296cad6484b"
        width="260"
      />
    </td>
    <td>
      <img
        alt="Editor (extra screenshot slot)"
        src="https://github.com/user-attachments/assets/2cb83367-9959-4c09-b629-1aeffd6873aa"
        width="260"
      />
    </td>
  </tr>
</table>

## Status

Inspired by/using backend processing maths from RapidRAW: https://github.com/CyberTimon/RapidRAW

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
