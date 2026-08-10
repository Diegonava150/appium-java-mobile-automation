# ADR-005: Run JUnit 6, and prove the Allure integration rather than assuming it

**Status:** Accepted · 2026-08-09

## Context

Two version choices interact here.

**JUnit.** JUnit 6 is the current line (6.1.3 at time of writing). It keeps the Jupiter programming
model and package names, unifies Platform and Jupiter version numbers — there is no separate `1.x`
platform any more — and requires Java 17+, which this project already exceeds.

**Allure.** Two surprises:

1. `io.qameta.allure:allure-junit5` is now a **relocation stub**. The real artifact is
   `allure-jupiter`. Depending on the old coordinate still resolves, which is exactly why it is
   easy to miss.
2. `allure-jupiter:2.35.4` declares `junit-jupiter-api:5.10.3` and `junit-platform-launcher:1.10.3`.
   It is compiled against JUnit 5, and we intend to run it on JUnit 6.

The Allure integration hooks in through the JUnit Platform's `TestExecutionListener`, a stable SPI,
so it *should* work. But "should" is doing a lot of work in that sentence, and the failure mode is
silent: the listener fails to load, the results directory comes up empty, and the report is simply
missing. On a device-backed job that is easy to blame on the emulator.

## Decision

Run **JUnit 6.1.3**, use **`allure-jupiter`**, and verify the combination automatically.

`allure-jupiter` is wired into `:core`'s device-free unit tests, not just the device suites. Those
run in the quality gate on every push, in seconds, on any machine. If the listener ever stops
loading, a normal CI run says so.

The verification is not ceremonial — it was run before this decision was recorded. `./gradlew
:core:test` produced 26 Allure result files with correct test names and timings. **The combination
works.**

## Consequences

- The project sits on the current JUnit major, which is a freshness signal and avoids a migration
  later.
- Ecosystem lag is a real ongoing risk: other JUnit 5-era libraries may not be so forgiving.
  `junit-pioneer`, for instance, still targets JUnit 5 — which is one reason the retry and
  quarantine extension is written directly against the Jupiter extension model instead of taking
  that dependency.
- If a future Allure release does break on JUnit 6, the fallback is a hand-rolled
  `TestExecutionListener` on top of `allure-java-commons`. That is a modest amount of code and
  arguably a better demonstration anyway.
- Downgrading to JUnit 5.14 stays a one-line change in `gradle/libs.versions.toml`.
