# ADR-009 — AI as opt-in instrumentation, never a silent self-heal

**Status:** accepted · 2026-08-13

## Context

"Self-healing locators" is the headline feature of most commercial mobile testing tools, and
the thing people most expect to see when a test framework claims an AI layer. The pitch is
that when a locator breaks, the tool finds the element another way and the test passes.

The pitch describes the mechanism accurately and the consequence not at all. A locator breaks
for a reason. Usually the reason is that someone renamed a `testID`, moved a control, or
replaced a component — a change in the application, which is the thing the suite exists to
detect. A tool that repairs the locator has not fixed the test; it has deleted the report.

Run that for a year and two things are true at once: the suite is green, and nobody can say
what it asserts. The locators in the source no longer correspond to the app, the healed ones
were never written down, and the only record that any of it drifted is a log line in a CI run
that expired after thirty days. This is the same failure as an unbounded `@Flaky` retry and
the same failure as an accessibility check that gets switched off once it goes red — a signal
that was inconvenient, absorbed rather than paid.

There is nevertheless something real being solved. A twenty-minute device suite that dies on
its first assertion because one `testID` changed reports one failure and leaves the other
nineteen tests unrun. Discovering the remaining eight problems takes eight more runs. That is
a genuine cost, and it is worth addressing — just not by hiding the first problem.

## Decision

Healing buys **one** thing: the run continues. It buys it on credit, and the credit is
recorded.

**1. The ledger.** Every heal is written to `LocatorDebtLedger` with the failed locator, what
replaced it, the test that hit it, and the path to the screenshot the answer was based on. An
accepted set lives in `locator-debt.txt`, committed. A heal that is not in the accepted set
**fails the build**, and the failure message names the locator and attaches the evidence
rather than reporting a count. An accepted entry that nothing rescued this run is flagged for
graduation, so the file cannot grow monotonically — which is precisely how quarantine lists
rot into permanent exemptions.

This is the same shape as the flake ledger (ADR-006) and the accessibility baseline, chosen
for the same reason: a check that is red forever gets switched off, so record the existing
debt, gate on regressions, and make the debt visible, attributed and dated.

**2. Optional by construction, and checked on every push.** `core` declares a `LocatorFallback`
interface and resolves implementations through `ServiceLoader`. The implementation lives in
`:ai`, which is on the test runtime classpath only — no test can import an AI type, so nothing
can come to depend on the layer existing. Delete `include("ai")` from `settings.gradle.kts` and
the framework compiles, runs, and simply finds no fallback.

That was the claim, and for a while it was only a claim. It is now a CI job: `-Pmobile.ai.absent=true`
drops the module from the build exactly as deleting the line would, and the **entire quality gate
runs that way on every push** — after first asserting the module is genuinely gone, so the job
cannot pass vacuously. If anything in `core`, `screens` or `tests` ever grows a dependency on the
AI layer, that job goes red and this ADR stops being true out loud rather than quietly.

Making it enforceable mattered more than it might look. Every other invariant here has a task
behind it — `checkNoXPath`, `QuarantinePolicy`, `checkLocatorDebt`, `allureWiringCheck` — and this
was the one load-bearing claim resting on nothing but prose.

**3. Off by default.** It needs `-Dmobile.ai.locatorFallback=true` **and** a key. Defaulting to
on with a key present would mean that anyone who happens to have `ANTHROPIC_API_KEY` exported
gets network calls out of a test run they never asked to make network calls in.

**4. The model cannot widen the locator policy.** Its answer passes through
`LocatorSuggestion.toBy()`, which permits `accessibility id` and `id` and refuses everything
else. This matters more than it looks. Shown a page source, a model will suggest
`//android.widget.Button[3]` — it is the locator most likely to match, and the worst possible
thing to admit into a suite. ADR-003 bans XPath and `./gradlew checkNoXPath` enforces that
against the source tree; a fallback able to synthesise one at runtime would be a hole straight
through that rule, in the one place the static check can never look. Refusal is a real
outcome: the original failure stands, which is correct. A test that fails because a `testID`
was renamed is useful. The same test passing against an index-based locator is a test that
will one day assert confidently about the wrong button.

**5. It never throws, and it runs only on the failure path.** The fallback is consulted in one
place, `BaseScreen.recover`, after the full explicit wait has already expired. With nothing
registered, that method is a rethrow of the original `TimeoutException` — the one that names
the locator and the screen. Any failure inside the fallback is swallowed and logged; replacing
a precise locator error with `connect timed out` would be worse than having no fallback at all.

## What is verified, and what is not

Being straight about this is the point of the section.

**Verified**, in 35 unit tests that need no key and no network, wired into `qualityGate`:
credential resolution and precedence; that the credential never prints its key; the hierarchy
digest's filtering, truncation marker and platform handling; the prompt's contents; and the
policy gate, including that every banned strategy is refused however plausible its value.

**Not verified:** the API call itself, and therefore the quality of the model's answers. I do
not have a key. Live calls are gated behind `@Tag("ai")` and excluded unless `ANTHROPIC_API_KEY`
is set, so the suite stays green without one — but "excluded" is not "passing", and nothing
here should be read as a claim that the vision fallback has been shown to work against a real
device. The parts that decide what is asked and what is done with the answer are testable and
tested; the HTTP call in the middle is deliberately thin for exactly that reason.

## Consequences

- A renamed `testID` costs one run, not one run per broken locator — and produces a build
  failure that names every locator to fix, with screenshots.
- Debt is visible in a committed file, and stops accumulating silently once it stops being
  needed.
- The framework has an AI feature that a reviewer can delete in one line, which is a stronger
  statement about the design than the feature itself.
- Someone who wants conventional self-healing does not get it here, and the reason is written
  down rather than left as an omission.
