# ADR-010: Reuse an Appium session across a class, by opt-in, with a real reset

**Status:** Accepted · 2026-08-30

## Context

The iOS lane runs about 36 minutes. Where that time goes was not obvious until the Appium log was
kept on green runs as well as red ones, and the answer turned out to be almost all of it:

```
14 sessions · 21.1 min · 58% of the run
```

Per session, roughly two thirds is spent after Appium has already decided to reuse the cached
WebDriverAgent — attaching to it, and confirming the protocol. That work is identical every time
and is discarded when the session ends.

The first instinct was to make each session cheaper. That road is closed, and it is worth writing
down why so nobody spends another week on it: **this runner's variance is larger than any change
we could make.** Three runs have produced cold starts of 432 s, 273 s and 494 s. Two conclusions
have already been drawn from single runs and then withdrawn — a "30% faster" figure, and a belief
that the simulator warm-up step had cut the cold start by 160 s, which the next run contradicted
with a number worse than before the step existed. See the comment above the `Session timings` step
in `.github/workflows/ios.yml`.

So a decision here cannot be justified on seconds measured once. It has to rest on something
runner-independent. **Session count is that thing:** it is a property of the suite, and it does
not vary with how busy the machine is.

## Decision

`@MobileTest(session = SessionScope.PER_CLASS)` opens one session for a class and resets the app
between its tests. `SessionScope.PER_TEST` remains the default.

**Opt-in, not opt-out.** A class choosing `PER_CLASS` is asserting something about itself, and that
assertion should be visible in the class rather than inherited silently by every class written
afterwards. It also means the classes that must not share a session — the ones whose subject is the
app process or the install — keep the right behaviour without anyone remembering to exclude them.

**The reset is a reinstall**, not a restart. `AppLifecycle.resetToCleanState()` removes and
reinstalls the app, which is exactly what Appium's own between-session reset does while `noReset`
and `fullReset` are both false. `AppLifecycle.coldRestart()` would have been cheaper and would have
been wrong: its documented purpose is to *preserve* what the app persists — that is the contract the
upgrade suite measures against — so a class resetting with it would carry a signed-in user or a
filled cart into the next test. The point of reusing a session is to pay less for the same
isolation, not to quietly redefine it.

**`checkSessionScope` enforces the boundary from source**, in `qualityGate`, with no device. A class
on `PER_CLASS` may not reference `AppLifecycle` or `AppUpgrade`. Adding the annotation looks like a
pure speed change and reads like one in review; this is the check that disagrees. `AppUpgradeTest`
is the sharp case — it deliberately unsets `mobile.app.path`, so the reinstall would have nothing to
install and would throw, on a device, in the slowest lane, which is the worst possible place to
learn it.

Three classes are opted in: `LoginParityTest`, `AddToCartParityTest`, `PurchaseFlowTest`. On iOS
they account for 10 of the 14 sessions, which becomes 3.

## Consequences

**14 sessions become 7.** Measured on the first CI run of this change, along with 21.1 min of
session creation falling to 12.1 min. It is checkable in the job summary of any single run, because
it is a count rather than a duration — which is exactly why the decision was staked on it.

**No wall-clock claim is made here.** Seven fewer sessions at 40-60 s each should be worth several
minutes, and the reinstall reset costs something back that has never been measured on this runner.
The net is expected to be positive and is deliberately not stated as a number, for the same reason
the two withdrawn claims above are not.

**The reset has to wait, not assume.** That same first run failed one test with
`Application "…" is unknown to FrontBoard`: `installApp` returns before iOS has finished making the
app launchable, and the activate that followed was a moment too early. The reset now waits for the
install to land and for the app to become launchable, separately, because those two failures mean
different things — a missing app path versus a slow platform. It is the one part of the reset that
can be covered without a device, and `AppResetWaitTest` covers it.

**Isolation genuinely narrows.** Device state outside the app — permission grants, orientation,
clipboard, anything a test simulated — now carries between tests in a `PER_CLASS` class, because
only the app is reinstalled. `checkSessionScope` catches the obvious violations of this, not the
subtle ones.

**A dead session no longer costs only one test.** `DriverExtension` opens a replacement rather than
letting every subsequent test fail with a stale-session error naming nothing, but the first
casualty is still a real failure.

**Timings within a class stop being comparable.** The first test pays for the session; the rest do
not. Anything reading per-test duration — the flake ledger's judgement, a human scanning the
report — should know that.

## Alternatives considered

**One session for the whole suite.** Larger saving, but a single failure would poison every
remaining test, and the device-state leak would span unrelated suites rather than three related
tests.

**Attach to a long-lived WebDriverAgent via `appium:webDriverAgentUrl`.** This targets the actual
26-39 s per session directly, and is the more precise fix. It needs WDA started outside Appium and
kept alive across sessions, which puts its launch configuration in the workflow YAML — the same
duplication that kept the simulator warm-up on `simctl` rather than a throwaway Appium session.
Worth revisiting; it is orthogonal to this ADR and would compose with it.

**Making `PER_CLASS` the default.** Rejected above: the trade should be stated per class.
