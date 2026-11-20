# Personal Todo App — Spec v1

Nov 20, 2026 · @Arkadipta

## Overview

A local-first personal task app for macOS and Android. Each device keeps its own SQLite database and works fully offline; changes sync through a Cloudflare Worker you deploy yourself. No accounts, no sharing, one user.

**Goals**

- Faster capture than Microsoft To Do: one hotkey on Mac, one tap on an Android home-screen widget.
- Feels native on each platform, not a lowest-common-denominator UI.
- Your data stays in your own Cloudflare account and on your devices, and can be exported as JSON at any time.
- A sync layer clean enough that Syncthing and CalDAV can be added later without touching the UI.

**Non-goals for v1**

- Accounts, sign-up, multiple users, shared lists.
- iOS, Windows and web clients (the shared core makes iOS cheap later).
- Attachments, tags, kanban, My Day.

**Decisions so far**

| Area | Decision |
| --- | --- |
| Stack | Kotlin Multiplatform shared core; SwiftUI on macOS; Jetpack Compose on Android |
| Storage | SQLite on each device via SQLDelight, plus an append-only change log |
| Sync, phase 1 | Cloudflare Worker + D1, protected by one shared secret |
| Sync, later | Syncthing (file-based), then CalDAV (VTODO) |
| Task structure | Lists > tasks > sub-tasks; exactly two levels |
| Notes | Markdown, stored as plain text |
| v1 extras | Reminders, recurring tasks, calendar view (tasks only), natural-language quick-add |
| Mac shell | Normal window + menu bar icon + global hotkey that opens a floating quick-add panel |
| Deferred | Kanban (waits for status/tags), tags, smart lists, My Day, attachments |

One consequence: the quick-add syntax you chose includes priority tokens, so v1 carries a simple priority field (none, low, medium, high) even though tags wait.

## Architecture

All logic that is not UI lives once, in a Kotlin Multiplatform module; each platform writes only its UI and OS integrations (hotkeys, widgets, notification scheduling). The UI never talks to the network; it reads and writes the local DB, and sync runs behind it.

&#91;embedded content: architecture · 2 apps, 1 shared core, 3 sync transports\]

Only the sync engine reaches outside the device, and it talks to one transport at a time; the two apps see only the local database.

**Repository layout (one monorepo)**

- `shared/` (KMP: `androidMain`, `macosMain`, `commonMain`)
  - `model/` entities and value types
  - `db/` SQLDelight schema, queries, migrations
  - `repo/` repositories exposing `Flow`s to the UIs; every write also appends to the change log
  - `sync/` a `SyncTransport` interface plus the Cloudflare implementation; Syncthing and CalDAV come later as more implementations
  - `parse/` quick-add natural-language parser
  - `recur/` RRULE subset: next-occurrence maths
  - `remind/` works out which reminders should fire in the next N days; the platform layer schedules them
- `macApp/` Xcode project, SwiftUI, consumes `shared` as a framework
- `androidApp/` Compose app + Glance widgets
- `worker/` Cloudflare Worker in TypeScript, with D1 migrations and a `wrangler.toml`

**Libraries**

| Concern | Choice | Note |
| --- | --- | --- |
| Local DB | SQLDelight 2 | Android driver + native driver on macOS; type-safe queries, `Flow` support |
| Time | kotlinx-datetime | Store instants as UTC epoch ms; keep the task's IANA time zone for floating dates |
| JSON | kotlinx-serialization | Sync payloads and JSON export |
| HTTP | Ktor client | OkHttp engine on Android, Darwin engine on macOS |
| Kotlin to Swift | SKIE | Turns `Flow` into `AsyncSequence`, sealed classes into Swift enums. Swift Export is still Alpha as of Kotlin 2.4, so not yet |
| Mac hotkey | KeyboardShortcuts (sindresorhus) | User-recordable shortcut, works in a sandboxed app |
| Android widgets | Jetpack Glance | Compose-style widget code |
| Markdown render | A SwiftUI markdown view on Mac; a Compose markdown renderer on Android | Editing stays plain text in both |
| Worker | TypeScript + D1 via `wrangler` | You already run a free-tier Worker, so the deploy flow is familiar |

