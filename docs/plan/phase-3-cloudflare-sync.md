# Phase 3: Cloudflare sync, encrypted

This phase builds three things: a Worker + D1 relay, the shared sync engine, and end-to-end encryption from the very first push (you chose phase 3 for encryption). It's the spec roadmap's phase 3. Task 3.1 depends only on 1.1, so it can run during the phase 2 trial week.

Gate (spec): every scripted scenario converges, and a 7-day soak stays under 5% of the D1 free tier. That's 250,000 rows read and 5,000 rows written per day.

---

### 3.1 Worker and D1
**Depends on:** 1.1 · can run in parallel with phase 2 · **Spec:** Sync › Phase 1 (whole subsection), Free-tier budget; Risks (wrong clock, D1 hard stop) · **Decisions:** D11, D16, D17, D18

**Build**
- `worker/`: TypeScript, `wrangler.toml` with a D1 binding, and `migrations/0001_init.sql` creating:
  - `state`, including `base_hlc`, with primary key (tbl, row_id, field) and an index on `seq`;
  - `meta`;
  - `devices`.
- `POST /v1/push`:
  - Accepts up to 500 changes and applies them in one D1 batch.
  - Upserts only where the incoming HLC is higher, and gives each accepted row a new `seq`.
  - Rejects changes stamped more than 5 min ahead of server time.
  - Returns a result for each change (D16) and updates `devices`.
- `GET /v1/pull?since=&limit=` (limit up to 500): returns rows ordered by `seq`, plus `next_seq` and `more`, and records the device's `last_pull_seq`.
- `GET /v1/ping`.
- Auth: `Authorization: Bearer` is compared against the `AUTH_TOKEN` secret in constant time, with 401 on a mismatch. Every response carries a `Date` header.
- Find the exact error D1 raises when the daily limit is hit, and map it to 429 with `Retry-After` set to 00:00 UTC.
- `worker/API.md`: the wire contract that 3.2 implements (JSON shapes, status codes, limits).
- Tests use vitest with `@cloudflare/vitest-pool-workers`, with the migrations applied.

**Done when**
- Tests are green for:
  - auth;
  - HLC comparison, including the equal-wall-time tie broken by device id;
  - replaying a push;
  - paging with `more`;
  - skew rejection;
  - the 429 mapping.
