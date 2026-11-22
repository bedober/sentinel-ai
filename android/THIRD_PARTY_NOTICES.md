# Third-party notices for Sentinel AI Android

## Termux application source

The full-fork Android build is based on:

- Project: https://github.com/termux/termux-app
- Pinned upstream commit: `8629e632fcb95da272221be327db653fb24befe9`
- Licence: GPL-3.0-only for the Termux app source.
- Exceptions and component-specific terms: retained upstream `LICENSE.md` and `termux-shared/LICENSE.md`.
- The fork build script preserves upstream licence files, source headers and copyright notices in its build tree. A generated `SENTINEL_FORK_NOTICE.md` identifies the source revision and Sentinel modifications.

The application id currently remains `com.termux` because official Termux bootstrap binaries have that path compiled into them. Rebranding the display name does not change those binary paths. A unique application id requires rebuilding the bootstrap and packages with a custom prefix.

## Previous PRoot/Alpine prototype

The existing Compose/PRoot prototype and its separate notices remain in the repository while the full Termux fork is integrated. They are not bundled into the Termux-fork APK by the fork build script.

## Sentinel AI

Sentinel-specific dashboard, security adapters and branding are project modifications. Each third-party security tool and package retains its own licence; no proprietary tool is redistributed without the required rights.
