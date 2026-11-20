package com.backlog.db

import app.cash.sqldelight.db.SqlDriver

/** A fresh in-memory database, created at [BackLogDatabase.Schema] the way the platform driver does it. */
expect fun testDriver(): SqlDriver

/** A fresh in-memory connection with no tables. */
expect fun emptyTestDriver(): SqlDriver

/** Which SQLite the tests run against, for the test report. */
expect val testSqliteName: String