**Rules for the shared core**

- IDs are UUIDv7 generated on the device, so nothing needs a server round-trip to create.
- The shared module exposes plain suspend functions and `Flow`s. No ViewModels in shared; each platform wraps it in its own (an `@Observable` class on Mac, a `ViewModel` on Android).
- The natural-language parser and recurrence maths are pure functions with unit tests in `commonTest`. These are the parts most likely to have subtle bugs.

## Data model

Two tables hold your data and three hold sync bookkeeping. Every user-data row has `updated_at` and a nullable `deleted_at`; nothing is ever hard-deleted until a tombstone is older than 90 days and every device has synced past it.

**`list`**

| Field | Type | Notes |
| --- | --- | --- |
| id | TEXT (UUIDv7) |  |
| name | TEXT |  |
| color | TEXT | Token name from the palette, not a hex value |
| icon | TEXT | SF Symbol / Material icon name, nullable |
| sort\_key | TEXT | Fractional index, so reordering touches one row |
| is\_inbox | INTEGER | Exactly one list is the Inbox; quick-add lands here by default |

**`task`** (sub-tasks are rows in the same table)

| Field | Type | Notes |
| --- | --- | --- |
| id | TEXT (UUIDv7) |  |
| list\_id | TEXT | Sub-tasks copy their parent's list\_id |
| parent\_id | TEXT, nullable | Null = top-level task. Must point at a task whose own parent\_id is null: two levels, enforced in the repo layer |
| title | TEXT |  |
| notes\_md | TEXT, nullable | Markdown source. Top-level tasks only in v1 |
| priority | INTEGER | 0 none, 1 low, 2 medium, 3 high |
| due\_date | TEXT, nullable | Local date `YYYY-MM-DD` |
| due\_time | INTEGER, nullable | Minutes after midnight; null = all-day |
| time\_zone | TEXT, nullable | IANA zone when due\_time is set, so 9:00 stays 9:00 if you travel |
| reminder\_at | INTEGER, nullable | UTC epoch ms |
| rrule | TEXT, nullable | RFC 5545 RRULE subset (see Features) |
| repeat\_from | TEXT | `due` or `completion` |
| series\_id | TEXT, nullable | Shared by all instances of one recurring task |
| completed\_at | INTEGER, nullable | Null = open |
| sort\_key | TEXT | Order within the list, or within the parent for sub-tasks |

**Sync bookkeeping**

| Table | Holds |
| --- | --- |
| `field_clock` | Latest hybrid logical clock (HLC) stamp per (table, row id, field). Used to decide merges |
| `outbox` | Local changes not yet pushed: table, row id, field, new value, HLC, device id |
| `sync_state` | Device id, device name, last server sequence number pulled, per transport |

**Integrity rules**

- Deleting a list tombstones its tasks; deleting a task tombstones its sub-tasks.
- Completing a parent does not complete its sub-tasks automatically, but the UI offers to (matches MS To Do).
- Moving a parent to another list moves its sub-tasks with it, as one batch of changes.
- A sub-task cannot have sub-tasks, notes or recurrence in v1. This keeps the second layer a checklist, which is how MS To Do steps work.

## Sync

Sync merges field by field: every edit is a (row, field, value, clock) record, and the newest clock wins per field. The same merge code serves all three transports; only how records travel changes. Start with Cloudflare, because it needs no folder permissions and no third-party app on the phone.

**Merge rules (shared core, all transports)**

- Each change carries a hybrid logical clock (HLC): wall-clock ms + counter + device id. Higher HLC wins; device id breaks ties.
- Per-field last-write-wins. Renaming a task on the Mac while completing it on the phone keeps both changes.
- `deleted_at` is just another field, so a delete is sticky unless a newer change explicitly restores the row.
- Notes exception: if two devices edited `notes_md` since their last sync, keep the winner and append the loser under a line `--- conflicting edit from <device>, <time> ---`. No silent loss of text.
- Recurring tasks: the next instance's id is UUIDv5(series\_id + occurrence date). If both devices complete the same instance offline, they create the same row, which then merges instead of duplicating.
- Applying a change is idempotent, so replaying a batch is always safe.

