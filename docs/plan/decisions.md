# Decisions and spec deltas

The [spec](../../Personal_Todo_App_Spec_v1.md) is still the main reference. This file records your answers to its open questions, plus the gaps that turned up while splitting it into tasks, each with the resolution the plan builds on. Where an entry here disagrees with the spec, the entry wins. Tasks cite entries by number. If an entry needs to change, change it before the session that depends on it, not during that session.

## Answers to the spec's open questions (20 Nov 2026)

| Question | Answer |
| --- | --- |
| Reminders on both devices, or per device? | Both, as the Features section already says. No per-device setting in v1 |
| Import from Microsoft To Do? | No; start fresh |
| macOS 14+ and Android 12+? | Assumed yes |
| End-to-end encryption in phase 3 or phase 6? | Phase 3, with sync and before Android (task 3.3). Cloudflare never stores a plaintext value |
| Kanban status values | v2; not in this plan |
| App name and bundle id | See D1 |

## Identity and tooling

**D1. App identity.** The name is BackLog. The Mac bundle id and the Android `applicationId` are both `com.backlog.app`, matching LifeLog's `com.lifelog.app`. Kotlin packages live under `com.backlog`, and Swift imports the shared framework as `BackLogCore`.

**D2. Pin Kotlin to what SKIE supports.** SKIE usually adds support for a new Kotlin release some time after that release ships. In 0.1, take the newest Kotlin version that SKIE's docs list as supported. Pin Kotlin, SKIE, SQLDelight, Ktor and the kotlinx libraries together in `gradle/libs.versions.toml` and upgrade them as a set. Swift Export stays off, as the spec says.

**D3. Generate the Xcode project.** `macApp/project.yml` (XcodeGen) is the source of truth and the generated `.xcodeproj` is gitignored. Sessions edit YAML and never `.pbxproj`. Build and test from the terminal with `xcodebuild`, so every Mac task can check its own work. Signing uses your Personal Team, with no notarisation (spec).

**D4. Toolchain paths.** `JAVA_HOME` points at Android Studio's JBR (path in the README) and `ANDROID_HOME` at `~/Library/Android/sdk`. Gradle runs only through `./gradlew`, and wrangler only through `npx` inside `worker/`. Task 0.1 writes these into CLAUDE.md.

## Data model and queries

**D5. One Inbox across devices.** If each device created its own Inbox with a UUIDv7 on first launch, the first sync would leave two Inboxes. Instead the Inbox gets a fixed id, `UUIDv5(NS_BACKLOG, "inbox")`. Each device seeds it with every field clock at HLC zero, so any real edit beats a seed and two seeds merge silently. Seeds aren't written to the change log. `NS_BACKLOG` is a namespace UUID created once in 1.1 and never changed.

**D6. Due times float.** `due_date` + `due_time` are wall-clock values: 09:00 means 09:00 wherever the device is (spec: "so 9:00 stays 9:00 if you travel"). `reminder_at` stays in UTC ms as the spec says. `time_zone` is set whenever `reminder_at` is set, not only when there's a `due_time`. The planner converts (`reminder_at`, `time_zone`) to a wall time, then to an instant in the device's current zone, so both platforms re-plan reminders on a time-zone change. Times that fall in a DST gap resolve forward, which is kotlinx-datetime's default, and they resolve the same way on every code path.

**D7. Reminder offsets get their own field (schema delta).** The spec allows a reminder "as an offset before the due time", but it stores only an absolute `reminder_at`, so an offset can't follow a rescheduled task. Add a new nullable column, `task.reminder_offset_min`:
- When it's set (`^30m`, or "30 min before" in the UI), the reminder follows the due time, or 09:00 on the due date for all-day tasks (the quick-add rule). The repository recomputes `reminder_at` whenever the due date or time changes.
- When it's null, `reminder_at` is absolute and stays put when the task is rescheduled.
- Either way, when a recurring task's next instance is created, its reminder moves by the same number of days as the due date and keeps its wall time.

**D8. Sort-key ties.** Two devices inserting at the same spot can generate equal fractional keys. Order by (`sort_key`, `id`) everywhere so every device still shows the same order.

**D9. Search index.** Android's built-in SQLite isn't guaranteed to include FTS5. In 1.2, check for FTS5 on an API 31 emulator and on macOS. If either lacks it, use FTS4, which both have. Record the outcome here.

**D10. Today and Planned.** "Today" is the device's current local date. It's re-evaluated at midnight and on a time-zone change. Planned has five groups:
- Overdue: before today.
- Today.
- Tomorrow.
- This week: after tomorrow through the end of the current week, taking the first weekday from the system locale.
- Later.

