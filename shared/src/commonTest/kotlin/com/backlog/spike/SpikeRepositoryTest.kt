package com.backlog.spike

import app.cash.sqldelight.db.SqlDriver
import com.backlog.db.BackLogDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A fresh in-memory database with the schema created, on each platform's test driver. */
expect fun inMemoryDriver(): SqlDriver

class SpikeRepositoryTest {
    @Test
    fun addEmitsNewList() = runTest {
        var clock = 1_000L
        val repo = SpikeRepository.create(inMemoryDriver(), StandardTestDispatcher(testScheduler)) { clock++ }

        assertEquals(emptyList(), repo.notes().first())

        val seen = mutableListOf<List<String>>()
        val job = launch { repo.notes().take(3).toList().mapTo(seen) { list -> list.map { it.text } } }
        testScheduler.advanceUntilIdle()
        repo.add("first")
        testScheduler.advanceUntilIdle()
        repo.add("  second  ")
        repo.add("   ") // ignored
        job.join()

        assertEquals(listOf(emptyList(), listOf("first"), listOf("second", "first")), seen)
    }
}

internal fun createSchema(driver: SqlDriver): SqlDriver = driver.also { BackLogDatabase.Schema.create(it) }