- `npx wrangler dev` serves locally.
- `EXPLAIN QUERY PLAN` shows the pull query using the `seq` index (the spec says it's mandatory).

**Out of scope:** deploying. That happens in 3.5, together with you.

**Notes:** _(filled in by the session)_

---

### 3.2 Sync engine and Cloudflare transport
**Depends on:** 1.3, 3.1 · **Spec:** Sync (intro, Merge rules, Phase 1 "When to sync"); Architecture (`sync/`) · **Decisions:** D12, D13, D17

**Build**
- A `SyncTransport` interface with `push(batch)` and `pull(cursor)`. Cursors are opaque and kept per transport, so Syncthing (6.2) and CalDAV (7.2) can slot in later.
- `CloudflareTransport` on Ktor, with the OkHttp engine on Android, Darwin on macOS, and a JVM engine for tests and the 3.4 client. It implements `worker/API.md`.
- `SyncEngine`:
  - Runs the D17 cycle: coalesced pushes (D12), page loops, cursor persistence, clock-skew handling, 429 handling until 00:00 UTC, and backoff with jitter.
  - Runs one cycle at a time. Deciding when to sync is left to each platform.
- A `syncStatus` Flow: idle, syncing, or error with a kind and message, plus the time of the last success.
- A `ValueCodec` hook, which is the identity function for now; 3.3 swaps in encryption.
- An in-memory fake server implementing `API.md`, used by the tests.

**Done when** fake-server tests are green on jvm and macosArm64 for:
- page boundaries;
- push succeeding while pull fails, and the reverse;
- skew re-stamping;
- the 429 pause;
- coalescing that keeps the earliest `base_hlc`;
- two in-process engines converging on a scripted edit sequence.

**Notes:** _(filled in by the session)_

---

### 3.3 End-to-end encryption
**Depends on:** 3.2 · **Spec:** Sync › "Optional, recommended before relying on it" · **Decisions:** D18, D19

**Build**
- Generate the master secret, and use HKDF-SHA256 to derive the auth token and the data key from it (D18).
- An AES-GCM `ValueCodec` with associated data and the versioned envelope. A decrypt failure becomes a sync error: it never crashes the app and never applies a bad value.
- A `SecretStore` interface. The Keychain implements it on the Mac (3.5), a Keystore-backed store on Android (4.5), and an in-memory store in tests.
- Confirm which cryptography-kotlin provider gives you AES-GCM and HKDF on `macosArm64` (the Apple/CryptoKit provider, or the prebuilt OpenSSL 3 provider), and which on Android and the JVM. Record the choice.

**Done when**
- Round-trip tests pass.
- A flipped byte or the wrong associated data fails closed.
- HKDF matches the RFC 5869 test vectors.
- After a scripted sync, a test checks that the fake server's state holds no plaintext values.

**Notes:** _(filled in by the session)_

---

### 3.4 Two-device test client and conflict scenarios
**Depends on:** 3.3, 1.6 · **Spec:** Roadmap › phase 3 (command-line test client, gate) · **Decisions:** D13, D14, D21

**Build**
- A small JVM command-line client in a `:synctest` Gradle module that depends on `:shared`. The spec puts it in `shared/jvm`; a separate module keeps test tooling out of the shared library.
- The client simulates several devices against `wrangler dev`. Each device has its own SQLite file, device id and adjustable clock.
- Scenarios:
  - Rename vs complete.
  - Delete vs edit, and a restore after a delete.
  - Notes conflicts, in both arrival orders and repeated.
  - The same recurring instance completed offline on both devices, in both repeat modes.
  - Two indents that together would make three levels.
  - A list deleted on one device while a task is added to it on the other.
  - A device whose clock runs 10 min fast.
  - A fresh device bootstrapping from `since=0` with about 5,000 fields, which exercises paging.
  - A forced 429, using a dev-only Worker flag.
- After every scenario, each device's `dump()` must be identical. If they differ, print a readable diff.

**Done when**
- Every scenario converges. This is the spec's phase 3 gate on scripted scenarios.
- The run command is in CLAUDE.md.

**Notes:** _(filled in by the session)_

---

### 3.5 Mac sync, pairing and deploy
**Depends on:** 3.4, 2.6 · **Spec:** Sync › Phase 1 (auth, pairing, when to sync); Visual design › Animated moments (sync dot) · **Decisions:** D19

**Build**
- Deploy, together with you, confirming each command before running it because they change your Cloudflare account:
  1. `npx wrangler d1 create`
  2. Apply the migrations remotely.
  3. `npx wrangler deploy`
  4. `npx wrangler secret put AUTH_TOKEN`, with the value from the Mac setup screen.
- Settings › Sync:
  - The D19 setup flow, a Keychain-backed `SecretStore` and "Test connection".
  - Unpair and reset.
  - A pairing QR code rendered with CoreImage.
- Sync triggers: 2 s after a local change (debounced), on window focus, every 5 min, and on wake.
- A sync dot in the sidebar footer: idle, pulsing while syncing, or amber, with a popover showing the error and the time of the last success.
- After any sync that changed tasks, re-plan reminders.

**Done when**
- The Mac syncs with the deployed Worker.
- The 3.4 client, acting as a second device, sees Mac edits within one cycle, and the Mac sees the client's.
- The 7-day soak has started. Under Notes, record D1 rows read and written each day from the Cloudflare dashboard. The gate is under 5% of the free tier.

**Notes:** _(filled in by the session)_
