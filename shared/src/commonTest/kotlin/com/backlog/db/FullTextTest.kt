package com.backlog.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The search index over title + notes_md and the triggers that keep it current (D9). */
class FullTextTest {
    private val db = LocalDatabase.open(testDriver())
    private val driver = db.driver

    private fun search(query: String): List<String> =
        db.queries.inspectQueries.searchLive(query).executeAsList().map { it.id }

    private fun assertIndexMatchesTasks() {
        // Throws (SQLITE_CORRUPT_VTAB) if the index disagrees with the task table.
        driver.exec("INSERT INTO task_fts(task_fts) VALUES ('integrity-check')")
    }

    @Test
    fun findsTitlesAndNotes() {
        driver.insertTask("t1", "Buy groceries")
        driver.insertTask("t2", "Call the bank", notes = "Ask about the **mortgage** rate")
        assertEquals(listOf("t1"), search("groceries"))
        assertEquals(listOf("t2"), search("mortgage"))
        assertEquals(listOf("t2"), search("BANK"))
        assertIndexMatchesTasks()
    }

    @Test
    fun matchesPrefixesAndIgnoresDiacritics() {
        driver.insertTask("t1", "Café with Zoë")
        driver.insertTask("t2", "Groceries")
        assertEquals(listOf("t2"), search("gro*"))
        assertEquals(listOf("t1"), search("cafe"))
        assertEquals(listOf("t1"), search("zoe"))
    }

    @Test
    fun followsEditsToTitleAndNotes() {
        driver.insertTask("t1", "Old title", notes = "old notes")
        driver.exec("UPDATE task SET title = 'New title', notes_md = NULL WHERE id = 't1'")
        assertEquals(emptyList(), search("old"))
        assertEquals(listOf("t1"), search("new"))
        // Unrelated columns don't touch the index.
        driver.exec("UPDATE task SET priority = 3 WHERE id = 't1'")
        assertEquals(listOf("t1"), search("new"))
        assertIndexMatchesTasks()
    }

    @Test
    fun tombstonesAreHiddenAndHardDeletesAreRemoved() {
        driver.insertTask("t1", "Water plants")
        driver.insertTask("t2", "Water the garden")
        driver.exec("UPDATE task SET deleted_at = 1 WHERE id = 't1'")
        assertEquals(listOf("t2"), search("water"))
        driver.exec("DELETE FROM task WHERE id = 't2'")
        assertEquals(emptyList(), search("water"))
        assertIndexMatchesTasks()
    }

    @Test
    fun snippetMarksTheHit() {
        driver.insertTask("t1", "Renew passport", notes = "Book an appointment at the passport office before June")
        val hit = db.queries.inspectQueries.searchLive("appointment").executeAsOne()
        assertTrue("[appointment]" in hit.snippet.orEmpty(), hit.snippet)
    }

    @Test
    fun survivesVacuum() {
        // An implicit rowid may be renumbered by VACUUM, which would point the
        // index at the wrong tasks. task.local_id is a declared INTEGER PRIMARY KEY.
        for (i in 1..20) driver.insertTask("t$i", "task number $i")
        for (i in 1..19 step 2) driver.exec("DELETE FROM task WHERE id = ?", "t$i")
        driver.exec("VACUUM")
        assertEquals(listOf("t20"), search("20"))
        assertEquals(listOf("t2"), search("2"))
        assertIndexMatchesTasks()
    }
}
