# Phase 5: calendar and polish

This phase adds the calendar views, the spec's animated moments, and a dark-mode pass on both platforms. It's the spec roadmap's phase 5.

---

### 5.1 Calendar data and Mac calendar
**Depends on:** 2.4, 1.6 · **Spec:** Features › Calendar view; Mac app › Main window keys (calendar row) · **Decisions:** D26

**Build**
- In shared code, `calendarRange(from, to, showCompleted)` returns each day's all-day and timed tasks, plus ghost occurrences computed with `occurrences()` from 1.6. Add a reschedule call that takes a date and an optional time.
- On the Mac:
  - A month grid and a week view. All-day tasks sit in a band at the top; timed tasks sit at their time in the week view.
  - Drag a task to another day to reschedule it, with the day under the drag highlighted as a drop target.
  - Ghosts are drawn faint and are read-only (D26).
  - A toggle shows or hides completed tasks.
  - Keys: arrows, T, and Option+Left/Right; Cmd+3 opens the calendar.

**Done when**
- A drag-reschedule is saved and syncs.
- Ghosts match the repeat rule for the next three months.
- The calendar can be used from the keyboard alone.

**Notes:** _(filled in by the session)_

---

### 5.2 Android calendar
**Depends on:** 5.1, 4.3 · **Spec:** Features › Calendar view; Android app › Screens (Calendar) · **Decisions:** D26

**Build**
- A month grid with dots on days that have tasks, and an agenda list under it for the selected day.
- Long-press and drag a task onto another day to reschedule it.
- Ghost occurrences, and a toggle for completed tasks.

**Done when** the same data shows the same days, ghosts and reschedules as on the Mac.

**Notes:** _(filled in by the session)_

---

### 5.3 Motion and polish: Mac
**Depends on:** 5.1 · **Spec:** Visual design and animation (whole section)

**Build**
- Every row of the Animated moments table:
  - Complete: the checkbox fills, the tick draws itself and a strike-through sweeps across. After 600 ms the row glides into the Completed group with `matchedGeometryEffect`.
  - The "all clear" empty state, shown at most once per day.
  - Sub-tasks expand on a height spring while the progress count counts up.
  - A dragged row lifts with a soft shadow.
  - Month pages slide.
  - The sync dot pulses.
- With Reduce Motion on, every animation becomes a crossfade.
- A dark-mode pass, an audit that every count and date uses tabular figures, and an empty state for every list and view.

**Done when** each animated moment has been checked in light mode, in dark mode and with Reduce Motion on, and no count or date jitters as it changes.

**Notes:** _(filled in by the session)_

---

### 5.4 Motion and polish: Android
**Depends on:** 5.2 · **Spec:** Visual design and animation (whole section)

**Build**
- The same table in Compose, using `animateItem` in lazy lists, plus haptics.
- When the system's "Remove animations" setting is on, every animation becomes a crossfade.
- A dark-mode pass, a check on your phone that it looks right in One UI, and empty states.

**Done when** the 5.3 checklist passes on your phone.

**Notes:** _(filled in by the session)_
