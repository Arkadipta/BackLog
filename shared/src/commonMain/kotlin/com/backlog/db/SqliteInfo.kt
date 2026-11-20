package com.backlog.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/** Which full-text modules this SQLite build has (D9). */
internal data class FullTextSupport(val fts4: Boolean, val fts5: Boolean)

/**
 * Tries to create a temporary table with each module. Compile options can't
 * answer this reliably: FTS4 ships under `ENABLE_FTS3`, and some builds load
 * modules without advertising them. Leaves nothing behind.
 */
internal fun SqlDriver.probeFullText(): FullTextSupport = FullTextSupport(
    fts4 = canCreate("CREATE VIRTUAL TABLE temp.backlog_probe_fts4 USING fts4(x)", "temp.backlog_probe_fts4"),
    fts5 = canCreate("CREATE VIRTUAL TABLE temp.backlog_probe_fts5 USING fts5(x)", "temp.backlog_probe_fts5"),
)

private fun SqlDriver.canCreate(create: String, table: String): Boolean =
    try {
        execute(null, create, 0)
        execute(null, "DROP TABLE $table", 0)
        true
    } catch (e: Exception) {
        false
    }

internal fun SqlDriver.sqliteVersion(): String = queryString("SELECT sqlite_version()") ?: "unknown"

/** `PRAGMA user_version`, which the drivers set to the schema version. */
internal fun SqlDriver.userVersion(): Long = queryLong("PRAGMA user_version") ?: 0

internal fun SqlDriver.queryString(sql: String): String? =
    executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0).value

internal fun SqlDriver.queryLong(sql: String): Long? =
    executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value
