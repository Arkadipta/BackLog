package com.backlog.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import co.touchlab.sqliter.DatabaseConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * The app's database, `backlog.db` in [directory], through the system SQLite
 * (the app links `-lsqlite3`). sqliter opens it in WAL mode. Created or
 * migrated to [BackLogDatabase.Schema] on first use.
 */
internal fun createMacDriver(directory: String = applicationSupportDirectory()): NativeSqliteDriver =
    NativeSqliteDriver(
        schema = BackLogDatabase.Schema,
        name = DATABASE_FILE_NAME,
        onConfiguration = { config ->
            config.copy(extendedConfig = config.extendedConfig.copy(basePath = directory))
        },
    )

/** An empty in-memory connection, for [probeFullText]. */
internal fun createMacProbeDriver(): NativeSqliteDriver = inMemoryDriver(EmptySchema)

/**
 * `Application Support/BackLog`, created if missing. In the sandboxed app that
 * is `~/Library/Containers/com.backlog.app/Data/Library/Application Support/BackLog`.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun applicationSupportDirectory(): String {
    val fm = NSFileManager.defaultManager
    val base = fm.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        ?: error("no Application Support directory")
    val dir = base.URLByAppendingPathComponent("BackLog", isDirectory = true)!!
    fm.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return dir.path!!
}
