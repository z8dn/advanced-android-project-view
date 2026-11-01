#!/usr/bin/env bash
# Marketplace screenshots, end to end. macOS with a Retina display.
#
#   marketplace/capture.sh ide       sample project + preconfigured sandbox IDE
#   marketplace/capture.sh           capture every shot, build the slides, offer to commit
#   marketplace/capture.sh <name>…   retake only these shots, then rebuild
#
# You arrange each screen and take the shot yourself with ⌃⇧⌘4, which copies it to the clipboard.
# The script sizes the window, saves what you copied, and builds the slides.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DIR="$ROOT/marketplace"
SHOTS="$DIR/screenshots"
SAMPLE="${A2PT_SAMPLE:-$HOME/a2pt-sample}"
MPSHOTS="github:jreznot/mpshots#v0.1.0"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

# Window size in points. ⌥-clicking the window in ⌃⇧⌘4 › Space mode then yields 2560×1440 on
# Retina, which is one of the sizes mpshots expects.
X=100 Y=100 W=1280 H=720

NAMES=(overview build-directory file-groups group-dialog search-scope plugin-install)
HINTS=(
  "Android view: expand app › build and the Docs group. Open README.md in the editor."
  "Project pane ⋮ › Appearance, hovered so the submenu shows Display Build Directory checked."
  "Settings (⌘,) › Tools › Advanced Android Project Tree, both groups listed. Dialog inside the IDE window."
  "In that settings page select Docs and click ✎. Wait for the match preview to fill."
  "⇧⌘F › Scope, with the dropdown open on the Project File Groups section."
  "Settings › Plugins › Marketplace, search \"Advanced Android Project Tree\". Used by the GitHub README only."
)

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
die() { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }
[[ $(uname) == Darwin ]] || die "Run this on macOS."

ide() {
  if [[ ! -d $SAMPLE ]]; then
    bold "Cloning the sample project into $SAMPLE"
    git clone --depth 1 https://github.com/android/nowinandroid.git "$SAMPLE"
  fi
  [[ -d $SDK ]] || die "Android SDK not found at $SDK. Set ANDROID_HOME."
  echo "sdk.dir=$SDK" > "$SAMPLE/local.properties"

  bold "Building the sample once so build/ has generated sources"
  (cd "$SAMPLE" && ./gradlew -q :app:assembleDemoDebug)

  bold "Preparing the sandbox IDE"
  local config
  config=$(cd "$ROOT" && ./gradlew -q --console=plain prepareSandbox printSandboxConfigDirectory | tail -1)
  [[ -d $config ]] || die "Sandbox config directory not found after prepareSandbox: ${config:-<empty>}"
  local options="$config/options"
  mkdir -p "$options"
  cat > "$options/androidViewSettings.xml" <<'XML'
<application>
  <component name="AndroidViewSettings">
    <option name="showBuildDirectory" value="true" />
    <option name="projectFileGroups">
      <list>
        <ProjectFileGroup>
          <option name="groupName" value="Docs" />
          <option name="patterns">
            <list>
              <option value="*.md" />
              <option value="!docs/*.md" />
            </list>
          </option>
        </ProjectFileGroup>
        <ProjectFileGroup>
          <option name="groupName" value="CI" />
          <option name="patterns">
            <list>
              <option value=".github/workflows/*.yml" />
            </list>
          </option>
        </ProjectFileGroup>
      </list>
    </option>
  </component>
</application>
XML
  echo "Plugin settings written to $options"

  bold "Starting the sandbox IDE (log: build/runIde.log)"
  (cd "$ROOT" && nohup ./gradlew runIde --args="$SAMPLE" > build/runIde.log 2>&1 &)
  echo
  echo "When the project has synced, run:  marketplace/capture.sh"
}

done_sound() { afplay /System/Library/Sounds/Glass.aiff 2>/dev/null & }

