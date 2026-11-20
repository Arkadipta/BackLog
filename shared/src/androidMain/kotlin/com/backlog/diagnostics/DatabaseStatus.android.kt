package com.backlog.diagnostics

import android.content.Context
import com.backlog.db.DATABASE_FILE_NAME
import com.backlog.db.createAndroidDriver
import com.backlog.db.createAndroidProbeDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens `backlog.db` (creating, migrating and seeding it) and reports on it. Never throws. */
suspend fun loadDatabaseStatus(context: Context): DatabaseStatus = withContext(Dispatchers.IO) {
    val app = context.applicationContext
    readDatabaseStatus(
        location = app.getDatabasePath(DATABASE_FILE_NAME).path,
        probe = { createAndroidProbeDriver(app) },
        open = { createAndroidDriver(app) },
    )
}
