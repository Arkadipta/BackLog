package com.backlog.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * The app's database, `backlog.db` in its private database directory,
 * through the platform's own SQLite (3.32 on API 31). Created or migrated to
 * [BackLogDatabase.Schema] on first use. [name] null opens a fresh in-memory
 * database, for tests and probes.
 */
internal fun createAndroidDriver(context: Context, name: String? = DATABASE_FILE_NAME): AndroidSqliteDriver =
    AndroidSqliteDriver(
        schema = BackLogDatabase.Schema,
        context = context,
        name = name,
        callback = object : AndroidSqliteDriver.Callback(BackLogDatabase.Schema) {
            // WAL lets the UI's Flow queries read while a sync batch writes.
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.enableWriteAheadLogging()
            }
        },
    )

/** An empty in-memory connection, for [probeFullText]. */
internal fun createAndroidProbeDriver(context: Context): AndroidSqliteDriver =
    AndroidSqliteDriver(EmptySchema, context, name = null)
