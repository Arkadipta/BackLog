package com.backlog.db

import com.backlog.model.ListColor
import com.backlog.model.Priority
import com.backlog.model.Reminder
import com.backlog.model.RepeatFrom
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RowsTest {
    private val db = LocalDatabase.open(testDriver())

    private fun task(id: String) = db.queries.inspectQueries.taskById(id).executeAsOne().toModel()

    @Test
    fun mapsEveryTaskColumn() {
        db.driver.exec(
            """
            INSERT INTO task(id, list_id, parent_id, title, notes_md, priority, due_date, due_time, time_zone,
              reminder_at, reminder_offset_min, rrule, repeat_from, series_id, completed_at, sort_key)
            VALUES ('t1', 'l1', NULL, 'Pay rent', 'notes', 2, '2026-12-01', 570, 'Europe/London',
              1764580500000, 30, 'FREQ=MONTHLY;BYMONTHDAY=1', 'completion', 't0', NULL, 'a1')
            """.trimIndent(),
        )
        val t = task("t1")
        assertEquals("l1", t.listId)
        assertEquals("Pay rent", t.title)
        assertEquals(Priority.MEDIUM, t.priority)
        assertEquals(LocalDate(2026, 12, 1), t.dueDate)
        assertEquals(LocalTime(9, 30), t.dueTime)
        assertEquals(Reminder.BeforeDue(30, 1_764_580_500_000, "Europe/London"), t.reminder)
        assertEquals(RepeatFrom.COMPLETION, t.repeatFrom)
        assertEquals("t0", t.seriesId)
        assertFalse(t.isCompleted)
        assertFalse(t.isSubtask)
    }

    @Test
    fun reminderWithoutOffsetIsAbsolute() {
        db.driver.exec("INSERT INTO task(id, reminder_at, time_zone) VALUES ('t1', 5, 'Asia/Kolkata')")
        assertEquals(Reminder.Absolute(5, "Asia/Kolkata"), task("t1").reminder)
    }

    @Test
    fun defaultsMapToEmptyValues() {
        db.driver.exec("INSERT INTO task(id, parent_id, completed_at) VALUES ('t1', 'p', 7)")
        val t = task("t1")
        assertEquals(Priority.NONE, t.priority)
        assertEquals(RepeatFrom.DUE, t.repeatFrom)
        assertNull(t.dueDate)
        assertNull(t.dueTime)
        assertNull(t.reminder)
        assertTrue(t.isCompleted)
        assertTrue(t.isSubtask)
    }

    @Test
    fun unreadableValuesFromOtherDevicesDecodeLeniently() {
        db.driver.exec("INSERT INTO task(id, priority, due_date, due_time, repeat_from) VALUES ('t1', 9, '2026-02-30', 1440, 'weekly')")
        db.driver.exec("INSERT INTO task(id, priority, due_time) VALUES ('t2', -1, 0)")
        val t1 = task("t1")
        assertEquals(Priority.HIGH, t1.priority)
        assertNull(t1.dueDate)
        assertNull(t1.dueTime)
        assertEquals(RepeatFrom.DUE, t1.repeatFrom)
        val t2 = task("t2")
        assertEquals(Priority.NONE, t2.priority)
        assertEquals(LocalTime(0, 0), t2.dueTime)
    }

    @Test
    fun unknownListColourReadsAsSky() {
        db.driver.exec("INSERT INTO list(id, name, color, sort_key) VALUES ('l1', 'Home', 'chartreuse', 'a1')")
        db.driver.exec("INSERT INTO list(id, name, color, sort_key) VALUES ('l2', 'Work', 'coral', 'a2')")
        val lists = db.queries.inspectQueries.liveLists().executeAsList().map { it.toModel() }
        assertEquals(listOf(ListColor.SKY, ListColor.SKY, ListColor.CORAL), lists.map { it.color })
        assertEquals(listOf(true, false, false), lists.map { it.isInbox })
    }
}
