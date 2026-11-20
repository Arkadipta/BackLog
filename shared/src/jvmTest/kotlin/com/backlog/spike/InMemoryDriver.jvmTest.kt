package com.backlog.spike

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual fun inMemoryDriver(): SqlDriver = createSchema(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))
