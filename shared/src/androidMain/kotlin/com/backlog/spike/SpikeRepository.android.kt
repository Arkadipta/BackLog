package com.backlog.spike

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.backlog.db.BackLogDatabase

/** Opens (or creates) the spike database in the app's private database directory. */
fun createSpikeRepository(context: Context): SpikeRepository =
    SpikeRepository.create(AndroidSqliteDriver(BackLogDatabase.Schema, context, "backlog.db"))
