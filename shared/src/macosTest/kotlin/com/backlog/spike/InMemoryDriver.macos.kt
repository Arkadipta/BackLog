package com.backlog.spike

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver as nativeInMemoryDriver
import com.backlog.db.BackLogDatabase

actual fun inMemoryDriver(): SqlDriver = nativeInMemoryDriver(BackLogDatabase.Schema)
