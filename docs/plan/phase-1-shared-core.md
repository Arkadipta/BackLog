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

**Notes:** _(filled in by the session)_

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

**Notes:** _(filled in by the session)_

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
