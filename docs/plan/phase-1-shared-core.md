# Phase 1: shared core

This phase builds everything that isn't UI, once, in `shared/`. Tests live in `commonTest` and run on jvm and macosArm64. This is the spec roadmap's phase 1, and its gate is checked at the end of 1.9.

Swift sees every public type in this module. Follow 0.2's notes on how Kotlin types look from Swift, and keep the public API small, as the spec's Risks table advises.

---

### 1.1 Core primitives
**Depends on:** 0.2 · **Spec:** Architecture › Rules for the shared core; Sync › Merge rules (HLC); Data model (`sort_key`) · **Decisions:** D5, D8, D11

**Build**
- A `Clock` interface plus a test clock you can control. Shared code reads the time only through `Clock`.
- UUIDv7 generation, and UUIDv5. UUIDv5 needs SHA-1 in common code: use cryptography-kotlin, which 3.3 needs anyway for HKDF and AES-GCM. Define the `NS_BACKLOG` namespace constant here (D5).
- An HLC with local tick, receive of a remote stamp, encode/decode in the D11 string format, and comparison.
- Fractional index keys: one key between two others (either end may be missing) and n keys between two. Port a known algorithm, such as the one behind the `fractional-indexing` npm package.

**Done when** `./gradlew :shared:allTests` is green on jvm and macosArm64, with tests covering:
- UUIDv7 version bits and time order.
- UUIDv5 against the RFC 9562 example.
- The HLC staying monotonic when the wall clock goes backwards, and receiving a remote clock that's ahead.
- HLC string order matching clock order (a property test over random clocks), and counter overflow.
- New fractional keys always falling strictly between their neighbours, and 1,000 random inserts staying sorted.

**Notes:**
- **Where things are.** Everything is `internal`, so none of it reaches Swift. Widen an item only when an app needs it.

  | Item | Location |
  | --- | --- |
  | `Clock`, `SystemClock` and `TestClock` | `com.backlog.time` |
  | `UuidV7Generator`, `uuidV5`, `NS_BACKLOG`, and helpers that parse and format UUIDs and read their version, variant and timestamp | `com.backlog.id` |
  | `Hlc` and `HlcClock` | `com.backlog.sync` |
  | `FractionalIndex.keyBetween` and `keysBetween` | `com.backlog.order` |

  `TestClock` is in `commonMain`, not `commonTest`, so that the two-device client in 3.4 can drive skewed clocks.
- **`Clock` returns epoch ms (`nowMillis(): Long`), not `Instant`.** Every stored instant is UTC ms, and this keeps `kotlin.time`'s experimental `Instant` and `Clock` out of the shared API. Wrap the value with `Instant.fromEpochMilliseconds` where kotlinx-datetime needs one.
- **`NS_BACKLOG` = `457aad8b-b487-40e0-bafc-670f5c162284`.** It's a random v4 UUID. The Inbox id `uuidV5(NS_BACKLOG, "inbox")` is `8494b556-8818-5209-99e5-3f3bce5d2b0f`, and a test pins it against Python's `uuid.uuid5`. Never change either value (D5).
- **cryptography-kotlin 0.6.0** (`cryptography-core` + `cryptography-provider-optimal`) is in the catalog and pulls in kotlinx-io 0.9.0. It was built against Kotlin 2.3.20, which is fine with 2.4.21. The optimal provider uses the JDK on jvm and Android, and CryptoKit plus CommonCrypto on macOS. 3.3 gets HKDF and AES-GCM from it without adding anything.
  - `SHA1` needs `@OptIn(DelicateCryptographyApi::class)`. The opt-in is scoped to the one hasher.
  - `CryptographyProvider.Default` resolves on all three test targets.
  - The Mac app links and builds with the dependency. But no app code calls SHA-1 yet, so dead-code stripping may have left the CryptoKit part out. When 1.2 seeds the Inbox from the app, check the first launch on the Mac.
