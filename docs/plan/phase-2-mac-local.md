# Phase 2: Mac app, local only

This phase builds the Mac app on the local database, with no network yet. It's the spec roadmap's phase 2. Each task builds and tests from the terminal (`xcodegen generate`, then `xcodebuild`, per D3). For time-dependent behaviour such as midnight rollover, use the shared `Clock` rather than waiting for real time to pass. Use plain transitions throughout; the spec's table of animated moments is task 5.3.

Gate (spec): you use the app for a week in place of MS To Do on the Mac. Write down anything that gets in the way in `docs/plan/dogfood.md`. Anything that blocks daily use becomes a 2.x follow-up task before 3.5.

---

### 2.1 Mac app shell and lists
**Depends on:** 1.5 · **Spec:** Mac app › Three surfaces (1); Features › Lists and views; Visual design › Shared tokens, Look per platform · **Decisions:** D1, D3, D5

**Build**
- Replace the spike with:
  - the real app entry point;
  - database bootstrap, including the Inbox seed;
  - one `@Observable` model per feature, each wrapping the shared repositories. Shared code has no ViewModels.
- A `NavigationSplitView`:
  - The sidebar shows Today, Planned, a Calendar placeholder, and then the lists, each with icon, colour and open count, on the translucent sidebar material.
  - The middle column and the inspector are placeholders for now.
- List management:
  - create, rename, colour (the 8 named tokens, with light and dark values in the asset catalog) and icon (a curated SF Symbols picker);
  - drag to reorder;
  - delete, after a confirmation. The Inbox is protected.
- A Settings window scaffold with date order and default list, stored in UserDefaults. The date order is passed to the parser `ctx`.

**Done when**
- Lists and their order survive a relaunch.
- `xcodebuild test` runs a small Swift Testing suite for the models.
- The app builds from the terminal.

**Notes:** _(filled in by the session)_

---

### 2.2 Task list, sub-tasks, Today and Planned
**Depends on:** 2.1 · **Spec:** Features › Lists and views, Tasks (sub-tasks); Data model › Integrity rules; Visual design › Shared tokens (44 pt rows, tabular figures) · **Decisions:** D8, D10

**Build**
- Task rows: a checkbox in the list colour, title, due chip (in tabular figures), a priority mark and `2/5` progress. Rows are 44 pt high and highlight on hover.
- An inline add button (the N key comes in 2.4), complete and uncomplete, delete, a collapsible Completed group, and drag to reorder.
- Sub-tasks:
  - shown under a disclosure triangle on the row;
  - can be added, ticked, renamed and reordered;
  - completing a parent that still has open sub-tasks offers to complete them too.
- The Today and Planned views, using the D10 groups and rolling over at midnight.

**Done when**
- Everything above works against the real database.
- A midnight rollover, driven by the shared clock, moves rows between groups without a relaunch.

**Notes:** _(filled in by the session)_

---

### 2.3 Inspector, notes and search
**Depends on:** 2.2, 1.6 · **Spec:** Mac app › Three surfaces (inspector); Features › Tasks (notes, search), Reminders, Recurring tasks (UI presets) · **Decisions:** D7, D9, D23

**Build**
- The inspector (Cmd+I toggles it) holds:
  - title, list and priority;
  - due date, with an optional time;
  - reminder, either absolute or "before due";
  - repeat (presets plus the custom builder, including "last day"), with the repeat-from toggle;
  - sub-tasks and notes.
- Notes:
  - A plain-text editor with a live markdown preview on the right. Pick a maintained SwiftUI markdown renderer and record the choice.
  - Links open in the browser.
  - `- [ ]` renders but isn't clickable.
  - Notes are hidden for sub-tasks.
- Search (Cmd+F): full-text results across all lists with match highlighting; Enter opens the selected result.

**Done when**
- Every field round-trips through the database.
- A repeat set in the inspector reads the same as the equivalent quick-add phrase does.

**Notes:** _(filled in by the session)_

---

### 2.4 Keyboard map, command palette, undo
**Depends on:** 2.3, 1.8 · **Spec:** Mac app › Main window keys · **Decisions:** D15

**Build**
- Every row of the main-window key table except the calendar row, which is in 5.1.
- The D/R/E/P/M popovers accept quick-add fragments through the shared parser, such as `fri 5pm`, `every mon`, `^30m`, `!high` and `@work`.
- Tab and Shift+Tab call indent and outdent and show the repository's refusal reason when there is one.
- Cmd+K: a command palette covering actions, lists, and tasks by fuzzy name.
- `UndoManager`, fed by the inverse change sets, with named menu items such as "Undo Complete".

**Done when**
- A scripted walkthrough of the whole key table works with no mouse.
- Undo and redo of complete, delete, move, indent and list delete each restore the previous state exactly.

**Notes:** _(filled in by the session)_

---

### 2.5 Quick-add panel and global hotkeys
**Depends on:** 2.1, 1.8 · **Spec:** Mac app › Three surfaces (3), Global hotkeys, Quick-add panel keys; Quick-add syntax › Rules (chips, unknown list); Visual design › Animated moments (panel open) · **Decisions:** D3

**Build**
- KeyboardShortcuts with two re-recordable shortcuts, both shown in Settings: Option+Shift+Space opens the panel and Option+Shift+T shows the main window.
- A floating `NSPanel` hosting SwiftUI. It:
  - doesn't activate the app, but can still become key so it takes typing;
  - joins all Spaces and shows over full-screen apps;
  - opens centred on the screen that holds the pointer.
- A field with live chips built from the parser's tokens:
  - Backspace on a chip adds that range to the literal ranges.
  - Shift+Enter starts a new line, and lines beginning ` - ` become sub-tasks.
- Keys:
  - Enter saves and closes.
  - Cmd+Enter saves and shows the task in the main window.
  - Option+Enter saves and keeps the panel open.
  - Esc closes and keeps the text for 60 s.
- An unknown `@list` gets an inline "Create list" offer.
- The open animation (scale 0.96 → 1 with a fade, 120 ms) and the chip pop-in springs. With Reduce Motion on, both become a plain fade.

**Done when**
- From a full-screen app, the hotkey followed by `Call bank tomorrow 5pm !high @finance` and Enter saves the task in under 2 s.
- The main window doesn't come forward and the Space doesn't change.
- Repeat both checks on a second display, if you have one.

**Notes:** _(filled in by the session)_

---

### 2.6 Menu bar, reminders, export, launch at login
**Depends on:** 2.2, 1.9 · **Spec:** Mac app › Three surfaces (2), Mac plumbing; Features › Reminders (Mac), Data ownership · **Decisions:** D6, D20, D24, D25

**Build**
- A `MenuBarExtra` in window style:
  - The icon shows today's open count.
  - The window lists Today with checkboxes, with a quick-add field at the top that reuses the 2.5 field and chips.
- Notifications:
  - Ask for permission.
  - Schedule from the planner diff on launch, after every change, on wake, on time-zone change and at the daily rollover.
  - Add a category with Complete, Snooze 10 min and Snooze 1 h actions. Clicking a notification opens its task, and the actions work with the main window closed.
- Launch at login through `SMAppService`, with a toggle in Settings. The app keeps running in the menu bar when its last window closes.
- Settings › Data: JSON export and import (D20).

**Done when**
- A reminder set 2 minutes out fires with the window closed, and each action works.
- Changing the system time zone re-plans reminders per D6.
- Launch at login survives a reboot.
- An export imports back cleanly.

**Notes:** _(filled in by the session)_
