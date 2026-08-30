# ADR-006: Treat flakiness as debt with a due date, not as a retry setting

**Status:** Accepted · 2026-08-10

## Context

Every mobile suite eventually acquires unstable tests, and every team reaches for the same tool:
turn on retries. It works, in the narrow sense. The build goes green.

What it also does is remove the only pressure that would ever have got the underlying problem
fixed. A retried test reports as passed. Nothing in the pipeline distinguishes "this test is
solid" from "this test fails half the time and we paper over it." Three years later the quarantine
list has forty entries, nobody remembers why any of them are there, and a genuine regression hides
comfortably among them because a second failure looks exactly like the usual noise.

The mistake is treating flakiness as a *configuration value* — `retries = 3` — when it is really a
*debt*: something borrowed against future effort, which accrues interest and needs a repayment
date.

## Decision

`@Flaky` replaces `@Test` and takes three things, two of them mandatory:

```java
@Flaky(
    maxAttempts = 3,
    reason = "the catalog animates in and the first tap can land before the list settles",
    expires = "2026-09-30")
```

- **`reason` is mandatory and must describe a mechanism.** "Flaky" is not a reason. If the author
  cannot describe what races what, they have not finished diagnosing it, and writing that sentence
  is most of the diagnosis.
- **`expires` is mandatory and is enforced.** Once the date passes, the test **fails immediately,
  without running**, and the failure message quotes the original reason back along with the three
  legitimate ways out: fix it, delete it, or extend the deadline and justify that in the pull
  request. This is the inversion that makes the whole thing work — the debt eventually breaks the
  build *on purpose*, at a date a human chose.
- **Every attempt is recorded** to `build/flake-report.json` via `FlakeLedger`, written from a
  shutdown hook so it survives a crashed run. A retry buys a green build and still leaves a
  receipt saying what it cost.

Two implementation choices worth recording:

**Intermediate failures become `TestAbortedException`, not silence.** A retried attempt shows up in
the report as *aborted*, not as passed. The run's own output therefore shows the instability
instead of hiding it, which is the difference between a retry and a cover-up.

**Passing first try is tracked as a signal.** `passedFirstAttempt` in the ledger is the graduation
candidate flag: a quarantined test that stops needing its retries should have the annotation
removed. Week three publishes this as a trend, so graduation is driven by accumulated evidence
rather than by someone remembering.

Built directly on Jupiter's `TestTemplateInvocationContextProvider` rather than taking
`junit-pioneer`, which still targets JUnit 5 (see ADR-005). Retries are generated lazily by a
spliterator that stops as soon as the test passes, so a stable test costs exactly one invocation.

That laziness needs a second, dumber stopping condition, and finding out why cost a machine twenty
minutes. A spliterator that stops on *outcomes* only stops if it is told about them, and JUnit tells
an extension about the test body throwing and about a `@BeforeEach` **method** throwing — not about
another extension's `BeforeEachCallback` throwing. `DriverExtension` is exactly that, and it throws
when a session will not open. So a quarantined test on a sick device produced no outcome, ever, and
the spliterator was asked for invocations without end until the CI job's own timeout killed it an
hour later. It is now also bounded by invocations *handed out*, which the extension knows for
certain; `FlakyInvocationBoundTest` holds that behaviour down, time-bounded, because the failure
mode it guards is a hang rather than a wrong answer.

## Consequences

- Quarantine becomes a loan rather than a landfill. The list cannot silently grow, because each
  entry has a date attached that will eventually fail the build.
- Retries stay honest: the green build is real, and the cost of it is written down.
- The deadline can be extended, and that is fine — an extension is a visible decision in a diff,
  which is the whole point. What is no longer possible is *forgetting*.
- A retried test gets a fresh device session per attempt, because the driver lifecycle is
  per-invocation. That makes a retry a genuine re-run rather than a second poke at an app already
  in a bad state — but it also means retries are expensive on mobile, which is further reason to
  want few of them.
- `FlakyExtensionTest` drives the real extension with a counter that fails a known number of times
  and then inspects the ledger it produced. Retry logic looks obviously correct and is quietly off
  by one, so it gets tested like anything else — with no device, in milliseconds, on every push.
