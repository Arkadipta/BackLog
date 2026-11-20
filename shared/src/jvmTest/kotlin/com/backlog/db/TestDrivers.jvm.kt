package com.backlog.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual fun testDriver(): SqlDriver = createJvmDriver(path = null)

actual fun emptyTestDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

actual val testSqliteName: String = "sqlite-jdbc (bundled SQLite)"
