# Claude-Red Android

A lightweight Android reader and launcher for [SnailSploit/claude-red](https://github.com/SnailSploit/claude-red).

## Features
- Downloads the public skill manifest from GitHub.
- Browses skills by category and searches names/descriptions.
- Loads each SKILL.md from upstream.
- Shares the selected skill with Claude (or another compatible app) through Android's share sheet.
- Android 6.0+; designed for Android 12 phones.

## Important
Claude-Red is a collection of Markdown instruction files. It is not an executable scanner, exploit framework, terminal, or AI model. This app is a skill browser/launcher; Claude must be installed or accessible separately to interpret a shared skill. The app does not execute commands from skill files.

## Build
Requirements: JDK 17, Android SDK platform 35, Android Gradle Plugin 8.7.3, Gradle 8.9.

Run: gradle :app:assembleDebug

APK: app/build/outputs/apk/debug/app-debug.apk. GitHub Actions builds and uploads a debug APK on every push to main.

## Use
1. Install the APK and open it.
2. Tap Load / refresh skills.
3. Choose a category or search.
4. Open a skill and tap Share to Claude.
5. Select Claude from the share sheet, or copy the text manually.

An internet connection is required to fetch the manifest and skill documents. Only public GitHub content is fetched. No API key or account password is requested or stored.

## Safety
Use security material only for systems you own or have explicit authorization to assess. This app is a reader and does not run tests against remote systems.

## Attribution
Skill content is fetched at runtime from SnailSploit/claude-red and remains the property of its respective authors. The upstream repository states that it is MIT licensed; see its repository and license for current terms. This Android wrapper is separately MIT licensed.
