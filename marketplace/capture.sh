#!/usr/bin/env bash
# Marketplace screenshots, end to end. macOS with a Retina display.
#
#   marketplace/capture.sh ide       sample project + preconfigured sandbox IDE
#   marketplace/capture.sh           capture every shot, build the slides, offer to commit
#   marketplace/capture.sh <name>…   retake only these shots, then rebuild
#
# Only the arranging of each screen is manual; everything else is scripted.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DIR="$ROOT/marketplace"
SHOTS="$DIR/screenshots"
SAMPLE="${A2PT_SAMPLE:-$HOME/a2pt-sample}"
MPSHOTS="github:jreznot/mpshots#v0.1.0"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

# Capture rectangle in points. On Retina this yields 2560×1440.
X=100 Y=100 W=1280 H=720
DELAY=8

NAMES=(overview build-directory file-groups group-dialog search-scope plugin-install)
HINTS=(
  "Android view: expand app › build and the Docs group. Open README.md in the editor."
  "During the countdown: project pane ⋮ › Appearance, hover so the submenu shows Display Build Directory checked."
  "Settings (⌘,) › Tools › Advanced Android Project Tree, both groups listed. Dialog inside the IDE window."
  "In that settings page select Docs and click ✎. Wait for the match preview to fill."
  "During the countdown: ⇧⌘F › Scope › open the dropdown on the Project File Groups section."
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
  (cd "$ROOT" && ./gradlew -q prepareSandbox)
  local plugins
  plugins=$(find "$ROOT/build" "$ROOT/.intellijPlatform" -type d -name plugins -path '*sandbox*' 2>/dev/null | head -1)
  [[ -n $plugins ]] || die "Sandbox not found after prepareSandbox."
  local options
  options="$(dirname "$plugins")/config/options"
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

tick() { afplay /System/Library/Sounds/Tink.aiff 2>/dev/null & }
done_sound() { afplay /System/Library/Sounds/Glass.aiff 2>/dev/null & }

fit_window() {
  bold "Click the IDE's main window within 3 seconds. It will be resized to ${W}×${H}."
  sleep 3
  osascript -e "tell application \"System Events\" to set {position, size} of front window of (first process whose frontmost is true) to {{$X, $Y}, {$W, $H}}" \
    || die "Resize failed. Allow Terminal under System Settings › Privacy & Security › Accessibility."
  done_sound
}

capture() {
  local name=$1 hint=$2 out="$SHOTS/$1.png"
  while true; do
    echo
    bold "▸ $name"
    echo "  $hint"
    read -rp "  Enter, then switch to the IDE (${DELAY}s countdown, one tick per second) " _
    for ((i = 0; i < DELAY; i++)); do tick; sleep 1; done
    screencapture -x -R"$X,$Y,$W,$H" "$out" \
      || die "Capture failed. Allow Terminal under System Settings › Privacy & Security › Screen Recording."
    done_sound
    local size
    size=$(sips -g pixelWidth -g pixelHeight "$out" | awk '/pixel/ {print $2}' | paste -sdx -)
    echo "  saved $name.png ($size)"
    [[ $size == 2560x1440 ]] || echo "  ⚠ expected 2560×1440. Is the window on the built-in Retina display?"
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
