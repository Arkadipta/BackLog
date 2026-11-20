package com.backlog.sync

import com.backlog.time.Clock
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A hybrid logical clock stamp: wall-clock ms, a counter, and the device that
 * made it. Stamps compare by wall time, then counter, then device id (spec:
 * "device id breaks ties").
 *
 * [encode] gives D11's fixed-width string, `<13-digit ms>-<4 hex>-<device>`,
 * whose plain string order equals this order, so SQLite and the Worker can
 * compare stamps with `>`.
 */
internal data class Hlc(val wallMillis: Long, val counter: Int, val deviceId: String) : Comparable<Hlc> {
    init {
        require(wallMillis in 0..MAX_WALL) { "HLC wall time out of range: $wallMillis" }
        require(counter in 0..MAX_COUNTER) { "HLC counter out of range: $counter" }
        require(isValidDeviceId(deviceId)) { "invalid HLC device id: '$deviceId'" }
    }

    override fun compareTo(other: Hlc): Int = when {
        wallMillis != other.wallMillis -> wallMillis.compareTo(other.wallMillis)
        counter != other.counter -> counter.compareTo(other.counter)
        else -> deviceId.compareTo(other.deviceId)
    }

    fun encode(): String =
        wallMillis.toString().padStart(WALL_DIGITS, '0') + '-' +
            counter.toString(16).padStart(COUNTER_DIGITS, '0') + '-' +
            deviceId

    override fun toString(): String = encode()

    companion object {
        const val WALL_DIGITS = 13
        const val COUNTER_DIGITS = 4

        /** The largest wall time that fits in 13 digits (year 2286). */
        const val MAX_WALL: Long = 9_999_999_999_999L
        const val MAX_COUNTER: Int = 0xFFFF

        /**
         * The stamp of seeded values such as the Inbox (D5). Every device seeds
         * with the same stamp, so two seeds are identical and any real edit,
         * which has a wall time above zero, beats them.
         */
        val ZERO = Hlc(0, 0, "0")

        /**
         * Device ids are restricted to `[0-9A-Za-z_-]`. Being ASCII, their
         * UTF-16 order (Kotlin) and byte order (SQLite) agree.
         */
        fun isValidDeviceId(id: String): Boolean =
            id.isNotEmpty() && id.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it == '-' || it == '_' }

        fun decode(s: String): Hlc = requireNotNull(decodeOrNull(s)) { "not an HLC: '$s'" }

        fun decodeOrNull(s: String): Hlc? {
            val prefix = WALL_DIGITS + 1 + COUNTER_DIGITS + 1
            if (s.length <= prefix || s[WALL_DIGITS] != '-' || s[prefix - 1] != '-') return null
            val wallText = s.substring(0, WALL_DIGITS)
            val counterText = s.substring(WALL_DIGITS + 1, prefix - 1)
            if (!wallText.all { it in '0'..'9' }) return null
            // Lower-case only, so each stamp has exactly one encoding.
            if (!counterText.all { it in '0'..'9' || it in 'a'..'f' }) return null
            val deviceId = s.substring(prefix)
            if (!isValidDeviceId(deviceId)) return null
            return Hlc(wallText.toLong(), counterText.toInt(16), deviceId)
        }
    }
}

/**
 * The device's HLC (Kulkarni et al., "Logical Physical Clocks"). Thread-safe.
 *
 * Every stamp it returns is higher than every stamp it returned or received
 * before, whatever the wall clock does. When the counter would pass `ffff`,
 * the wall part moves 1 ms ahead instead (D11).
 *
 * @param last the highest stamp this device has seen, restored from the
 *   database at launch so a restarted clock never goes back.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class HlcClock(
    val deviceId: String,
    private val clock: Clock,
    last: Hlc? = null,
) {
    init {
        require(Hlc.isValidDeviceId(deviceId)) { "invalid HLC device id: '$deviceId'" }
    }

    // (wall ms << 16) | counter. 44 + 16 bits fits in a Long.
    private val state = AtomicLong(if (last == null) 0L else pack(last.wallMillis, last.counter))

    /** The latest stamp, without ticking. */
    fun current(): Hlc = unpack(state.load())

    /** Stamps a local event, such as a field edit. */
    fun tick(): Hlc = update { l, c ->
        val pt = clock.nowMillis()
        if (pt > l) pack(pt, 0) else bump(l, c)
    }

    /** Takes in a remote stamp, so later local stamps are higher than it. */
    fun receive(remote: Hlc): Hlc = update { l, c ->
        val pt = clock.nowMillis()
        val ml = remote.wallMillis
        val mc = remote.counter
        when {
            pt > l && pt > ml -> pack(pt, 0)
            l == ml -> bump(l, maxOf(c, mc))
            l > ml -> bump(l, c)
            else -> bump(ml, mc)
        }
    }

    private inline fun update(next: (wall: Long, counter: Int) -> Long): Hlc {
        while (true) {
            val prev = state.load()
            val new = next(prev ushr 16, (prev and 0xFFFF).toInt())
            if (state.compareAndSet(prev, new)) return unpack(new)
        }
    }

    private fun bump(wall: Long, counter: Int): Long =
        if (counter < Hlc.MAX_COUNTER) pack(wall, counter + 1) else pack(wall + 1, 0)

    private fun pack(wall: Long, counter: Int): Long {
        require(wall in 0..Hlc.MAX_WALL) { "HLC wall time out of range: $wall" }
        return (wall shl 16) or counter.toLong()
    }

    private fun unpack(packed: Long) = Hlc(packed ushr 16, (packed and 0xFFFF).toInt(), deviceId)
}