**Phase 1: Cloudflare Worker + D1**

The Worker is a thin relay that stores the winning value per field; clients do all the thinking.

- D1 table `state(tbl, row_id, field, value, hlc, device_id, seq)`, primary key (tbl, row\_id, field), index on `seq`.
- `POST /v1/push` takes up to 500 changes. For each, upsert only where the incoming HLC is greater, and stamp a new `seq`. Done in one D1 batch, so it is atomic.
- `GET /v1/pull?since=<seq>&limit=500` returns rows with `seq` above that, plus `next_seq` and `more`.
- Because `state` keeps one row per field rather than a full history, it compacts itself. A new device bootstraps with `since=0`.
- Auth: one random 256-bit secret, sent as `Authorization: Bearer`. Stored as a Worker secret (`wrangler secret put`), in the Keychain on Mac and the Android Keystore on the phone.
- Pairing: the Mac app shows a QR code holding the Worker URL + secret; the Android app scans it. That is the whole "no accounts" story.
- When to sync: Mac on every change (2 s debounce), on window focus and every 5 min. Android on change while in the foreground, on app open, and a WorkManager job every 15 min (Android's minimum) when online.

**Free-tier budget.** [Cloudflare's D1 pricing page](https://developers.cloudflare.com/d1/platform/pricing/) gives the free plan 5 million rows read and 100,000 rows written per day, and since September 2026 queries fail outright once a limit is hit ([changelog](https://developers.cloudflare.com/changelog/post/2026-09-01-d1-free-tier-limit-enforcement/)). A single user makes a few hundred field edits a day, so writes are far below the cap. The real risk is reads: an unindexed pull scans the whole table and every scanned row counts, so the `seq` index is mandatory. The Worker should return HTTP 429 cleanly when D1 reports the limit, and clients back off until 00:00 UTC.

**Optional, recommended before relying on it: end-to-end encryption.** Encrypt `value` on the device with AES-GCM using a key derived from the pairing secret (via the cryptography-kotlin library). Cloudflare then only sees ids, field names and timestamps. Costs about a day of work; can land in phase 2.

**Phase 2: Syncthing**

- You pick a folder that Syncthing already syncs. Each device writes only to its own subfolder: `todo-sync/<device_id>/<date>-<n>.ndjson`, one change per line, append-only.
- Because no two devices ever write the same file, Syncthing never produces conflict copies.
- Each device remembers how far it has read in every other device's files, reads new lines and merges with the same rules.
- Each device writes a compacted snapshot weekly and deletes its own older files.
- Mac: folder chosen once, kept as a security-scoped bookmark. Android: folder chosen through the system folder picker (Storage Access Framework). The official Syncthing Android app was discontinued in late 2024, so this depends on a community fork being installed.

**Phase 3: CalDAV (VTODO)**

CalDAV is a bridge rather than a pure transport: the server owns whole objects with ETags, so it fits the field-merge model less cleanly.

| App concept | CalDAV mapping |
| --- | --- |
| List | A calendar collection that supports VTODO |
| Task | VTODO: SUMMARY, DESCRIPTION (notes), DUE, PRIORITY (high 1, medium 5, low 9), STATUS / COMPLETED, RRULE |
| Sub-task | VTODO with `RELATED-TO;RELTYPE=PARENT` |
| Reminder | VALARM |
| Order | `X-APPLE-SORT-ORDER` |

- Pull with `sync-collection` (RFC 6578) where the server supports it, else compare ETags.
- Push with `If-Match`; on HTTP 412, fetch the server copy, merge field by field locally, retry.
- Gain: tasks show in other CalDAV clients (Apple Reminders, Thunderbird, Tasks.org on Android). Works with Fastmail, Nextcloud, Radicale. Google Calendar's CalDAV does not serve tasks, so Google is out.

Only one transport is active at a time in each phase. Running several at once later is possible, because merges are idempotent.

## Features (v1)

v1 covers what MS To Do does, minus My Day and sharing, plus a calendar view and a stronger quick-add.

**Lists and views**

- Inbox (cannot be deleted), plus your own lists with name, colour, icon and drag-to-reorder.
- Two automatic views built from due dates: **Today** (due today + overdue) and **Planned** (everything with a date, grouped Overdue / Today / Tomorrow / This week / Later). These are queries, not stored lists, so they cost little. This is not My Day: you cannot pin an undated task to Today.
- Completed tasks fold into a collapsible "Completed" group at the bottom of each list.

**Tasks**

- Fields: title, list, priority, due date, optional due time, reminder, repeat, notes.
- Sub-tasks: an ordered checklist under a task, with their own checkbox, title and drag order. Progress shows on the parent row as `2/5`.
- Notes: markdown, edited as plain text. Mac shows a live preview on the right; Android toggles Edit / Preview. Links open in the browser. Checkbox syntax in notes is rendered but is not a sub-task.
- Undo: Cmd+Z on Mac; an "Undo" snackbar on Android after complete, delete or move.
- Search across titles and notes, backed by a SQLite full-text index.

**Reminders**

- One reminder per task (MS To Do behaves the same way), set as an absolute time or as an offset before the due time.
- Shared core computes every reminder due in the next 14 days. Each platform schedules them locally, so reminders fire with no network.
- Mac: `UNUserNotificationCenter` with actions **Complete** and **Snooze 10 min / 1 hour**.
- Android: `AlarmManager` exact alarms plus a high-priority notification channel, same actions. Reschedule after reboot, time-zone change and every sync. Exact alarms need the `USE_EXACT_ALARM` or `SCHEDULE_EXACT_ALARM` permission. Google Play restricts the first to alarm and calendar apps, but a side-loaded personal build can simply declare it.
- Reminders fire on both devices. Completing on one cancels the other's at its next sync.

**Recurring tasks**

- Supported RRULE subset: `FREQ` daily / weekly / monthly / yearly, `INTERVAL`, `BYDAY` (including `2MO`, `-1FR` for monthly), `BYMONTHDAY`, `COUNT`, `UNTIL`.
- UI presets: Daily, Weekdays, Weekly on (days), Monthly on (day / nth weekday), Yearly, plus a custom builder.
- Repeat from due date (default) or from completion date ("water plants 3 days after I last did").
- Completing an instance stamps it done and creates the next one with the same list, priority, notes and unchecked sub-tasks. This matches MS To Do and keeps history.

**Calendar view (tasks only)**

- Month grid and week view on Mac; month grid + agenda list on Android.
- All-day tasks sit in a band at the top of each day; timed tasks sit at their time in week view.
- Drag a task to another day to reschedule it (long-press drag on Android).
- Future occurrences of recurring tasks show as faint "ghost" items computed from the rule; they become real rows only when the previous one is completed.
- Toggle to show or hide completed tasks.

**Data ownership**

- Export everything as one JSON file, and import that file back. Also serves as the backup.

## Quick-add syntax

One line of text becomes a full task: recognised tokens are lifted out of the title and shown as coloured chips while you type. The parser lives in the shared core, so the Mac panel, the Android app and the Android widget behave identically.

| Token | Sets | Examples |
| --- | --- | --- |
| `@name` | List (prefix match, case-insensitive; quote names with spaces) | `@work`, `@"Home admin"` |
| `!` / `!!` / `!!!` or `!low` / `!med` / `!high` | Priority low / medium / high | `!!!`, `!high` |
| Date words | Due date | `today`, `tod`, `tomorrow`, `tmr`, `fri`, `next mon`, `in 3 days`, `in 2 weeks`, `dec 5`, `5 dec`, `2026-12-05`, `5/12` |
| Time words | Due time | `5pm`, `17:00`, `at 9`, `noon`, `morning` (09:00), `evening` (18:00) |
| `every …` / `daily` / `weekly` / `monthly` / `yearly` | Repeat rule | `every weekday`, `every mon wed`, `every 2 weeks`, `every month on 15`, `every last fri` |
| `after done` | Repeat from completion | `every 3 days after done` |
| `^offset` or `^time` | Reminder | `^30m` (30 min before due), `^1h`, `^9am` |
| `//` | Everything after it becomes the notes | `Book flights // check Revolut fx first` |
| Lines starting ` -  ` | Sub-tasks (multi-line input on Mac, or a paste) | see last example |

**Rules**

- Numeric dates read as day/month by default (Indian and European order); a setting flips this to month/day.
- A bare weekday means its next occurrence; today's weekday means one week out unless a time later today is also given.
- Month names count as dates only next to a number, so "Lunch with May" stays a title.
- A time with no date means today if still ahead, else tomorrow.
- A reminder offset with no due time is relative to 09:00 on the due date.
- Escape a token with a backslash (`\@home`) or by putting that part of the title in quotes.
- Backspace on a chip turns it back into plain title text: a one-key fix for a wrong guess.
- Unknown `@list`: the Mac panel offers "Create list"; the widget files the task in the Inbox and flags it.

**Examples**

| Input | Result |
| --- | --- |
| `Call bank tomorrow 5pm !high @finance` | "Call bank" in Finance, due tomorrow 17:00, high priority |
| `Pay credit card every month on 5 ^9am` | Repeats monthly on the 5th; reminder at 09:00 |
| `Water plants every 3 days after done` | Next due 3 days after each completion |
| `Read Network Momentum paper fri // focus on the graph construction` | Due Friday, with notes |
| `Plan Goa trip @personal` + lines `- book flights`, `- book hotel` | Task with two sub-tasks |

The parser needs a test table of at least 100 input/output pairs in `commonTest` before the UI is built.

## Mac app

Every action can be done without the mouse; the global hotkey adds a task from anywhere in under two seconds without pulling the main window forward. Target macOS 14 (Sonoma) or later, for SwiftUI's `@Observable`.

**Three surfaces**

1. **Main window**: SwiftUI `NavigationSplitView`. Sidebar (Today, Planned, Calendar, then lists), task list in the middle, detail inspector on the right with fields, sub-tasks and a notes editor with live preview.
2. **Menu bar icon** (`MenuBarExtra`): shows today's open-task count; the popover lists Today with checkboxes and a quick-add field at the top.
3. **Quick-add panel**: a floating `NSPanel` that can take typing without activating the app, so it never switches Spaces or raises the main window. It appears centred on the screen holding the mouse pointer, above full-screen apps.

**Global hotkeys** (both re-recordable in Settings via KeyboardShortcuts)

- Quick-add panel: default Option+Shift+Space. Option+Space is often taken by Raycast or Alfred.
- Show main window: default Option+Shift+T.

**Quick-add panel keys**

| Key | Action |
| --- | --- |
| Enter | Save and close |
| Cmd+Enter | Save and open the task in the main window |
| Shift+Enter | New line (lines starting ` -  ` become sub-tasks) |
| Option+Enter | Save and keep the panel open for the next task |
| Esc | Close without saving (text kept for 60 s, in case it was an accident) |

**Main window keys**

| Key | Action |
| --- | --- |
| N | New task at the cursor in the current list |
| Cmd+Shift+N | New list |
| Up / Down, or J / K | Move selection |
| Enter | Edit the selected title |
| Space | Complete / un-complete |
| Tab / Shift+Tab | Make it a sub-task of the task above / back to top level |
| D, R, E, P, M | Set due date, reminder, repeat, priority, move to list (each opens a small popover that accepts quick-add words, such as `fri 5pm`) |
| Cmd+Up / Cmd+Down | Reorder |
| Cmd+I | Show or hide the inspector |
| Cmd+K | Command palette: any action, any list, any task by name |
| Cmd+F | Search |
| Cmd+1 / 2 / 3 | Today / Planned / Calendar |
| Cmd+4 to Cmd+9 | First six lists |
| Cmd+Backspace | Delete (undoable) |
| Cmd+Z / Cmd+Shift+Z | Undo / redo |
| In calendar: arrows, T, Option+Left / Right | Move between days, jump to today, move the selected task one day |

**Mac plumbing**

- Launch at login via `SMAppService`, toggle in Settings.
- Sandboxed app; sync secret in the Keychain; Syncthing folder (phase 2) kept as a security-scoped bookmark.
- For personal use, build and run from Xcode with your own Apple ID; no notarisation needed.

## Android app

Capture is one tap from the home screen, and the app follows Material 3 with dynamic colour so it sits naturally on One UI. Target Android 12 (API 31) or later.

**Screens**

- **Home**: Today and Planned cards at the top, then your lists. Same structure as MS To Do, so nothing to relearn.
- **List**: task rows with checkbox, title, due chip, `2/5` sub-task progress. Swipe right to complete, swipe left to reschedule (Tomorrow / Next week / Pick). Long-press to drag-reorder.
- **Add**: a floating action button opens a bottom sheet with the quick-add field; chips appear under it as tokens are recognised.
- **Task detail**: fields, sub-task checklist (add, tick, drag), notes with Edit / Preview toggle.
- **Calendar**: month grid with dots per day, agenda list under it; long-press drag a task onto another day.
- **Settings**: sync (scan pairing QR), date order, default list, theme, export/import.
- Edge-to-edge layout, predictive back gesture, haptic tick on complete.

**Widgets (Jetpack Glance)**

| Widget | Size | Behaviour |
| --- | --- | --- |
| Quick add | 1x1 icon or 4x1 bar | Tap opens a small see-through window with the field focused and the keyboard already up. Enter saves and closes; you never see the full app. A mic button uses system speech input |
| List | Resizable, 2x2 to 4x5 | Shows one chosen list, or Today. Tick checkboxes in place. A `+` opens quick-add preset to that list. Refreshes whenever the local DB changes |

Glance widgets cannot host a text field, so the quick-add widget opens a see-through activity. That is the standard workaround and feels instant if the activity is light.

**Other fast paths**

- Long-press the app icon: "New task" shortcut.
- Quick Settings tile: "New task".
- Share sheet target: sharing a link or text from any app creates a task, with the link in the notes.

**Notifications and background work**

- Channels: Reminders (high importance) and Sync problems (low).
- Reminder actions: Complete, Snooze 10 min, Snooze 1 h.
- Samsung's battery manager can delay background jobs. Settings should link to the system screen to exempt the app (One UI's "Never sleeping apps"). Exact alarms are not affected; the 15-minute sync job is.

## Visual design and animation

Each platform looks like itself; what is shared is a small set of tokens (list colours, spacing, motion timings) and the moments that get animation. Motion is short (150 to 350 ms), spring-based, and drops to plain crossfades when the OS "Reduce motion" setting is on.

**Shared tokens**

- Eight list colours defined by name (`sky`, `mint`, `amber`, `coral`, `violet`, `slate`, `rose`, `teal`), each with a light and dark value per platform. Sync stores the name, never a hex value.
- Spacing on a 4-point grid; row height 44 on Mac, 56 on Android.
- System fonts: SF Pro on Mac, the system font on Android (One UI's own on Samsung). Due-date chips and counts use tabular figures so they do not jitter.
- Full dark mode on both, following the system.

**Look per platform**

- Mac: translucent sidebar material, hover highlights on rows, inspector as a native side panel, SF Symbols.
- Android: Material 3 with dynamic colour from the wallpaper (falls back to the list colour palette), Material icons, large rounded bottom sheets.

**Animated moments**

| Moment | Animation |
| --- | --- |
| Complete a task | Checkbox fills in the list colour, the tick draws itself, a strike-through sweeps left to right. After 600 ms the row glides into the Completed group (`matchedGeometryEffect` on Mac, `animateItem` in a lazy list on Android). Light haptic on Android |
| Last task in a list completed | Short "all clear" empty state with a small drawn illustration, once per day |
| Quick-add panel opens | Scale 0.96 to 1 + fade, 120 ms; chips pop in on a spring as tokens are recognised |
| Sub-tasks expand / collapse | Height spring; progress `2/5` counts up |
| Drag to reorder | Row lifts with a soft shadow; neighbours slide out of the way |
| Calendar | Month pages slide; the day under a dragged task glows as a drop target |
| Sync | A small dot in the sidebar footer or the home top bar: idle, syncing (pulse), error (amber). No spinners in the content |

Nice to have later: a gentle "ink" ripple behind the checkbox on complete, and a custom app icon in the list-colour palette.

## Roadmap

Build the shared core first, then the Mac app, then sync, so the Android app is born syncing. Each phase ends with a gate you can check, which gives Claude Code a clear stopping point per session.

1. **Foundations (shared core)**
   - Monorepo, Gradle KMP setup with `androidTarget`, `macosArm64` and `jvm` (for fast tests and the CLI test client), SQLDelight schema + migrations, repositories, change log, HLC.
   - Quick-add parser, RRULE subset, reminder planner, JSON export/import.
   - Gate: `commonTest` green, including 100+ parser cases, recurrence cases across month ends and DST changes, and merge tests for every rule in the Sync section.
2. **Mac app, local only**
   - Main window, lists, tasks, sub-tasks, markdown notes, Today and Planned, quick-add panel + global hotkey, menu bar, reminders, full keyboard map.
   - Gate: you use it for a week in place of MS To Do on the Mac.
3. **Cloudflare sync**
   - Worker + D1 + migrations, push/pull, bearer auth, 429 handling, QR pairing, sync status dot.
   - A small command-line test client in `shared/jvm` that plays scripted two-device conflict scenarios against a local `wrangler dev`.
   - Gate: all scripted scenarios converge to the same state; a 7-day soak stays under 5% of the free-tier limits.
4. **Android app**
   - All screens, both widgets, Quick Settings tile, share target, reminders, background sync.
   - Gate: an edit on either device shows on the other within one sync cycle, and the quick-add widget saves a task in under 3 s from tap.
5. **Calendar view and polish**
   - Month and week views, drag to reschedule, ghost recurrences, the animation table, empty states, dark mode pass.
6. **Hardening + Syncthing**
   - End-to-end encryption of values; Syncthing transport; weekly snapshot compaction.
7. **CalDAV**
   - VTODO mapping, `sync-collection`, 412 retry merge; tested against Radicale running locally.
8. **v2 features**
   - Tags and a status field, then kanban boards on top of them; smart lists; My Day; possibly iOS from the same core.

## Risks and open questions

The biggest technical risk is the Kotlin-to-Swift boundary on macOS; the biggest product risk is reminders firing twice. Both have a fallback.

| Risk | Fallback |
| --- | --- |
| KMP macOS target is less travelled than iOS; slow builds, odd interop | Keep the shared API small (repositories + parser). If it hurts, the Mac app can call the same SQLite file through GRDB and reimplement only sync, at the cost of duplicate merge code |
| A device with a badly wrong clock wins every merge | The Worker rejects changes stamped more than 5 min ahead of its own clock; clients resync their HLC from the server's `Date` header |
| D1 free-tier hard stop | `seq` index, batched pushes, 429 back-off; paid plan is USD 5/month if ever needed |
| Notes edited on two devices while offline | Conflict block appended, never dropped |
| Syncthing on Android depends on a community fork | Cloudflare stays the default transport |

**Open questions for you**

- [ ] Reminders: fire on both devices (simple), or choose per device in Settings ("Mac only while it's awake, else phone")?
- [ ] Import from Microsoft To Do: worth a one-time importer through Microsoft Graph while your account still works, or start fresh?
- [ ] Are macOS 14+ and Android 12+ fine as minimum versions?
- [ ] End-to-end encryption in phase 3 with sync, or leave it for phase 6?
- [ ] For the later kanban: status values fixed (To do / Doing / Waiting / Done), or per-list?
- [ ] App name and bundle id, needed before the first build.

**Sources**

- [Cloudflare D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/)
- [D1 free-tier enforcement, Sep 2026](https://developers.cloudflare.com/changelog/post/2026-09-01-d1-free-tier-limit-enforcement/)
- [Kotlin Multiplatform for iOS / Swift interop (SKIE, KMP-NativeCoroutines)](https://kotlinlang.org/docs/multiplatform/kmp-for-ios.html)
- [Swift Export reaches Alpha in Kotlin 2.4.0](https://www.youngju.dev/transcribe/2026-07-16-kotlin-swift-export-alpha.en)