- **UUIDv7** uses RFC 9562 §6.2 method 1. `rand_a` is a 12-bit counter, seeded below 2048 on each new millisecond. When the counter overflows, the timestamp moves 1 ms ahead. If the wall clock goes backwards, the generator keeps counting from the last id. So one generator's ids are strictly increasing, which a test checks over 5,000 ids with random clock jumps, backward ones included. Randomness comes from `CryptographyRandom`.
- **HLC.**
  - `HlcClock(deviceId, clock, last)`. `last` restores the clock at launch from the highest stored stamp; in 1.3 that's the max of `field_clock`.
  - `tick()` is for local edits, `receive(remote)` for applying remote changes, and `current()` reads the clock without ticking.
  - State is packed into one `AtomicLong`, (wall << 16) | counter, so the clock is lock-free and thread-safe. `kotlin.concurrent.atomics` needs `@OptIn(ExperimentalAtomicApi::class)`.
  - A remote stamp far in the future is adopted as is. D17's `clock_skew` handling on the Worker is the guard against that.
- **HLC encoding (D11).**
  - The counter is written as 4 **lower-case** hex digits. `decode` rejects upper case, so each stamp has exactly one encoding.
  - Device ids are limited to `[0-9A-Za-z_-]`. That's ASCII, so Kotlin's UTF-16 order and SQLite's byte order agree. UUID device ids fit.
  - The wall part is at most `9999999999999` (13 digits).
  - `Hlc.ZERO` = `0000000000000-0000-0` is the seed stamp for D5.
- **Fractional index.** This is a port of `fractional-indexing`, compared line by line with the 3.4.0 source; its tests' vectors pass. Two deviations, documented in the KDoc:
  - `a > b` throws; upstream 3.4 swaps the arguments.
  - Characters outside base-62 are rejected.

  Keys stay short for common moves. 10,000 appends or prepends give keys of 4 characters or fewer. 1,000 inserts in a row at the same spot grow a key by about one character per 6 inserts.
- **Evidence, 21 Nov 2026.**
  - `./gradlew :shared:allTests :shared:testAndroidHostTest :androidApp:assembleDebug`: BUILD SUCCESSFUL. 31 tests on each of jvm, macosArm64 and the Android host, with 0 failures. The 31 are `UuidsTest` 9, `HlcTest` 14 and `FractionalIndexTest` 6, plus the 2 earlier tests.
  - `xcodegen generate && xcodebuild -scheme BackLog build`: BUILD SUCCEEDED.

---

### 1.2 Schema, migrations and drivers
**Depends on:** 1.1 · **Spec:** Data model (all tables); Features › Tasks (search) · **Decisions:** D5, D6, D7, D9, D12

**Build**
- `model/`: entities and value types (lists, tasks, `Priority`, `RepeatFrom`, ids). Shape them by 0.2's Swift notes; if value classes look bad from Swift, use plain `String` ids.
- The SQLDelight schema:
  - `list`.
  - `task`, including the new `reminder_offset_min` column (D7).
  - `field_clock`, `change_log` (D12) and `sync_state`.
  - A full-text table over `title` + `notes_md`, kept current by triggers (D9).
  - Indexes for list views, sub-tasks, Today/Planned and tombstones.
- Migrations as `.sqm` files from version 1, with migration verification switched on.
- A driver factory and database file location for each platform.
- Seed the Inbox with its fixed id and HLC-zero clocks (D5).
- Delete the 0.2 spike table.

**Done when**
- The schema compiles and migration verification passes.
- Driver smoke tests pass on jvm and macosArm64.
- FTS5 vs FTS4 has been checked on an API 31 emulator and on macOS, and the result is recorded in D9.

