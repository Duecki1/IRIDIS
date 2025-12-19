<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <meta name="color-scheme" content="dark light" />
    <title>KawaiiRawEditor — Android RAW editor (Compose + Rust)</title>
    <meta
      name="description"
      content="KawaiiRawEditor is a draft mobile RAW photo editor built with Jetpack Compose and a Rust native decoder/processor."
    />
    <style>
      :root {
        --bg: #0b0e14;
        --surface: rgba(255, 255, 255, 0.06);
        --surface-2: rgba(255, 255, 255, 0.09);
        --border: rgba(255, 255, 255, 0.12);
        --text: rgba(255, 255, 255, 0.92);
        --muted: rgba(255, 255, 255, 0.70);
        --muted-2: rgba(255, 255, 255, 0.58);
        --shadow: 0 18px 40px rgba(0, 0, 0, 0.45);
        --mono: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
        --sans: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, "Apple Color Emoji",
          "Segoe UI Emoji";
        --radius: 18px;
        --radius-sm: 12px;
        --accent-a: #ff4dd8;
        --accent-b: #36d1ff;
        --accent-c: #69f0ae;
        --link: #8fe1ff;
        --warning: #ffd166;
        --danger: #ff6b6b;
        --ok: #69f0ae;
        --code-bg: rgba(0, 0, 0, 0.45);
        --chip: rgba(255, 255, 255, 0.08);
      }

      @media (prefers-color-scheme: light) {
        :root {
          --bg: #f7f8fb;
          --surface: rgba(0, 0, 0, 0.04);
          --surface-2: rgba(0, 0, 0, 0.06);
          --border: rgba(0, 0, 0, 0.10);
          --text: rgba(0, 0, 0, 0.88);
          --muted: rgba(0, 0, 0, 0.64);
          --muted-2: rgba(0, 0, 0, 0.55);
          --shadow: 0 18px 40px rgba(0, 0, 0, 0.15);
          --link: #0066cc;
          --code-bg: rgba(0, 0, 0, 0.06);
          --chip: rgba(0, 0, 0, 0.05);
        }
      }

      html[data-theme="dark"] {
        color-scheme: dark;
      }
      html[data-theme="light"] {
        color-scheme: light;
      }
      html[data-theme="dark"] :root,
      html[data-theme="dark"] {
        --bg: #0b0e14;
        --surface: rgba(255, 255, 255, 0.06);
        --surface-2: rgba(255, 255, 255, 0.09);
        --border: rgba(255, 255, 255, 0.12);
        --text: rgba(255, 255, 255, 0.92);
        --muted: rgba(255, 255, 255, 0.70);
        --muted-2: rgba(255, 255, 255, 0.58);
        --shadow: 0 18px 40px rgba(0, 0, 0, 0.45);
        --link: #8fe1ff;
        --code-bg: rgba(0, 0, 0, 0.45);
        --chip: rgba(255, 255, 255, 0.08);
      }
      html[data-theme="light"] :root,
      html[data-theme="light"] {
        --bg: #f7f8fb;
        --surface: rgba(0, 0, 0, 0.04);
        --surface-2: rgba(0, 0, 0, 0.06);
        --border: rgba(0, 0, 0, 0.10);
        --text: rgba(0, 0, 0, 0.88);
        --muted: rgba(0, 0, 0, 0.64);
        --muted-2: rgba(0, 0, 0, 0.55);
        --shadow: 0 18px 40px rgba(0, 0, 0, 0.15);
        --link: #0066cc;
        --code-bg: rgba(0, 0, 0, 0.06);
        --chip: rgba(0, 0, 0, 0.05);
      }

      * {
        box-sizing: border-box;
      }
      body {
        margin: 0;
        font-family: var(--sans);
        background: radial-gradient(1200px 600px at 20% 0%, rgba(255, 77, 216, 0.18), transparent 60%),
          radial-gradient(900px 520px at 85% 15%, rgba(54, 209, 255, 0.16), transparent 60%),
          radial-gradient(800px 520px at 45% 85%, rgba(105, 240, 174, 0.10), transparent 60%), var(--bg);
        color: var(--text);
        line-height: 1.55;
      }

      a {
        color: var(--link);
        text-decoration: none;
      }
      a:hover {
        text-decoration: underline;
      }

      .container {
        max-width: 1060px;
        margin: 0 auto;
        padding: 28px 18px 60px;
      }

      .topbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 16px;
        position: sticky;
        top: 0;
        padding: 14px 0;
        z-index: 10;
        backdrop-filter: blur(10px);
      }
      .topbar::before {
        content: "";
        position: absolute;
        inset: 0;
        background: linear-gradient(to bottom, rgba(0, 0, 0, 0.26), rgba(0, 0, 0, 0));
        pointer-events: none;
        opacity: 0.7;
      }
      @media (prefers-color-scheme: light) {
        .topbar::before {
          background: linear-gradient(to bottom, rgba(255, 255, 255, 0.85), rgba(255, 255, 255, 0));
          opacity: 1;
        }
      }

      .topbar > * {
        position: relative;
      }

      .brand {
        display: flex;
        align-items: center;
        gap: 10px;
        min-width: 0;
      }

      .logo {
        width: 38px;
        height: 38px;
        border-radius: 12px;
        background: linear-gradient(135deg, var(--accent-a), var(--accent-b), var(--accent-c));
        box-shadow: 0 12px 26px rgba(0, 0, 0, 0.35);
      }

      .brand .title {
        font-weight: 800;
        letter-spacing: -0.02em;
        font-size: 16px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .brand .subtitle {
        color: var(--muted-2);
        font-size: 12px;
        margin-top: -2px;
      }

      .actions {
        display: flex;
        gap: 10px;
        align-items: center;
        flex-wrap: wrap;
        justify-content: flex-end;
      }

      .btn {
        border: 1px solid var(--border);
        background: var(--surface);
        color: var(--text);
        padding: 9px 12px;
        border-radius: 999px;
        cursor: pointer;
        box-shadow: none;
        font-size: 13px;
        line-height: 1;
      }
      .btn:hover {
        background: var(--surface-2);
      }

      .btn.primary {
        border: none;
        background: linear-gradient(135deg, rgba(255, 77, 216, 0.95), rgba(54, 209, 255, 0.95));
        color: #081019;
        font-weight: 700;
      }

      .hero {
        padding: 26px 0 12px;
      }
      .hero h1 {
        margin: 0;
        font-size: clamp(32px, 4vw, 46px);
        letter-spacing: -0.03em;
        line-height: 1.1;
      }
      .hero p {
        margin: 10px 0 0;
        color: var(--muted);
        max-width: 74ch;
        font-size: 15.5px;
      }

      .chips {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
        margin-top: 14px;
      }
      .chip {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        padding: 6px 10px;
        border: 1px solid var(--border);
        background: var(--chip);
        border-radius: 999px;
        font-size: 12.5px;
        color: var(--muted);
      }
      .chip strong {
        color: var(--text);
        font-weight: 700;
      }

      .grid {
        display: grid;
        grid-template-columns: 1fr;
        gap: 14px;
        margin-top: 18px;
      }
      @media (min-width: 860px) {
        .grid {
          grid-template-columns: 1fr 1fr;
        }
      }

      .card {
        border: 1px solid var(--border);
        background: linear-gradient(180deg, var(--surface), transparent 120%);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        padding: 16px;
        overflow: hidden;
      }

      .card h2,
      .card h3 {
        margin: 0 0 10px;
        letter-spacing: -0.015em;
      }

      .card h2 {
        font-size: 18px;
      }
      .card h3 {
        font-size: 16px;
      }

      .card p {
        margin: 0 0 10px;
        color: var(--muted);
      }

      .card ul {
        margin: 0;
        padding-left: 18px;
        color: var(--muted);
      }
      .card li {
        margin: 6px 0;
      }

      code,
      pre {
        font-family: var(--mono);
      }

      pre {
        margin: 10px 0 0;
        padding: 12px;
        border-radius: var(--radius-sm);
        background: var(--code-bg);
        border: 1px solid var(--border);
        overflow-x: auto;
      }
      pre code {
        font-size: 12.5px;
        color: var(--text);
      }

      .toc {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        margin-top: 12px;
      }
      .toc a {
        border: 1px solid var(--border);
        background: var(--surface);
        padding: 8px 10px;
        border-radius: 999px;
        font-size: 13px;
        color: var(--muted);
      }
      .toc a:hover {
        background: var(--surface-2);
        text-decoration: none;
        color: var(--text);
      }

      .callout {
        border: 1px solid var(--border);
        border-radius: var(--radius);
        padding: 14px;
        background: linear-gradient(180deg, rgba(255, 209, 102, 0.12), transparent 120%);
      }
      .callout strong {
        color: var(--warning);
      }

      .callout.danger {
        background: linear-gradient(180deg, rgba(255, 107, 107, 0.12), transparent 120%);
      }
      .callout.danger strong {
        color: var(--danger);
      }

      .callout.ok {
        background: linear-gradient(180deg, rgba(105, 240, 174, 0.12), transparent 120%);
      }
      .callout.ok strong {
        color: var(--ok);
      }

      .screenshots {
        display: grid;
        grid-template-columns: 1fr;
        gap: 12px;
        margin-top: 12px;
      }
      @media (min-width: 900px) {
        .screenshots {
          grid-template-columns: 1fr 1fr;
        }
      }
      .shot {
        border-radius: var(--radius);
        overflow: hidden;
        border: 1px solid var(--border);
        background: var(--surface);
      }
      .shot img {
        display: block;
        width: 100%;
        height: auto;
      }
      .shot figcaption {
        padding: 10px 12px;
        color: var(--muted-2);
        font-size: 12.5px;
      }

      .footer {
        margin-top: 22px;
        color: var(--muted-2);
        font-size: 12.5px;
      }

      .sep {
        height: 1px;
        background: var(--border);
        margin: 18px 0;
      }

      .k {
        color: var(--text);
        font-weight: 700;
      }
    </style>
  </head>
  <body>
    <div class="container">
      <div class="topbar" role="banner">
        <div class="brand" aria-label="KawaiiRawEditor">
          <div class="logo" aria-hidden="true"></div>
          <div style="min-width: 0">
            <div class="title">KawaiiRawEditor</div>
            <div class="subtitle">Android RAW editor • Jetpack Compose + Rust</div>
          </div>
        </div>
        <div class="actions">
          <a class="btn" href="#build">Build</a>
          <a class="btn" href="#usage">Use</a>
          <a class="btn" href="#ai">AI mask</a>
          <a class="btn" href="#license">License</a>
          <button class="btn" id="themeToggle" type="button" aria-label="Toggle theme">
            Theme: <span class="state">auto</span>
          </button>
        </div>
      </div>

      <header class="hero">
        <h1>Mobile RAW editing, the fun way.</h1>
        <p>
          <span class="k">KawaiiRawEditor</span> is a draft / test Android port inspired by RapidRAW. It combines a Jetpack
          Compose UI with a Rust native decoder/processor to preview and export JPEGs from RAW files.
        </p>

        <div class="chips" aria-label="Project metadata">
          <div class="chip"><strong>App</strong> Android (Compose)</div>
          <div class="chip"><strong>Native</strong> Rust cdylib (JNI)</div>
          <div class="chip"><strong>minSdk</strong> 26</div>
          <div class="chip"><strong>compileSdk</strong> 36</div>
          <div class="chip"><strong>License</strong> AGPL-3.0</div>
        </div>

        <div class="toc" aria-label="Table of contents">
          <a href="#screenshots">Screenshots</a>
          <a href="#features">Features</a>
          <a href="#build">Build from source</a>
          <a href="#usage">Using the app</a>
          <a href="#ai">AI subject mask</a>
          <a href="#storage">Storage</a>
          <a href="#tech">Tech notes</a>
          <a href="#credits">Credits</a>
          <a href="#license">License</a>
        </div>
      </header>

      <div class="sep" role="separator"></div>

      <section id="screenshots" class="card">
        <h2>Screenshots</h2>
        <p>Current UI (from the repo’s original README):</p>
        <div class="screenshots">
          <figure class="shot">
            <img
              alt="KawaiiRawEditor screenshot (gallery)"
              src="https://github.com/user-attachments/assets/c1088104-acea-435e-a874-04b29267131b"
              loading="lazy"
              decoding="async"
            />
            <figcaption>Gallery view</figcaption>
          </figure>
          <figure class="shot">
            <img
              alt="KawaiiRawEditor screenshot (editor)"
              src="https://github.com/user-attachments/assets/f662f9fc-3bde-4c8d-8344-9ae6784a31da"
              loading="lazy"
              decoding="async"
            />
            <figcaption>Editor view</figcaption>
          </figure>
        </div>
      </section>

      <div class="grid">
        <section id="features" class="card">
          <h2>Features (current)</h2>
          <ul>
            <li>Import RAW files via Android’s document picker (SAF).</li>
            <li>Gallery with thumbnails, multi-select, bulk export, and star ratings.</li>
            <li>Editor with fast preview rendering (super-low → low → full preview pipeline).</li>
            <li>Adjustments: brightness, contrast, highlights/shadows, whites/blacks, saturation, temperature/tint, vibrance.</li>
            <li>Curves (Luma/R/G/B) + histogram overlay.</li>
            <li>Color grading (shadows/midtones/highlights) + blending/balance.</li>
            <li>Effects: vignette controls.</li>
            <li>Masks: brush, linear gradient, radial, and AI subject mask (lasso-assisted).</li>
            <li>Export full-resolution JPEG to <span class="k">Pictures/KawaiiRawEditor</span>.</li>
          </ul>
        </section>

        <section class="card">
          <h2>Status</h2>
          <div class="callout">
            <p style="margin: 0">
              <strong>Draft / test project:</strong> Expect rough edges, performance tuning in progress, and features changing quickly.
            </p>
          </div>
          <p style="margin-top: 12px">
            Upstream inspiration: <a href="https://github.com/CyberTimon/RapidRAW">RapidRAW</a>.
          </p>
        </section>
      </div>

      <div class="sep" role="separator"></div>

      <section id="build" class="card">
        <h2>Build from source</h2>
        <p>
          The Android app module (<code>app/</code>) depends on a Rust shared library (<code>rust/</code>). The Gradle build is set up to
          compile Rust for all Android ABIs during <code>preBuild</code>.
        </p>

        <h3>Requirements</h3>
        <ul>
          <li>Android Studio (or Gradle CLI) with Android SDK Platform <span class="k">36</span> installed.</li>
          <li>Android NDK <span class="k">27.0.12077973</span> (pinned in <code>app/build.gradle.kts</code>).</li>
          <li>Rust toolchain (minimum stated in <code>rust/Cargo.toml</code>: <span class="k">rust 1.70+</span>).</li>
          <li>Rust Android targets (see commands below).</li>
        </ul>

        <h3>One-time setup</h3>
        <pre><code># From repo root
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android

# Ensure your Android SDK location is set (Android Studio usually creates this file)
cat local.properties
# sdk.dir=/path/to/Android/Sdk</code></pre>

        <h3>Build</h3>
        <pre><code># Debug build (also builds Rust and copies .so into app/src/main/jniLibs)
./gradlew :app:assembleDebug

# Install to a connected device/emulator
./gradlew :app:installDebug</code></pre>

        <h3>Notes</h3>
        <div class="callout ok">
          <p style="margin: 0">
            <strong>Rust build is automatic:</strong> <code>app/build.gradle.kts</code> wires <code>preBuild → copyRustLibs → buildRust</code>.
          </p>
        </div>
      </section>

      <div class="sep" role="separator"></div>

      <section id="usage" class="card">
        <h2>Using the app</h2>
        <ul>
          <li><span class="k">Gallery</span>: tap <span class="k">+</span> to import a RAW file. Long-press to multi-select.</li>
          <li><span class="k">Bulk export</span>: select multiple items and tap the download action.</li>
          <li><span class="k">Editor</span>: adjust sliders, curves, and color grading; export JPEG when ready.</li>
          <li><span class="k">Masks</span>: add a mask, choose a submask type (brush/gradient/radial/subject), then paint/position it.</li>
        </ul>
      </section>

      <div class="sep" role="separator"></div>

      <section id="ai" class="card">
        <h2>AI subject mask (important)</h2>

        <div class="callout danger">
          <p style="margin: 0">
            <strong>Network download:</strong> the first time you use the AI subject mask, the app downloads the U<sup>2</sup>-Net ONNX
            model from Hugging Face and caches it on-device.
          </p>
        </div>

        <p style="margin-top: 12px">
          This behavior is implemented in <code>app/src/main/java/com/dueckis/kawaiiraweditor/AiSubjectMaskGenerator.kt</code> and explains
          the <code>android.permission.INTERNET</code> entry in <code>app/src/main/AndroidManifest.xml</code>.
        </p>

        <h3>Privacy note</h3>
        <ul>
          <li>The app downloads a model file; it does not upload your photos for inference.</li>
          <li>Once downloaded (and verified via SHA-256), the model is stored under the app’s internal storage.</li>
        </ul>
      </section>

      <div class="sep" role="separator"></div>

      <section id="storage" class="card">
        <h2>Storage</h2>
        <p>
          Imported RAW files and edits are stored in the app’s internal files directory via <code>ProjectStorage</code>:
        </p>
        <pre><code>filesDir/
  projects/
    &lt;project-id&gt;/
      image.raw
      adjustments.json
      thumbnail.jpg
  projects.json</code></pre>
        <p style="margin-top: 10px">
          Exports are written to the system gallery (MediaStore), under <span class="k">Pictures/KawaiiRawEditor</span> (Android 10+).
        </p>
      </section>

      <div class="sep" role="separator"></div>

      <section id="tech" class="card">
        <h2>Tech notes</h2>
        <ul>
          <li><span class="k">UI</span>: Kotlin + Jetpack Compose (Material 3).</li>
          <li><span class="k">Native</span>: Rust <code>cdylib</code> loaded via JNI (<code>LibRawDecoder</code>).</li>
          <li><span class="k">RAW pipeline</span>: decoding + processing happens in Rust (see <code>rust/</code>).</li>
          <li><span class="k">AI</span>: on-device ONNX Runtime; downloads U<sup>2</sup>-Net model on demand.</li>
        </ul>
      </section>

      <div class="sep" role="separator"></div>

      <section id="credits" class="card">
        <h2>Credits</h2>
        <ul>
          <li>Inspired by / forked from <a href="https://github.com/CyberTimon/RapidRAW">RapidRAW</a>.</li>
          <li>Rust RAW processing uses the vendored <code>rawler</code> crate (<code>rust/third_party/rawler</code>).</li>
          <li>AI segmentation uses ONNX Runtime and the U<sup>2</sup>-Net model.</li>
        </ul>
      </section>

      <div class="sep" role="separator"></div>

      <section id="license" class="card">
        <h2>License</h2>
        <p>
          This project is licensed under <span class="k">AGPL-3.0</span>. See <code>LICENSE</code> in the repository root.
        </p>
      </section>

      <div class="footer">
        Tip: Open this file locally in your browser (<code>README.html</code>) or publish it via GitHub Pages if you want a hosted landing page.
      </div>
    </div>

    <script>
      (() => {
        const key = "kawaiiraweditor-theme";
        const root = document.documentElement;
        const saved = localStorage.getItem(key);
        if (saved === "dark" || saved === "light") {
          root.dataset.theme = saved;
        }

        const setButtonState = (btn) => {
          const state = root.dataset.theme || "auto";
          btn.querySelector(".state").textContent = state;
        };

        document.addEventListener("DOMContentLoaded", () => {
          const btn = document.getElementById("themeToggle");
          if (!btn) return;
          setButtonState(btn);
          btn.addEventListener("click", () => {
            const current = root.dataset.theme || "auto";
            const next = current === "auto" ? "dark" : current === "dark" ? "light" : "auto";
            if (next === "auto") {
              delete root.dataset.theme;
              localStorage.removeItem(key);
            } else {
              root.dataset.theme = next;
              localStorage.setItem(key, next);
            }
            setButtonState(btn);
          });
        });
      })();
    </script>
  </body>
</html>

