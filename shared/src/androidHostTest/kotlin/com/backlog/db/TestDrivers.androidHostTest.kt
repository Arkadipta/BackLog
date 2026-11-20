package com.backlog.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

// The host test runs on a plain JVM without the Android framework, so it uses
// the JDBC driver. The platform's own SQLite is checked on an emulator (D9).
actual fun testDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), BackLogDatabase.Schema)

actual fun emptyTestDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

actual val testSqliteName: String = "sqlite-jdbc on the Android host test"