**Notes:**
- **Where things are.**

  | Item | Location |
  | --- | --- |
  | Schema (`1.sqm`) and query files (`*.sq`) | `shared/src/commonMain/sqldelight/com/backlog/db/sql/` |
  | Generated rows and queries | `com.backlog.db.sql` (`Task`, `List`, `InspectQueries`, …). `BackLogDatabase` itself is in `com.backlog.db` |
  | `LocalDatabase.open(driver, clock)`, `Seeds`, `MetaKeys`, `EmptySchema` | `com.backlog.db` (internal) |
  | Row → model mapping (`toModel()`), `probeFullText()`, `sqliteVersion()`, `userVersion()` | `com.backlog.db` (internal) |
  | Driver factories: `createAndroidDriver(context)`, `createMacDriver()`, `createJvmDriver(path)` | `androidMain`, `macosMain` and `jvmMain`, package `com.backlog.db` (internal) |
  | Models: `TaskList`, `TaskItem`, `Reminder`, `Priority`, `RepeatFrom`, `ListColor` | `com.backlog.model` (public) |
  | `DatabaseStatus` and `loadDatabaseStatus()` | `com.backlog.diagnostics` (public; a placeholder that 2.1 and 4.1 delete) |

  The `.sq` files live in a `sql/` sub-package on purpose. Generated row classes are named after their tables, so a file in the same package as the generated `List` resolves `List<T>` to the row class, not `kotlin.collections.List`.
