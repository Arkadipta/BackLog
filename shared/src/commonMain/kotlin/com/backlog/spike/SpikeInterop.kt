package com.backlog.spike

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.jvm.JvmInline

/*
 * Sample shapes for the 0.2 interop notes (how Kotlin types look from Swift).
 * Nothing in the apps depends on these. Task 1.2 deletes this file.
 */

@JvmInline
value class SpikeId(val value: String)

enum class SpikePriority { NONE, LOW, HIGH }

sealed interface SpikeResult {
    data class Ok(val id: SpikeId, val count: Int) : SpikeResult
    data class Failed(val reason: String) : SpikeResult
    data object Empty : SpikeResult
}

data class SpikeShapes(
    val id: SpikeId,
    val count: Int,
    val epochMillis: Long,
    val optionalMillis: Long?,
    val optionalCount: Int?,
    val flag: Boolean,
    val optionalFlag: Boolean?,
    val tags: List<String>,
    val priority: SpikePriority,
)

class SpikeInterop {
    fun shapes(): SpikeShapes = SpikeShapes(
        id = SpikeId("abc"),
        count = 1,
        epochMillis = 1_700_000_000_000,
        optionalMillis = null,
        optionalCount = 2,
        flag = true,
        optionalFlag = null,
        tags = listOf("a", "b"),
        priority = SpikePriority.HIGH,
    )

    fun result(kind: Int): SpikeResult = when (kind) {
        0 -> SpikeResult.Ok(SpikeId("x"), 3)
        1 -> SpikeResult.Failed("nope")
        else -> SpikeResult.Empty
    }

    fun withDefault(text: String, times: Int = 2): String = text.repeat(times)

    fun takesId(id: SpikeId): String = id.value

    fun counts(): Flow<Int> = flowOf(1, 2, 3)

    fun optionalMillisFlow(): Flow<Long?> = flowOf(1L, null)

    @Throws(IllegalStateException::class)
    suspend fun mayFail(fail: Boolean): Long {
        check(!fail) { "asked to fail" }
        return 42
    }
}

fun spikeTopLevel(): String = "top-level"
