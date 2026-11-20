package com.backlog.spike

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import com.backlog.db.BackLogDatabase
import com.backlog.db.Spike_note
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Throwaway repository for the 0.2 interop spike: one live query and one write.
 * Task 1.2 deletes it along with the `spike_note` table.
 */
class SpikeRepository internal constructor(
    private val database: BackLogDatabase,
    private val dispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long,
) {
    /** Newest first. Emits again after every write to `spike_note`. */
    fun notes(): Flow<List<Spike_note>> =
        database.spikeNoteQueries.selectAll().asFlow().mapToList(dispatcher)

    suspend fun add(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        withContext(dispatcher) {
            database.spikeNoteQueries.insert(trimmed, nowMillis())
        }
    }

    companion object {
        @OptIn(ExperimentalTime::class)
        internal fun create(
            driver: SqlDriver,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
            nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
        ): SpikeRepository = SpikeRepository(BackLogDatabase(driver), dispatcher, nowMillis)
    }
}