Tasks without a date never appear in either view.

## Sync and merge

**D11. HLC encoding.** An HLC is stored as one fixed-width string that sorts in clock order: `<wall ms, 13 digits, zero-padded>-<counter, 4 hex digits>-<device id>`. Comparing two strings compares wall time, then counter, then device id, which is the spec's tie-break, so the Worker can compare HLCs with a plain SQL `>`. If the counter overflows, the wall time goes up by 1 ms.

**D12. The change log is the outbox.** One append-only `change_log` table with a local autoincrement `seq` does the job of the spec's `outbox`. `sync_state` keeps two things per transport: the last local `seq` pushed and that transport's pull cursor. Syncthing and CalDAV can then join later without a second outbox. A push sends only the latest change per (table, row, field) since the cursor, and it keeps the earliest `base_hlc` of that run of edits (see D13). A row is pruned once every configured transport has pushed past it.

**D13. Detecting notes conflicts.** The spec's rule ("if two devices edited `notes_md` since their last sync") needs a precise test, because the server keeps only the winning value.
- Every `notes_md` change carries `base_hlc`: the field's HLC just before the edit.
- A device may apply an incoming `notes_md` change that wins (it has the higher HLC) while its current value is its own edit and the incoming `base_hlc` differs from its current HLC. In that case the other device edited without seeing this device's text.
- Only that losing device acts. It writes the winning text, then the line `--- conflicting edit from <its device name>, <its edit time> ---`, then its own text. It saves this as a new local edit, with a new HLC and `base_hlc` set to the winner's HLC. The losing device is the only one sure to hold both texts.
- The winning device does nothing special; its edit stands.

**D14. Repair after merge.** Each device enforces the integrity rules locally, but concurrent edits can still break them once merged. After applying a remote batch, the change engine runs the repairs below and writes them as ordinary local changes. The repairs are deterministic, so two devices that make the same repair converge.
- A task whose parent has a parent (three levels) moves under its grandparent.
- A sub-task that has notes or a repeat rule is promoted to the top level, so nothing gets hidden.
- A sub-task whose `list_id` differs from its parent's takes the parent's.
- A live task in a deleted list moves to the Inbox, so deleting a list on one device while adding a task to it on the other doesn't lose the task.
- A live sub-task under a deleted parent is deleted with it.

**D15. Undo writes inverse changes.** Every repository write returns the inverse change set, meaning the previous value of every field it touched. Undo applies that set as new local changes with new HLCs. History is never rewritten, so an undo syncs like any other edit. The Mac feeds these sets into `UndoManager`, and the Android snackbar uses the same call. Undoing the completion of a recurring task also deletes the next instance it created.

**D16. Worker additions.** On top of the spec's `state` table, the Worker gets:
- A nullable `state.base_hlc` column, so pulls carry D13's base.
- A `meta` counter row to stamp `seq`. SQLite has no sequences, and only accepted changes get a new number.
- A table `devices(device_id, name, last_pull_seq, last_seen_at)`, updated on every request that carries an `X-BackLog-Device` header. Task 6.1 needs it.
- `GET /v1/ping` (authenticated), for the setup screen's "Test connection".
- A push response with a result for each change (`accepted`, `stale` or `clock_skew`) plus the highest `seq`.

**D17. Sync cycle.** Each cycle pulls every page and applies it, then pushes the change log in batches of 500, then advances the cursors. Only one cycle runs at a time on a device. Errors are handled like this:
- `clock_skew` result: the client takes the clock offset from the response's `Date` header, re-stamps the rejected changes and their `field_clock` rows with corrected HLCs, and retries once.
- HTTP 429: pushes and pulls stop until 00:00 UTC, and the sync dot turns amber.
- Network errors: retry with exponential backoff and jitter, never waiting longer than the platform's sync interval.

**D18. Encryption keys (security fix).** The spec derives the AES key from the pairing secret. But that secret is also the bearer token stored as a Worker secret, so anyone with access to the Worker could derive the key. The plan does this instead:
- Pairing shares a random 256-bit master secret.
- HKDF-SHA256 derives two keys from it: the auth token (info `backlog/auth/v1`) and the data key (info `backlog/data/v1`).
- Only the auth token goes into `wrangler secret put AUTH_TOKEN`. The master secret never leaves your devices.
- Values are sealed with AES-GCM, using a random 96-bit nonce and `tbl/row_id/field` as associated data, so a ciphertext can't be moved into another field. The envelope is `e1.` + base64url(nonce ‖ ciphertext ‖ tag).
- `tbl`, `row_id`, `field`, `hlc`, `device_id` and `base_hlc` stay in plaintext, because the Worker needs the HLC to merge.

