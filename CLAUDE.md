# Working in this repository

Notes for an agent — or a new contributor, who needs the same things — working on this
framework. Everything here is a constraint that is enforced somewhere, not a style preference.

## Run this before you claim anything works

```bash
./gradlew qualityGate
```

Format, compile, the locator policy, and every device-free unit test — about a minute, no
emulator, no API key. It names its tasks explicitly instead of delegating to `check`, because
`check` in `:tests` correctly includes device-backed tests and "the gate is green" must never
quietly mean "the gate skipped everything".

The device suite is separate and needs a running emulator plus an Appium server:

```bash
./gradlew :tests:test                          # Android, the default
./gradlew :tests:test -Dmobile.platform=ios    # macOS only
```

`JAVA_HOME` must point at a JDK 21. On a machine with Android Studio and nothing else, that is
usually `C:\Program Files\Android\Android Studio\jbr` or `/Applications/Android
Studio.app/Contents/jbr/Contents/Home`.

## Invariants the build enforces

| Rule | Why | Enforced by |
| --- | --- | --- |
| One locator strategy: `Locators.id("<testID>")` | React Native's `testID` surfaces as `content-desc` on Android and the accessibility id on iOS, so one call resolves on both. XPath forces a full hierarchy walk and splits one concept across two syntaxes. | `./gradlew checkNoXPath` (ADR-003) |
| Explicit waits only — never an implicit wait | Mixing the two makes the effective timeout an undocumented interaction of both values, varying by command. | ADR-004, by convention and review |
| Screen objects expose intent and return the next screen; they never assert | A failure message should describe what the test wanted, not what the page did. | Review |
| Quarantined tests carry an expiry date | An exemption with no deadline is a permanent one. | `QuarantinePolicy`, which fails the build on an expired `@Flaky` (ADR-006) |
| AI-healed locators must be accepted explicitly | Otherwise healing deletes the report instead of fixing the test. | `./gradlew checkLocatorDebt` (ADR-009) |

If you need to break one of these, the escape hatch exists and is deliberately visible — for
XPath, `// xpath-ok: <reason>` on the line. Use it and justify it; do not weaken the check.

## Read the ADRs before changing an approach

`docs/adr/` records why each choice was made over the alternatives that also would have worked.
Several of them contain measurements that contradict the obvious answer — ADR-002 records that a
two-device pool produced **no speedup** on a single host, which is the kind of thing worth
knowing before "optimising" the parallelism.

ADR-009 is the one to read if you only read one. It argues that self-healing locators, the
headline feature of most commercial tools in this space, are an anti-feature, and then builds the
useful half of them on credit.

## Three debt ledgers, one idea

Flakiness (`FlakeLedger`), accessibility (`a11y-baseline.txt`) and healed locators
(`locator-debt.txt`) all work the same way, because they all face the same failure mode: a check
that is red forever gets switched off. So each records the debt that already exists, gates only
on regressions, and reports entries that have stopped being needed so the file cannot grow
monotonically. If you add a fourth kind of check, copy this shape.

## The AI layer is optional, and must stay that way

`:ai` is on the test **runtime** classpath only. No test and nothing in `:core` may import a type
from it — `core` declares the `LocatorFallback` interface and finds implementations through
`ServiceLoader`. Deleting `include("ai")` from `settings.gradle.kts` must leave a framework that
compiles and runs. If a change would break that, it is the wrong change.

Check it before you claim a change is safe:

```bash
./gradlew qualityGate -Pmobile.ai.absent=true    # quote the flag in PowerShell
```

CI runs exactly that on every push, as the "Deletable AI layer" job.

It is also off by default: `-Dmobile.ai.locatorFallback=true` plus a key. A test run should not
start making network calls on the strength of an exported environment variable.

## MCP

`.mcp.json` registers `appium-mcp`, which exposes a live device to an agent — inspect the
hierarchy, try a locator, take a screenshot, without writing a throwaway test to do it. It needs
a running emulator and Appium server, the same as `:tests:test`.

Use it for exploration. Do not use it to guess at locators and paste them in: the app's `testID`
values are the source of truth, and a locator that works once against a running device may be one
the policy bans.

## Honesty about what is verified

This repository states in several places what has and has not actually been run. Keep doing that.
The live Anthropic calls in `:ai` have never been executed — there is no key — and ADR-009 says
so in as many words. Do not quietly upgrade "expected to work" into "works".
