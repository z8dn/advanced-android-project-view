# Marketplace screenshots

Slides for the JetBrains Marketplace listing are composed by
[mpshots](https://github.com/jreznot/mpshots) from raw IDE captures in `screenshots/`.
Slide copy, layout and crops live in `shots.yaml`.

Needs macOS on the built-in Retina display, an Android SDK, and Node.js 18.17+.

## 1. Start the IDE

```bash
marketplace/capture.sh ide
```

This clones [nowinandroid](https://github.com/android/nowinandroid) to `~/a2pt-sample`
(override with `A2PT_SAMPLE`), builds it once so `build/` has content, preloads the plugin
settings (build directory on, `Docs` and `CI` file groups), and opens the project in a sandbox
Android Studio with the plugin installed. Wait for Gradle sync to finish.

## 2. Capture

```bash
marketplace/capture.sh
```

The script resizes the IDE window to 1280×720 pt, then walks through each shot. For each one it
says what to put on screen, counts down 8 seconds with a tick per second, captures exactly the
window, and checks the result is 2560×1440. Press `r` to retake.

Afterwards it builds the slides into `out/` (git-ignored), opens `out/index.html`, and offers to
commit and push the captures.

To retake only some shots:

```bash
marketplace/capture.sh group-dialog search-scope
```

The first run asks for Accessibility (window resize) and Screen Recording (capture) permission
for your terminal.

## 3. Ship

Upload `out/*.png` to the plugin's Marketplace page. `plugin-install.png` is not a slide. It is
only for the GitHub README.

## Adjusting a crop

```bash
cd marketplace && npx --yes github:jreznot/mpshots#v0.1.0 grid screenshots/overview.png
```

Read the rectangle off `screenshots/overview.grid.png` and update that slide's `crop` in
`shots.yaml`. Keep the width and height close to the existing values so IDE text stays legible.
`validate` warns if a crop makes it too small or upscaled.
