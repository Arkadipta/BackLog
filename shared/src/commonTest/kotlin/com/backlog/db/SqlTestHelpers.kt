package com.backlog.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/** Raw SQL for tests: rows that no query file writes yet (1.3 adds the write path). */
internal fun SqlDriver.exec(sql: String, vararg args: Any?) {
    execute(null, sql, args.size) { bindAll(args) }
}

/** Every row of [sql], each column read as a string (NULL as null). */
internal fun SqlDriver.rows(sql: String, columns: Int, vararg args: Any?): List<List<String?>> =
    executeQuery(
        null,
        sql,
        { c ->
            val out = mutableListOf<List<String?>>()
            while (c.next().value) out += (0 until columns).map { c.getString(it) }
            QueryResult.Value(out)
        },
        args.size,
    ) { bindAll(args) }.value

internal fun SqlDriver.column(sql: String, vararg args: Any?): List<String?> = rows(sql, 1, *args).map { it[0] }

private fun SqlPreparedStatement.bindAll(args: Array<out Any?>) {
    args.forEachIndexed { i, a ->
        when (a) {
            null -> bindString(i, null)
            is String -> bindString(i, a)
            is Long -> bindLong(i, a)
            is Int -> bindLong(i, a.toLong())
            else -> error("unsupported bind type: $a")
        }
    }
}

internal fun SqlDriver.insertTask(id: String, title: String, notes: String? = null) =
    exec("INSERT INTO task(id, list_id, title, notes_md, sort_key) VALUES (?, ?, ?, ?, 'a0')", id, Seeds.INBOX_ID, title, notes)
