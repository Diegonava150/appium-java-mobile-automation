# ADR-002: Parallelise on a device pool, not on test threads

**Status:** Accepted · 2026-08-09

## Context

Running mobile tests in parallel is not the same problem as running web tests in parallel. A
browser is cheap and unbounded; a device is a scarce, stateful, physical-ish resource. Two tests
cannot share one emulator, and spinning up an emulator per test is not viable.

There is also a specific, well-known failure that catches nearly everyone the first time. Appium's
Android driver uses a `systemPort` to talk to the UiAutomator2 server on the device, and the iOS
driver uses a `wdaLocalPort` to reach WebDriverAgent. **Both default to a fixed value.** Start two
sessions concurrently without overriding them and the second session silently takes over the first
one's channel. The symptom is tests failing against the wrong device, intermittently, with error
messages that point nowhere near the cause. It usually gets misfiled as "Appium is flaky."

## Decision

A `DevicePool` holds a fixed set of `DeviceSlot`s in a `BlockingQueue`. Each slot owns four ports,
all derived from its index:

| Port              | Base   | Slot _n_   |
| ----------------- | ------ | ---------- |
| Appium server     | `4723` | `4723 + n` |
| `systemPort`      | `8200` | `8200 + n` |
| `wdaLocalPort`    | `8100` | `8100 + n` |
| `mjpegServerPort` | `9100` | `9100 + n` |

`DriverExtension` leases a slot and releases it inside a `finally`. Where that happens depends on
the class's `SessionScope` — `beforeEach`/`afterEach` by default, `beforeAll`/`afterAll` for a class
reusing one session (ADR-010). The pool contract is the same either way: whatever leases, releases.

JUnit's thread count is set from `mobile.device.count`, but that is an optimisation, not the
control. **The pool is the gate.** A thread with no slot blocks rather than racing onto a busy
device.

The parallelism unit is the **class**, not the method (`@Execution(SAME_THREAD)` inside
`@MobileTest`). A single device cannot meaningfully interleave two test methods.

## Consequences

- Port collision becomes structurally impossible rather than something to remember. `DevicePoolTest`
  asserts it in milliseconds, with no emulator attached, on every push.
- Slot leaks are the remaining hazard, so releases are strictly balanced in a `finally`. A leaked
  slot in a pool of two means the next test blocks for five minutes and then fails for a reason
  unrelated to the test — precisely the class of bug this design exists to avoid.
- Scaling to a device cloud later means replacing the pool's construction, not its interface.
- One test method cannot use two devices. A multi-device scenario (chat between two users, say)
  would need a second lease and an explicit API for it. Out of scope for now.

## Postscript: what two devices actually bought (measured 2026-08-10)

The design was verified end to end on two emulators — same 11 tests, same machine:

| Configuration                        | Wall clock |
| ------------------------------------ | ---------- |
| 1 device (`emulator-5554`)           | 135.4 s    |
| 2 devices (`emulator-5554` + `-5556`) | 136.7 s    |

**No speedup.** That is worth stating plainly rather than quietly not measuring.

Two things are true at once here, and only one of them is a problem:

1. **The isolation works.** Slot 0 took `systemPort` 8200 and `mjpegServerPort` 9100; slot 1 took
   8201 and 9101. Both sessions ran concurrently against their own device with no cross-talk, and
   all 11 tests passed. The bug this ADR exists to prevent did not occur.
2. **The host was the bottleneck.** Two hardware-accelerated emulators on one laptop contend for
   the same CPU and GPU, so each ran roughly half as fast. Parallel device execution only pays
   when the devices have genuinely separate hardware behind them — separate CI runners, a device
   cloud, or physical handsets.

Class-level parallelism compounds this: with three test classes, the critical path is the longest
single class, so a second worker cannot help much regardless of hardware.

The honest conclusion is that this design is a correctness mechanism first and a speed mechanism
second. It makes multi-device execution *possible and safe*; whether it is *faster* is a property
of the hardware, not of the framework. Claiming a speedup that a reader cannot reproduce on their
own laptop would be the easier thing to write and the wrong thing to write.