# A ⌃⇧⌘4 screenshot lands on the pasteboard, not on disk. AppleScript is the only way to write it
# out without a Homebrew dependency such as pngpaste. Exits non-zero when the clipboard holds no
# image, which is how the caller tells "nothing captured" from "captured".
clipboard_png() {
  osascript - "$1" <<'OSA'
on run argv
  set outPath to item 1 of argv
  try
    set pngData to (the clipboard as «class PNGf»)
  on error
    error "no image on the clipboard" number 1
  end try
  set fh to open for access (POSIX file outPath) with write permission
  try
    set eof fh to 0
    write pngData to fh
  end try
  close access fh
end run
OSA
}

frontmost_window_geometry() {
  osascript -e 'tell application "System Events" to get {position, size} of front window of (first process whose frontmost is true)' 2>/dev/null | tr -d ' ' || true
}

fit_window() {
  bold "Click the IDE's main window within 3 seconds. It will be resized to ${W}×${H}."
  sleep 3
  # The sandbox IDE runs straight from the JBR, so it is the process named "java" rather than
  # "Android Studio". Going through whatever is frontmost avoids having to name it, and leaves
  # a real Android Studio alone if one is also open.
  osascript <<OSA || die "Resize failed. Allow your terminal under System Settings › Privacy & Security › Accessibility."
tell application "System Events"
  tell front window of (first process whose frontmost is true)
    set position to {$X, $Y}
    set size to {$W, $H}
  end tell
end tell
OSA
  local got
  got=$(frontmost_window_geometry)
  if [[ $got == "$X,$Y,$W,$H" ]]; then
    echo "  window at $got"
  else
    # A fullscreen window silently keeps its own geometry, and every capture afterwards would be
    # off without saying why.
    die "Window is at ${got:-unknown}, expected $X,$Y,$W,$H. Take the IDE out of fullscreen and retry."
  fi
  done_sound
}

capture() {
  local name=$1 hint=$2 out="$SHOTS/$1.png" answer size
  while true; do
    echo
    bold "▸ $name"
    echo "  $hint"
    echo "  Take the shot with ⌃⇧⌘4 (⌃ keeps it on the clipboard instead of writing a file)."
    echo "  Space then ⌥-click the window captures it whole, without the desktop or a shadow."
    read -rp "  Enter once it is on the clipboard " _
    if ! clipboard_png "$out" 2>/dev/null; then
      echo "  ⚠ No image on the clipboard. ⇧⌘4 alone saves to a file; hold ⌃ as well to copy."
      continue
    fi
    done_sound
    size=$(sips -g pixelWidth -g pixelHeight "$out" | awk '/pixel/ {print $2}' | paste -sdx -)
    echo "  saved $name.png ($size)"
    # mpshots wants 2560×1440 here. A region selection around an open menu will not be that, which
    # is a warning and not an error: the crop still works, `validate --strict` just complains.
    [[ $size == 2560x1440 ]] || echo "  ⚠ not 2560×1440. Fine for a menu shot, otherwise ⌥-click the window."
    open -g "$out"
    read -rp "  Enter to keep, r to retake " answer
    [[ $answer == r ]] || break
  done
}

build() {
  bold "Building slides"
  cd "$DIR"
  npx --yes "$MPSHOTS" build
  open out/index.html
}

commit() {
  read -rp "Commit and push the captures? [y/N] " answer
  [[ $answer == y ]] || return 0
  cd "$ROOT"
  git add marketplace/screenshots
  git commit -m "docs: add raw Marketplace captures"
  git push
}

shots() {
  mkdir -p "$SHOTS"
  fit_window
  local only="$*"
  for i in "${!NAMES[@]}"; do
    if [[ -n $only && " $only " != *" ${NAMES[$i]} "* ]]; then continue; fi
    capture "${NAMES[$i]}" "${HINTS[$i]}"
  done
  build
  commit
}

case "${1:-}" in
  ide) ide ;;
  -h|--help) sed -n '2,8p' "$0" ;;
  *) shots "$@" ;;
esac
