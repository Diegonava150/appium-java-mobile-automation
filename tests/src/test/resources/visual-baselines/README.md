# Visual baselines

One directory per device profile: `{platform}-{sdk}-{density}-{width}x{height}`.

## Why per profile, and not one golden image

A screenshot is a function of resolution, density, cutout shape, system font scale and OS version.
The same screen on a Pixel at 420dpi and on a tablet is genuinely a different picture, and a
framework that compares them reports a 100% difference and teaches everyone to ignore the check.
This is the main reason mobile visual testing is harder than the web equivalent, and it is why a
size mismatch here is an explicit error rather than a score.

## First run records, it does not fail

On a profile with no baseline, the suite writes one and passes. Adding a screen or running on a new
device should not be a red build. After an intended UI change, rewrite deliberately:

```bash
./gradlew :tests:test -Dmobile.platform=android -Dmobile.visual.update=true
```

Then review the diff — the failure path writes `-baseline.png`, `-actual.png` and `-diff.png` into
the test artifacts, with unchanged pixels dimmed, differences in red, and ignored regions in grey.

## The status bar is excluded

The clock and battery indicator change between any two runs. Leaving the status bar in scope means
every comparison fails on the time.

## ⚠️ CI records; it does not yet gate

The committed profile is the local development emulator (API 36, 420dpi, 1080x2400). CI runs API 31
and 34 at different densities, so those profiles have no baseline and every CI run **records rather
than compares** — which means the visual check is currently not gating anything on CI.

To fix that for a given profile: run the Android workflow, download its `visual-baselines-apiNN`
artifact, and commit the directory. From then on that profile compares properly.

This is left as-is rather than quietly hidden because the alternative — committing baselines
generated blind — would bake in whatever the app happened to look like on a run nobody reviewed.
Baselines are reference data and deserve to be looked at before they are trusted.

## Repository weight

Screenshots of a photo-heavy catalog are around 1.7 MB each. Two profiles of two screens is a few
megabytes, which is fine; twenty profiles would not be. If this grows, the answer is fewer, more
deliberately chosen screens rather than more compression.
