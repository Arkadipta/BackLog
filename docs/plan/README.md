# BackLog v1 build plan

This plan splits the [v1 spec](../../Personal_Todo_App_Spec_v1.md) into 39 tasks. Each task fits one Claude Code session and ends with a check you can run. [decisions.md](decisions.md) records your answers to the spec's open questions and how the plan fills the gaps found while splitting it. Where decisions.md disagrees with the spec, decisions.md wins.

The phases follow the spec's roadmap (phases 1–7), plus a phase 0 for tooling and a risk spike. Two things are left out: phase 8 (the v2 features) and a Microsoft To Do importer.

## Running a session

1. Start a fresh session with: `Read CLAUDE.md and docs/plan/README.md, then do task 1.3.`
2. Before writing any code, the session reads decisions.md, the phase file that contains the task, and the spec sections listed under the task's **Spec**.
3. Work on a branch named `task/<id>-<slug>`. Write conventional commits that end with the task id, as LifeLog does: `feat(sync): merge changes field by field (1.3)`.
4. A task is done when every **Done when** item is met and there's evidence for each one: test output, a build log, or a manual check you confirm.
5. Before ending the session:
   - Set the task's status in the tables below.
   - Under **Notes** in the phase file, write anything later tasks need, such as versions picked, deviations and measurements.
   - Update decisions.md if a decision changed.
   - Merge the branch.
6. If a task turns out too big for one session, stop at a point where the tests pass and the work is committed, then add the remainder to the phase file as task `<id>b`.

Tasks whose dependencies are all done can run at the same time in separate worktrees. The natural side lanes are:
- 1.7 alongside 1.3–1.6.
- 3.1 any time after 1.1, for example during the week you trial the Mac app.
- 7.1 any time after 1.6.

To run tasks one at a time instead, go down the tables in order; that order respects every dependency.

## Environment (checked 20 Nov 2026)

| Tool | State |
| --- | --- |
| Xcode | 27.2, selected with `xcode-select` (installed before 0.1) |
| XcodeGen | Installed from Homebrew (D3) |
| JDK | Android Studio's bundled JBR 21. Set `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home` |
| Kotlin | Supplied by the Kotlin Gradle plugin at build time. Android Studio's bundled compiler isn't used |
| Gradle | No global install. The repo uses the Gradle wrapper (9.9.2) |
| Android SDK | `~/Library/Android/sdk`, with platforms 33–37.2, the emulator and platform-tools. 0.1 added 37.2 |
| Node | 26, from Homebrew. `wrangler` becomes a devDependency of `worker/` and runs through `npx` |
| Machine | Apple silicon, macOS 27.2 |

## Tasks

Status values: `todo`, `doing`, `done`.

### Phase 0: toolchain and skeleton ([phase-0-setup.md](phase-0-setup.md))

| ID | Task | Depends on | Status |
| --- | --- | --- | --- |
| 0.1 | Toolchain and monorepo skeleton | none | done |
| 0.2 | Kotlin ↔ Swift walking skeleton (risk spike) | 0.1 | done |

Gate: the spike proves that the SQLDelight → `Flow` → SKIE → SwiftUI chain works on macOS. If it doesn't, choose the spec's GRDB fallback before starting phase 1. **Result (0.2): go.** The chain works, so the GRDB fallback isn't needed.

### Phase 1: shared core ([phase-1-shared-core.md](phase-1-shared-core.md))

| ID | Task | Depends on | Status |
| --- | --- | --- | --- |
| 1.1 | Core primitives: UUIDv7/v5, HLC, fractional keys | 0.2 | todo |
| 1.2 | Schema, migrations and drivers | 1.1 | todo |
| 1.3 | Change engine: local writes and field merge | 1.2 | todo |
| 1.4 | Repositories: writes, integrity rules, undo | 1.3 | todo |
| 1.5 | View queries and search | 1.4 | todo |
| 1.6 | Recurrence engine | 1.4 | todo |
| 1.7 | Quick-add parser A: lists, priority, dates, times, notes, sub-tasks | 1.2 | todo |
| 1.8 | Quick-add parser B: repeats and reminders | 1.6, 1.7 | todo |
| 1.9 | Reminder planner, JSON export/import, phase gate | 1.6 | todo |

