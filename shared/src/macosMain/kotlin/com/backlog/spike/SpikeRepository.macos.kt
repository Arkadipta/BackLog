package com.backlog.spike

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.backlog.db.BackLogDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Opens (or creates) the spike database under Application Support. In the
 * sandboxed app that resolves inside the app container,
 * `~/Library/Containers/com.backlog.app/Data/Library/Application Support/BackLog/`.
 */
fun createSpikeRepository(): SpikeRepository {
    val dir = applicationSupportDirectory()
    return SpikeRepository.create(
        NativeSqliteDriver(
            schema = BackLogDatabase.Schema,
            name = "backlog.db",
            onConfiguration = { config ->
                config.copy(extendedConfig = DatabaseConfiguration.Extended(basePath = dir))
            },
        ),
    )
}

/** Where the database file lives. Exposed so the spike UI can show it. */
@OptIn(ExperimentalForeignApi::class)
fun applicationSupportDirectory(): String {
    val fm = NSFileManager.defaultManager
    val base = fm.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        ?: error("no Application Support directory")
    val dir = base.URLByAppendingPathComponent("BackLog", isDirectory = true)!!
    fm.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return dir.path!!
}
