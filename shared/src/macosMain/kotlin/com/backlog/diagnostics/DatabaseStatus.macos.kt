package com.backlog.diagnostics

import com.backlog.db.DATABASE_FILE_NAME
import com.backlog.db.applicationSupportDirectory
import com.backlog.db.createMacDriver
import com.backlog.db.createMacProbeDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Opens `backlog.db` (creating, migrating and seeding it) and reports on it.
 * Failures are reported in [DatabaseStatus.error], not thrown.
 */
@Throws(Exception::class)
suspend fun loadDatabaseStatus(): DatabaseStatus = withContext(Dispatchers.IO) {
    val dir = applicationSupportDirectory()
    readDatabaseStatus(
        location = "$dir/$DATABASE_FILE_NAME",
        probe = { createMacProbeDriver() },
        open = { createMacDriver(dir) },
    )
}
