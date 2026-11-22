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
import android.os.Handler;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

public final class SentinelDashboardActivity extends Activity {
    private static final String PREFIX = "/data/data/com.termux/files/usr";
    private static final String OUT = PREFIX + "/home/.sentinel-command-output.txt";
    private static final String RC = PREFIX + "/home/.sentinel-command-status.txt";
    private final Handler handler = new Handler();
    private EditText targetInput;
    private EditText portsInput;
    private TextView statusView;
    private TextView outputView;

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
        page.addView(text("Standalone Linux terminal • local tools • authorized testing", 14, false));
        page.addView(button("OPEN LINUX TERMINAL", this::openTerminal));
        page.addView(text("NMAP — TCP CONNECT SCAN", 17, true));
        targetInput = field("Authorized target hostname or IPv4", "");
        page.addView(targetInput);
        portsInput = field("Ports (22,80,443 or 1-1000)", "22,80,443");
        page.addView(portsInput);
        page.addView(button("INSTALL / UPDATE NMAP", () ->
            runCaptured("Installing Nmap", PREFIX + "/bin/pkg", new String[]{"install", "-y", "nmap"})));
        page.addView(button("RUN AUTHORIZED TCP SCAN", this::runNmap));
        page.addView(text("EXECUTION STATUS", 16, true));
        statusView = text("Idle — no command running", 13, true);
        page.addView(statusView);
        page.addView(text("SCAN / COMMAND OUTPUT", 16, true));
        outputView = text("Output will appear here after a command starts.", 12, false);
        outputView.setTextIsSelectable(true);
        page.addView(outputView);
        page.addView(text("TOOL CATALOGUE — 21 REQUESTED TOOLS", 17, true));
        String[][] tools = new String[][]{
            {"Nmap", "GUI workflow present • install on demand • device test pending"},
            {"Masscan", "Not integrated • raw packet privileges/root generally required"},
            {"Amass", "Not integrated • package/build and adapter not verified"},
            {"Gobuster", "Not integrated • package/build and adapter not verified"},
            {"ffuf", "Not integrated • package/build and adapter not verified"},
            {"Nessus", "External server/product • licensed install required • not bundled"},
            {"OpenVAS / Greenbone", "External server stack required • not bundled"},
            {"Nikto", "Not integrated • package/build and adapter not verified"},
            {"Burp Suite", "External desktop/commercial product • not bundled"},
            {"OWASP ZAP", "Not integrated • Java/RAM constraints • adapter not verified"},
            {"sqlmap", "Not integrated • package/build and adapter not verified"},
            {"Metasploit Framework", "Not integrated • heavy isolated lab setup required"},
            {"BloodHound", "External services/database and collector required"},
            {"Impacket", "Not integrated • Python dependencies and adapter unverified"},
            {"NetExec", "Not integrated • dependencies and adapter unverified"},
            {"LinPEAS / WinPEAS", "Target-host scripts • not an Android app package"},
            {"Hashcat", "Not integrated • supported GPU/OpenCL backend unverified"},
            {"John the Ripper", "Not integrated • package/build and adapter not verified"},
            {"Hydra", "Not integrated • package/build and adapter not verified"},
            {"Aircrack-ng", "Not integrated • monitor mode/root/external Wi-Fi hardware may be required"},
            {"BeEF", "External service/server required • not bundled"}
        };
        for (String[] item : tools) {
            page.addView(text(item[0] + " — " + item[1], 12, false));
        }
        page.addView(text("A catalogue entry is not a claim that the tool is installed or executable. Only Nmap has a dashboard workflow at this stage. Use tools only on systems you own or are authorized to assess.", 12, false));
        page.addView(text("Sentinel AI • Local device execution • No separate Termux app required", 11, false));
        setContentView(scroll);
    }

    private void openTerminal() {
        Intent intent = new Intent();
        intent.setClassName(getPackageName(), "com.termux.app.TermuxActivity");
        startActivity(intent);
    }

    private String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private String readFile(String path) {
        File file = new File(path);
        if (!file.isFile()) return "";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) value.append(line).append('\n');
        } catch (Exception ignored) { }
        return value.toString();
    }

    private void pollResult(int attempt) {
        if (outputView == null || statusView == null) return;
        String output = readFile(OUT);
        if (!output.isEmpty()) outputView.setText(output);
        String rc = readFile(RC).trim();
        if (!rc.isEmpty()) {
            statusView.setText("Finished • exit code " + rc);
            return;
        }
        if (attempt < 600) handler.postDelayed(() -> pollResult(attempt + 1), 1000);
        else statusView.setText("Still running or status unavailable • check the built-in terminal");
    }

    private void runCaptured(String label, String executable, String[] args) {
        if (!new File(PREFIX + "/bin/sh").isFile()) {
            Toast.makeText(this, "Open the built-in terminal once and complete first-run setup.", Toast.LENGTH_LONG).show();
            openTerminal();
            return;
        }
        StringBuilder command = new StringBuilder("rm -f ").append(quote(RC)).append("; ");
        command.append(quote(executable));
        for (String arg : args) command.append(' ').append(quote(arg));
        command.append(" > ").append(quote(OUT)).append(" 2>&1; result=$?; printf '%s' \"$result\" > ").append(quote(RC));
        try {
            new File(OUT).delete();
            new File(RC).delete();
            statusView.setText(label + " • RUNNING");
            outputView.setText("Waiting for command output…");
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), "com.termux.app.TermuxService");
            intent.setAction("com.termux.service_execute");
            intent.setData(Uri.fromFile(new File(PREFIX + "/bin/sh")));
            intent.putExtra("com.termux.execute.arguments", new String[]{"-c", command.toString()});
            intent.putExtra("com.termux.execute.workdir", PREFIX + "/home");
            startService(intent);
            handler.postDelayed(() -> pollResult(0), 1000);
        } catch (Exception error) {
            statusView.setText("FAILED TO START");
            outputView.setText(error.toString());
        }
    }

    private void runNmap() {
        String host = targetInput.getText().toString().trim();
        String ports = portsInput.getText().toString().trim();
        Pattern hostPattern = Pattern.compile("(?i)^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?[.])*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$|^(?:[0-9]{1,3}[.]){3}[0-9]{1,3}$");
        Pattern portsPattern = Pattern.compile("^[0-9, -]{1,100}$");
        if (!hostPattern.matcher(host).matches()) {
            targetInput.setError("Enter a valid hostname or IPv4 address");
            return;
        }
        if (!portsPattern.matcher(ports).matches()) {
            portsInput.setError("Use numeric ports, commas and ranges only");
            return;
        }
        if (host.matches("^[0-9.]+$")) {
            String[] octets = host.split("[.]");
            if (octets.length != 4) {
                targetInput.setError("Enter a valid IPv4 address");
                return;
            }
            for (String octet : octets) {
                try {
                    int value = Integer.parseInt(octet);
                    if (value < 0 || value > 255) throw new NumberFormatException();
                } catch (NumberFormatException invalid) {
                    targetInput.setError("IPv4 octets must be between 0 and 255");
                    return;
                }
            }
        }
        String normalized = ports.replace(" ", "");
        String[] entries = normalized.split(",", -1);
        if (entries.length > 32) {
            portsInput.setError("Use at most 32 port entries");
            return;
        }
        int count = 0;
        for (String entry : entries) {
            String[] range = entry.split("-", -1);
            try {
                int first = Integer.parseInt(range[0]);
                int last = range.length == 1 ? first : Integer.parseInt(range[1]);
                if (range.length > 2 || first < 1 || last > 65535 || first > last) throw new NumberFormatException();
                count += last - first + 1;
                if (count > 4096) throw new NumberFormatException();
            } catch (NumberFormatException invalid) {
                portsInput.setError("Ports must be 1–65535; maximum 4,096 ports per scan");
                return;
            }
        }
        runCaptured("Nmap TCP scan", PREFIX + "/bin/nmap",
            new String[]{"-sT", "-Pn", "-T3", "--reason", "-p", normalized, host});
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

# Install Sentinel's custom launcher artwork for legacy and adaptive launchers.
# The source PNG is committed at assets/android/sentinel-ai-launcher.png.
icon_source = Path(__file__).resolve().parents[1] / "assets/android/sentinel-ai-launcher.png"
if not icon_source.is_file():
    raise SystemExit("Missing Sentinel launcher artwork: " + str(icon_source))
res = root / "app/src/main/res"
for density in ("mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"):
    folder = res / density
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "ic_launcher.png").write_bytes(icon_source.read_bytes())
    (folder / "ic_launcher_round.png").write_bytes(icon_source.read_bytes())
adaptive = res / "mipmap-anydpi-v26/ic_launcher.xml"
adaptive.parent.mkdir(parents=True, exist_ok=True)
adaptive.write_text("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/black"/>
    <foreground android:drawable="@drawable/sentinel_launcher_foreground"/>
</adaptive-icon>
""", encoding="utf-8")
round_adaptive = res / "mipmap-anydpi-v26/ic_launcher_round.xml"
round_adaptive.write_text(adaptive.read_text(encoding="utf-8"), encoding="utf-8")
foreground = res / "drawable-nodpi/sentinel_launcher_foreground.png"
foreground.parent.mkdir(parents=True, exist_ok=True)
foreground.write_bytes(icon_source.read_bytes())

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
