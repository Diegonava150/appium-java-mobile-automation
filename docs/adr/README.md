# Architecture decision records

Short records of the choices that shaped this framework, and the reasoning behind them.
They exist because the interesting part of a test framework is rarely the code — it is why
one approach was taken over the three that also would have worked.

| #                                      | Decision                                                    |
| -------------------------------------- | ----------------------------------------------------------- |
| [001](0001-app-under-test.md)          | Use a downloaded third-party app as the system under test    |
| [002](0002-device-pool-parallelism.md) | Parallelise on a device pool, not on test threads            |
| [003](0003-locator-strategy.md)        | One locator strategy — accessibility IDs, enforced by build  |
| [004](0004-explicit-waits-only.md)     | Explicit waits only — no implicit wait, ever                 |
| [005](0005-junit-6-and-allure.md)      | Run JUnit 6, and prove the Allure integration                |
| [006](0006-flake-as-a-debt-ledger.md)  | Treat flakiness as debt with a due date, not a retry setting |
| [008](0008-appium-and-maestro.md)      | Use Appium and Maestro, and be clear about which does what   |

ADR-002 carries a measured postscript worth reading on its own: two devices produced **no
speedup** on a single host. The isolation design is correct and was verified; the speed benefit
turns out to be a property of the hardware, not the framework.

Planned, as the corresponding work lands:

- **007** — Performance and accessibility measurement, and what thresholds are defensible
- **009** — AI as opt-in instrumentation, never a silent self-heal
