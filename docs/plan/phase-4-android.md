# Phase 4: Android app

This phase builds the Android app, which syncs from the start. It's the spec roadmap's phase 4. Test on your phone (One UI) as well as on an emulator. Sideloaded debug builds are fine; per the spec, a sideloaded build is what allows `USE_EXACT_ALARM`.

Gate (spec): an edit on either device shows on the other within one sync cycle, and the quick-add widget saves a task in under 3 s from the tap.

---

### 4.1 Android shell, home, lists and settings
**Depends on:** 1.5, 1.9 · **Spec:** Android app › Screens (Home, Settings, edge-to-edge); Visual design (Material 3, dynamic colour, tokens) · **Decisions:** D1, D5, D20

**Build**
- App structure:
  - `ViewModel`s that wrap the shared repositories.
  - Dependency injection: Hilt, as in LifeLog, or manual if Hilt fights the KMP module. Record the choice.
  - Navigation, edge-to-edge layout and the predictive back gesture.
  - Material 3 dynamic colour, falling back to the list palette, in light and dark.
- Home: Today and Planned cards with counts, then the lists with icon, colour and count.
- List management: create, rename, colour, and a Material icon picker; drag to reorder.
- Settings: date order, default list, theme, and JSON export/import through the system file picker (D20). The sync and battery entries come in later tasks.

**Done when**
- Lists survive an app restart.
- An export made on the phone imports on the Mac, and the reverse.
- The build runs on your phone.

**Notes:** _(filled in by the session)_

---

### 4.2 List screen, Today and Planned
**Depends on:** 4.1 · **Spec:** Android app › Screens (List); Features › Lists and views, Tasks (undo) · **Decisions:** D10, D15

**Build**
- Task rows: a checkbox, title, due chip (in tabular figures) and `2/5` progress, 56 dp high.
- Swiping:
  - Swipe right to complete, with a haptic tick.
  - Swipe left to reschedule: Tomorrow, Next week, or Pick.
- Long-press to drag-reorder, and a collapsible Completed group.
- An "Undo" snackbar after complete, delete and move (D15).
- The Today and Planned screens, using the D10 groups and rolling over at midnight.

**Done when**
- Every gesture works on your phone.
- Undo restores the previous state exactly.
- Rollover works, tested by driving the shared clock.

**Notes:** _(filled in by the session)_

---

### 4.3 Task detail, add sheet and search
**Depends on:** 4.2, 1.8 · **Spec:** Android app › Screens (Add, Task detail); Features › Tasks, Reminders, Recurring tasks; Quick-add syntax › Rules · **Decisions:** D7, D23

**Build**
- Quick-add as a reusable composable, because 4.6 needs it too:
  - The FAB opens a bottom sheet with the quick-add field.
  - Chips appear under the field as tokens are recognised, and Backspace on a chip turns it back into text.
  - An unknown `@list` gets a "Create list" offer, as on the Mac.
- Task detail:
  - every field;
  - a sub-task checklist (add, tick, drag);
  - notes with an Edit / Preview toggle, using a Compose markdown renderer (record the choice);
  - repeat presets plus the custom builder;
  - a reminder, either absolute or an offset;
  - delete.
- A search screen over the full-text index.

**Done when**
- Every field round-trips through the database.
- The spec's five quick-add examples create the same tasks as they do on the Mac.

**Notes:** _(filled in by the session)_

---

### 4.4 Android reminders
**Depends on:** 4.2, 1.9 · **Spec:** Features › Reminders (Android); Android app › Notifications and background work · **Decisions:** D6, D24

**Build**
- Exact alarms through `AlarmManager`, scheduled from the planner diff.
- Permissions:
  - `USE_EXACT_ALARM`, for API 33 and later.
  - `SCHEDULE_EXACT_ALARM` with `maxSdkVersion="32"`, for API 31–32.
  - The `POST_NOTIFICATIONS` runtime prompt.
- Notification channels: Reminders (high importance) and Sync problems (low).
- The Complete, Snooze 10 min and Snooze 1 h actions, handled in a receiver that writes through the shared repository without opening the app.
- Re-plan on boot, on a time or time-zone change, after an app update, after every local change and after every sync.
- A Settings entry that opens the system battery screen, so you can put the app in One UI's "Never sleeping apps".

**Done when**
- A reminder fires with the app swiped away and the screen off.
- Reminders survive a reboot.
- A time-zone change keeps the 09:00 wall time (D6).

**Notes:** _(filled in by the session)_

---

### 4.5 Android sync and pairing
**Depends on:** 4.1, 3.5 · **Spec:** Sync › Phase 1 (pairing, when to sync); Android app › Screens (Settings), Notifications and background work · **Decisions:** D18, D19

**Build**
- Settings › Sync:
  - Scan the Mac's QR code with Google's code scanner, which needs no camera permission.
  - Store the master secret encrypted with an Android Keystore key; this is the Android `SecretStore`.
  - Test connection, and unpair.
- Sync triggers:
  - on local change while the app is in the foreground (debounced);
  - on app open;
  - a WorkManager job every 15 minutes with a network constraint.
- A sync dot in the Home top bar. Errors that persist post a notification to the Sync problems channel.
- After a sync that changed tasks, re-plan reminders and refresh widgets. Widgets connect to this hook in 4.6 and 4.7.

**Done when** phase 4's first gate check passes: an edit on either device shows on the other within one sync cycle. The worst case is 5 minutes on the Mac and 15 on the phone; with both apps open it should take seconds.

**Notes:** _(filled in by the session)_

---

### 4.6 Capture paths: quick-add widget, tile, shortcut, share
**Depends on:** 4.3 · **Spec:** Android app › Widgets (Quick add), Other fast paths; Quick-add syntax › Rules (unknown list in the widget)

**Build**
- A light, see-through `QuickAddActivity`:
  - It opens with the field focused and the keyboard already up, and shows chips as you type.
  - Enter saves and closes the activity.
  - A mic button uses the system's speech input.
  - An unknown `@list` files the task in the Inbox and flags it.
- Glance quick-add widgets, as a 1x1 icon and a 4x1 bar. Both open the activity.
- A "New task" app shortcut on long-press of the app icon, a Quick Settings tile, and a share target: sharing text or a link creates a task with the link in its notes.
- Keep the activity's cold start lean: no heavy dependency graph at launch, and open the database lazily.

**Done when**
- Phase 4's second gate check passes: from tapping the widget, a typed task is saved in under 3 s. Measure cold and warm starts and record both under Notes.
- Sharing a page from Chrome creates a task with the link in its notes.

**Notes:** _(filled in by the session)_

---

### 4.7 List widget
**Depends on:** 4.2, 4.6 · **Spec:** Android app › Widgets (List)

**Build**
- A resizable Glance widget, from 2x2 to 4x5, with a configuration screen to choose a list or Today.
- Checkboxes can be ticked in place, writing through the repository.
- A `+` button opens quick-add with that list preset.
- The widget refreshes whenever the database changes, including changes that arrive by sync.

**Done when**
- Ticking in the widget updates the app, and the Mac after the next sync.
- Changes made in the app, or arriving by sync, show in the widget within seconds.

**Notes:** _(filled in by the session)_
