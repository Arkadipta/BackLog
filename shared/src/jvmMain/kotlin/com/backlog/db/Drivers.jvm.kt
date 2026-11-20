package com.backlog.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

/**
 * A database at [path], or a fresh in-memory one when [path] is null, through
 * sqlite-jdbc's bundled SQLite. Created or migrated to [BackLogDatabase.Schema]
 * on open. The jvm target exists for tests and tooling (3.4's two-device client).
 */
internal fun createJvmDriver(path: String? = null): JdbcSqliteDriver =
    JdbcSqliteDriver(
        url = if (path == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:$path",
        properties = Properties(),
        schema = BackLogDatabase.Schema,
    )
