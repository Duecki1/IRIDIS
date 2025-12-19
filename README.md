# KawaiiRawEditor

Draft / test Android RAW editor built with Jetpack Compose + a Rust native decoder/processor.

Pretty web version: [`README.html`](README.html)

## Screenshots

<table>
  <tr>
    <td>
      <img
        alt="Gallery"
        src="https://github.com/user-attachments/assets/c1088104-acea-435e-a874-04b29267131b"
        width="260"
      />
    </td>
    <td>
      <img
        alt="Editor"
        src="https://github.com/user-attachments/assets/f662f9fc-3bde-4c8d-8344-9ae6784a31da"
        width="260"
      />
    </td>
    <td>
      <img
        alt="Editor (extra screenshot slot)"
        src="https://github.com/user-attachments/assets/f662f9fc-3bde-4c8d-8344-9ae6784a31da"
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
- Export full-resolution JPEG to `Pictures/KawaiiRawEditor`.

## License

AGPL-3.0 — see `LICENSE`.
