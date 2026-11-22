# Third-party notices for Sentinel AI Android

Sentinel AI's Android application code and terminal UI are original project code. The Linux userland is distributed separately from Android and uses the following third-party components:

## PRoot

- Project: https://github.com/green-green-avk/proot
- Android build scripts: https://github.com/green-green-avk/build-proot-android
- Builder commit: 01f83b8841358450c78333d1b33ab30d4943bec4
- Upstream source version selected by the builder: PRoot 0.15_release
- License: GNU General Public License version 2 or later (GPL-2.0-or-later). Because the PRoot binary is built with talloc under LGPL-3.0-or-later, the combined PRoot/talloc binary is distributed under the GPLv3-compatible terms; the APK includes the GPL-3.0 text as well as the upstream GPLv2 COPYING file.
- The APK build embeds the corresponding PRoot source archive and COPYING notice under app assets/licenses.

## talloc

- Upstream source version selected by the pinned PRoot builder: 2.1.14
- Project: https://talloc.samba.org/
- License: GNU Lesser General Public License version 3 or later (LGPL-3.0-or-later).
- The APK build embeds the corresponding talloc source archive and the official LGPL-3.0 HTML licence text under app assets/licenses.

## Android PRoot builder

- Project: https://github.com/green-green-avk/build-proot-android
- License: MIT
- Its LICENSE notice is included in the APK assets/licenses directory.

## Alpine Linux

- Project: https://alpinelinux.org/
- The application downloads the official Alpine 3.24.4 minirootfs for arm64-v8a at first setup and verifies its published SHA-256 checksum before extraction.
- Alpine packages are installed by Alpine's apk package manager from configured signed repositories. Each package retains its upstream licence and package metadata in the guest environment.

The app does not bundle Termux's application UI or copy Termux application code. PRoot is a separately licensed, open-source process compatibility tool used as a runtime component; the app's Compose interface, installer orchestration, package catalogue UI and scanner are maintained in this repository.
