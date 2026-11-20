package com.backlog.time

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * The only source of wall-clock time in shared code. Times are UTC epoch
 * milliseconds, the unit every stored instant uses.
 */
internal interface Clock {
    fun nowMillis(): Long
}

/** The device's wall clock. */
internal object SystemClock : Clock {
    @OptIn(ExperimentalTime::class)
    override fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}

/**
 * A clock that only moves when told to. It may be set backwards, which is how
 * tests simulate a device clock being corrected.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class TestClock(startMillis: Long = 0L) : Clock {
    private val millis = AtomicLong(startMillis)

    override fun nowMillis(): Long = millis.load()

    fun set(epochMillis: Long) = millis.store(epochMillis)

    fun advance(by: Duration) {
        millis.addAndFetch(by.inWholeMilliseconds)
    }

    fun advanceMillis(by: Long) {
        millis.addAndFetch(by)
    }
}
