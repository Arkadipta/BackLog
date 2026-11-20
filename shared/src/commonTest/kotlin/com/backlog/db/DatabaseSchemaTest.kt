package com.backlog.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseSchemaTest {
    private val schema = BackLogDatabase.Schema

    @Test
    fun driverCreatesTheCurrentVersion() {
        // Derived from the migrations: version = highest N.sqm + 1.
        assertEquals(2, schema.version)
        assertEquals(schema.version, testDriver().userVersion())
    }

    @Test
    fun migratingAnEmptyDatabaseGivesTheSameSchemaAsCreating() {
        val created = emptyTestDriver().also { schema.create(it) }
        val migrated = emptyTestDriver().also { schema.migrate(it, 1, schema.version) }
        val master = "SELECT type, name, tbl_name, sql FROM sqlite_master ORDER BY type, name"
        val expected = created.rows(master, 4)
        assertTrue(expected.size > 10, "schema looks empty: $expected")
        assertEquals(expected, migrated.rows(master, 4))
    }

    @Test
    fun hasTheV1TablesIndexesAndTriggers() {
        val driver = testDriver()
        val names = driver.column("SELECT name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND (name NOT LIKE 'task_fts_%' OR type = 'trigger')").toSet()
        val expected = setOf(
            "list", "task", "task_fts", "field_clock", "change_log", "sync_state", "local_meta",
            "list_order", "task_by_list", "task_by_parent", "task_by_due", "task_by_reminder",
            "list_tombstones", "task_tombstones", "field_clock_by_hlc", "change_log_by_field",
            "task_fts_after_insert", "task_fts_before_delete", "task_fts_before_update", "task_fts_after_update",
        )
        assertEquals(expected, names)
        assertTrue("spike_note" !in names)
    }

    @Test
    fun listViewQueryUsesItsPartialIndex() {
        val plan = testDriver().rows(
            "EXPLAIN QUERY PLAN SELECT id FROM task WHERE list_id = ? AND parent_id IS NULL AND deleted_at IS NULL ORDER BY sort_key, id",
            4,
            "x",
        ).joinToString { it[3].orEmpty() }
        assertTrue("task_by_list" in plan, plan)
        assertTrue("TEMP B-TREE" !in plan, "the index should give the order: $plan")
    }

    @Test
    fun aRemoteRowCanBeCreatedFromItsIdAlone() {
        // 1.3's remote apply creates a missing row first, then sets fields.
        val driver = testDriver()
        driver.exec("INSERT INTO task(id) VALUES ('t1')")
        driver.exec("INSERT INTO list(id) VALUES ('l1')")
        assertEquals(listOf(listOf("", "", "0", "due", "")), driver.rows("SELECT list_id, title, priority, repeat_from, sort_key FROM task", 5))
        assertEquals(listOf(listOf("", "sky", "0")), driver.rows("SELECT name, color, is_inbox FROM list", 3))
    }
}
