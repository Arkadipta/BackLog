# Phase 6: hardening and Syncthing

This is the spec roadmap's phase 6. Its end-to-end encryption already happened in 3.3. Purging tombstones has moved in here (D27), because the roadmap never scheduled it.

---

### 6.1 Purging old tombstones
**Depends on:** 3.5, 4.5 · **Spec:** Data model (90-day tombstone rule) · **Decisions:** D16, D27

**Build**
- Implement D27:
  - The Worker exposes the lowest pulled `seq` among active devices.
  - A Worker cron trigger purges old tombstoned rows from `state`.
  - Each device purges those rows from its own tables, along with their `field_clock` and `change_log` rows.
  - A device whose cursor is older than the purge horizon gets the "resync required" refusal, and handles it on the client side.
- A "Forget device" action in Settings on both platforms.
- New scenarios in the 3.4 client:
  - A device that has been offline past the horizon comes back and doesn't resurrect half a row.
  - A forgotten device no longer blocks purging.

**Done when**
- The new scenarios pass.
- On the deployed Worker, a purge removes only test tombstones that are past the horizon. To check, back-date some test data, then confirm each step with you first.

**Notes:** _(filled in by the session)_

---

### 6.2 Syncthing transport (shared)
**Depends on:** 3.3 · **Spec:** Sync › Phase 2 · **Decisions:** D12, D18, D28

**Build**
- A `FileStore` interface with list, read from offset, append, write and delete. The platforms implement it in 6.3; tests use a temp-directory version.
- A `SyncthingTransport`:
  - Writes an append-only ndjson folder per device. Lines are encrypted with the D18 envelope.
  - Keeps a read cursor for every other device, and reads only complete lines.
  - Writes a snapshot once a week and then deletes its own older files.
- Transport selection stored in `sync_state`.

**Done when** JVM tests pass with two simulated devices sharing a temp directory. The tests cover:
- Delayed files and files that are only half written.
- Snapshot compaction keeping the two devices converged.
- Replaying every file being harmless.

**Notes:** _(filled in by the session)_

---

### 6.3 Syncthing on Mac and Android
**Depends on:** 6.2, 3.5, 4.5 · **Spec:** Sync › Phase 2 (folder access, Android caveat); Mac app › Mac plumbing · **Decisions:** D28

**Build**
- Mac:
  - A folder picker that saves a security-scoped bookmark, so access survives the sandbox.
  - Watch the folder for changes (FSEvents or `NSFilePresenter`) and sync when it changes.
- Android:
  - A Storage Access Framework folder picker with a persisted permission, and a `FileStore` built on `DocumentFile`.
  - Sync when the folder changes while the app is in the foreground, and through WorkManager otherwise.
  - Settings says the phone needs one of the community Syncthing forks, since the official app was discontinued.
- A transport switch in Settings on both platforms (Cloudflare or Syncthing). Switching does a full push to the new transport.

**Done when** with Cloudflare switched off, the Mac and the phone converge through a folder shared by Syncthing.

**Notes:** _(filled in by the session)_
