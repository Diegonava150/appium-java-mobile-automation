# ADR-003: One locator strategy — accessibility IDs, enforced by the build

**Status:** Accepted · 2026-08-09

## Context

Cross-platform mobile frameworks usually carry two locator sets, one per platform, and a helper
that picks between them. That is the shape the Appium Java client encourages with `@AndroidFindBy`
and `@iOSXCUITFindBy` on the same field.

For a React Native app, this turns out to be unnecessary. RN's `testID` prop surfaces as:

- `content-desc` on Android, and
- the accessibility identifier on iOS.

Appium's `accessibility id` strategy targets exactly those two attributes. **One locator resolves
on both platforms.**

Worth recording because it is a checkable claim: Sauce Labs' own reference test suite for this app
does not do this. Its helper reads

```ts
const locatorStrategy = (selector: string): string =>
  driver.isIOS ? `id=${selector}` : `//*[@content-desc="${selector}"]`;
```

That works, but the Android branch pays for a full view-hierarchy walk on every single lookup, and
it splits one concept across two syntaxes for no benefit.

## Decision

`Locators.id(String testId)` is the only locator constructor in the framework:

```java
public static By id(String testId) {
    return AppiumBy.accessibilityId(testId);
}
```

**XPath is banned**, and the ban is enforced, not merely documented. `./gradlew checkNoXPath` fails
the build on `AppiumBy.xpath`, `By.xpath`, or `-android uiautomator`, and it runs as part of the
quality gate on every push.

Escape hatch: a line ending in `// xpath-ok: <reason>` is exempt. Some elements genuinely live
outside the app's view tree — OS biometric prompts, permission sheets — and no `testID` will ever
reach them. Those cases stay possible, but they have to be argued for in the diff rather than
slipping in unnoticed.

Where the platforms diverge in **behaviour** rather than in naming, the divergence is absorbed
inside the screen object. `Navigation` is the current example: Android renders a header with a
hamburger and a cart badge, iOS renders a bottom tab bar. Those are two different navigation
patterns, each idiomatic to its platform — not two names for one control. Tests call
`navigation.openCart()` on both.

## Consequences

- Lookups are faster and far more stable than XPath equivalents.
- Tests contain no platform branching. The parity claim is visible in the source, which is what
  makes it worth making.
- The framework is coupled to the app exposing `testID`s. This is a virtue: it makes accessibility
  identifiers a testability requirement, which is the same requirement screen readers have. It also
  gives the accessibility audit in v2 something real to check.
- A screen with no `testID` cannot be automated without the escape hatch. That is the correct
  pressure to apply — the fix belongs in the app.