Gate (spec): `commonTest` is green, including 100+ parser cases, recurrence cases across month ends and DST changes, and merge tests for every rule in the spec's Sync section. Counts: _(recorded by 1.9)_

### Phase 2: Mac app, local only ([phase-2-mac-local.md](phase-2-mac-local.md))

| ID | Task | Depends on | Status |
| --- | --- | --- | --- |
| 2.1 | Mac app shell and lists | 1.5 | todo |
| 2.2 | Task list, sub-tasks, Today and Planned | 2.1 | todo |
| 2.3 | Inspector, notes and search | 2.2, 1.6 | todo |
| 2.4 | Keyboard map, command palette, undo | 2.3, 1.8 | todo |
| 2.5 | Quick-add panel and global hotkeys | 2.1, 1.8 | todo |
| 2.6 | Menu bar, reminders, export, launch at login | 2.2, 1.9 | todo |

Gate (spec): you use the app for a week in place of MS To Do on the Mac.

### Phase 3: Cloudflare sync, encrypted ([phase-3-cloudflare-sync.md](phase-3-cloudflare-sync.md))

| ID | Task | Depends on | Status |
| --- | --- | --- | --- |
| 3.1 | Worker and D1 | 1.1 | todo |
| 3.2 | Sync engine and Cloudflare transport | 1.3, 3.1 | todo |
| 3.3 | End-to-end encryption | 3.2 | todo |
| 3.4 | Two-device test client and conflict scenarios | 3.3, 1.6 | todo |
| 3.5 | Mac sync, pairing and deploy | 3.4, 2.6 | todo |

Gate (spec): every scripted scenario converges, and a 7-day soak stays under 5% of the D1 free-tier limits.

### Phase 4: Android app ([phase-4-android.md](phase-4-android.md))

| ID | Task | Depends on | Status |
| --- | --- | --- | --- |
| 4.1 | Android shell, home, lists and settings | 1.5, 1.9 | todo |
| 4.2 | List screen, Today and Planned | 4.1 | todo |
| 4.3 | Task detail, add sheet and search | 4.2, 1.8 | todo |
| 4.4 | Android reminders | 4.2, 1.9 | todo |
| 4.5 | Android sync and pairing | 4.1, 3.5 | todo |
| 4.6 | Capture paths: quick-add widget, tile, shortcut, share | 4.3 | todo |
| 4.7 | List widget | 4.2, 4.6 | todo |

Gate (spec): an edit on either device shows on the other within one sync cycle, and the quick-add widget saves a task in under 3 s from the tap.

### Phase 5: calendar and polish ([phase-5-calendar-polish.md](phase-5-calendar-polish.md))

| ID | Task | Depends on | Status |
| --- | --- | --- | --- |
| 5.1 | Calendar data and Mac calendar | 2.4, 1.6 | todo |
| 5.2 | Android calendar | 5.1, 4.3 | todo |
| 5.3 | Motion and polish: Mac | 5.1 | todo |
| 5.4 | Motion and polish: Android | 5.2 | todo |

### Phase 6: hardening and Syncthing ([phase-6-hardening-syncthing.md](phase-6-hardening-syncthing.md))

| ID | Task | Depends on | Status |
| --- | --- | --- | --- |
| 6.1 | Purging old tombstones | 3.5, 4.5 | todo |
| 6.2 | Syncthing transport (shared) | 3.3 | todo |
| 6.3 | Syncthing on Mac and Android | 6.2, 3.5, 4.5 | todo |

End-to-end encryption has moved from this phase to 3.3.

### Phase 7: CalDAV ([phase-7-caldav.md](phase-7-caldav.md))

| ID | Task | Depends on | Status |
| --- | --- | --- | --- |
| 7.1 | VTODO mapping | 1.6 | todo |
| 7.2 | CalDAV transport | 7.1, 3.2 | todo |
| 7.3 | CalDAV setup on both platforms | 7.2, 6.3 | todo |