- **Hidden from Swift.** SQLDelight's generated code is public. Its `Task` and `List` would shadow `_Concurrency.Task` and `SwiftUI.List` in the Mac app. `shared/build.gradle.kts` applies `SkieVisibility.Level.Private` to the `com.backlog.db` package prefix. SKIE marks those classes `Availability: nonswift` in the API notes. A type-check against the framework confirmed that `Task { }` and `List(…)` still resolve to the system types, and that `BackLogCore.Task` is "unavailable in Swift". So the model types are named `TaskItem` and `TaskList`; don't name a public type `Task` or `List`.
- **Schema version is 2.** With `deriveSchemaFromMigrations`, `1.sqm` holds the whole initial schema, and `Schema.version` is the highest `N.sqm` + 1, because version 1 means an empty database. The next schema change is `2.sqm`, which makes version 3. Never edit `1.sqm` after this.
- **Migration verification.** `verifyMigrations = true` makes code generation fail on any migration statement that doesn't compile against the schema before it. A test with a deliberately broken `2.sqm` failed the build with `2.sqm:2:16 No column found with name missing_column`. With a derived schema there's no separate `.sq` schema to diff, so no `N.db` snapshots are kept (`generate…Schema` doesn't exist in this mode). `DatabaseSchemaTest` checks at runtime that `Schema.create` and `Schema.migrate(1 → current)` give an identical `sqlite_master`.
- **SQL dialect: `sqlite-3-30-dialect`.** Android 12, the minSdk, ships SQLite 3.32.2. That allows `NULLS FIRST/LAST` and partial indexes, but not `RETURNING` (3.35) or `UPDATE … FROM` (3.33).
- **Schema rules that 1.3 builds on:**
  - No foreign keys and no CHECK constraints, because fields arrive one at a time and in any order.
  - Every NOT NULL column has a default, so `INSERT INTO task(id) VALUES (?)` creates a row that fields can then fill in. `DatabaseSchemaTest` covers this.
  - Unknown values decode leniently in `toModel()`. Priority clamps to 0–3, and an unknown `repeat_from` or colour reads as the default. A bad date or time reads as null.
  - `updated_at` is local bookkeeping, not a synced field. `deleted_at` is synced.
  - `task.local_id INTEGER PRIMARY KEY` is local only and never synced or dumped. It's the rowid that `task_fts` points at. A declared integer key keeps rowids stable across `VACUUM`, which `FullTextTest.survivesVacuum` checks. `task.id` is `TEXT NOT NULL UNIQUE`, which costs the same as a TEXT primary key.
  - `field_clock(tbl, row_id, field, hlc)` is `WITHOUT ROWID`. A missing row reads as `Hlc.ZERO`. It has an index on `hlc` for the launch-time `max(hlc)`.
  - `change_log(seq AUTOINCREMENT, tbl, row_id, field, value, hlc, device_id, base_hlc)`. `value` is NOT NULL and holds the JSON encoding (`null` for a cleared field). 1.3 defines the encoding.
  - `sync_state(transport PK, pushed_seq, pull_cursor, last_synced_at)`. `pull_cursor` is opaque text that the transport owns: the Worker's seq, D28's per-peer file and offset, or a CalDAV token. NULL means bootstrap.
  - `local_meta(key, value)` holds settings that never sync. It currently has only `device_id`, a UUIDv7 made on first open. The device name for 3.x can go here too.
- **Inbox seed (D5).** `LocalDatabase.open` runs `Seeds.apply` and the device-id insert in one transaction, both `INSERT OR IGNORE`, on every open. The seed is the Inbox row (`uuidV5(NS_BACKLOG, "inbox")`, "Inbox", `sky`, sort key `a0`, `is_inbox` 1, `updated_at` 0) plus `field_clock` rows at `Hlc.ZERO` for `Seeds.LIST_FIELDS` (name, color, icon, sort_key, is_inbox, deleted_at). Nothing goes to `change_log`. When 1.3 adds the field registry, derive `LIST_FIELDS` from it.
- **Search (D9: FTS4).** `task_fts` is an external-content FTS4 table. Four triggers keep it current. The deletes run BEFORE an update or delete, because FTS4 reads the old text from `task`. The inserts run AFTER. Only `UPDATE OF title, notes_md` touches the index. Tombstoned rows stay indexed, so queries join `task` and filter `deleted_at IS NULL` (`Inspect.sq:searchLive`). Some FTS4 details that differ from FTS5:
  - Options take no spaces around `=` (`content="task"`), or SQLite reports `unrecognized parameter`.
  - The join key is `docid`.
  - The signature is `snippet(task_fts, '[', ']', '…', -1, 8)`.
  - `snippet()` can't share a SELECT with an aggregate.
  - There's no `bm25()`.
  - `INSERT INTO task_fts(task_fts) VALUES ('integrity-check')` raises "malformed" if the index drifts from `task`. The tests use it.
- **Drivers.**
  - Android: `AndroidSqliteDriver` with WAL turned on in `onConfigure`.
  - Mac: `NativeSqliteDriver`, WAL by default, at `Application Support/BackLog/backlog.db`.
  - jvm: `JdbcSqliteDriver(url, Properties(), Schema)`, which sets `user_version`.
  - Tests use in-memory drivers through `expect fun testDriver()` / `emptyTestDriver()` in `commonTest/…/db/TestDrivers.kt`. The androidHostTest actual uses sqlite-jdbc, because the host test has no Android framework.
  - The spike files, the `spike_note` table and `SpikeRepositoryTest` are gone.
- **Apps.** Both spike screens are replaced by a status screen built on `loadDatabaseStatus()`. It shows the SQLite version, FTS4/FTS5 support, `user_version`, the lists, the device id and the file path. 2.1 and 4.1 replace it. `KotlinSendable.swift` now declares `DatabaseStatus` instead of `SpikeRepository`.
- **Evidence, 21 Nov 2026.**
  - `./gradlew :shared:allTests :shared:testAndroidHostTest :androidApp:assembleDebug :shared:verifySqlDelightMigration`: BUILD SUCCESSFUL. 53 tests on each of jvm, macosArm64 and the Android host, with 0 failures. The new ones are `DatabaseSchemaTest` 5, `LocalDatabaseTest` 5, `FullTextTest` 6, `FullTextSupportTest` 2 and `RowsTest` 5.
  - The D9 probe printed by the tests: sqlite-jdbc 3.53.4 has FTS4 and FTS5, and the macOS system SQLite 3.54.0 has FTS4 and FTS5.
  - Android, on a new `BackLog_API_31` AVD (android-31 google_apis arm64, 4 GB data): the first build, with FTS5, showed `SQLite 3.32.2 · FTS4 yes · FTS5 no` and `no such module: fts5`. After switching to FTS4, the app showed schema version 2 and "Inbox (inbox)", with no error. It also recovered from the earlier failed open. On a copy of the app's database, the device's own `sqlite3` 3.32.2 checked:
    - an insert, rename and delete keep the index in step;
    - `cafe` matches "Café";
    - `gro*` matches;
    - the snippet comes back as `ask about [mortgage]`;
    - `integrity-check` passes.
  - Mac: `xcodegen generate && xcodebuild -scheme BackLog build`: BUILD SUCCEEDED. The first launch on a fresh container database showed `SQLite 3.54.0 · FTS4 yes · FTS5 yes`, schema version 2 and "Inbox (inbox)". `sqlite3` on the container file showed `user_version` 2, `journal_mode` wal, the Inbox `8494b556-…` with sort key `a0`, six `field_clock` rows at `0000000000000-0000-0`, an empty `change_log` and the device id. After a relaunch there was still one list with the same device id. Because `uuidV5` (SHA-1 through CryptoKit) ran at launch, 1.1's question about dead-code stripping is answered: it works.

---

### 1.3 Change engine: local writes and field merge
**Depends on:** 1.2 · **Spec:** Sync › Merge rules; Data model › Sync bookkeeping · **Decisions:** D11, D12, D13, D14

**Build**
- A `Change(tbl, rowId, field, value, hlc, deviceId, baseHlc?)` record, with a JSON encoding for each column type.
- A field registry that lists, per table, the synced columns and how to read and write them. The engine stays generic, so a new field costs one registry line.
- The local write path: a single transaction applies a batch of field changes, stamps HLCs, updates `field_clock` and appends to `change_log`.
- Remote apply:
  - Each field is applied when the incoming HLC is higher; ties go by device id, which the D11 encoding handles.
  - A missing row is created.
  - Applying the same change again does nothing, and the local HLC receives the remote stamp.
- The notes-conflict rule (D13) and the post-merge repairs (D14).
- `dump()`: a canonical snapshot of the user tables, used for convergence checks here and in 3.4.

**Done when** merge tests on jvm and macosArm64 cover:
- Renaming on one device while completing on the other keeps both changes.
- A delete is sticky, and a newer restore brings the row back.
- Equal wall times are decided by device id.
- Replaying a batch twice gives the same result as applying it once, and batches arriving in reverse order converge.
- A notes conflict, with the changes arriving in either order and across two rounds of edits, loses no text and writes no duplicate conflict block.
- Every repair in D14 converges when both devices perform it.

**Notes:** _(filled in by the session)_

---

### 1.4 Repositories: writes, integrity rules, undo
**Depends on:** 1.3 · **Spec:** Architecture › Repository layout (`repo/`), Rules for the shared core; Data model › Integrity rules · **Decisions:** D5, D7, D14, D15

**Build**
- `ListRepository` writes: create (sorted to the end), rename, set colour, set icon, reorder, and delete. Delete tombstones the list's tasks and their sub-tasks, and refuses the Inbox.
- `TaskRepository` writes:
  - create, at top level or as a sub-task;
  - edit fields: title, priority, due date, time and zone, reminder (absolute or offset, D7), repeat rule and repeat-from, notes;
  - complete and uncomplete (recurring tasks are wired up in 1.6);
  - delete and restore, both cascading to sub-tasks;
  - move to another list, taking the sub-tasks along in one batch;
  - reorder, indent under the task above, and outdent.
- These rules are enforced here:
  - Tasks nest at most two levels deep, and a sub-task takes its parent's list.
  - Sub-tasks can't have notes, repeat rules or sub-tasks. Indent refuses with a typed reason the UI can show, such as `HasNotes`, `HasRepeat` or `HasSubtasks`.
  - Completing a parent leaves its sub-tasks alone, but reports any that are still open so the UI can offer to complete them.
- Every write returns its inverse change set (D15).
- The API is plain suspend functions only, with no ViewModels (spec rule).

**Done when** tests on jvm and macosArm64 cover:
- Each integrity rule and each indent refusal.
- Cascading delete followed by undo restoring exactly the rows that the delete touched.
- For every write, applying it and then its inverse leaves `dump()` unchanged.

**Notes:** _(filled in by the session)_

---

### 1.5 View queries and search
**Depends on:** 1.4 · **Spec:** Features › Lists and views, Tasks (progress, search) · **Decisions:** D8, D9, D10

**Build**
- These `Flow`s:
  - Lists with open counts.
  - A list's open tasks followed by its Completed group.
  - Sub-tasks with `done/total`.
  - Today, and Planned with the D10 groups.
  - Task detail.
  - Today's open count, for the menu bar and widgets.
- Search: full-text over titles and notes, with prefix matching, open tasks ranked first, and a snippet for highlighting.
- A `currentDate: Flow<LocalDate>` that ticks at local midnight and when the platform reports a time-zone change. Date-based views are built on it so they roll over without a relaunch.
- Every ordering uses (`sort_key`, `id`) (D8).

**Done when** tests on jvm and macosArm64 cover:
- Group boundaries at midnight, at the end of the week and for overdue tasks.
- A Flow emitting again after a write.
- Search hits in titles and in notes, ranked as described.
- Tombstoned rows never appearing.

**Notes:** _(filled in by the session)_

---

### 1.6 Recurrence engine
**Depends on:** 1.4 · **Spec:** Features › Recurring tasks; Sync › Merge rules (recurring ids) · **Decisions:** D7, D21, D22, D23, D26

**Build**
- `recur/`: a model for the RRULE subset, with parse and serialize. The subset is `FREQ`, `INTERVAL`, `BYDAY` including ordinals, `BYMONTHDAY` including `-1`, `COUNT` and `UNTIL`; anything else is rejected with a clear error.
- `next(rule, from, mode)` for both repeat modes (from due date, from completion), and `occurrences(rule, start, range)` for the calendar's ghosts in 5.1.
- Presets mapped to and from rules: Daily, Weekdays, Weekly on (days), Monthly on (day / nth weekday / last day), Yearly. Also a readable description, such as "Every 2 weeks on Mon, Wed", for the UIs.
- Completing a recurring task in `TaskRepository`:
  - stamps the instance done;
  - creates the next instance with D21 ids, the same list, priority and notes, and unchecked copies of the sub-tasks;
  - moves the reminder per D7 and applies `COUNT`/`UNTIL` per D22.
  
  Undoing the completion removes the next instance.

**Done when** tests on jvm and macosArm64 cover:
- The 31st and 30th through short months, 29 Feb yearly, `-1FR`, gaps in `5MO`, and weekly multi-day rules with `INTERVAL=2`.
- `COUNT` running out, and the `UNTIL` boundary being inclusive.
- Intervals in "from completion" mode.
- The reminder moving across DST changes in Europe/London and America/New_York, with Asia/Kolkata as a no-DST control.
- Two databases completing the same instance offline, then merging through 1.3, ending up with one next instance and one checklist, in both repeat modes.

**Notes:** _(filled in by the session)_

---

### 1.7 Quick-add parser A: lists, priority, dates, times, notes, sub-tasks
**Depends on:** 1.2 · can run in parallel with 1.3–1.6 · **Spec:** Quick-add syntax (whole section) · **Decisions:** D6

**Build**
- `parse/`: `QuickAddParser.parse(text, ctx)` returns a `ParseResult` with the title, list match, priority, due date and time, notes, sub-tasks and `tokens`.
  - `ctx` carries the clock, time zone, date-order setting, known list names and `literalRanges`.
  - Each token has a kind, a text range and a value, so UIs can draw chips.
  - Any range passed back in `literalRanges` is read as plain title text. This is how Backspace on a chip works without rewriting the text.
- Tokens covered in this part:
  - `@list`: prefix match, case-insensitive, quotable; an unknown name returns `ListMatch.Unknown(name)`.
  - `!`, `!!`, `!!!` and `!low`/`!med`/`!high`.
  - Date words and time words.
  - `//` notes.
  - Sub-task lines starting with ` - `.
  - Escapes: `\@home`, and quoted text.
- The spec's rules: day/month order by default, flipped by the setting; a bare weekday; today's weekday combined with a later time today; month names only count next to a number; a time with no date means today if it's still ahead, otherwise tomorrow.
- A data-driven test table in `commonTest`, with columns for input, ctx overrides and the expected result. Aim for at least 70 cases in this part, including traps: `Lunch with May`, `Buy 5 apples`, a bare `@`, `!!!!`, emoji, trailing spaces, and `5/12` under both date orders.

**Done when**
- The table is green on jvm and macosArm64.
- The parser never throws on 10,000 random strings.
- The title is never empty when the input contains text that isn't a token.

**Notes:** _(filled in by the session)_

---

### 1.8 Quick-add parser B: repeats and reminders
**Depends on:** 1.6, 1.7 · **Spec:** Quick-add syntax (repeat, `after done`, reminder rows, Rules, Examples) · **Decisions:** D7, D23

**Build**
- Repeat phrases that produce rules from 1.6: `daily`, `weekly`, `monthly`, `yearly`, `every weekday`, `every mon wed`, `every 3 days`, `every 2 weeks`, `every month on 15`, `every month on last day`, and `every last fri`. `after done` sets repeat-from-completion. A repeat with no date gets its first occurrence as the due date.
- Reminders:
  - `^30m` and `^1h` are offsets (D7).
  - `^9am` is an absolute time on the due date.
  - An offset with no due time is relative to 09:00 (spec rule).
- Decide, add test cases for, and record under Notes what `^9am` and `^30m` do on a task that has no due date. Suggestion: `^time` acts like a time with no date and sets the reminder only; `^offset` stays as title text and gets a warning chip.
- A `createFromQuickAdd(result)` repository call that resolves the list, creates the task and its sub-tasks, and returns the inverse change set.
- The spec's five examples, verbatim, as tests.

**Done when**
- The combined 1.7 + 1.8 table has at least 100 cases, all green (the phase 1 parser gate).
- The fuzz and title properties from 1.7 still hold.

**Notes:** _(filled in by the session)_

---

### 1.9 Reminder planner, JSON export/import, phase gate
**Depends on:** 1.6 · **Spec:** Features › Reminders, Data ownership; Roadmap (phase 1 gate) · **Decisions:** D6, D7, D20, D24, D25

**Build**
- `remind/`:
  - `plan(now, zone, horizon = 14 days, cap)` returns the reminders for open, non-deleted tasks. Times are converted per D6 and offsets per D7, and each reminder has a stable notification id.
  - `diff(previous, next)` returns schedule and cancel lists, so the platforms do as little work as possible.
- Snooze (D24), plus "complete from a notification".
- JSON export/import (D20), with a format version.
- The phase gate: run the full `allTests` and record the number of parser cases, recurrence cases and merge tests in the README's phase 1 gate line.

**Done when**
- Planner tests cover:
  - Spring-forward and fall-back days, including a reminder time inside the gap.
  - A time-zone change (Kolkata → London keeps the 09:00 wall time).
  - Cap ordering, and diffs after an edit, a completion and a deletion.
- Export → wipe → import gives an equal `dump()`, and importing twice is the same as importing once.
- Every item in the phase 1 gate is met.

**Notes:** _(filled in by the session)_
