# Maestro smoke lane

Two YAML flows covering the paths that, if broken, make it pointless to spend twenty minutes on
the Appium suite: the app launches and the catalog renders, and a user can sign in.

## Why both tools

See [ADR-008](../docs/adr/0008-appium-and-maestro.md). Briefly: Maestro absorbs timing flakiness
automatically and starts in seconds, which is the right trade for a gate. The Appium suite makes
precise claims about waits, device state, upgrades and parallelism — claims that a tool designed
to paper over timing cannot make.

## Running

```bash
maestro test maestro/
```

Maestro needs a booted emulator or simulator; it finds the device itself.

## ⚠️ Status: written, not yet executed

**These flows have not been run.** Maestro has no Windows build, and this framework was developed
on Windows, so there was no way to execute them locally. They are wired into
[`.github/workflows/maestro.yml`](../.github/workflows/maestro.yml), and that CI job will be their
first real execution.

Expect to fix selectors on the first run. The likeliest problem is the `id:` strategy: Maestro
matches it against the Android `resource-id` and the iOS accessibility identifier, while React
Native's `testID` reliably populates `content-desc` on Android. If `id:` misses, the fix is to
switch those selectors to `text:` or to Maestro's `~accessibilityLabel` form.

This is flagged rather than quietly shipped because a portfolio repository claiming a feature it
has never run is worse than one that says which parts are still unproven.
