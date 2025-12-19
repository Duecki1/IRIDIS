# IRIDIS

Draft / test Android RAW editor built with Jetpack Compose + a Rust native decoder/processor.

## Screenshots

<table>
  <tr>
    <td>
      <img
        alt="Gallery"
        src="https://github.com/user-attachments/assets/fb5b933e-32ca-47fe-9640-b9c5cf9655ea"
        width="260"
      />
    </td>
    <td>
      <img
        alt="Editor"
        src="https://github.com/user-attachments/assets/1c1ecd56-cca2-4bfb-adf3-5d8253ea8b00"
        width="260"
      />
    </td>
    <td>
      <img
        alt="Editor (extra screenshot slot)"
        src="https://github.com/user-attachments/assets/5c6c3b5d-a7a1-4098-aece-e6ec35ed1372"
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

## License

AGPL-3.0 — see `LICENSE`.
