package com.backlog.spike

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.backlog.db.BackLogDatabase

/** In-memory when [path] is null. The jvm target exists for tests and tooling. */
fun createSpikeRepository(path: String? = null): SpikeRepository {
    val url = if (path == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:$path"
    return SpikeRepository.create(JdbcSqliteDriver(url, schema = BackLogDatabase.Schema))
}