**D19. Pairing flow.** Mac Settings › Sync › Set up works like this:
1. The Mac generates the master secret.
2. It shows the derived auth token with a copy button, next to the exact `npx wrangler secret put AUTH_TOKEN` command to run.
3. You paste the Worker URL, and "Test" calls `/v1/ping`.
4. The secret is saved to the Keychain.
5. "Pair a phone" shows a QR code holding `backlog://pair?u=<worker url>&k=<master, base64url>`.

Later, the same QR code shares the key for Syncthing, with or without a URL.

**D20. Export and import.** An export is one versioned JSON file containing all lists and tasks, each with its field values and per-field HLCs. Import applies the file as remote changes through the change engine, so importing twice is harmless and an import never overwrites a newer edit. A "replace everything" restore isn't part of v1.

## Recurrence and reminders

**D21. Ids of next instances (spec delta).** The spec's id, `UUIDv5(series_id + occurrence date)`, breaks for "repeat from completion". Two devices that complete the same instance offline on different days compute different dates, so the next instance is created twice. Use `UUIDv5(NS_BACKLOG, "next:" + id of the completed instance)` for both repeat modes. Copied sub-tasks need fixed ids too, `UUIDv5(NS_BACKLOG, "sub:" + next instance id + ":" + source sub-task id)`, or the checklist is duplicated. `series_id` is the id of the first instance.

**D22. COUNT.** Each new instance stores its rule with `COUNT` lowered by one, and an instance with `COUNT=1` has no successor. This needs no extra column. Ghost occurrences are computed from the current instance's rule.

**D23. Month ends.** Follow RFC 5545:
- `BYMONTHDAY=31` skips months without a 31st.
- `BYDAY=5MO` skips months without a fifth Monday.
- A yearly rule on 29 Feb skips non-leap years.

Add `BYMONTHDAY=-1` (the last day of the month) to the supported subset, with a "Monthly on the last day" preset and the quick-add phrase `every month on last day`. Following the RFC keeps CalDAV exports exact in phase 7.

**D24. Snooze.** Snooze sets an absolute `reminder_at` of now plus the snooze time and clears `reminder_offset_min`. It syncs like any edit, so both devices ring again at the new time.

**D25. Notification cap on the Mac.** Apple limits how many local notifications an app can have pending (64 on iOS). Assume macOS has the same limit: schedule no more than the earliest 60 reminders in the 14-day window, and re-plan on every change, launch, wake and time-zone change.

**D26. Ghost occurrences.** Ghosts are display-only. In v1 they can't be dragged or completed, and they never ring. Only the real instance carries a reminder, as in MS To Do.

## Later phases

**D27. Purging tombstones (task 6.1).** The data model says a tombstone is purged once it's more than 90 days old and every device has synced past it, but the roadmap never schedules this. Task 6.1 owns it:
- The Worker's `devices` table gives the lowest `last_pull_seq` among active devices.
- A Worker cron trigger purges the `state` rows of rows that were deleted more than 90 days ago and that every device has pulled past. Devices purge the same rows locally.
- A device whose cursor is older than the purge horizon gets a "resync required" refusal. It re-bootstraps and re-pushes its unsynced edits, so it can't bring back half a row.
- Settings gets a "Forget device" action, so a lost phone can't block purging forever.

**D28. Syncthing files.** The folder is `backlog-sync/<device_id>/`. The spec says `todo-sync/`; it's renamed to match the app.
- Readers consume only complete lines that end in `\n`, because Syncthing can deliver a file while it's still being written. A cursor is a (file, byte offset) pair.
- Each line carries the D18 envelope, so the shared folder never holds plaintext.
- A reader that finds a snapshot newer than its cursor reads the snapshot. Applying changes is idempotent, so any overlap is harmless.

**D29. CalDAV mapping.** Timed tasks map to `DUE` as a floating local time with no `TZID`, which matches D6. All-day tasks map to `DUE;VALUE=DATE`.
- Store each object's raw iCalendar text and ETag. On push, change only the properties BackLog owns, so fields set by Apple Reminders or Tasks.org survive.
- Edits made on the server become field changes by diffing against the stored copy, stamped with a fresh HLC at pull time.
- D18 encryption can't apply here, because the server has to read the VTODOs. The CalDAV setup screen says so.
