# ADR-004: Explicit waits only — no implicit wait, ever

**Status:** Accepted · 2026-08-09

## Context

Selenium and Appium offer an implicit wait: a global timeout applied to every element lookup.
It is tempting because it makes flakiness disappear with one line of setup.

It also interacts badly with explicit waits. When both are configured, the effective timeout is
some undocumented product of the two, varying by command and by driver version. The classic symptom
is a `WebDriverWait` with a 10-second timeout that takes 60 seconds to fail, or a
`findElements` call that returns an empty list only after the full implicit wait has elapsed —
making "assert this element is gone" cost the maximum timeout every time it passes.

## Decision

The implicit wait is explicitly set to zero when the session opens:

```java
driver.manage().timeouts().implicitlyWait(Duration.ZERO);
```

Every wait in the framework goes through `BaseScreen`, which uses `WebDriverWait` with a timeout
from `mobile.timeout.element.seconds` (default 20).

`awaitLoaded()` on each screen wraps its timeout to say which screen failed to appear and which
locator it was waiting on, rather than surfacing Selenium's default message.

`Thread.sleep` is not used anywhere.

## Consequences

- Timeouts mean what they say, and a slow failure is a real signal rather than an artifact.
- Negative assertions ("the cart is empty", "the error is gone") are cheap instead of costing a
  full timeout.
- Slightly more code: every wait has to name its condition. That is the trade, and it pays for
  itself the first time a failure message is precise enough to act on without reproducing.
- Screen objects never assert. They expose intent and return the next screen; assertions live in
  tests, where the failure message can describe what the test wanted rather than what the app did.
