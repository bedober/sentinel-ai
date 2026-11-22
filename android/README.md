# Sentinel AI Android

Sentinel is being developed as a standalone Android Linux/security workspace. The target experience is one APK with its own terminal, Linux userland, package manager, workspace, and reviewed security/development tool catalogue. **Termux is not a product dependency.**

The standalone Linux runtime is not complete yet. See [the architecture and implementation status](../docs/ANDROID_STANDALONE_ARCHITECTURE.md). The current Kotlin app is a scanner client scaffold; the older Termux-based shell prototype is not the final architecture.

Do not describe the current APK as a Termux replacement until the PRoot runtime, rootfs installer, native PTY terminal, package manager UI, package verification, SBOM/licence notices, and physical Android tests are complete.
