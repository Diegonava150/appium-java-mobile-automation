# Mobile test automation framework — Appium 3 · Java 21

[![Quality gate](https://github.com/Diegonava150/appium-java-mobile-automation/actions/workflows/quality-gate.yml/badge.svg)](https://github.com/Diegonava150/appium-java-mobile-automation/actions/workflows/quality-gate.yml)
[![Android](https://github.com/Diegonava150/appium-java-mobile-automation/actions/workflows/android.yml/badge.svg)](https://github.com/Diegonava150/appium-java-mobile-automation/actions/workflows/android.yml)
[![Maestro smoke](https://github.com/Diegonava150/appium-java-mobile-automation/actions/workflows/maestro.yml/badge.svg)](https://github.com/Diegonava150/appium-java-mobile-automation/actions/workflows/maestro.yml)

**📊 [Live Allure report](https://diegonava150.github.io/appium-java-mobile-automation/)** — published
from every Android run, with history carried forward so trends, retries and performance drift
accumulate rather than vanishing with the run that produced them.

A cross-platform mobile test framework built around the problems that only exist on a device:
fragmentation, app lifecycle, in-place upgrades, and flake.

One test suite. Two platforms. No platform branching in any test.

> **Status: week 2 of 4.** Everything marked done below has been run on a real emulator and
> passed. Everything not yet run says so, in this README and in the code. The
> [limitations](#known-limitations) section is not boilerplate — read it.

---

## Why this exists

Most portfolio test frameworks are a page-object base class, five tests, and no CI. This one is
trying to demonstrate something narrower and harder: that the author has dealt with the failure
modes specific to mobile.

**In-place app upgrade.** Install 1.2.0, use it, then `adb install -r` 1.3.0 underneath the live
session and assert the app survived. Almost nobody automates this, because a suite that installs
fresh every time can never see a migration bug — migration bugs only exist for users who already
had the app. The state assertion is deliberately not a hard-coded number: the test measures what
survives a plain cold restart, then asserts the upgrade honours *the same* contract. Guessing at
the app's persistence design would have made the test a fiction.

**A device pool that makes port collision impossible.** Appium defaults `systemPort` (UiAutomator2)
and `wdaLocalPort` (WebDriverAgent) to fixed values, so two concurrent sessions silently share one
channel and tests start failing against the wrong device. It is the most common mobile-parallelism
bug and it is almost always misfiled as "Appium is flaky." Every port here derives from the slot
index. [ADR-002](docs/adr/0002-device-pool-parallelism.md), and `DevicePoolTest` asserts it in
milliseconds with no emulator attached.

**Flake as debt with a due date.** `@Flaky` requires a mechanism-level `reason` and a mandatory
`expires` date. Once that date passes the test **fails without running**, quoting the original
reason back. Quarantine is a loan, and this is the repayment being enforced rather than requested.
[ADR-006](docs/adr/0006-flake-as-a-debt-ledger.md).

**Failure evidence a browser cannot produce.** Screenshot, native view hierarchy, and logcat —
three independent views of the same moment, captured while the device is still on the failing
screen. This is a `TestExecutionExceptionHandler`, not a `TestWatcher`, because a watcher fires
after the driver has already quit and there is nothing left to photograph.

That last one has paid for itself twice. The first device run had two failures; the captured
hierarchy showed both were the same root cause — React Native puts `testID` on a wrapper
`ViewGroup` while the text lives in a child `TextView`, so `getText()` returned `""` rather than
erroring. One fix in `BaseScreen`, both green.

The second time was better. Three iOS tests failed in CI with "the screen is present and nothing
on it can be tapped" — the kind of message that tells you nothing. The screenshot the extension
had captured on the CI runner showed iOS's **"Save Password?"** system dialog sitting over the
catalog, which is why only the tests that signed in were affected. That was diagnosed from a
machine the author does not own, off one PNG, in about a minute. No amount of log-reading would
have found it.

---

## Quick start

**Prerequisites:** JDK 21 · Node ≥ 22.12 · Android SDK with an emulator · Appium 3.

```bash
npm install -g appium@3
appium driver install uiautomator2
```

Then:

```bash
# Emulator-free: format, compile, 20 unit tests, locator policy. ~10s, no secrets.
./gradlew qualityGate

# Fetch both builds under test (never committed — ADR-001)
./gradlew downloadAndroidApp downloadAndroidPreviousApp

# Start an emulator and `appium --port 4723`, then:
./gradlew :tests:test -Dmobile.platform=android
```

> **Windows / PowerShell:** quote the system properties, or PowerShell eats them —
> `./gradlew :tests:test "-Dmobile.platform=android"`.

Two devices:

```bash
# One Appium server per slot: 4723, 4724, …
./gradlew :tests:test -Dmobile.device.count=2 \
  -Dmobile.android.devices=emulator-5554,emulator-5556
```

Configuration is layered — thread-scoped override, then system property, then environment
variable, then `mobile.properties`, then the built-in default. Copy
[`mobile.properties.example`](mobile.properties.example) to `mobile.properties`; it is git-ignored.

---

## Layout

```
core/       driver lifecycle, device pool, adb client, JUnit extensions, flake ledger, locators
screens/    screen objects — one class per screen, no assertions
tests/      smoke (parity) · checkout · conditions · upgrade
maestro/    YAML smoke flows for the fast gate
build-logic/ Gradle convention plugins
docs/adr/   why things are the way they are
```

The lifecycle composes into one annotation:

```java
@MobileTest
class AddToCartParityTest {

    @Test
    void addingAnItemPutsItInTheCart() {
        CatalogScreen catalog = App.launch();
        catalog.openFirstItem().addToCart();

        CartScreen cart = App.navigation().openCart();

        assertThat(cart.itemCount()).isEqualTo(1);
        assertThat(cart.isCheckoutAvailable()).isTrue();
    }
}
```

That runs unchanged under `-Dmobile.platform=android` and `-Dmobile.platform=ios`. There is no
`if (isAndroid())` in it, and none anywhere in `tests/`. Where the platforms genuinely diverge —
Android's header and cart badge versus iOS's bottom tab bar — the divergence is absorbed inside
`Navigation`.

`@MobileUpgradeTest` is the same idea for a harder case: it declares its extensions in an order
that lets adb install the old build *before* the driver opens, and unsets the `app` capability so
Appium attaches rather than helpfully reinstalling the thing under test.

## Stack

| Layer      | Choice                                 | Version |
| ---------- | -------------------------------------- | ------- |
| Language   | Java (Gradle toolchain)                | 21 LTS  |
| Driver     | Appium                                 | 3.6.0   |
| Client     | `io.appium:java-client`                | 10.1.1  |
| Runner     | JUnit Jupiter + Platform               | 6.1.3   |
| Assertions | AssertJ                                | 3.27.7  |
| Build      | Gradle, Kotlin DSL, convention plugins | 9.7.0   |
| Reporting  | Allure (`allure-jupiter`)              | 2.35.4  |
| Smoke lane | Maestro (YAML)                         | latest  |

App under test: [Sauce Labs My Demo App (RN)](https://github.com/saucelabs/my-demo-app-rn),
v1.3.0 with v1.2.0 as the upgrade baseline.

Two version traps worth flagging: `allure-junit5` is now a **relocation stub** — the live artifact
is `allure-jupiter`. And JUnit 6 unified Platform and Jupiter versioning, so there is no `1.x`
platform any more. Allure 2.35.4 is compiled against Jupiter 5.10.3 while this runs JUnit 6; that
combination is **verified on every push** rather than assumed, by wiring Allure into the
device-free unit tests. [ADR-005](docs/adr/0005-junit-6-and-allure.md).

---

## Status

**Week 1 — done, verified on device**

- [x] Gradle multi-module build, convention plugins, version catalog
- [x] Driver lifecycle, device pool with per-slot port derivation, capability factory
- [x] `@MobileTest` composing the extension stack
- [x] Failure artifacts: screenshot, view hierarchy, logcat
- [x] Six screen objects, cross-platform parity suites — **6/6 green on Android**
- [x] `checkNoXPath` locator policy enforced in the build
- [x] CI: quality gate (Java 21 + 25) and Android emulator matrix with AVD snapshot caching

**Week 2 — done, verified on device**

- [x] App upgrade 1.2.0 → 1.3.0 with state-contract assertion — **3/3 green**
- [x] Device conditions: backgrounding, cold start, warm start, airplane mode — **5/5 green**
- [x] Full purchase flow, catalog to order confirmation, with form scrolling — **4/4 green**
- [x] Display conditions: dark theme, 1.3× font scale, orientation contract — **3/3 green**
- [x] `@Flaky` retry + expiry-dated quarantine + JSON ledger — **self-tested, 20 unit tests green**
- [x] Two-device parallel execution — **11/11 green, isolation verified** (see the caveat below)

**21 device tests and 20 unit tests, all green** on an Android emulator.

**CI — running for real**

- [x] Quality gate, Java 21 and 25 — green, ~1 minute
- [x] Android emulator matrix, API 31 and 34 — **green, 21/21, ~8½ minutes**
- [x] Maestro smoke — green, ~4 minutes
- [x] iOS simulator — **13 pass, 8 correctly skipped as Android-only, 1 quarantined**, ~40 minutes

iOS went 0 → passing over six CI rounds, every diagnosis made from the failure-artifact bundle
rather than by guesswork. The remaining quarantined test is
[ADR-006](docs/adr/0006-flake-as-a-debt-ledger.md) doing its job on a real case: drawer navigation
intermittently outruns the element timeout on loaded CI hardware, with a mechanism-level reason and
an expiry date after which it fails without running.

**Week 2 — not done**

- [ ] Biometric authentication — **not attempted.** Android emulator fingerprint enrolment needs a
      multi-step Settings UI flow that differs by API level. Shipping a fragile version of it
      would have been worse than saying it is not done.

**Week 3 — measurement, verified on device**

- [x] **Performance gates** — cold start from `am start -W`, jank percentiles from `gfxinfo`.
      Measured: median cold start **902 ms** against a 4 s budget, **18.4 %** janky frames against
      60 %. Asserts on the median of repeated launches and attaches every measurement pass or fail.
- [x] **Accessibility audit** — touch targets, missing labels, duplicate labels, from Google's ATF
      rule catalogue reimplemented against the page source. It found real defects (below).
- [x] **Visual regression** — per-device-profile baselines, per-channel tolerance, status bar
      excluded. Verified: **0 of 2,523,960 pixels** differ on a second run.
- [x] **Allure history on GitHub Pages** — [published live](https://diegonava150.github.io/appium-java-mobile-automation/),
      history restored from the previous report so the trend survives.
- [x] ~~Hybrid API + UI seeding with REST Assured~~ — **not possible against this app, and that is
      the finding.** The API base URL came out of the APK bundle; all four endpoints
      (`initCall`, `item-load`, `remove-item`, `checkout`) return S3 `AccessDenied`. They are
      write-only telemetry sinks, not a state API — there is no server-side cart to seed. Rejected
      the alternatives (point it at an unrelated API, stand up a proxy) as props rather than tests.
      [ADR-007](docs/adr/0007-no-api-seeding.md).

### What the accessibility audit actually found

Real defects in the app under test, not a clean bill of health:

- The **Login button** and both input fields are under the 48 dp minimum target size.
- The five rating stars are **25 dp** — about half the minimum — and every product tile reuses the
  same five labels, so a screen reader announces "review star 1" once per product with nothing to
  tell them apart.
- **Product tiles announce nothing at all.** The catalog is unusable with TalkBack, and those tiles
  are unreachable by this framework's own locator strategy for exactly the same reason — which is
  the argument in [ADR-003](docs/adr/0003-locator-strategy.md) arriving from the other direction.

These are a third-party app's defects and cannot be fixed from here, so the audit gates on *new*
findings against [a recorded baseline](tests/src/test/resources/a11y-baseline.txt) — the same shape
as the flake ledger, for the same reason: a check that is red forever gets switched off.

**Week 4 — the AI layer, as instrumentation**

- [ ] Vision-fallback locator emitting a **locator-debt report** that fails on new debt — healing
      keeps the run alive, the ledger makes you pay it back
- [ ] Failure triage over the screenshot + hierarchy + logcat bundle
- [ ] `appium-mcp` wiring so an agent can drive a live emulator to draft screen objects

All of it stays behind `@Tag("ai")` and degrades gracefully with no API key. Cloning this repo and
running it green must never require an Anthropic account.

---

## Deliberately not included

- **Detox** — React-Native-only grey-box. Overlaps the Maestro lane and couples the framework to
  the app's implementation.
- **Espresso / XCUITest native lanes** — double the maintenance surface for signal Appium already
  carries. An escalation path, not a default.
- **Paid device clouds as a hard dependency** — an optional lane is fine; a required account makes
  the repo unrunnable for whoever clones it.
- **Record-and-playback** — nothing here should produce a test a human did not review.

## Known limitations

**Two devices bought no speedup.** Measured on one host: 11 tests took 135.4 s on one emulator and
136.7 s on two. The isolation works — slot 0 took `systemPort` 8200, slot 1 took 8201, both ran
concurrently, all 11 passed — but two hardware-accelerated emulators contend for the same CPU, so
each runs at roughly half speed. **This design is a correctness mechanism first and a speed
mechanism second.** Real speedup needs genuinely separate hardware: separate runners, a device
cloud, or physical handsets. Full numbers in
[ADR-002](docs/adr/0002-device-pool-parallelism.md#postscript-what-two-devices-actually-bought-measured-2026-08-10).

**Visual regression records on CI, it does not yet gate there.** Baselines are per device profile,
and the committed one is the local emulator (API 36, 420 dpi). CI's API 31 and 34 profiles have no
baseline, so every CI run records rather than compares. The fix is to download a run's
`visual-baselines-apiNN` artifact and commit it; that is left as a deliberate step rather than
committing baselines generated on a run nobody looked at.
[Details](tests/src/test/resources/visual-baselines/README.md).

**Performance budgets are loose, on purpose.** A software-rendered emulator on a shared two-core
runner is several times slower than any handset. A threshold tuned locally would fail constantly
and train everyone to ignore the gate, so these catch a regression of the order that matters — a
launch that doubles — and the accumulated numbers in the Allure trend are the more useful signal.

**Rotation state-loss is untestable against this app.** The intended scenario was the classic
Android one — rotate mid-flow, watch the Activity get recreated, see what the app forgot to save.
The app's manifest declares `android:screenOrientation=1` (portrait), and forcing `user_rotation`
through adb does not move it either. Rather than delete the test, it became the assertion that
does hold: the app is portrait-locked and stays that way. A lock that silently disappears in a
future release would ship an untested rotation path.

**iOS is partially green.** 17 of 21 pass on a simulator. The remaining failures are all one
cause — XCUITest reports React Native's zero-size wrapper containers as invisible, so lookups
match and then fail a visibility check. Container and screen-level lookups now accept presence on
iOS while interactive elements still demand visibility; the last few cases are being worked
through. Android is unaffected and stays on the stricter check.

**Everything below was found only by pushing.** Three of the four workflows had never executed
anywhere, and every one of them failed on its first real run — an unset executable bit on
`gradlew`, a Maestro selector strategy that cannot match this app, a missing iOS bundle id, and
three separate ways of hanging or self-terminating the Android job. None of these were visible
from a green local suite. It is the clearest argument in this repository for CI being part of the
deliverable rather than a decoration on it.

**Upgrade testing is Android-only.** The iOS simulator has no equivalent of `adb install -r`, so
an in-place upgrade of an installed simulator build is not expressible. Recorded on the test with
`@EnabledOnPlatform` and a reason, not silently skipped.

**The framework depends on the app exposing `testID`s.** Intentional pressure
([ADR-003](docs/adr/0003-locator-strategy.md)), but a screen without them needs the documented
`// xpath-ok:` escape hatch.

**The app under test is someone else's release.** Its bugs are not fixable from here, and a
deleted release would break the upgrade suite's baseline.

---

## Decisions

Every non-obvious choice is written down in [`docs/adr/`](docs/adr/). Start with
[ADR-002](docs/adr/0002-device-pool-parallelism.md) if you only read one — including its
postscript, where the headline feature is measured and found not to do what the marketing version
of this README would have claimed.
