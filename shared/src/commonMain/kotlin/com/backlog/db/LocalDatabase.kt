package com.backlog.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.backlog.id.NS_BACKLOG
import com.backlog.id.UuidV7Generator
import com.backlog.id.uuidV5
import com.backlog.model.ListColor
import com.backlog.order.FractionalIndex
import com.backlog.sync.Hlc
import com.backlog.time.Clock
import com.backlog.time.SystemClock

/** The file name on every platform. */
internal const val DATABASE_FILE_NAME = "backlog.db"

/**
 * An open database with its seeds in place and this device's id. Open it with
 * a platform driver whose schema is [BackLogDatabase.Schema], so the driver
 * creates or migrates the file before the first query.
 */
internal class LocalDatabase private constructor(
    val driver: SqlDriver,
    val queries: BackLogDatabase,
    /** A UUIDv7 made on first open and kept in `local_meta`. It is the HLC device id (D11). */
    val deviceId: String,
) {
    companion object {
        fun open(driver: SqlDriver, clock: Clock = SystemClock): LocalDatabase {
            val queries = BackLogDatabase(driver)
            val deviceId = queries.transactionWithResult {
                Seeds.apply(queries)
                queries.localMetaQueries.insertIfAbsent(MetaKeys.DEVICE_ID, UuidV7Generator(clock).next())
                queries.localMetaQueries.selectValue(MetaKeys.DEVICE_ID).executeAsOne()
            }
            check(Hlc.isValidDeviceId(deviceId)) { "stored device id isn't a valid HLC device id: '$deviceId'" }
            return LocalDatabase(driver, queries, deviceId)
        }
    }
}

internal object MetaKeys {
    const val DEVICE_ID = "device_id"
}

/**
 * Rows every device creates for itself with the same ids (D5). Each field's
 * clock is [Hlc.ZERO], so any real edit beats a seed and two seeds merge
 * silently. Seeds never reach `change_log`, and applying them again is a no-op.
 */
internal object Seeds {
    /** `uuidV5(NS_BACKLOG, "inbox")` = 8494b556-8818-5209-99e5-3f3bce5d2b0f (pinned in 1.1's tests). */
    val INBOX_ID: String by lazy { uuidV5(NS_BACKLOG, "inbox") }
    const val INBOX_NAME = "Inbox"

    /**
     * The list's synced fields, seeded at [Hlc.ZERO]. A missing clock also
     * reads as zero, so 1.3's field registry may add fields without a new seed.
     */
    val LIST_FIELDS: List<String> = listOf("name", "color", "icon", "sort_key", "is_inbox", "deleted_at")

    fun apply(queries: BackLogDatabase) {
        queries.seedQueries.insertListIfAbsent(
            id = INBOX_ID,
            name = INBOX_NAME,
            color = ListColor.SKY.token,
            sort_key = FractionalIndex.keyBetween(null, null),
            is_inbox = 1,
        )
        val zero = Hlc.ZERO.encode()
        for (field in LIST_FIELDS) {
            queries.seedQueries.insertFieldClockIfAbsent("list", INBOX_ID, field, zero)
        }
    }
}

/** For probing a connection without creating any tables. */
internal object EmptySchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1
    override fun create(driver: SqlDriver) = QueryResult.Unit
    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: app.cash.sqldelight.db.AfterVersion,
    ) = QueryResult.Unit
}
