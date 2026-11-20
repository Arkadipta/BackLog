# Phase 7: CalDAV

This is the spec roadmap's phase 7. CalDAV acts as a bridge rather than a pure transport, because the server owns whole objects. Values aren't end-to-end encrypted on this path (D29).

---

### 7.1 VTODO mapping
**Depends on:** 1.6 · can run any time after 1.6 · **Spec:** Sync › Phase 3 (mapping table) · **Decisions:** D6, D7, D29

**Build**
- An iCalendar reader and writer for the subset BackLog needs:
  - line folding and escaping;
  - `DATE` vs `DATE-TIME` values, and floating times vs `TZID`;
  - `VALARM` with relative and absolute `TRIGGER`s.
- Map between tasks and VTODOs following the spec's table:
  - Priority: high is 1, medium 5 and low 9. When reading, map the full 1–9 range back to BackLog's levels.
  - `RELATED-TO;RELTYPE=PARENT` holds the parent task.
  - `X-APPLE-SORT-ORDER` maps to and from `sort_key`.
  - `RRULE`s inside the 1.6 subset pass straight through.
- Decide how to show a rule from another client that's outside the subset, and record the choice. Suggestion: keep it raw and show it read-only as "Custom (set elsewhere)".
- Properties BackLog doesn't own are preserved on round-trip.

**Done when** mapping tests and round-trip fixtures pass without losing any property. Make the fixtures resemble what Apple Reminders, Thunderbird and Tasks.org write; 7.2 can then swap in real captures.

**Notes:** _(filled in by the session)_

---

### 7.2 CalDAV transport
**Depends on:** 7.1, 3.2 · **Spec:** Sync › Phase 3 · **Decisions:** D29

**Build**
- Discovery: the well-known URL, then the current user principal, then the calendar home set, then the collections that support VTODO.
- Map lists to collections. Use `MKCALENDAR` for a new list where the server allows it.
- Pull with `sync-collection` (RFC 6578), falling back to comparing ETags. Diff each object against its stored copy to produce field changes stamped with fresh HLCs.
- Push with PUT, using `If-Match`, or `If-None-Match: *` for new objects. On HTTP 412, fetch the server's copy, merge field by field and retry, with a limit on retries. Deletes are pushed the same way.
- Credentials go through the `SecretStore`.

**Done when** integration tests pass against a local Radicale that the test harness starts (for example after `pip install radicale`). They cover create, edit and delete in both directions, plus a 412 race.

**Notes:** _(filled in by the session)_

---

### 7.3 CalDAV setup on both platforms
**Depends on:** 7.2, 6.3 · **Spec:** Sync › Phase 3 (gain, supported servers) · **Decisions:** D29

**Build**
- An account screen on both platforms: server URL, username and app password.
- A review of which list maps to which collection.
- A CalDAV entry in the transport switch from 6.3.
- A clear warning that the CalDAV server sees your tasks in plaintext.

**Done when** a task created in Apple Reminders, through a CalDAV account on your server, shows up in BackLog, and a task created in BackLog shows up in Apple Reminders. Checking against Fastmail or Nextcloud is optional.

**Notes:** _(filled in by the session)_
