package com.backlog.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver

actual fun testDriver(): SqlDriver = inMemoryDriver(BackLogDatabase.Schema)

actual fun emptyTestDriver(): SqlDriver = inMemoryDriver(EmptySchema)

actual val testSqliteName: String = "macOS system SQLite"
