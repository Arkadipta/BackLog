package com.backlog.db

import com.backlog.id.uuidVersion
import com.backlog.model.ListColor
import com.backlog.model.TaskList
import com.backlog.sync.Hlc
import com.backlog.time.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalDatabaseTest {
    private val inbox = TaskList(
        id = "8494b556-8818-5209-99e5-3f3bce5d2b0f",
        name = "Inbox",
        color = ListColor.SKY,
        icon = null,
        sortKey = "a0",
        isInbox = true,
    )

    @Test
    fun openSeedsTheInboxWithZeroClocksAndNoLog() {
        val db = LocalDatabase.open(testDriver(), TestClock(1_763_700_000_000))
        assertEquals(inbox.id, Seeds.INBOX_ID)
        assertEquals(listOf(inbox), db.queries.inspectQueries.liveLists().executeAsList().map { it.toModel() })

        val clocks = db.queries.inspectQueries.fieldClocks("list", inbox.id).executeAsList()
        assertEquals(Seeds.LIST_FIELDS.sorted(), clocks.map { it.field_ })
        assertTrue(clocks.all { it.hlc == Hlc.ZERO.encode() }, clocks.toString())
        assertEquals(listOf("0"), db.driver.column("SELECT count(*) FROM change_log"))
    }

    @Test
    fun reopeningKeepsOneInboxAndTheSameDeviceId() {
        val driver = testDriver()
        val first = LocalDatabase.open(driver)
        val second = LocalDatabase.open(driver)
        assertEquals(first.deviceId, second.deviceId)
        assertEquals(listOf("1"), driver.column("SELECT count(*) FROM list"))
        assertEquals(listOf("6"), driver.column("SELECT count(*) FROM field_clock"))
    }

    @Test
    fun theDeviceIdIsAUuidV7ThatHlcAccepts() {
        val id = LocalDatabase.open(testDriver(), TestClock(1_763_700_000_000)).deviceId
        assertEquals(7, uuidVersion(id))
        assertTrue(Hlc.isValidDeviceId(id))
    }

    @Test
    fun seedingAgainNeverOverwritesAnEdit() {
        val driver = testDriver()
        LocalDatabase.open(driver)
        val edit = Hlc(1_763_700_000_000, 0, "dev").encode()
        driver.exec("UPDATE list SET name = 'Inbox (Mac)' WHERE id = ?", inbox.id)
        driver.exec("UPDATE field_clock SET hlc = ? WHERE row_id = ? AND field = 'name'", edit, inbox.id)

        val db = LocalDatabase.open(driver)
        assertEquals("Inbox (Mac)", db.queries.inspectQueries.liveLists().executeAsOne().name)
        assertEquals(listOf(edit), driver.column("SELECT hlc FROM field_clock WHERE row_id = ? AND field = 'name'", inbox.id))
    }

    @Test
    fun twoDevicesSeedTheSameInbox() {
        // D5: identical seeds on every device, so the first sync merges them silently.
        val dump = "SELECT l.id, l.name, l.color, l.sort_key, l.is_inbox, c.field, c.hlc FROM list l JOIN field_clock c ON c.row_id = l.id ORDER BY c.field"
        val a = LocalDatabase.open(testDriver(), TestClock(1_000))
        val b = LocalDatabase.open(testDriver(), TestClock(9_000_000))
        assertTrue(a.deviceId != b.deviceId)
        assertEquals(a.driver.rows(dump, 7), b.driver.rows(dump, 7))
    }
}
