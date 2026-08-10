# ADR-008: Use Appium and Maestro, and be clear about which does what

**Status:** Accepted · 2026-08-10

## Context

The 2026 mobile testing conversation is largely tribal. Maestro's pitch is that Appium is slow,
brittle and high-maintenance. Appium's is that Maestro is a toy that cannot express real
scenarios. Both camps are arguing about tools when the actual question is what a given test is
*for*.

The two have genuinely different design centres:

- **Maestro** absorbs timing flakiness by design. It retries and waits implicitly, so a flow that
  would race in Appium usually just works. Flows are YAML, start in seconds, and need no build.
- **Appium** exposes the device. Sessions, capabilities, per-device ports, app installation,
  radios, process lifecycle. It will let you write a test that races, and it will also let you
  write a test that installs a different build mid-session.

Maestro's automatic flakiness absorption is its best feature and its hard limit. A tool that
silently waits until things settle cannot make a precise claim about *when* things settle — so it
cannot be the tool for a framework whose entire ADR-004 position is that every wait is explicit
and timeouts mean what they say.

## Decision

Both, with a boundary that follows from what each is good at.

**Maestro owns the gate.** Two flows: the app launches and the catalog renders, and a user can
sign in. If either fails, the Appium matrix is not worth twenty minutes of runner time. Timing
flakiness is precisely what you want absorbed in a smoke check — a gate that cries wolf is worse
than no gate.

**Appium owns everything that makes a claim.** Cross-platform parity, the device pool and its port
isolation, in-place upgrade from 1.2.0 to 1.3.0, network conditions, process lifecycle, explicit
waits, and week three's performance and accessibility measurement. Every one of those needs
control that a YAML runner deliberately does not give you.

Put another way: **Maestro answers "is it alive?" Appium answers "is it correct, and how do you
know?"**

## Consequences

- Two toolchains to keep working, which is a real cost. It is justified by the gate saving far
  more runner time than the flows cost to maintain, and by the flows being about twenty lines.
- The selector vocabulary is shared. Both use the app's `testID` values, so the two layers cannot
  drift into describing the app differently.
- Choosing a tool per job rather than per team is the actual position here, and it is worth
  stating because most comparisons of these two tools are trying to sell one of them.
- **The Maestro flows have not been executed.** Maestro has no Windows build and this framework
  was developed on Windows, so the CI job is their first run. That is flagged in
  [`maestro/README.md`](../../maestro/README.md) and in the README's limitations rather than
  presented as working.
