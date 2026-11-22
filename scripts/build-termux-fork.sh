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
# Add Sentinel's first native GUI screen inside the forked application.
# It is the APK launcher; the terminal remains the upstream TermuxActivity.
dashboard_source = r"""package com.termux.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.regex.Pattern;

public final class SentinelDashboardActivity extends Activity {
    private static final String PREFIX = "/data/data/com.termux/files/usr";
    private EditText targetInput;
    private EditText portsInput;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xFFEAF0FF);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private EditText field(String hint, String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value);
        input.setHint(hint);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF9AA8C2);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF6688CC));
        return input;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF101827);
        getWindow().setNavigationBarColor(0xFF101827);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(20));
        page.setBackgroundColor(0xFF101827);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);

        page.addView(text("SENTINEL AI", 27, true));
        page.addView(text("ANDROID SECURITY WORKSTATION", 12, true));
        page.addView(text("A standalone Linux terminal and authorized security testing workspace.", 14, false));
        page.addView(button("OPEN LINUX TERMINAL", this::openTerminal));
        page.addView(text("QUICK TOOL — NMAP TCP CONNECT SCAN", 17, true));

        targetInput = field("Target hostname or IP address", "");
        page.addView(targetInput);
        portsInput = field("Ports (example: 22,80,443 or 1-1000)", "22,80,443");
        page.addView(portsInput);
        page.addView(button("INSTALL / UPDATE NMAP", () ->
            runInTerminal(PREFIX + "/bin/pkg", new String[]{"install", "-y", "nmap"})));
        page.addView(button("RUN AUTHORIZED TCP SCAN", this::runNmap));
        page.addView(text("Only scan systems you own or have explicit permission to test. This workflow uses TCP connect mode (-sT), does not attempt stealth or exploit actions, and opens output in Sentinel's built-in terminal.", 12, false));
        page.addView(text("TOOL STATUS", 17, true));
        page.addView(text("Nmap is installed on demand from the configured Termux package repository. Other catalogue entries are not represented as installed until their packages and adapters are verified.", 13, false));
        page.addView(button("OPEN TOOL TERMINAL / PACKAGE MANAGER", this::openTerminal));
        page.addView(text("Sentinel AI • Local device execution • No separate Termux app required", 11, false));

        setContentView(scroll);
    }

    private void openTerminal() {
        Intent intent = new Intent();
        intent.setClassName(getPackageName(), "com.termux.app.TermuxActivity");
        startActivity(intent);
    }

    private void runInTerminal(String executable, String[] args) {
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), "com.termux.app.TermuxService");
            intent.setAction("com.termux.service_execute");
            intent.setData(Uri.fromFile(new File(executable)));
            intent.putExtra("com.termux.execute.arguments", args);
            intent.putExtra("com.termux.execute.workdir", PREFIX + "/home");
            startService(intent);
            Toast.makeText(this, "Command sent to Sentinel terminal.", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Could not start command: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void runNmap() {
        String host = targetInput.getText().toString().trim();
        String ports = portsInput.getText().toString().trim();
        Pattern hostPattern = Pattern.compile("(?i)^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?[.] )*");
        hostPattern = Pattern.compile("(?i)^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?[.])*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$|^(?:[0-9]{1,3}[.]){3}[0-9]{1,3}$");
        Pattern portsPattern = Pattern.compile("^[0-9, -]{1,100}$");
        if (!hostPattern.matcher(host).matches()) {
            targetInput.setError("Enter a valid hostname or IPv4 address");
            return;
        }
        if (!portsPattern.matcher(ports).matches()) {
            portsInput.setError("Use numeric ports, commas and ranges only");
            return;
        }
        for (String part : ports.replace(" ", "").split(",")) {
            String[] range = part.split("-", -1);
            try {
                int first = Integer.parseInt(range[0]);
                int last = range.length == 1 ? first : Integer.parseInt(range[1]);
                if (range.length > 2 || first < 1 || last > 65535 || first > last) throw new NumberFormatException();
            } catch (NumberFormatException invalid) {
                portsInput.setError("Ports must be between 1 and 65535");
                return;
            }
        }
        runInTerminal(PREFIX + "/bin/nmap", new String[]{"-sT", "-Pn", "-T3", "--reason", "-p", ports.replace(" ", ""), host});
    }
}
"""
dashboard_file = root / "app/src/main/java/com/termux/app/SentinelDashboardActivity.java"
dashboard_file.parent.mkdir(parents=True, exist_ok=True)
dashboard_file.write_text(dashboard_source, encoding="utf-8")

manifest_path = root / "app/src/main/AndroidManifest.xml"
manifest = manifest_path.read_text(encoding="utf-8")
launcher_filter = """            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
"""
if manifest.count(launcher_filter) != 1:
    raise SystemExit("Could not uniquely identify Termux launcher filter")
manifest = manifest.replace(launcher_filter, "", 1)
activity_anchor = """        <activity
            android:name=".app.TermuxActivity"
"""
dashboard_manifest = """        <activity
            android:name=".app.SentinelDashboardActivity"
            android:exported="true"
            android:label="Sentinel AI"
            android:theme="@style/Theme.TermuxActivity.DayNight.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

"""
if activity_anchor not in manifest:
    raise SystemExit("Could not find upstream TermuxActivity manifest entry")
manifest = manifest.replace(activity_anchor, dashboard_manifest + activity_anchor, 1)
manifest_path.write_text(manifest, encoding="utf-8")

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
