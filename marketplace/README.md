# Marketplace screenshots

Slides for the JetBrains Marketplace listing are composed by
[mpshots](https://github.com/jreznot/mpshots) from raw IDE captures in `screenshots/`.
Slide copy, layout and crops live in `shots.yaml`.

Needs Node.js 18.17+. Taking new captures needs macOS on a Retina display.

## Captures

`screenshots/*.png` are taken by hand, one per slide, each named after that slide's `id` in
`shots.yaml`. They come from a sandbox IDE (`./gradlew runIde`, which skips Android Studio's
first-run wizard) with the plugin's settings already configured, opened on a project with enough
modules to be worth showing. The current set uses
[nowinandroid](https://github.com/android/nowinandroid) with the build directory shown and `Docs`
and `CI` file groups defined.

In `⌃⇧⌘4`, pressing Space and then `⌥`-clicking the window grabs the window alone — no desktop
behind it, no drop shadow — which on Retina gives the 2560×1440 mpshots expects. A menu or dialog
that reaches outside the window needs a dragged region instead and comes out another size;
`validate` warns about that, but the crop still works.

## Build

```bash
cd marketplace && npx --yes github:jreznot/mpshots#v0.1.0 build
```

Slides land in `out/`, which is git-ignored. Open `out/index.html` to see them at roughly the
size the Marketplace carousel shows.

## Ship

Upload `out/*.png` to the plugin's Marketplace page. `plugin-install.png` is not a slide. It is
only for the GitHub README.

## Adjusting a crop

```bash
cd marketplace && npx --yes github:jreznot/mpshots#v0.1.0 grid screenshots/overview.png
```

Read the rectangle off `screenshots/overview.grid.png` and update that slide's `crop` in
`shots.yaml`. Crops are measured on the image, not the window: captures taken with the drop
shadow put the window itself at an offset, +111+75 for the current set.

How large the IDE ends up is `zoom = max(rect.w / crop.w, rect.h / crop.h)`, where `rect` is
1600×700 for `hero` and 928×980 for `text-left` and `text-right`. With `uiScale: 2` and a 2560
output, zoom has to stay between 0.62, below which IDE text stops being legible, and 0.79, above
which the capture is upscaled and looks soft. `validate` warns on both.
