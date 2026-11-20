package com.backlog.diagnostics

import app.cash.sqldelight.db.SqlDriver
import com.backlog.db.LocalDatabase
import com.backlog.db.probeFullText
import com.backlog.db.sqliteVersion
import com.backlog.db.toModel
import com.backlog.db.userVersion
import com.backlog.model.TaskList

/**
 * What the apps' placeholder screens show until 2.1 (Mac) and 4.1 (Android)
 * replace them: proof that the real database opens, migrates and seeds on the
 * device. The full-text flags come from the platform's own SQLite (D9).
 */
data class DatabaseStatus(
    /** The database file's path. */
    val location: String,
    val sqliteVersion: String,
    val fts4: Boolean,
    val fts5: Boolean,
    /** `PRAGMA user_version` after opening; 0 when opening failed. */
    val schemaVersion: Long,
    val deviceId: String,
    val lists: List<TaskList>,
    /** Why opening failed, or null. */
    val error: String?,
)

/**
 * Probes [probe] (an empty in-memory connection, so the probe works even when
 * the schema can't be created), then opens the real database with [open].
 */
internal fun readDatabaseStatus(location: String, probe: () -> SqlDriver, open: () -> SqlDriver): DatabaseStatus {
    val probeDriver = probe()
    val (support, sqlite) = try {
        probeDriver.probeFullText() to probeDriver.sqliteVersion()
    } finally {
        probeDriver.close()
    }
    return try {
        val db = LocalDatabase.open(open())
        try {
            DatabaseStatus(
                location = location,
                sqliteVersion = sqlite,
                fts4 = support.fts4,
                fts5 = support.fts5,
                schemaVersion = db.driver.userVersion(),
                deviceId = db.deviceId,
                lists = db.queries.inspectQueries.liveLists().executeAsList().map { it.toModel() },
                error = null,
            )
        } finally {
            db.driver.close()
        }
    } catch (e: Exception) {
        DatabaseStatus(location, sqlite, support.fts4, support.fts5, 0, "", emptyList(), e.toString())
    }
}
