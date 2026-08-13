# ADR-007: No API seeding, because this app has no state API

**Status:** Accepted · 2026-08-13

## Context

The plan for this framework included the pattern that works well in its web sibling: seed state
through the product's REST API, then assert on it in the UI. It makes setup fast and it removes a
long chain of UI steps from tests that are not about those steps.

Porting it here needed the app's API. The app is a compiled React Native binary with no published
API documentation, so the base URL came out of the bundle itself:

```
unzip -o MyDemoApp-1.3.0.apk 'assets/*'
grep -aoE 'https://[a-zA-Z0-9._/-]+' assets/index.android.bundle | sort -u
```

Four endpoints under `https://my-demo-app.net/api/`: `initCall`, `item-load`, `remove-item`,
`checkout`. The names suggested a cart API. They are not one.

Every one of them returns an S3 access-denied document:

```
$ curl https://my-demo-app.net/api/item-load
HTTP 403 | application/xml
<Error><Code>AccessDenied</Code><Message>Access Denied</Message>...
```

They are write-only telemetry sinks behind S3, not a readable resource API. There is no
server-side cart, no user record, and nothing to seed. The catalog ships inside the binary — which
the offline test in the device-conditions suite already demonstrates, since the catalog renders
perfectly with every radio switched off.

## Decision

Do not implement API seeding. Record why.

The alternatives were considered and rejected:

- **Point the layer at some other public API.** It would produce a REST Assured module and a green
  test that demonstrate nothing about this app. A hybrid test that seeds one system and asserts on
  an unrelated one is a prop.
- **Stand up a mock backend.** The app talks to a hard-coded host and has no injection point, so
  this means a proxy and a rewritten host — real work, and it would test the proxy.
- **Leave it in the roadmap as an unticked box.** Reads as unfinished rather than as decided, and
  invites someone to try the same dead end again.

## Consequences

- Setup for the deep flows stays in the UI. It is slower, and it is the only honest option here:
  the state genuinely lives on the device.
- `rest-assured` stays in the version catalog, unused. Removing it would erase the trail; the
  comment there points at this record.
- The finding has some value of its own. "Where is this app's state?" is a real question to ask
  before designing a suite, and the answer here — entirely on the device, with telemetry going one
  way to a bucket — changes what the suite should look like. It is also why the upgrade suite in
  ADR-001 has to measure persistence rather than assume it.
- If the app ever grows a real API, the pattern slots straight in: seed, then assert in the UI.
