#!/usr/bin/env bash
set -euo pipefail
# Reproducible source build of the Sentinel AI Termux application fork.
# Keep com.termux: official bootstrap binaries have this prefix compiled in.
readonly TERMUX_REPO="https://github.com/termux/termux-app.git"
readonly TERMUX_COMMIT="8629e632fcb95da272221be327db653fb24befe9"
readonly DEST="${1:-$PWD/.build/termux-fork}"
rm -rf "$DEST"
mkdir -p "$(dirname "$DEST")"
git clone --filter=blob:none "$TERMUX_REPO" "$DEST"
git -C "$DEST" checkout --detach "$TERMUX_COMMIT"
python3 - "$DEST" <<'PY'
from pathlib import Path
import sys
import re
root = Path(sys.argv[1])
def replace_once(path, old, new):
    file = root / path
    data = file.read_text(encoding="utf-8")
    if old not in data:
        raise SystemExit("Expected upstream marker missing in " + path + ": " + old)
    file.write_text(data.replace(old, new, 1), encoding="utf-8")
replace_once("app/build.gradle",
    'manifestPlaceholders.TERMUX_APP_NAME = "Termux"',
    'manifestPlaceholders.TERMUX_APP_NAME = "Sentinel AI"')
replace_once("app/build.gradle",
    'versionName "0.118.0"',
    'versionName "0.118.0"\n\n        // Sentinel first release target: 64-bit ARM Android devices.\n        ndk { abiFilters "arm64-v8a" }')
# The fork APK targets ARM64 only. Avoid downloading unused bootstrap archives
# for 32-bit ARM and x86 devices in this first Android release.
app_gradle = root / "app/build.gradle"
gradle_data = app_gradle.read_text(encoding="utf-8")
removed, count = re.subn(
    r'(?m)^\s*downloadBootstrap\("(?:arm|i686|x86_64)",[^\r\n]*\)\r?\n',
    "",
    gradle_data,
)
if count != 6:
    raise SystemExit("Expected to remove six non-ARM64 bootstrap downloads across both variants; removed " + str(count))
gradle_data = removed
app_gradle.write_text(gradle_data, encoding="utf-8")
replace_once("app/src/main/res/values/strings.xml",
    '<!ENTITY TERMUX_APP_NAME "Termux">',
    '<!ENTITY TERMUX_APP_NAME "Sentinel AI">')
constants = "termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java"
replace_once(constants,
    'public static final String TERMUX_APP_NAME = "Termux";',
    'public static final String TERMUX_APP_NAME = "Sentinel AI";')
replace_once(constants,
    'public static final String TERMUX_GITHUB_ORGANIZATION_NAME = "termux";',
    'public static final String TERMUX_GITHUB_ORGANIZATION_NAME = "bedober";')
replace_once(constants,
    'public static final String TERMUX_GITHUB_REPO_NAME = "termux-app";',
    'public static final String TERMUX_GITHUB_REPO_NAME = "sentinel-ai";')
shared_strings = root / "termux-shared/src/main/res/values/strings.xml"
if shared_strings.exists():
    data = shared_strings.read_text(encoding="utf-8")
    data = data.replace('<!ENTITY TERMUX_APP_NAME "Termux">', '<!ENTITY TERMUX_APP_NAME "Sentinel AI">')
    shared_strings.write_text(data, encoding="utf-8")
(root / "SENTINEL_FORK_NOTICE.md").write_text(
    "# Sentinel AI Android application fork\n\n"
    "Based on Termux Android application source commit "
    "8629e632fcb95da272221be327db653fb24befe9 "
    "(https://github.com/termux/termux-app). The upstream app is GPL-3.0-only; "
    "component exceptions are documented in LICENSE.md and termux-shared/LICENSE.md. "
    "Those upstream notices and source headers are retained.\n\n"
    "Sentinel AI branding and repository links are modifications by bedober/sentinel-ai. "
    "This is a standalone APK and does not require a separately installed Termux app.\n\n"
    "The Android application id remains com.termux because the upstream bootstrap "
    "binaries have /data/data/com.termux/files/usr compiled into them. A custom id "
    "requires rebuilding the bootstrap and package repository with a matching prefix. "
    "This fork cannot coexist with the official Termux app under a different id; "
    "uninstall the official app first if Android reports a package/signature conflict.\n",
    encoding="utf-8")
PY
export TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS=0
export TERMUX_APP_VERSION_NAME="${SENTINEL_TERMUX_VERSION:-0.1.0-sentinel}"
(
  cd "$DEST"
  chmod +x ./gradlew
  ./gradlew --no-daemon :app:assembleDebug
)
APK="$(find "$DEST/app/build/outputs/apk/debug" -maxdepth 1 -type f -name '*.apk' | head -n 1)"
test -n "$APK" && test -s "$APK"
OUTPUT="${SENTINEL_TERMUX_APK_OUTPUT:-$PWD/sentinel-ai-termux-fork-debug.apk}"
mkdir -p "$(dirname "$OUTPUT")"
cp "$APK" "$OUTPUT"
echo "Built standalone Sentinel AI Termux-fork APK: $OUTPUT"
