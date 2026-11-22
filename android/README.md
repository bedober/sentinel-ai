# Sentinel AI Android / Termux mode

The local Linux environment is provided by Termux, not by a privileged Android container.

1. Install Termux from F-Droid or the official Termux GitHub releases.
2. Follow [the Termux shell setup guide](../docs/ANDROID_TERMUX_SHELL.md).
3. Start the local shell from Termux using `./scripts/termux-shell.sh`.

The Android app remains a native client scaffold. It does not yet embed or control Termux sessions. A remote website cannot access the phone's localhost terminal. The next native-app milestone is an explicit, user-approved local connection screen and WebView/terminal integration; this must be tested on a physical Android device before claiming support.
