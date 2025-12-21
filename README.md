# IRIDIS

Draft / test Android RAW editor built with Jetpack Compose + a Rust native decoder/processor.

If you are interested in this Project and want it to be continued, let me know by giving this repo a star :)

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

## License

AGPL-3.0 — see `LICENSE`.
