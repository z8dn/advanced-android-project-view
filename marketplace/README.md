# Marketplace screenshots

Slides for the JetBrains Marketplace listing are composed by
[mpshots](https://github.com/jreznot/mpshots) from raw IDE captures in `screenshots/`.
The slide copy and layout live in `shots.yaml`.

## 1. Capture

Use Android Studio with the default dark theme (Darcula / Islands Dark) and a sample Android
project with a few modules. Keep personal data out of the frame.

- **Size:** 2560×1440. On a Retina Mac, capture a 1280×720 window at 100% zoom (`uiScale: 2`).
  On a 1x display, use **View › Appearance › Zoom IDE** at 150% and set `uiScale: 1.5` in `shots.yaml`.
- **No window shadow:** on macOS, hold <kbd>Option</kbd> when clicking the window in
  <kbd>⇧⌘4</kbd> <kbd>Space</kbd> mode.

| File | What to show |
|---|---|
| `overview.png` | Android view with `build/` expanded under `app`, and a file group with a few files in it |
| `build-directory.png` | Project pane ⋮ › Appearance menu open, with **Display Build Directory** checked |
| `file-groups.png` | Settings › Tools › Advanced Android Project Tree with two or three groups |
| `group-dialog.png` | Edit Custom File Group with patterns including one `!` exclusion, and the match preview filled |
| `search-scope.png` | Find in Files, with the scope dropdown open on a project file group |

## 2. Frame and build

Requires Node.js 18.17+. mpshots is installed from GitHub, not npm, so the tag below is the pin.

```bash
cd marketplace
npx --yes github:jreznot/mpshots#v0.1.0 grid screenshots/overview.png
```

The grid image gives pixel coordinates for each slide's `crop` and `highlight` in `shots.yaml`.
A tighter crop makes the IDE text larger.

```bash
npx --yes github:jreznot/mpshots#v0.1.0 build
```

Slides are written to `out/` (git-ignored). Open `out/index.html` to see them at roughly carousel
size, then upload the PNGs to the Marketplace listing.
