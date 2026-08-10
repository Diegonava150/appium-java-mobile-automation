# ADR-001: Use a downloaded third-party app as the system under test

**Status:** Accepted · 2026-08-09

## Context

A test framework needs something to test. The options were:

1. **Write our own demo app.** Total control over testability — deep links, mock backends,
   deliberately awkward widgets. But it doubles the project: now there are two codebases to
   maintain, and a reviewer has to trust that the app was not quietly shaped to make the tests
   pass.
2. **Use a toy APK** such as `ApiDemos`. Cheap, but it has no coherent user journey, so the tests
   read as widget pokes rather than as scenarios anyone recognises.
3. **Use a realistic, publicly published app.**

The framework also needs to demonstrate app-upgrade testing, which requires at least two
consecutively versioned builds of the same app.

## Decision

Use [Sauce Labs' My Demo App (React Native)](https://github.com/saucelabs/my-demo-app-rn), pinned
at **v1.3.0**, downloaded at build time by `./gradlew downloadAndroidApp`.

It fits because:

- Releases ship an Android `.apk`, an **iOS simulator** `.zip`, and a real-device `.ipa`. Genuine
  cross-platform parity is possible without an Apple developer account.
- It has a real journey: catalog → product → cart → checkout, plus login, biometric
  authentication, geolocation, and a webview.
- Every interactive element carries a React Native `testID`, which is what makes ADR-003 possible.
- **v1.2.0 and v1.3.0 both publish APKs**, so the upgrade suite has two real builds to move
  between rather than a synthetic version bump.

Binaries are **never committed**. `.gitignore` excludes `*.apk`, `*.ipa`, and `*.app/`.

## Consequences

- Every clone is small and fast. A 30 MB APK in git is a permanent cost paid by everyone forever,
  and it goes stale the moment upstream cuts a release.
- CI needs a network fetch before the device job. It is a few seconds against a GitHub release CDN,
  and it is cacheable.
- The framework is coupled to someone else's release cadence. If Sauce Labs deletes v1.2.0, the
  upgrade suite loses its baseline. Mitigation: the version constants live in one place in
  `build.gradle.kts`, and the release archive is stable in practice.
- We inherit the app's bugs and cannot fix them. That is the honest situation of every QA
  engineer, so it is arguably the point.
